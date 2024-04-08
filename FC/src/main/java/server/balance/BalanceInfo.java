package server.balance;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import constants.Strings;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;
import static database.redisTools.ReadRedis.readLong;
import static server.Starter.addSidBriefToName;
import static server.Starter.jedisPool;
import static server.Starter.sidBrief;

public class BalanceInfo {
    private static final Logger log = LoggerFactory.getLogger(BalanceInfo.class);
    public static final String BALANCE_BACKUP_JSON = "balanceBackup.json";
    private String user;
    private long bestHeight;
    private String consumeVia;
    private String orderVia;
    private String pending;
    private String serviceName;

    public static void recoverUserBalanceFromFile() {
        try(Jedis jedis = jedisPool.getResource()) {
            BalanceInfo balanceInfo = JsonTools.readObjectFromJsonFile(null,BALANCE_BACKUP_JSON, BalanceInfo.class);
            if(balanceInfo==null)return;
            recoverBalanceToRedis(balanceInfo, jedis);
        } catch (IOException e) {
            log.debug("Failed to recoverUserBalanceFromFile: "+BALANCE_BACKUP_JSON);
        }
    }

    public String getPending() {
        return pending;
    }

    public void setPending(String pending) {
        this.pending = pending;
    }

    public static void deleteOldBalance(ElasticsearchClient esClient) {
        String index = addSidBriefToName(BALANCE);
        long BALANCE_BACKUP_KEEP_MINUTES=144000;
        long height = readLong(BEST_HEIGHT)-BALANCE_BACKUP_KEEP_MINUTES;
        try {
            esClient.deleteByQuery(d -> d.index(index).query(q -> q.range(r -> r.field(BEST_HEIGHT).lt(JsonData.of(height)))));
        }catch (Exception e){
            log.error("Delete old balances in ES error",e);
        }
    }
    public String getConsumeVia() {
        return consumeVia;
    }

    public void setConsumeVia(String consumeVia) {
        this.consumeVia = consumeVia;
    }

    public static void recoverUserBalanceFromEs(ElasticsearchClient esClient) {
        Gson gson = new Gson();
        String index = addSidBriefToName(BALANCE);

        String balancesStr = null;
        String viaTStr = null;
        try(Jedis jedis = jedisPool.getResource()) {
            BalanceInfo balanceInfo = null;
            try {
                SearchResponse<BalanceInfo> result = esClient.search(s -> s.index(index).size(1).sort(so -> so.field(f -> f.field(BEST_HEIGHT).order(SortOrder.Desc))), BalanceInfo.class);
                if (result.hits().hits().size() == 0) {
                    System.out.println("No backup found in ES.");
                    return;
                }
                balanceInfo = result.hits().hits().get(0).source();
                if(balanceInfo==null)return;
                balancesStr = balanceInfo.getUser();
                viaTStr = balanceInfo.getConsumeVia();
            } catch (IOException e) {
                log.error("Get balance from ES error when recovering balances and viaTStr.", e);
            }
            if (balancesStr != null) {
                recoverBalanceToRedis(balanceInfo, jedis);
            } else {
                log.debug("Failed recovered balances from ES.");
            }

            if (viaTStr != null) {
                Map<String, String> viaTMap = gson.fromJson(viaTStr, new TypeToken<HashMap<String, String>>() {
                }.getType());
                for (String id : viaTMap.keySet()) {
                    jedis.hset(sidBrief + "_" + CONSUME_VIA, id, viaTMap.get(id));
                }
                log.debug("Consuming ViaT recovered from ES.");
            } else {
                log.debug("Failed recovered consuming ViaT from ES.");
            }
        }
    }

    private static void recoverBalanceToRedis(BalanceInfo balanceInfo, Jedis jedis) {
        Gson gson = new Gson();
        Map<String, String> balanceMap = gson.fromJson(balanceInfo.getUser(), new TypeToken<HashMap<String, String>>() {
        }.getType());

        Map<String, String> viaTMap = gson.fromJson(balanceInfo.getOrderVia(), new TypeToken<HashMap<String, String>>() {
        }.getType());
        for (String id : balanceMap.keySet()) {
            jedis.hset(sidBrief + "_" + Strings.FID_BALANCE, id, balanceMap.get(id));
        }
        for (String id : viaTMap.keySet()) {


            jedis.hset(sidBrief + "_" + CONSUME_VIA, id, viaTMap.get(id));
        }
        log.debug("Balances recovered from ES.");
    }

    public static void backupBalance(ElasticsearchClient esClient)  {
        try(Jedis jedis0Common = jedisPool.getResource()) {
            Map<String, String> balanceMap = jedis0Common.hgetAll(sidBrief + "_" + Strings.FID_BALANCE);
            Map<String, String> consumeViaMap = jedis0Common.hgetAll(sidBrief + "_" + CONSUME_VIA);
            Map<String, String> orderViaMap = jedis0Common.hgetAll(sidBrief + "_" + ORDER_VIA);
            Map<String, String> pendingStrMap = jedis0Common.hgetAll(REWARD_PENDING_MAP);
            Gson gson = new Gson();

            String balanceStr = gson.toJson(balanceMap);
            String consumeViaStr = gson.toJson(consumeViaMap);
            String orderViaStr = gson.toJson(orderViaMap);
            String pendingStr = gson.toJson(pendingStrMap);

            BalanceInfo balanceInfo = new BalanceInfo();

            balanceInfo.setUser(balanceStr);
            balanceInfo.setConsumeVia(consumeViaStr);
            balanceInfo.setOrderVia(orderViaStr);
            balanceInfo.setPending(pendingStr);

            long bestHeight = readLong(BEST_HEIGHT);

            balanceInfo.setBestHeight(bestHeight);

            backupBalanceToEx(esClient, balanceInfo, bestHeight);

            String fileName = addSidBriefToName(BALANCE);
            backupBalanceToFile(balanceInfo, fileName);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void backupBalanceToEx(ElasticsearchClient esClient, BalanceInfo balanceInfo, long bestHeight) throws IOException {
        String index = addSidBriefToName(BALANCE);
        IndexResponse result = null;
        try {
            result = esClient.index(i -> i.index(index).id(String.valueOf(bestHeight)).document(balanceInfo));
        } catch (IOException e) {
            log.error("Read ES wrong.", e);
        }

        File file = new File(BALANCE_BACKUP_JSON);
        if(!file.exists())file.createNewFile();
        JsonTools.writeObjectToJsonFile(balanceInfo, BALANCE_BACKUP_JSON,false);
        System.out.println("User balance backed up to file:"+BALANCE_BACKUP_JSON);

        if (result != null) {
            System.out.println("User balance backup: " + result.result().toString());
        }
        log.debug(result.result().jsonValue());
    }

    private static void backupBalanceToFile(BalanceInfo balanceInfo, String index) {
        for(int i=0;i<30;i++){
            File file = new File(index +i+DOT_JSON);
            if(file.exists()) {
                if (i == 0){
                    if(new File(index +29+DOT_JSON).exists())
                        file.delete();
                } else {
                    if(new File(index +29+DOT_JSON).exists()) {
                        file.renameTo(new File(index + (i - 1) + DOT_JSON));
                        if(i==29){
                            JsonTools.writeObjectToJsonFile(balanceInfo, index +i+DOT_JSON,false);
                            break;
                        }
                    }
                }
            }else {
                JsonTools.writeObjectToJsonFile(balanceInfo, index +i+DOT_JSON,false);
                break;
            }
        }
    }

    public long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getOrderVia() {
        return orderVia;
    }

    public void setOrderVia(String orderVia) {
        this.orderVia = orderVia;
    }

}
