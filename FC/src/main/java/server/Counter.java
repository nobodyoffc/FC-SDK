package server;

import apip.apipData.WebhookPushBody;
import appTools.Inputer;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import fch.fchData.Block;
import fch.fchData.FchTools;
import feip.feipData.serviceParams.Params;
import clients.apipClient.ApipClient;
import apip.apipData.Fcdsl;
import fch.ParseTools;
import clients.apipClient.DataGetter;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.gson.reflect.TypeToken;
import clients.esClient.EsTools;
import clients.redisClient.RedisTools;
import config.ApiAccount;
import constants.*;
import redis.clients.jedis.JedisPool;
import server.balance.BalanceInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;

import fch.fchData.Cash;
import fch.fchData.OpReturn;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import server.order.Order;
import server.order.OrderOpReturn;
import server.reward.RewardReturn;
import server.reward.Rewarder;
import server.rollback.Rollbacker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static constants.Constants.*;
import static constants.FieldNames.NEW_CASHES;
import static constants.FieldNames.OWNER;
import static constants.IndicesNames.BLOCK;
import static constants.IndicesNames.ORDER;
import static constants.Strings.*;
import static clients.redisClient.RedisTools.readHashLong;
import static server.Settings.addSidBriefToName;

public class Counter implements Runnable {

    /*
    1. automatic distribution
    2. the threshold of the new order sum actives the distribution
     */
    protected volatile AtomicBoolean running = new AtomicBoolean(true);
    protected static final Logger log = LoggerFactory.getLogger(Counter.class);
    protected final ElasticsearchClient esClient; //for the data of this service
    protected ApipClient apipClient; //for the data of the FCH and FEIP
    protected final JedisPool jedisPool; //for the running data of this service
    protected final Gson gson = new Gson();
    protected final String account;
    protected final String minPayment;
    protected final String listenPath;
    protected final boolean fromWebhook;
    protected final String sid;
    protected final List<ApiAccount> chargedAccountList;
    protected byte[] counterPriKey;

    public Counter(Settings settings, Params params, List<ApiAccount> chargedAccountList, byte[] symKey) {
        this.sid = settings.getSid();
        this.listenPath = settings.getListenPath();
        this.fromWebhook = settings.isFromWebhook();

        this.account= params.getAccount();
        this.minPayment  = params.getMinPayment();

        this.esClient = (ElasticsearchClient) settings.getEsAccount().getClient();
        if(settings.getApipAccount()!=null)
            this.apipClient =(ApipClient)settings.getApipAccount().getClient();
        this.jedisPool = (JedisPool) settings.getRedisAccount().getClient();
        this.chargedAccountList = chargedAccountList;
        CryptoDataByte result = new Decryptor().decryptJsonBySymKey(settings.getMainFidPriKeyCipher(), symKey);
        if(result.getCode()!=0)log.error("Failed to decrypt the priKey of the counter.");
        else this.counterPriKey=result.getData();
    }

