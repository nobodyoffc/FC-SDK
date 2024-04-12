package server.rollback;

import APIP.apipClient.ApipClient;
import APIP.apipData.BlockInfo;
import FCH.fchData.Block;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.Strings;
import database.esTools.EsTools;
import database.redisTools.ReadRedis;
import javaTools.http.HttpMethods;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.order.Order;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import static FCH.BlockFileTools.getBlockByHeight;
import static constants.IndicesNames.ORDER;
import static constants.Strings.ORDER_LAST_HEIGHT;
import static server.Starter.addSidBriefToName;

public class Rollbacker {
    /**
     * 检查上一个orderHeight与orderBlockId是否一致
     * 不一致则orderHeight减去30
     * 对回滚区块的es的order做减值处理。
    * */
    public static boolean isRolledBack(long lastHeight, String lastBlockId, ApipClient apipClient) throws IOException {

        if (lastHeight==0 || lastBlockId ==null)return false;

        Map<String, BlockInfo> heightBlockInfoMap = apipClient.blockByHeights(new String[]{String.valueOf(lastHeight)}, HttpMethods.POST);
        if(heightBlockInfoMap==null)heightBlockInfoMap = apipClient.blockByHeights(new String[]{String.valueOf(lastHeight)}, HttpMethods.POST);
        if(heightBlockInfoMap==null)throw new RuntimeException("Failed to get last block info. Check APIP service.");

        BlockInfo blockInfo = heightBlockInfoMap.get(String.valueOf(lastHeight));
        return !blockInfo.getId().equals(lastBlockId);
    }

    public static void rollback(long height, ElasticsearchClient esClient, JedisPool jedisPool)  {
        ArrayList<Order> orderList = null;
        try (Jedis jedis0Common = jedisPool.getResource()){
            String index = addSidBriefToName(ORDER);
            orderList= EsTools.getListSinceHeight(esClient, index,"height",height,Order.class);

            if(orderList==null || orderList.size()==0)return;
            minusFromBalance(orderList, esClient, jedisPool);

            jedis0Common.set(addSidBriefToName(ORDER_LAST_HEIGHT), String.valueOf(height));
            Block block = getBlockByHeight(esClient, height);
            jedis0Common.set(Strings.ORDER_LAST_BLOCK_ID, block.getBlockId());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void minusFromBalance(ArrayList<Order> orderList, ElasticsearchClient esClient, JedisPool jedisPool) throws Exception {
        ArrayList<String> idList= new ArrayList<>();
        try(Jedis jedis = jedisPool.getResource()) {
            for (Order order : orderList) {
                String addr = order.getFromFid();
                long balance = ReadRedis.readHashLong(jedis, addSidBriefToName(Strings.FID_BALANCE), addr);
                jedis.hset(addSidBriefToName(Strings.FID_BALANCE), addr, String.valueOf(balance - order.getAmount()));

                idList.add(order.getOrderId());
            }
        }
        String index = addSidBriefToName(ORDER);
        EsTools.bulkDeleteList(esClient, index, idList);
    }

}
