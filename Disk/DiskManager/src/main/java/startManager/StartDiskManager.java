package startManager;

import clients.apipClient.ApipClient;
import feip.feipData.Service;
import feip.feipData.serviceParams.DiskParams;
import appTools.Inputer;
import appTools.Menu;
import clients.diskClient.DiskDataInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.Configure;
import constants.ApiNames;
import constants.FieldNames;
import constants.Strings;
import crypto.old.EccAes256K1P7;
import crypto.CryptoDataByte;
import clients.esClient.EsTools;
import javaTools.Hex;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.*;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.reward.Rewarder;
import server.serviceManagers.DiskManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;

public class StartDiskManager {
    public static final String STORAGE_DIR = System.getProperty("user.home")+"/disk_data";
    private static DiskManagerSettings settings;
    private static JedisPool jedisPool;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static DiskParams params;

    public static String sid;
    private static BufferedReader br;

    public static void main(String[] args) throws IOException {
        br = new BufferedReader(new InputStreamReader(System.in));

        //Load config info from the file
        Configure configure = Configure.loadConfig(br);
        byte[] symKey = configure.checkPassword(configure);

        sid = configure.chooseSid(symKey);
        //Load the local settings from the file of localSettings.json
        settings = DiskManagerSettings.loadFromFile(sid,DiskManagerSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new DiskManagerSettings();
        service = settings.initiateServer(sid,symKey,configure,br);

        if(service==null){
            System.out.println("It's not an disk service. Check it.");
            close();
            return;
        }

        sid = service.getSid();
        params = (DiskParams) service.getParams();

        //Prepare API clients
        apipClient = (ApipClient) settings.getApipAccount().getClient();
        esClient = (ElasticsearchClient) settings.getEsAccount().getClient();
        jedisPool = (JedisPool) settings.getRedisAccount().getClient();

        //Set params to redis
        setParamsToRedis(configure, symKey, service, params);

        //Check indices in ES
        checkEsIndices(esClient);

        //Check user balance
        if (!Counter.checkUserBalance(sid, jedisPool, esClient, br)) {
            close();
            return;
        }

        //Check webhooks for new orders.
        if(settings.getFromWebhook()!=null && settings.getFromWebhook().equals(Boolean.TRUE))
            if (!Order.checkWebhook(ApiNames.NewCashByFids, sid, params, settings.getApipAccount(), br, jedisPool)){
                close();
                return;
            }

        Rewarder.checkRewarderParams(sid, params,jedisPool, br);

        Counter counter = new Counter(settings, params,symKey);
        //Show the main menu
        Menu menu = new Menu();
        menu.setName("Disk Manager");
        menu.add("Start the counter");
        menu.add("Manage the service");
        menu.add("Reset the price multipliers(nPrice)");
        menu.add("Recreate all indices");
        menu.add("Manage the rewards");
        menu.add("Settings");

        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> counter.run();
                case 2 -> new DiskManager(service, settings.getApipAccount(),br,symKey, DiskParams.class).menu();
                case 3 -> Order.resetNPrices(br, sid, jedisPool);
                case 4 -> recreateAllIndices(esClient, br);
                case 5 -> new RewardManager(sid,params.getAccount(),apipClient,esClient,null,jedisPool,br)
                        .menu(params.getConsumeViaShare(), params.getOrderViaShare());
                case 6 -> settings.setting(symKey, br);
                case 0 -> {
                    counter.shutdown();
                    close();
                    return;
                }
            }
        }
    }

    private static void close() throws IOException {
        settings.close();
        br.close();
    }

    private static void setParamsToRedis(Configure configure, byte[] symKey, Service myService, DiskParams myServiceParams) {
        try(Jedis jedis = jedisPool.getResource()) {
            jedis.hset(FieldNames.DISK_INFO, SID,myService.getSid());
            jedis.hset(FieldNames.DISK_INFO, CONFIG_FILE_PATH,Configure.getConfDir());
            jedis.hset(FieldNames.DISK_INFO,LOCAL_DATA_PATH, Settings.getLocalDataDir(myService.getSid()));

            myServiceParams.writeParamsToRedis(Settings.addSidBriefToName(sid, Strings.PARAMS), jedis);

            Map<String, String> nPrice = jedis.hgetAll(Settings.addSidBriefToName(sid,Strings.N_PRICE));
            if (nPrice == null) Settings.setNPrices(sid, ApiNames.DiskAPIs, jedis, br);

            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),ES_ACCOUNT_ID, settings.getEsAccountId());
            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),WINDOW_TIME, String.valueOf(settings.getWindowTime()));

            CryptoDataByte eccDateBytes = EccAes256K1P7.encryptWithPassword(symKey, Hex.fromHex(configure.getNonce()));
            if(eccDateBytes==null)
                throw new RuntimeException("Failed to encrypt symKey.");
            String symKeyCipher = eccDateBytes.toNiceJson();
            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),INIT_SYM_KEY_CIPHER,symKeyCipher);

            if(!jedis.exists(Settings.addSidBriefToName(sid, Strings.N_PRICE)))
                Order.setNPrices(br, ApiNames.diskApiList, sid, jedisPool);
        }
    }


    private static void recreateAllIndices(ElasticsearchClient esClient,BufferedReader br) {
        if(!Inputer.askIfYes(br,"Recreate the diskItem, order, balance, reward indices?"))return;
        try {
            EsTools.recreateIndex(Settings.addSidBriefToName(sid,DATA), DiskDataInfo.MAPPINGS,esClient);
            EsTools.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient);
            EsTools.recreateIndex(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS,esClient);
            EsTools.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkEsIndices(ElasticsearchClient esClient) {
        Map<String,String> nameMappingList = new HashMap<>();
        nameMappingList.put(Settings.addSidBriefToName(sid,DATA), DiskDataInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
        EsTools.checkEsIndices(esClient,nameMappingList);
    }

}