    public static boolean checkUserBalance(String sid, JedisPool jedisPool, ElasticsearchClient esClient, BufferedReader br) {
        try(Jedis jedis = jedisPool.getResource()) {
            Map<String, String> balanceMap = jedis.hgetAll(addSidBriefToName(sid, Strings.BALANCE));
            if(balanceMap==null){
                String fileName = Settings.getLocalDataDir(sid)+BALANCE;
                File file = new File(fileName + 0 + DOT_JSON);
                if(!file.exists()||file.length()==0){
                    while(true) {
                        if (Inputer.askIfYes(br, "No balance in redis and files. Import from file? y/n")) {
                            String importFileName = Inputer.inputString(br, "Input the path and file name");
                            File file1 = new File(importFileName);
                            if(!file1.exists()){
                                System.out.println("File does not exist. Try again.");
                                continue;
                            }
                            BalanceInfo.recoverUserBalanceFromFile(file1.getPath(), jedisPool);
                            return true;
                        }else if(Inputer.askIfYes(br, "Import from ES? y/n")) {
                            BalanceInfo.recoverUserBalanceFromEs(esClient, jedisPool);
                            return true;
                        }else return false;
                    }
                }
            }else{
                System.out.println("There are "+balanceMap.size()+" users.");
                jedis.select(0);
                String lastHeightStr = jedis.get(addSidBriefToName(sid,ORDER_LAST_HEIGHT));
                if(lastHeightStr==null) {
                    jedis.set(addSidBriefToName(sid,ORDER_LAST_HEIGHT),"0");
                    jedis.set(addSidBriefToName(sid,ORDER_LAST_BLOCK_ID), zeroBlockId);
                    System.out.println("No balance yet. New start.");
                    return true;
                }
                long lastHeight = Long.parseLong(lastHeightStr);
                String lastOrderDate = FchTools.heightToNiceDate(lastHeight);
                System.out.println("The last order was created at "+lastOrderDate);
            }
        }
        return true;
    }

//    public static void updateBalance(String sid, String apiName, long bytesLength, FcReplier replier, RequestCheckResult result, JedisPool jedisPool) {
//        try(Jedis jedis = jedisPool.getResource()) {
//            if (Boolean.TRUE.equals(result.getFreeRequest())) return;
//            long balance = updateBalance(sid, apiName, result.getFid(), bytesLength, result.getSessionName(), result.getVia(), jedis);
//            replier.setBalance(String.valueOf(balance));
//        }
//    }

//    public static long updateBalance(String sid, String api, String fid, FcReplier replier, String sessionName, String via, JedisPool jedisPool) {

    public AtomicBoolean isRunning(){
        return running;
    }

    public void run() {
        System.out.println("The counter is running...");
        int countBackUpBalance = 0;
        int countReward = 0;
        Rewarder rewarder;

        rewarder = new Rewarder(sid,account,this.apipClient,this.esClient,this.jedisPool);
        checkIfNewStart();

        while (running.get()) {
            checkRollback();
            getNewOrders();
            countBackUpBalance++;
            countReward++;
            if (countBackUpBalance == BalanceBackupInterval) {
                countBackUpBalance = backupBalance();

                localTask();
            }

            if (countReward == RewardInterval) {
                try {
                    RewardReturn result = rewarder.doReward(account,chargedAccountList,counterPriKey);
                    if (result.getCode() != 0) {
                        log.debug(result.getClass() + ": [" + result.getCode() + "] " + result.getMsg());
                    }
                } catch (Exception e) {
                    log.error("Do reward wrong.", e);
                }
                countReward = 0;
            }

            try {
                TimeUnit.SECONDS.sleep(60);
            } catch (InterruptedException ignore) {
            }
//            waitNewOrder();
        }
    }
    public static boolean isDirectoryEmpty(File directory) {
        if (directory.isDirectory()) {
            String[] files = directory.list();
            return files == null || files.length == 0;
        }
        return false;
    }
    protected void localTask() {}

    protected int backupBalance() {
        int countBackUpBalance;
        try {
            BalanceInfo.backupBalance(sid,this.esClient,jedisPool);
            BalanceInfo.deleteOldBalance(sid,esClient);
        } catch (Exception e) {
            log.error("Failed to backup user balance, consumeVia, orderVia, or pending reward to ES.", e);
        }
        countBackUpBalance = 0;
        return countBackUpBalance;
    }

    protected void checkIfNewStart() {
        try(Jedis jedis = jedisPool.getResource()) {
            String orderLastHeightKey = addSidBriefToName(sid, ORDER_LAST_HEIGHT);
            String lastHeightStr = jedis.get(orderLastHeightKey);
            if (lastHeightStr == null) {
                jedis.set(orderLastHeightKey, "0");
                String orderLastBlockIdKey = addSidBriefToName(sid, ORDER_LAST_BLOCK_ID);
                jedis.set(orderLastBlockIdKey, Constants.zeroBlockId);
                return;
            }
            if("0".equals(lastHeightStr)){
                String orderLastBlockIdKey = addSidBriefToName(sid, ORDER_LAST_BLOCK_ID);
                jedis.set(orderLastBlockIdKey, Constants.zeroBlockId);
            }
        }
    }

protected void waitNewOrder() {
//    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//    log.debug(LocalDateTime.now().format(formatter) + "  Wait for new order...");
        ParseTools.waitForChangeInDirectory(listenPath,running);
}

