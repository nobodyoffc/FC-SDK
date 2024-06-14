package server.reward;

import apip.apipData.CidInfo;
import clients.apipClient.DataGetter;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.SumAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import fch.TxCreator;
import feip.feipData.serviceParams.Params;
import clients.apipClient.ApipClient;
import apip.apipData.Fcdsl;
import apip.apipData.Sort;
import apip.apipData.TxInfo;
import fch.ParseTools;
import fch.fchData.*;
import feip.feipData.DataOnChain;
import fch.Inputer;
import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.google.gson.Gson;
import clients.esClient.EsTools;
import config.ApiAccount;
import constants.IndicesNames;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Counter;
import server.Settings;
import server.order.Order;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static constants.FieldNames.*;
import static constants.Values.TRUE;
import static feip.FeipTools.parseFeip;
import static constants.Constants.*;
import static constants.IndicesNames.ORDER;
import static constants.Strings.*;
import static server.Settings.addSidBriefToName;
import static server.reward.RewardManager.getLastRewardInfo;

public class Rewarder {
    private static final Logger log = LoggerFactory.getLogger(Rewarder.class);
    private final ElasticsearchClient esClient;
    private ApipClient apipClient;
    private final JedisPool jedisPool;
    private String account;
    private String lastOrderId;
    private long incomeT=0;
    private long paidSum;
    private Map<String, Long> pendingMap;
    private final String sid;

    private final int recover4Decimal = 10000;

    public Rewarder(String sid,String account,ApipClient apipClient,ElasticsearchClient esClient,JedisPool jedisPool) {
        this.esClient = esClient;
        if(apipClient!=null)this.apipClient=apipClient;
        this.jedisPool = jedisPool;
        this.sid =sid;
        this.account = account;
    }
    public static void checkRewarderParams(String sid, Params params, ElasticsearchClient esClient, JedisPool jedisPool, BufferedReader br) {
        checkRewarderParams(sid,params,null,esClient,jedisPool,br);
    }
    public static void checkRewarderParams(String sid, Params params, @Nullable ApipClient apipClient, @Nullable ElasticsearchClient esClient, JedisPool jedisPool, BufferedReader br) {
        Rewarder rewarder = new Rewarder(sid, params.getAccount(), apipClient, esClient, jedisPool);
        RewardParams rewardParams = Rewarder.getRewardParams(rewarder.sid, rewarder.jedisPool);

        if(rewardParams==null) {
            rewardParams = rewarder.setRewardParameters(br, params.getConsumeViaShare(), params.getOrderViaShare());
            writeRewardParamsToRedis(sid,rewardParams,jedisPool);
        }

        if(rewardParams.getOrderViaShare()==null){
            rewardParams.setOrderViaShare(params.getOrderViaShare());
            writeRewardParamsToRedis(sid,rewardParams,jedisPool);
        }

        if(rewardParams.getConsumeViaShare()==null){
            rewardParams.setConsumeViaShare(params.getConsumeViaShare());
            writeRewardParamsToRedis(sid,rewardParams,jedisPool);
        }

        System.out.println("Check the reward parameters:");
        JsonTools.gsonPrint(rewardParams);
        Menu.anyKeyToContinue(br);

    }

