package server;

import APIP.apipClient.ApipClient;
import APIP.apipClient.ApipDataGetter;
import APIP.apipData.Fcdsl;
import FCH.ParseTools;
import com.google.gson.reflect.TypeToken;
import database.esTools.EsTools;
import database.redisTools.ReadRedis;
import javaTools.http.HttpMethods;
import redis.clients.jedis.JedisPool;
import server.balance.BalanceInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;
import constants.Constants;
import constants.IndicesNames;
import constants.Strings;
import constants.Values;

import FCH.fchData.Cash;
import FCH.fchData.OpReturn;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import server.order.Order;
import server.order.OrderOpReturn;
import server.reward.RewardReturn;
import server.reward.Rewarder;
import server.rollback.Rollbacker;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static constants.Constants.BalanceBackupInterval;
import static constants.Constants.RewardInterval;
import static constants.FieldNames.NEW_CASHES;
import static constants.FieldNames.OWNER;
import static constants.IndicesNames.ORDER;
import static constants.Strings.*;
import static database.redisTools.ReadRedis.readHashLong;
import static server.Starter.addSidBriefToName;
import static server.Starter.myService;
    /*
    - 账户：fid
    - 存储：APIP-chain data，es-order and reward, redis-consume，file-backup
    - 收入：APIP webhook, or NaSa node
    - 消费：price for kBytes
    - 分配：buy share, consume share
    - 结算：every 10 days
     */

public class Counter implements Runnable {
    private volatile AtomicBoolean running = new AtomicBoolean(true);
    private static final Logger log = LoggerFactory.getLogger(Counter.class);
//    public static  String serviceName;
    private final ElasticsearchClient esClient; //for the data of this service
    private final ApipClient apipClient; //for the data of the FCH and FEIP
    private final JedisPool jedisPool; //for the running data of this service
    private final Gson gson = new Gson();

    private final String account;
    private final String minPayment;
    private final String listenDir;
    private final boolean fromWebhook;

    public Counter(String listenPath, String account, String minPayment, boolean fromWebhook,ElasticsearchClient esClient, ApipClient apipClient, JedisPool jedisPool) {
        this.listenDir = listenPath;
        this.esClient = esClient;
        this.apipClient = apipClient;
        this.jedisPool = jedisPool;
        this.account= account;
        this.minPayment  = minPayment;
        this.fromWebhook = fromWebhook;
    }
    public AtomicBoolean isRunning(){
        return running;
    }

    public void run() {
        log.debug("The counter is running...");
//        ConfigAPIP configAPIP = new ConfigAPIP();
//        try(Jedis jedis0Common = Starter.jedisPool.getResource()) {
//            configAPIP.setConfigFilePath(jedis0Common.hget(CONFIG, CONFIG_FILE_PATH));
//            try {
//                configAPIP = configAPIP.getClassInstanceFromFile(ConfigAPIP.class);
//            } catch (IOException e) {
//                log.error("Order scanner read config file wrong.");
//                throw new RuntimeException(e);
//            }
//
//            if (configAPIP.getEsIp() == null || configAPIP.getEsPort() == 0) {
//                log.error("Es IP is null. Config first.");
//                return;
//            }
//            serviceName = configAPIP.getServiceName() + "_";
//
//            service = gson.fromJson(jedis0Common.get(serviceName + Strings.SERVICE), Service.class);
//            log.debug("Order scanner got the service. SID: {}", service.getSid());
//
//            params = service.getParams();
//            String serviceAccount = params.getAccount();
//            if (serviceAccount == null) {
//                log.error("No service account.");
//                return;
//            }
//
//            log.debug("SID: " + service.getSid()
//                    + "\nService Name: "
//                    + service.getStdName()
//                    + "\nAccount: " + params.getAccount());
//            System.out.println("Any Key to continue...");
        int countBackUpBalance = 0;
        int countReward = 0;
        Rewarder rewarder;

        rewarder = new Rewarder(this.apipClient,this.esClient,this.jedisPool);

        while (running.get()) {
            checkIfNewStart();
            checkRollback();
            getNewOrders();

            countBackUpBalance++;
            countReward++;
            if (countBackUpBalance == BalanceBackupInterval) {
                try {
                    BalanceInfo.backupBalance(this.esClient);
                    BalanceInfo.deleteOldBalance(esClient);
                } catch (Exception e) {
                    log.error("Failed to backup user balance, consumeVia, orderVia, or pending reward to ES.", e);
                }
                countBackUpBalance = 0;
            }

            if (countReward == RewardInterval) {
                try {
                    RewardReturn result = rewarder.doReward(account);
                    if (result.getCode() != 0) {
                        log.error(result.getClass() + ": [" + result.getCode() + "] " + result.getMsg());
                    }
                } catch (Exception e) {
                    log.error("Do reward wrong.", e);
                }
                countReward = 0;
            }
            waitNewOrder();
        }
    }