    protected void checkRollback() {
        try(Jedis jedis = jedisPool.getResource()) {
            long lastHeight = RedisTools.readLong(Settings.addSidBriefToName(sid,ORDER_LAST_HEIGHT));
            String lastBlockId = jedis.get(Settings.addSidBriefToName(sid,Strings.ORDER_LAST_BLOCK_ID));
            try {
                boolean rolledBack;
                if(apipClient!=null)rolledBack= Rollbacker.isRolledBack(lastHeight, lastBlockId, apipClient);
                else rolledBack = Rollbacker.isRolledBack( esClient,lastHeight, lastBlockId);

                if (rolledBack)
                    Rollbacker.rollback(sid,lastHeight - 30, esClient, jedisPool);
            } catch (IOException e) {
                log.debug("Order rollback wrong.");
                e.printStackTrace();
            } catch (Exception e) {
                log.debug("Order rollback wrong.");
                throw new RuntimeException(e);
            }
        }
    }

    protected void getNewOrders() {
        long lastHeight = RedisTools.readLong(addSidBriefToName(sid,ORDER_LAST_HEIGHT));
        List<Cash> cashList;
        if(fromWebhook){
            if(!isDirectoryEmpty(new File(this.listenPath)))
                cashList=getNewCashListFromFile(lastHeight);
            else return;
        } else{
            if(apipClient!=null)
                cashList = getNewCashListFromApip(lastHeight, account, apipClient);
            else cashList = getNewCashListFromEs(lastHeight, account, esClient);
        }
        if (cashList != null && cashList.size() > 0) {
            setLastOrderInfoToRedis(cashList);
            getValidOrderList(cashList);
        }
    }