    public RewardReturn doReward(String account, List<ApiAccount> chargedAccountList,byte[] priKey){
        long reservedFee = 100000;
        RewardReturn rewardReturn = new RewardReturn();

        lastOrderId = getLastOrderIdFromEs(esClient);

        if(account ==null){
            rewardReturn.setCode(1);
            return rewardReturn;
        }

        Map<String, RewardInfo> unpaidRewardInfoMap = getUnpaidRewardInfoMapFromEs(esClient);

        checkPayment(apipClient,esClient,unpaidRewardInfoMap);

        if(incomeT==0){
            incomeT = makeIncomeT(lastOrderId,esClient);
        }else{
            System.out.println("Set income: "+incomeT);
            long sum = makeIncomeT(lastOrderId,esClient);
            if(sum<incomeT){
                System.out.println("Notice: New order sum is "+sum+", while you are paying "+incomeT+".");
            }
            if(lastOrderId==null){
                lastOrderId = Objects.requireNonNull(getLastRewardInfo(sid,esClient)).getRewardId();
                if(lastOrderId==null){
                    rewardReturn.setCode(2);
                    return rewardReturn;
                }
            }
        }

        if(incomeT==0){
            rewardReturn.setCode(3);
            log.debug("No income.");
            return rewardReturn;
        }

        RewardParams rewardParams = getRewardParams(sid, jedisPool);

        if(rewardParams == null){
            rewardReturn.setCode(4);
            log.debug(RewardReturn.codeToMsg(4));
            return rewardReturn;
        }


        incomeT -= reservedFee; // for tx fee
        long apiCost = 0;
        if(chargedAccountList!=null){
            apiCost = sumApiCost(chargedAccountList);
            incomeT -= apiCost;
        }

        RewardInfo rewardInfo = makeRewardInfo(incomeT, rewardParams);
        if(apiCost!=0)rewardInfo.setApiCost(apiCost);

        if(rewardInfo==null){
            rewardReturn.setCode(5);
            log.debug(RewardReturn.codeToMsg(5));
            return rewardReturn;
        }

        log.debug("Made a rewardInfo. The sum of payment is {}.",calcSumPay(rewardInfo));

        List<SendTo> sendToList = new ArrayList<>();
        addPaymentsToSendToList(rewardInfo.getCostList(), sendToList);
        addPaymentsToSendToList(rewardInfo.getOrderViaList(),sendToList);
        addPaymentsToSendToList(rewardInfo.getConsumeViaList(),sendToList);
        addPaymentsToSendToList(rewardInfo.getBuilderList(),sendToList);

        String opReturnJson = makeRewardOpReturn(rewardInfo);

        long fee = TxCreator.calcTxSize(0, sendToList.size(), opReturnJson.length());

        /*
        1. check the balance
        2. get cash list
        3. create tx
        4. sign tx
        5. broadcast
        6. update reword state
         */

        if(apipClient!=null){
            apipClient.cidInfoByIds(new String[]{account});
            Object data = apipClient.checkApipV1Result();
            if(data==null)return null;
            Map<String, CidInfo> result= DataGetter.getCidInfoMap(data);
            long balance = result.get(account).getBalance();

            if(balance<(rewardInfo.getRewardT()+fee)){
                rewardReturn.setCode(6);
                log.error(RewardReturn.codeToMsg(6));
                return null;
            }
        }else {
            SearchRequest.Builder srb = new SearchRequest.Builder();
            srb.index(IndicesNames.CASH);

            TermQuery.Builder tb = QueryBuilders.term();
            tb.field(OWNER).value(account);
            TermQuery tq = tb.build();

            TermQuery.Builder tb1 = QueryBuilders.term();
            tb1.field(VALID).value(TRUE);
            TermQuery tq1 = tb1.build();

            List<Query> querieList = new ArrayList<>();
            querieList.add(new Query.Builder().term(tq).build());
            querieList.add(new Query.Builder().term(tq1).build());

            BoolQuery.Builder bu = QueryBuilders.bool();
            bu.must(querieList);

            BoolQuery boolQuery = bu.build();

            SumAggregation.Builder as = AggregationBuilders.sum();
            as.field(VALUE);

            srb.query(new Query.Builder().bool(boolQuery).build());
            srb.aggregations(SUM,new Aggregation.Builder().sum(as.build()).build());


            try {
                esClient.search(srb.build(),Cash.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


        }



//
//
//
//        String urlHead = Constants.UrlHead_CID_CASH;
//
//        System.out.println("Input the opreturn message. Enter to ignore:");
//        String msg = appTools.Inputer.inputString(br);
//
//        long fee = TxCreator.calcTxSize(0, sendToList.size(), msg.length());
//
//
//        System.out.println("Getting cashes from " + urlHead + " ...");
//
//        apipClientData = WalletAPIs.cashValidForPayPost(urlHead, sender, sum + ((double) fee / COIN_TO_SATOSHI), initApiAccount.getVia(), sessionKey);
//        if (apipClientData == null || apipClientData.checkResponse() != 0) {
//            System.out.println("Failed to get cashes." + apipClientData.getMessage() + apipClientData.getResponseBody().getData());
//            return;
//        }
//
//        List<Cash> cashList = DataGetter.getCashList(apipClientData.getResponseBody().getData());
//
//        String txSigned = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, msg);
//
//        System.out.println("Signed tx:");
//        Shower.printUnderline(10);
//        System.out.println(txSigned);
//        Shower.printUnderline(10);
//
//        System.out.println("Broadcast with " + urlHead + " ...");
//        apipClientData = WalletAPIs.broadcastTxPost(urlHead, txSigned, initApiAccount.getVia(), sessionKey);
//        if (apipClientData.checkResponse() != 0) {
//            System.out.println(apipClientData.getCode() + ": " + apipClientData.getMessage());
//            if (apipClientData.getResponseBody().getData() != null)
//                System.out.println(apipClientData.getResponseBody().getData());
//            return;
//        }

//
//        AffairMaker affairMaker = new AffairMaker(sid,account, rewardInfo,esClient,jedisPool);
//
//        String affairSignTxJson = affairMaker.makeAffair();
//
//        pendingMap = affairMaker.getPendingMapFromRedis(jedisPool);
//        if(pendingMap!=null && !pendingMap.isEmpty()) {
//            if (!backUpPending()) log.debug("Backup pendingMap failed.");
//        }
//        if(!makeSignTxAffairHtml(affairSignTxJson)){
//            rewardReturn.setCode(5);
//            rewardReturn.setMsg("Save affairSignTxJson to tomcat directory failed. Check tomcat directory.");
//            return rewardReturn;
//        }
//
//        if(!backUpRewardInfo(rewardInfo,esClient)){
//            rewardReturn.setCode(6);
//            rewardReturn.setMsg("BackUp payment failed. Check ES.");
//            return rewardReturn;
//        }
//        rewardReturn.setCode(0);
        return rewardReturn;
    }

    private static String makeRewardOpReturn(RewardInfo rewardInfo) {
        String opReturnJson;
        DataOnChain dataOnChain = new DataOnChain();
        dataOnChain.setType(FBBP);
        dataOnChain.setSn("1");
        dataOnChain.setVer("1");

        RewardData rewardData = new RewardData();

        rewardData.setOp(REWARD);
        rewardData.setRewardId(rewardInfo.getRewardId());
        dataOnChain.setData(rewardData);

        opReturnJson = JsonTools.getString(dataOnChain);
        return opReturnJson;
    }

    private static void addPaymentsToSendToList(List<Payment> paymentList, List<SendTo> sendToList) {
        for(Payment payment: paymentList){
            SendTo sendTo = new SendTo();
            sendTo.setFid(payment.getFid());
            sendTo.setAmount(payment.getAmount().doubleValue());
            sendToList.add(sendTo);
        }
    }

    private static long sumApiCost(List<ApiAccount> chargedAccountList) {
        long apiCost = 0;
        for(ApiAccount apiAccount: chargedAccountList){
            for(String key:apiAccount.getPayments().keySet()){
                double paid = apiAccount.getPayments().get(key);
                apiCost += ParseTools.coinToSatoshi(paid);
            }
        }
        return apiCost;
    }

    public RewardReturn doReward(String account){
        long reservedFee = 100000;
        RewardReturn rewardReturn = new RewardReturn();
        lastOrderId = getLastOrderIdFromEs(esClient);

        if(account ==null){
            rewardReturn.setCode(2);
            rewardReturn.setMsg("Get account failed. Check the service parameters in redis.");
            return rewardReturn;
        }

        Map<String, RewardInfo> unpaidRewardInfoMap = getUnpaidRewardInfoMapFromEs(esClient);

        checkPayment(apipClient,esClient,unpaidRewardInfoMap);

        if(incomeT==0){
            incomeT = makeIncomeT(lastOrderId,esClient);
        }else{
            System.out.println("Set income: "+incomeT);
            long sum = makeIncomeT(lastOrderId,esClient);
            if(sum<incomeT){
                System.out.println("Notice: New order sum is "+sum+", while you are paying "+incomeT+".");
            }
            if(lastOrderId==null){
                lastOrderId = Objects.requireNonNull(getLastRewardInfo(sid,esClient)).getRewardId();
                if(lastOrderId==null){
                    rewardReturn.setCode(2);
                    rewardReturn.setMsg("Get last order id failed.");
                    return rewardReturn;
                }
            }
        }

        if(incomeT==0){
            rewardReturn.setCode(2);
            rewardReturn.setMsg("No income.");
            log.debug("No income.");
            return rewardReturn;
        }

        RewardParams rewardParams = getRewardParams(sid, jedisPool);

        if(rewardParams == null){
            rewardReturn.setCode(3);
            rewardReturn.setMsg("Get reward parameters failed. Check redis.");
            log.debug("Get reward parameters failed. Check redis.");
            return rewardReturn;
        }


        incomeT -= reservedFee; // for tx fee
        RewardInfo rewardInfo = makeRewardInfo(incomeT, rewardParams);
        if(rewardInfo==null){
            rewardReturn.setCode(4);
            rewardReturn.setMsg("Making rewardInfo wrong.");
            log.debug("Making rewardInfo wrong.");
            return rewardReturn;
        }

        log.debug("Made a rewardInfo. The sum of payment is {}.",calcSumPay(rewardInfo));

        AffairMaker affairMaker = new AffairMaker(sid,account, rewardInfo,esClient,jedisPool);

        String affairSignTxJson = affairMaker.makeAffair();

        pendingMap = affairMaker.getPendingMapFromRedis(jedisPool);
        if(pendingMap!=null && !pendingMap.isEmpty()) {
            if (!backUpPending()) log.debug("Backup pendingMap failed.");
        }
        if(!makeSignTxAffairHtml(affairSignTxJson)){
            rewardReturn.setCode(5);
            rewardReturn.setMsg("Save affairSignTxJson to tomcat directory failed. Check tomcat directory.");
            return rewardReturn;
        }

        if(!backUpRewardInfo(rewardInfo,esClient)){
            rewardReturn.setCode(6);
            rewardReturn.setMsg("BackUp payment failed. Check ES.");
            return rewardReturn;
        }
        rewardReturn.setCode(0);
        return rewardReturn;
    }

    private boolean backUpPending() {
        Map<String ,String > pendingStrMap = new HashMap<>();
        for(String key: pendingMap.keySet()){
            String amountStr = String.valueOf(pendingMap.get(key));
            pendingStrMap.put(key,amountStr);
        }
        try(Jedis jedis = jedisPool.getResource()) {
            jedis.hmset(addSidBriefToName(sid,REWARD_PENDING_MAP),pendingStrMap);
            return true;
        }catch (Exception e){
            log.error("Write pending map into redis wrong.");
            return false;
        }
    }

    private void checkPayment(ApipClient apipClient, ElasticsearchClient esClient, Map<String, RewardInfo> unpaidRewardInfoMap)  {

        System.out.println("Check payment.");
        if(unpaidRewardInfoMap==null || unpaidRewardInfoMap.size()==0)return;
        List<OpReturn> opReturnList;
        if(apipClient!=null) {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addNewQuery().addNewTerms().addNewFields(SIGNER).addNewValues(account);
            fcdsl.addSize(2000);
            fcdsl.addNewSort(HEIGHT, DESC);
            opReturnList = apipClient.opReturnSearch(fcdsl);
        }else {
            try {
                opReturnList = opReturnSearchFromEs(esClient);
            } catch (IOException e) {
                log.debug("Failed to check payment:"+e.getMessage());
                return;
            }
        }

        if(opReturnList==null||opReturnList.isEmpty())return;
        DataOnChain feip;
        RewardData rewardData;
        Gson gson = new Gson();

        for(OpReturn opReturn :opReturnList){
            if(opReturn==null)continue;
            String txId = opReturn.getTxId();
            try {
                feip = parseFeip(opReturn,log);
            }catch (Exception e){
                System.out.println(txId+" isn't reward.");
                continue;
            }
            
            if(feip!=null && "FBBP".equals(feip.getType())&&"1".equals(feip.getSn())){
                try {
                    rewardData = gson.fromJson(gson.toJson(feip.getData()), RewardData.class);
                }catch (Exception e){
                    continue;
                }
                RewardInfo rewardInfo = unpaidRewardInfoMap.get(rewardData.getRewardId());
                if( rewardInfo!=null) {

                    RewardState rewardState = checkPaymentState(apipClient,txId,rewardInfo);

                    updateRewardInfo(this.esClient,txId,rewardData.getRewardId(),rewardState);

                    unpaidRewardInfoMap.remove(rewardData.getRewardId());
                    log.debug("Find reward just paid: {}",rewardData.getRewardId());
                }
                if(unpaidRewardInfoMap.size()==0){
                    log.debug("All reward paid.");
                    return;
                }
            }
        }
        if(unpaidRewardInfoMap.size()!=0) {
            System.out.println(unpaidRewardInfoMap.size() + " unpaid rewards: ");
            for (String id : unpaidRewardInfoMap.keySet()) {
                RewardInfo re = unpaidRewardInfoMap.get(id);
                String time = ParseTools.convertTimestampToDate(re.getTime());
                double amount = (double) re.getRewardT() /FchToSatoshi;
                System.out.println(time +" "+ amount+"f "+re.getRewardId());
            }
        }
    }

    private List<OpReturn> opReturnSearchFromEs(ElasticsearchClient esClient) throws IOException {
        List<FieldValue> valueList = new ArrayList<>();
        valueList.add(FieldValue.of(account));

        SearchResponse<OpReturn> result = esClient.search(s -> s.index(IndicesNames.OPRETURN)
                .query(q -> q.terms(t -> t.field(SIGNER).terms(t1 -> t1.value(valueList))))
                .size(2000)
                .sort(so -> so.field(f -> f.field(HEIGHT).order(SortOrder.Desc))), OpReturn.class);

        List<OpReturn> opReturnList = new ArrayList<>();
        if (result ==null || result.hits().total()==null||result.hits().total().value() == 0) return null;
        for (Hit<OpReturn> hit : result.hits().hits()) {
            opReturnList.add(hit.source());
        }
        return opReturnList;
    }

    private RewardState checkPaymentState(ApipClient apipClient, String txId, RewardInfo rewardInfo) {

        Map<String, SendTo> sendToMapWithoutDust = makeNoDustSendToMap(rewardInfo);
        Map<String, SendTo> sentMap = calcSentMap(apipClient,txId);
        return getPaymentState(sendToMapWithoutDust,sentMap);
    }

    private RewardState getPaymentState(Map<String, SendTo> sendToMapWithoutDust, Map<String, SendTo> sentMap) {
        for(String fid:sendToMapWithoutDust.keySet()){
            double sendToAmount = sendToMapWithoutDust.get(fid).getAmount();
            double sentAmount = sentMap.get(fid).getAmount();
            if(sentAmount!=sendToAmount){
                System.out.println("Difference found. Owe to send: "+sendToAmount+" Paid:"+sentAmount);
                return RewardState.paidRevised;
            }
        }
        return RewardState.paid;
    }

    private Map<String, SendTo> calcSentMap(ApipClient apipClient, String txId) {

        if(txId==null)return null;
        Map<String, TxInfo> txHasMap = apipClient.txByIds(new String[]{txId});
        TxInfo txInfo = null;
        if(txHasMap!=null)txInfo = txHasMap.get(txId);
        if(txInfo==null)return null;

        Map<String, SendTo> sentToMap = new HashMap<>();
        for(CashMark cashMark: txInfo.getIssuedCashes()){
            SendTo sendTo = new SendTo();
            sendTo.setFid(cashMark.getOwner());
            sendTo.setAmount((double)cashMark.getValue()/FchToSatoshi);
            sentToMap.put(cashMark.getOwner(),sendTo);
        }
        return sentToMap;
    }

    private Map<String, SendTo> makeNoDustSendToMap(RewardInfo rewardInfo) {
        Map<String, SendTo> sendToMap = AffairMaker.makeSendToMap(rewardInfo);
        sendToMap.entrySet().removeIf(entry -> entry.getValue().getAmount() < MinPayValue);
        return sendToMap;
    }


    private void updateRewardInfo(ElasticsearchClient esClient, String txId, String rewardId, RewardState rewardState) {

        try {
            Map<String, JsonData> paramMap = new HashMap<>();
            paramMap.put("sendTxId",JsonData.of(txId));
            paramMap.put("state",JsonData.of(rewardState.name()));

            esClient.update(u->u
                            .index(addSidBriefToName(sid,REWARD).toLowerCase())
                            .id(rewardId)
                            .script(s->s
                                    .inline(in->in
                                            .source("ctx._source.state = params.state; ctx._source.txId = params.sendTxId")
                                            .params(paramMap))
                                    )
                    ,Void.class);
        } catch (IOException e) {
            log.debug("Update reward info wrong.",e);
        }
    }


    public static RewardParams getRewardParams(String sid, JedisPool jedisPool) {
        RewardParams rewardParams = new RewardParams();
        try(Jedis jedis = jedisPool.getResource()) {
            try {
                Map<String, String> shareMap = jedis.hgetAll(Settings.addSidBriefToName(sid,BUILDER_SHARE_MAP));
                rewardParams.setBuilderShareMap(shareMap);
                if (shareMap.isEmpty()) return null;
            } catch (Exception e) {
                System.out.println("Get builder's shares from redis failed. It's required for rewarding.");
                return null;
            }

            try {
                Map<String, String> costMap = jedis.hgetAll(Settings.addSidBriefToName(sid,COST_MAP));
                rewardParams.setCostMap(costMap);

                rewardParams.setOrderViaShare(jedis.hget(Settings.addSidBriefToName(sid,PARAMS), ORDER_VIA_SHARE));

                rewardParams.setConsumeViaShare(jedis.hget(Settings.addSidBriefToName(sid,PARAMS), CONSUME_VIA_SHARE));

            } catch (Exception ignore) {
            }
        }
        return rewardParams;
    }

    public String getLastOrderIdFromEs(ElasticsearchClient esClient) {
        SearchResponse<RewardInfo> result;
        try {
            result = esClient.search(s -> s
                            .index(addSidBriefToName(sid,REWARD).toLowerCase())
                            .size(1)
                            .sort(so -> so.field(f -> f
                                    .field(TIME)
                                    .order(SortOrder.Desc)))
                    , RewardInfo.class);
        } catch (IOException e) {
            log.debug("Read last reward info from ES wrong.");
            return null;
        }

        if(result.hits().hits().size()==0){
            log.debug("No reward info found.");
            return null;
        }

        return result.hits().hits().get(0).id();
    }


    public String getLastOrderIdFromRedis() {
        try(Jedis jedis = jedisPool.getResource()) {
            String lastReward = jedis.get(addSidBriefToName(sid,LAST_REWARD));
            if(lastReward==null)return null;
            Gson gson = new Gson();
            RewardInfo rewardInfo = gson.fromJson(lastReward,RewardInfo.class);
            if(rewardInfo==null)return null;
            return rewardInfo.getRewardId();
        }
    }

    private Map<String, RewardInfo> getUnpaidRewardInfoMapFromEs(ElasticsearchClient esClient) {
        SearchResponse<RewardInfo> result;
        try {
            result = esClient.search(s -> s
                            .index(addSidBriefToName(sid,REWARD).toLowerCase())
                            .query(q->q
                                    .term(t->t
                                            .field(STATE)
                                            .value(UNPAID)))
                            .size(200)
                            .sort(so -> so.field(f -> f
                                    .field(BEST_HEIGHT)
                                    .order(SortOrder.Desc)))
                    , RewardInfo.class);
        } catch (IOException e) {
            log.debug("Read unpaid reward info from ES wrong.");
            return null;
        }

        Map<String,RewardInfo> unpaidRewardInfoMap = new HashMap<>();

        if(result.hits().hits().size()==0){
            log.debug("No unpaid reward info found.");
            return null;
        }

        for(Hit<RewardInfo> hit: result.hits().hits()){
            RewardInfo re = hit.source();
            if(re!=null)unpaidRewardInfoMap.put(re.getRewardId(),re);
        }

        return unpaidRewardInfoMap;
    }

    public long makeIncomeT(String lastOrderId, ElasticsearchClient esClient) {
        List<SortOptions> sortOptionsList = Sort.makeHeightTxIndexSort();

        long sum = 0;
        SearchResponse<Order> result;
        try {
            result = esClient.search(s -> s
                            .index(addSidBriefToName(sid,ORDER).toLowerCase())
                            .sort(sortOptionsList)
                            .size(EsTools.READ_MAX)
                    , Order.class);
        } catch (Exception e) {
            log.error("Get order list wrong.",e);
            return 0;
        }

        if(result==null||result.hits().hits().isEmpty()){
            log.debug("No any order. Check ES.");
            return 0;
        }
        List<Hit<Order>> hitList = result.hits().hits();
        List<String> last = hitList.get(hitList.size() - 1).sort();

        for(Hit<Order> hit : hitList){
            Order order = hit.source();

            if(order==null)continue;

            if(lastOrderId!=null && lastOrderId.equals(order.getOrderId())){
                this.lastOrderId = hitList.get(0).source().getOrderId();
                return sum;
            }
            sum += order.getAmount();
        }

        while (hitList.size()>=EsTools.READ_MAX) {
            try {
                List<String> finalLast = last;
                result = esClient.search(s -> s
                                .index(addSidBriefToName(sid,ORDER).toLowerCase())
                                .sort(sortOptionsList)
                                .size(EsTools.READ_MAX)
                                .searchAfter(finalLast)
                        , Order.class);
            } catch (IOException e) {
                log.error("Get order list wrong.", e);
                return 0;
            }
            if(result==null||result.hits().hits().size()==0){
                log.debug("No any order. Check ES.");
                return 0;
            }
            hitList = result.hits().hits();
            last = hitList.get(hitList.size() - 1).sort();

            for(Hit<Order> hit : hitList){
                Order order = hit.source();
                if(order==null)continue;
                if(lastOrderId!=null && lastOrderId.equals(order.getOrderId())){
                    this.lastOrderId = hitList.get(0).source().getOrderId();
                    return sum;
                }
                sum += order.getAmount();
            }
        }

        if(hitList.size()>0){
            this.lastOrderId = hitList.get(0).source().getOrderId();
        }
        return sum;
    }

    public RewardInfo makeRewardInfo(long incomeT, RewardParams rewardParams) {

        RewardInfo rewardInfo = new RewardInfo();

        ArrayList<Payment> builderRewardList= new ArrayList<>();
        ArrayList<Payment> orderViaRewardList= new ArrayList<>();
        ArrayList<Payment> consumeViaRewardList= new ArrayList<>();
        ArrayList<Payment> costList= new ArrayList<>();

        Map<String, String> orderViaMap;
        Map<String, String> consumeViaMap;
        Map<String, String> builderShareMap;
        Map<String, String> costMap;
        try(Jedis jedis = jedisPool.getResource()) {
            try {
                orderViaMap = jedis.hgetAll(Settings.addSidBriefToName(sid,ORDER_VIA));
                consumeViaMap = jedis.hgetAll(Settings.addSidBriefToName(sid,CONSUME_VIA));
                builderShareMap = jedis.hgetAll(Settings.addSidBriefToName(sid,BUILDER_SHARE_MAP));
                costMap = jedis.hgetAll(Settings.addSidBriefToName(sid,COST_MAP));
            } catch (Exception e) {
                log.error("Get {},{},{} or {} from redis wrong.", Settings.addSidBriefToName(sid,ORDER_VIA), Settings.addSidBriefToName(sid,CONSUME_VIA), Settings.addSidBriefToName(sid,BUILDER_SHARE_MAP), Settings.addSidBriefToName(sid,COST_MAP), e);
                return null;
            }

            Integer orderViaShare = parseViaShare(rewardParams, Settings.addSidBriefToName(sid,ORDER_VIA));
            Integer consumeViaShare = parseViaShare(rewardParams, Settings.addSidBriefToName(sid,CONSUME_VIA));
            if (orderViaShare < 0) return null;
            if (consumeViaShare < 0) return null;

            orderViaRewardList = makeViaPayList(orderViaMap, orderViaShare, Settings.addSidBriefToName(sid,ORDER_VIA));
            consumeViaRewardList = makeViaPayList(consumeViaMap, consumeViaShare, Settings.addSidBriefToName(sid, CONSUME_VIA));

            costList = makeCostPayList(costMap, incomeT);
            builderRewardList = makeBuilderPayList(builderShareMap, incomeT);

            rewardInfo.setOrderViaList(orderViaRewardList);
            rewardInfo.setConsumeViaList(consumeViaRewardList);
            rewardInfo.setBuilderList(builderRewardList);
            rewardInfo.setCostList(costList);

            rewardInfo.setRewardT(paidSum);
            rewardInfo.setState(RewardState.unpaid);
            rewardInfo.setRewardId(lastOrderId);

            rewardInfo.setTime(System.currentTimeMillis());
            rewardInfo.setBestHeight(jedis.get(BEST_HEIGHT));
        }
        return rewardInfo;
    }

    public long calcSumPay(RewardInfo rewardInfo){
        long sum = 0;
        ArrayList<Payment> consumeList = rewardInfo.getConsumeViaList();
        ArrayList<Payment> orderList = rewardInfo.getOrderViaList();
        ArrayList<Payment> costList = rewardInfo.getCostList();
        ArrayList<Payment> builderList = rewardInfo.getBuilderList();
        ArrayList<Payment> all = new ArrayList<>();
        all.addAll(consumeList);
        all.addAll(orderList);
        all.addAll(costList);
        all.addAll(builderList);
        for(Payment payment: all){
            sum+=payment.getAmount();
        }
        return sum;
    }

    private ArrayList<Payment> makeCostPayList(Map<String, String> costMap, long income) {
        long costTotal = 0;
        Map<String,Long> costAmountMap = new HashMap<>();
        for(String fid: costMap.keySet()) {
            long amount;
            try {
                amount = (long) (Float.parseFloat(costMap.get(fid))*FchToSatoshi);
            } catch (Exception e) {
                log.error("Get cost of {} from redis wrong.", fid, e);
                return null;
            }
            costTotal += amount;
            costAmountMap.put(fid,amount);
        }
        int payPercent = (int) (recover4Decimal*(income-paidSum)/costTotal);
        if(payPercent>recover4Decimal)payPercent=recover4Decimal;
        return payCost(costAmountMap,payPercent);
    }
    private ArrayList<Payment> payCost(Map<String, Long> costAmountMap, int payPercent) {
        ArrayList<Payment> costList = new ArrayList<>();
        for (String fid : costAmountMap.keySet()) {
            Long amount = costAmountMap.get(fid);
            Payment payDetail = new Payment();
            payDetail.setFid(fid);
            payDetail.setFixed(amount);
            long finalPay = amount*payPercent/recover4Decimal;
            payDetail.setAmount(finalPay);
            paidSum+=finalPay;
            costList.add(payDetail);
        }
        return costList;
    }


    private ArrayList<Payment> makeBuilderPayList(Map<String, String> builderShareMap, long incomeT) {
        long builderSum = incomeT-paidSum;
        ArrayList<Payment> builderList = new ArrayList<>();
        for (String builder : builderShareMap.keySet()) {
            int share;
            try {
                share = (int) (Float.parseFloat(builderShareMap.get(builder))*recover4Decimal);
            }catch (Exception ignore){
                log.error("Get builder share of {} from redis wrong.",builder);
                return null;
            }
            long amount = builderSum*share/recover4Decimal;

            Payment payDetail = new Payment();
            payDetail.setFid(builder);
            payDetail.setShare(share);
            payDetail.setAmount(amount);
            builderList.add(payDetail);
        }
        paidSum += builderSum;
        return builderList;
    }

    private ArrayList<Payment> makeViaPayList(Map<String, String> viaMap, Integer viaShare, String orderVia) {
        ArrayList<Payment> viaPayDetailList = new ArrayList<>();
        for(String via: viaMap.keySet()){
            long amount;
            try {
                amount = viaShare *Long.parseLong(viaMap.get(via))/recover4Decimal;
                Payment payDetail = new Payment();
                payDetail.setFid(via);
                payDetail.setAmount(amount);
                payDetail.setShare(viaShare);
                viaPayDetailList.add(payDetail);

                paidSum += amount;

            }catch (Exception e){
                log.debug("Make {} of {} wrong.",via,orderVia,e);
            }
        }
        return viaPayDetailList;
    }

    private Integer parseViaShare(RewardParams rewardParams, String orderVia) {
        int viaShare;
        try {
            viaShare = (int) (Float.parseFloat(rewardParams.getOrderViaShare())*recover4Decimal);
        }catch (Exception e){
            e.printStackTrace();
            log.error("Parsing {} from redis wrong.",orderVia+"Share");
            throw new RuntimeException();
//            return -1;
        }
        return viaShare;
    }

    private boolean makeSignTxAffairHtml(String signTxAffairJson) {
        String tomcatBashPath = null;
        try(Jedis jedis = jedisPool.getResource()) {
            tomcatBashPath = jedis.hget(CONFIG, TOMCAT_BASE_PATH);
            if (!tomcatBashPath.endsWith("/")) tomcatBashPath += "/";
        }catch (Exception e){
            log.error("Redis wrong.");
        }
        String directoryPath = tomcatBashPath + REWARD;
        String fileName = REWARD_HTML_FILE;

        try {
            // Create the directory if it doesn't exist
            File directory = new File(directoryPath);
            if (!directory.exists()) {
                boolean success = directory.mkdirs();
                if (!success) {
                    log.debug("Failed to create directory: " + directoryPath);
                    return false;
                }
            }

            // Create the file if it doesn't exist
            File file = new File(directory, fileName);
            if (!file.exists()) {
                boolean success = file.createNewFile();
                if (!success) {
                    log.debug("Failed to create file: " + fileName);
                    return false;
                }
            }

            // Write data to the file
            return makeHtml(signTxAffairJson, file);
        } catch (IOException e) {
            log.error("An error occurred when writing affair to file: " + e.getMessage());
        }
        return false;
    }


        private boolean makeHtml(String jsonString,File file) {
        String htmlString = "<html>\n" +
                "<head>\n" +
                "    <title>JSON Copier</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <p id='jsonText'>" + jsonString + "</p>\n" +
                "    <button onclick='copyToClipboard()'>Copy</button>\n" +
                "    <script>\n" +
                "        function copyToClipboard() {\n" +
                "            var text = document.getElementById('jsonText').innerText;\n" +
                "            var el = document.createElement('textarea');\n" +
                "            el.value = text;\n" +
                "            el.setAttribute('readonly', '');\n" +
                "            el.style = {position: 'absolute', left: '-9999px'};\n" +
                "            document.body.appendChild(el);\n" +
                "            el.select();\n" +
                "            document.execCommand('copy');\n" +
                "            document.body.removeChild(el);\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(htmlString);
        } catch (IOException e) {
            log.error("Write signTxAffairJson into html wrong. Check tomcat.");
            return false;
        }
        log.debug("SignTxAffairJson was wrote into html.");
        return true;
    }

    private boolean backUpRewardInfo(RewardInfo rewardInfo, ElasticsearchClient esClient) {

        try {
            esClient.index(i->i.index(addSidBriefToName(sid,REWARD).toLowerCase()).id(rewardInfo.getRewardId()).document(rewardInfo));
        } catch (IOException e) {
            log.error("Backup rewardInfo wrong. Check ES.",e);
            return false;
        }
        log.debug("Backup rewardInfo into ES success. BestHeight.");

        JsonTools.writeObjectToJsonFile(rewardInfo,REWARD_HISTORY_FILE,true);

        log.debug("Backup rewardInfo into "+REWARD_HISTORY_FILE+" success. BestHeight {}.",rewardInfo.getBestHeight());
        return true;
    }

    public RewardParams setRewardParameters(BufferedReader br, String consumeViaShare, String orderViaShare) {

        System.out.println("Set reward parameters. \n\tInput numbers like '1.23456789' for an amount of FCH or '0.1234' for a share which means '12.34%'.");
        System.out.println("\tThe reward will be executed once every 10 days. So, the cost is also for 10 days.");
        RewardParams rewardParams = getRewardParams(sid, jedisPool);

        if(rewardParams==null)rewardParams = new RewardParams();
//        Params params = Starter.myService.getParams();
        Double share;
        if(consumeViaShare==null){
            System.out.println("Set consumeViaShare(0~1)");
            share = Inputer.inputGoodShare(br);
            if (share != null) {
                consumeViaShare = String.valueOf(share);
                rewardParams.setConsumeViaShare(consumeViaShare);
            }
        }

        if(orderViaShare==null) {
            System.out.println("Set orderViaShare(0~1)");
            share = Inputer.inputGoodShare(br);
            if (share != null) {
                orderViaShare = String.valueOf(share);
                rewardParams.setOrderViaShare(orderViaShare);
            }
        }

        Map<String, String> costMap = Inputer.inputGoodFidValueStrMap(br, Settings.addSidBriefToName(sid, COST_MAP), false);
        if (costMap != null) {
            rewardParams.setCostMap(costMap);
        }

        Map<String, String> builderShareMap;
        while(true) {
            builderShareMap = Inputer.inputGoodFidValueStrMap(br, Settings.addSidBriefToName(sid, BUILDER_SHARE_MAP),true);

            if(builderShareMap==null ||builderShareMap.isEmpty()){
                System.out.println("BuilderShareMap can't be empty.");
                continue;
            }
            if(!Menu.isFullShareMap(builderShareMap)) continue;
            rewardParams.setBuilderShareMap(builderShareMap);
            break;
        }

        writeRewardParamsToRedis(sid,rewardParams,jedisPool);

        log.debug("Reward parameters were set.");
        return rewardParams;
    }

    private static void writeRewardParamsToRedis(String sid,RewardParams rewardParams,JedisPool jedisPool) {

        System.out.println("Rewards:\n"+JsonTools.getNiceString(rewardParams));

        try(Jedis jedis = jedisPool.getResource()) {
            if(rewardParams.getOrderViaShare()!=null)
                jedis.hset(Settings.addSidBriefToName(sid,PARAMS),ORDER_VIA_SHARE,rewardParams.getOrderViaShare());
            if(rewardParams.getConsumeViaShare()!=null)
                jedis.hset(Settings.addSidBriefToName(sid,PARAMS),CONSUME_VIA_SHARE,rewardParams.getConsumeViaShare());
            if(!rewardParams.getBuilderShareMap().isEmpty())
                jedis.hmset(Settings.addSidBriefToName(sid,BUILDER_SHARE_MAP),rewardParams.getBuilderShareMap());
            if(rewardParams.getCostMap()!=null&&!rewardParams.getCostMap().isEmpty())
                jedis.hmset(Settings.addSidBriefToName(sid,COST_MAP), rewardParams.getCostMap());
        }catch (Exception e){
            log.error("Write rewardParams into redis wrong.",e);
        }
    }

    public void setIncomeT(long incomeT) {
        this.incomeT = incomeT;
    }

    public String getLastOrderIdFromEs() {
        return lastOrderId;
    }

    public void setLastOrderId(String lastOrderId) {
        this.lastOrderId = lastOrderId;
    }
}