    private void checkIfNewStart() {
        try(Jedis jedis0Common = Starter.jedisPool.getResource()) {
            String lastHeightStr = jedis0Common.get(Starter.sidBrief + "_" + ORDER_LAST_HEIGHT);
            if (lastHeightStr == null) {
                jedis0Common.set(Starter.sidBrief + "_" + ORDER_LAST_HEIGHT, "0");
                jedis0Common.set(Starter.sidBrief + "_" + Strings.ORDER_LAST_BLOCK_ID, Constants.zeroBlockId);
            }
        }
    }

private void waitNewOrder() {
//    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//    log.debug(LocalDateTime.now().format(formatter) + "  Wait for new order...");
    ParseTools.waitForChangeInDirectory(listenDir,running);
}

    private void checkRollback() {
        try(Jedis jedis0Common = Starter.jedisPool.getResource()) {
            long lastHeight = ReadRedis.readLong(Starter.sidBrief + "_" + ORDER_LAST_HEIGHT);
            String lastBlockId = jedis0Common.get(Starter.sidBrief + "_" + Strings.ORDER_LAST_BLOCK_ID);
            try {
                if (Rollbacker.isRolledBack(lastHeight, lastBlockId,apipClient))
                    Rollbacker.rollback(lastHeight - 30, esClient, jedisPool);
            } catch (IOException e) {
                log.debug("Order rollback wrong.");
                e.printStackTrace();
            } catch (Exception e) {
                log.debug("Order rollback wrong.");
                throw new RuntimeException(e);
            }
        }
    }

    private void getNewOrders() {
        long lastHeight = ReadRedis.readLong( addSidBriefToName(ORDER_LAST_HEIGHT));
        List<Cash> cashList;
        if(fromWebhook){
            cashList=getNewCashList(lastHeight, jedisPool);
        } else{
            cashList = getNewCashList(lastHeight, account, apipClient);
        }
        if (cashList != null && cashList.size() > 0) {
            setLastOrderInfoToRedis(cashList);
            getValidOrderList(cashList);
        }
    }

    private List<Cash> getNewCashList(long lastHeight, JedisPool jedisPool) {

        try(Jedis jedis = jedisPool.getResource()){
            String newCashesKey = addSidBriefToName(NEW_CASHES);
            String cashListStr = jedis.get(newCashesKey);
            Type t = new TypeToken<ArrayList<Cash>>() {}.getType();
            List<Cash> cashList= new Gson().fromJson(cashListStr, t);
            jedis.del(newCashesKey);
            cashList.removeIf(cash -> cash.getBirthHeight() > lastHeight || !cash.isValid());
            return cashList;
        }
    }