    protected List<Cash> getNewCashListFromFile(long lastHeight) {
        String method = ApiNames.NewCashByFidsAPI;
        long bestHeight=lastHeight;
        List<Cash> allCashList = new ArrayList<>();

        int i=0;
        File file;
        String newOrderDir = System.getProperty(UserDir) + Settings.addSidBriefToName(sid,method);
        while (true) {
            file = new File(newOrderDir, method+i+DOT_JSON);
            if(!file.exists())break;
            if(file.length()==0)return null;
            try(FileInputStream fis = new FileInputStream(file)){
                String webhookPushBodyStr = new String(fis.readAllBytes());
                WebhookPushBody webhookPushBody = gson.fromJson(webhookPushBodyStr,WebhookPushBody.class);
                if(webhookPushBody==null)return null;
                if(webhookPushBody.getBestHeight() > bestHeight) {
                    if (webhookPushBody.getSinceHeight() > lastHeight) {
                        if(apipClient!=null) {
                            allCashList = getNewCashListFromApip(lastHeight, account, apipClient);
                            if (apipClient.getClientData() != null && apipClient.getClientData().getCode() == 0)
                                bestHeight = apipClient.getClientData().getResponseBody().getBestHeight();
                        }else {
                            allCashList = getNewCashListFromEs(lastHeight,account,esClient);
                            Block block = EsTools.getBestOne(esClient, BLOCK, HEIGHT, SortOrder.Desc, Block.class);
                            if(block!=null)bestHeight = block.getHeight();
                        }
                    } else {
                        List<Cash> cashList = DataGetter.getCashList(webhookPushBodyStr);
                        allCashList.addAll(cashList);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            deleteFile(file);
            i++;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(addSidBriefToName(sid, ORDER_LAST_HEIGHT), String.valueOf(bestHeight));
        }
        allCashList.removeIf(cash -> cash.getBirthHeight() < lastHeight);

        return allCashList;

    }

    private static void deleteFile(File file) {
        if(!file.delete()){
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException ignore) {
            }
            if(!file.delete()){
                throw new RuntimeException("Failed to delete: "+ file.getName());
            }
        }
    }

    protected List<Cash> getNewCashListFromJedis(long lastHeight, JedisPool jedisPool) {

        try(Jedis jedis = jedisPool.getResource()){
            String newCashesKey = addSidBriefToName(sid,NEW_CASHES);
            String cashListStr = jedis.get(newCashesKey);
            Type t = new TypeToken<ArrayList<Cash>>() {}.getType();
            List<Cash> cashList= new Gson().fromJson(cashListStr, t);
            jedis.del(newCashesKey);
            cashList.removeIf(cash -> cash.getBirthHeight() > lastHeight || !cash.isValid());
            return cashList;
        }
    }

    protected void getValidOrderList(List<Cash> cashList) {
        try(Jedis jedis0Common = jedisPool.getResource()) {
            ArrayList<Order> orderList = getNewOrderList(cashList);
            if (orderList.size() == 0) return;

            Map<String, OrderInfo> opReturnOrderInfoMap;

            ArrayList<String> txidList = getTxIdList(orderList);
            opReturnOrderInfoMap = getOpReturnOrderInfoMap(txidList);

            Iterator<Order> iter = orderList.iterator();
            while (iter.hasNext()){
                Order order = iter.next();
                OrderInfo orderInfo = opReturnOrderInfoMap.get(order.getTxId());
                if(orderInfo==null)continue;
                if(orderInfo.isIgnored()){
                    iter.remove();
                    continue;
                }
                String via = orderInfo.getVia();
                if (via != null) order.setVia(via);
            }

            ArrayList<String> orderIdList = new ArrayList<>();
            for (Order order : orderList) {
                String payer = order.getFromFid();
                if (payer != null) {
                    long balance = readHashLong(jedis0Common, Settings.addSidBriefToName(sid,Strings.BALANCE), payer);
                    jedis0Common.hset(Settings.addSidBriefToName(sid,Strings.BALANCE), payer, String.valueOf(balance + order.getAmount()));
                } else continue;

                String via = order.getVia();
                if (via != null) {
                    order.setVia(via);
                    long viaT = readHashLong(jedis0Common, Settings.addSidBriefToName(sid,Strings.ORDER_VIA), via);
                    jedis0Common.hset(Settings.addSidBriefToName(sid,Strings.CONSUME_VIA), via, String.valueOf(viaT + order.getAmount()));
                }

                log.debug("New order from [" + order.getFromFid() + "]: " + order.getAmount() / 100000000 + " F");

                orderIdList.add(order.getOrderId());
            }
            try {
                String index = addSidBriefToName(sid,ORDER).toLowerCase();
                EsTools.bulkWriteList(esClient, index, orderList, orderIdList, Order.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected ArrayList<String> getTxIdList(ArrayList<Order> orderList) {
        ArrayList<String> txIdList = new ArrayList<>();
        for(Order order :orderList){
            txIdList.add(order.getTxId());
        }
        return txIdList;
    }

    protected ArrayList<Order> getNewOrderList(List<Cash> cashList) {
        long minPayment = (long) Double.parseDouble(this.minPayment) * 100000000;

        ArrayList<Order> orderList = new ArrayList<>();

        Iterator<Cash> iterator = cashList.iterator();
        while (iterator.hasNext()) {
            Cash cash = iterator.next();
            if (cash.getValue() < minPayment) {
                iterator.remove();
                continue;
            }

            String issuer = cash.getIssuer();
            if(issuer.equals(account) ||"999".equals(ParseTools.getLast3(cash.getValue()))){
                iterator.remove();
                continue;
            }

            Order order = new Order();
            order.setOrderId(cash.getCashId());
            order.setFromFid(cash.getIssuer());
            order.setAmount(cash.getValue());
            order.setHeight(cash.getBirthHeight());
            order.setTime(cash.getBirthTime());
            order.setToFid(cash.getOwner());
            order.setTxId(cash.getBirthTxId());
            order.setTxIndex(cash.getBirthTxIndex());

            orderList.add(order);
        }
        return orderList;
    }

    protected Map<String, OrderInfo> getOpReturnOrderInfoMap(ArrayList<String> txidList) {

        Map<String, OrderInfo> validOrderInfoMap = new HashMap<>();
        EsTools.MgetResult<OpReturn> result1 = new EsTools.MgetResult<>();

        try {
            result1 = EsTools.getMultiByIdList(esClient, IndicesNames.OPRETURN, txidList, OpReturn.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (result1.getResultList() == null || result1.getResultList().size() == 0) return validOrderInfoMap;

        List<OpReturn> opReturnList = result1.getResultList();

        for (OpReturn opReturn : opReturnList) {
            try {
                String goodOp = JsonTools.strToJson(opReturn.getOpReturn());
                OrderOpReturn orderOpreturn = gson.fromJson(goodOp, OrderOpReturn.class);
                if(orderOpreturn==null)continue;
                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setId(opReturn.getTxId());
                if(orderOpreturn.getType().equals("apip")
                        && orderOpreturn.getSn().equals("0")
                        && orderOpreturn.getData().getOp().equals(Strings.IGNORE)){
                    orderInfo.setIgnored(true);
                }else if (orderOpreturn.getType().equals("apip")
                        && orderOpreturn.getSn().equals("0")
                        && orderOpreturn.getData().getOp().equals(Values.BUY)
                        && orderOpreturn.getData().getSid().equals(sid)
                ) {
                    orderInfo.setVia(orderOpreturn.getData().getVia());
                }
                validOrderInfoMap.put(opReturn.getTxId(), orderInfo);
            } catch (Exception ignored) {
//                e.printStackTrace();
//                throw new RuntimeException(e);
            }
        }
        return validOrderInfoMap;
    }

    protected void setLastOrderInfoToRedis(List<Cash> cashList) {
        long lastHeight = 0;
        String lastBlockId = null;
        for (Cash cash : cashList) {
            if (cash.getBirthHeight() > lastHeight) {
                lastHeight = cash.getBirthHeight();
                lastBlockId = cash.getBirthBlockId();
            }
        }
        try(Jedis jedis0Common = jedisPool.getResource()) {
            jedis0Common.set(Settings.addSidBriefToName(sid,ORDER_LAST_HEIGHT), String.valueOf(lastHeight));
            jedis0Common.set(Settings.addSidBriefToName(sid,Strings.ORDER_LAST_BLOCK_ID), lastBlockId);
        }
    }

    protected List<Cash> getNewCashListFromApip(long lastHeight, String account, ApipClient apipClient) {

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(String.valueOf(lastHeight));
        fcdsl.addNewSort(BIRTH_HEIGHT,DESC).appendSort(FieldNames.CASH_ID,ASC);
        fcdsl.addNewFilter().addNewTerms().addNewFields(OWNER).addNewValues(account);
        fcdsl.setSize(String.valueOf(3000));
        apipClient.cashSearch(fcdsl);
        Object data = apipClient.checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCashList(data);
    }

    protected List<Cash> getNewCashListFromEs(long lastHeight, String account, ElasticsearchClient esClient) {
        List<Cash> cashList = null;
        try {
            cashList = EsTools.rangeGt(
                    esClient,
                    IndicesNames.CASH,
                    BIRTH_HEIGHT,
                    lastHeight,
                    FieldNames.CASH_ID,
                    SortOrder.Asc,
                    OWNER,
                    account,
                    Cash.class);
            if (cashList.size() == 0) return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cashList;
    }

    static class OrderInfo {
        protected String id;
        protected String via;
        protected boolean ignored;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getVia() {
            return via;
        }

        public void setVia(String via) {
            this.via = via;
        }

        public boolean isIgnored() {
            return ignored;
        }

        public void setIgnored(boolean ignored) {
            this.ignored = ignored;
        }
    }

    public void shutdown() {
        running.set(false);
    }
    public void restart(){
        running.set(true);
    }

    public AtomicBoolean getRunning() {
        return running;
    }

    public void setRunning(AtomicBoolean running) {
        this.running = running;
    }

    public ElasticsearchClient getEsClient() {
        return esClient;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public Gson getGson() {
        return gson;
    }

    public String getAccount() {
        return account;
    }

    public String getMinPayment() {
        return minPayment;
    }

    public String getListenPath() {
        return listenPath;
    }

    public boolean isFromWebhook() {
        return fromWebhook;
    }

}