    private void getValidOrderList(List<Cash> cashList) {
        try(Jedis jedis0Common = Starter.jedisPool.getResource()) {
            ArrayList<Order> orderList = getNewOrderList(cashList);
            if (orderList.size() == 0) return;

            String isCheckOrderOpReturn = jedis0Common.hget(CONFIG, Strings.CHECK_ORDER_OPRETURN);
            Map<String, OrderInfo> validOpReturnOrderInfoMap;

            if ("true".equals(isCheckOrderOpReturn)) {
                ArrayList<String> txidList = getTxIdList(orderList);
                validOpReturnOrderInfoMap = getValidOpReturnOrderInfoMap(txidList);

                for (Order order : orderList) {
                    OrderInfo orderInfo = validOpReturnOrderInfoMap.get(order.getTxId());
                    if (orderInfo == null) continue;
                    String via = orderInfo.getVia();
                    if (via != null) order.setVia(via);
                }
            }

            ArrayList<String> orderIdList = new ArrayList<>();
            for (Order order : orderList) {
                String payer = order.getFromFid();
                if (payer != null) {
                    long balance = readHashLong(jedis0Common, Starter.sidBrief + "_" + Strings.FID_BALANCE, payer);
                    jedis0Common.hset(Starter.sidBrief + "_" + Strings.FID_BALANCE, payer, String.valueOf(balance + order.getAmount()));
                } else continue;

                String via = order.getVia();
                if (via != null) {
                    order.setVia(via);
                    long viaT = readHashLong(jedis0Common, Starter.sidBrief + "_" + Strings.ORDER_VIA, via);
                    jedis0Common.hset(Starter.sidBrief + "_" + Strings.CONSUME_VIA, via, String.valueOf(viaT + order.getAmount()));
                }

                log.debug("New order from [" + order.getFromFid() + "]: " + order.getAmount() / 100000000 + " F");

                orderIdList.add(order.getOrderId());
            }
            try {
                String index = addSidBriefToName(ORDER);
                EsTools.bulkWriteList(esClient, index, orderList, orderIdList, Order.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private ArrayList<String> getTxIdList(ArrayList<Order> orderList) {
        ArrayList<String> txIdList = new ArrayList<>();
        for(Order order :orderList){
            txIdList.add(order.getTxId());
        }
        return txIdList;
    }

    private ArrayList<Order> getNewOrderList(List<Cash> cashList) {
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
            if(issuer.equals(account)||issuer.equals(Starter.myService.getOwner())){
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

    private Map<String, OrderInfo> getValidOpReturnOrderInfoMap(ArrayList<String> txidList) {

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

                if(orderOpreturn != null && orderOpreturn.getType().equals("APIP")
                        && orderOpreturn.getSn().equals("0")
                        && orderOpreturn.getData().getOp().equals(Strings.IGNORE)){
                    continue;
                }
                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setId(opReturn.getTxId());
                if (orderOpreturn != null
                        && orderOpreturn.getType().equals("APIP")
                        && orderOpreturn.getSn().equals("0")
                        && orderOpreturn.getData().getOp().equals(Values.BUY)
                        && orderOpreturn.getData().getSid().equals(Starter.myService.getSid())
                ) {
                    orderInfo.setVia(orderOpreturn.getData().getVia());
                }
                validOrderInfoMap.put(opReturn.getTxId(), orderInfo);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return validOrderInfoMap;
    }

    private void setLastOrderInfoToRedis(List<Cash> cashList) {
        long lastHeight = 0;
        String lastBlockId = null;
        for (Cash cash : cashList) {
            if (cash.getBirthHeight() > lastHeight) {
                lastHeight = cash.getBirthHeight();
                lastBlockId = cash.getBirthBlockId();
            }
        }
        try(Jedis jedis0Common = Starter.jedisPool.getResource()) {
            jedis0Common.set(Starter.sidBrief + "_" + ORDER_LAST_HEIGHT, String.valueOf(lastHeight));
            jedis0Common.set(Starter.sidBrief + "_" + Strings.ORDER_LAST_BLOCK_ID, lastBlockId);
        }
    }

    private List<Cash> getNewCashList(long lastHeight, String account, ApipClient apipClient) {
        List<Cash> cashList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(String.valueOf(lastHeight));
        fcdsl.addNewSort(BIRTH_HEIGHT,DESC).appendSort(CASH_ID,ASC);
        fcdsl.addNewFilter().addNewTerms().addNewFields(OWNER).addNewValues(account);

        cashList =apipClient.cashSearch(fcdsl, HttpMethods.POST);

//        try {
//            cashList = EsTools.rangeGt(
//                    esClient,
//                    IndicesNames.CASH,
//                    "birthHeight",
//                    lastHeight,
//                    "cashId",
//                    SortOrder.Asc,
//                    "owner",
//                    account,
//                    //params.getAccount(),
//                    Cash.class);
//            if (cashList.size() == 0) return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return cashList;
    }

    static class OrderInfo {
        private String id;
        private String via;

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

    public String getListenDir() {
        return listenDir;
    }

    public boolean isFromWebhook() {
        return fromWebhook;
    }
}

