package startManager;

import clients.apipClient.ApipClient;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.DiskParams;
import appTools.Inputer;
import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.ApiAccount;
import config.Configure;
import constants.*;
import crypto.eccAes256K1P7.EccAes256K1P7;
import crypto.eccAes256K1P7.EccAesDataByte;
import clients.esClient.EsTools;
import javaTools.Hex;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.*;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.serviceManagers.DiskManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;

public class StartDiskManager {
    public static final String STORAGE_DIR = System.getProperty("user.home")+"/disk_data";
    private static DiskManagerSettings setter;
    private static JedisPool jedisPool;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static DiskParams params;

    public static String sid;
    private static BufferedReader br;

    public static void main(String[] args) throws IOException {
        br = new BufferedReader(new InputStreamReader(System.in));

        //Load config info from the file
        Configure configure = Configure.loadConfig(br);
        byte[] symKey = configure.checkPassword(configure);
        configure.initiate(symKey);

        Service myService = configure.getMyService(symKey);
        sid = myService.getSid();
        params = DiskParams.getParamsFromService(myService);
        myService.setParams(params);

        //Load the local settings from the file of localSettings.json
        setter = DiskManagerSettings.loadMySettings(sid,br);


        //Check necessary APIs and set them if anyone can't be connected.
        setter.initiate(symKey, configure);

        //Prepare API clients
        ApiAccount apipAccount = setter.getApipAccount();
        apipClient = (ApipClient) apipAccount.getClient();
        esClient = (ElasticsearchClient) setter.getEsAccount().getClient();
        jedisPool = (JedisPool) setter.getRedisAccount().getClient();

        configure.saveConfig();
//        setter.saveSettings();

        if(params==null ||params.getAccount()==null){
            System.out.println("It's not an disk service. Check it.");
            br.close();
            return;
        }

        //Set params to redis
        setParamsToRedis(configure, symKey, myService, params);

        //Check indices in ES
        checkEsIndices(esClient);

        DiskManager diskManager = new DiskManager(apipAccount, DiskParams.class);

        //Prepare the counter who scan the orders, update the user balances and do distribution.
        Counter counter = new Counter(setter, params);
        RewardManager rewardManager = new RewardManager(sid,params.getAccount(),apipClient,esClient,jedisPool,br);

        //Show the main menu
        Menu menu = new Menu();

        menu.add("Start the counter");
        menu.add("Manage the myService");
        menu.add("Reset the price multipliers(nPrice)");
        menu.add("Recreate all indices");
        menu.add("Manage the rewards");
        menu.add("Settings");

        menu.show();
        int choice = menu.choose(br);
        switch (choice){
            case 1 -> counter.run();
            case 2 -> diskManager.manageService(br, symKey);
            case 3 -> resetNPrices(br);
            case 4 -> recreateAllIndices(esClient,br);
            case 5 -> rewardManager.menu(params.getConsumeViaShare(),params.getOrderViaShare());

            case 6 -> setter.setting(symKey,br);
            case 0 -> setter.close();
        }
    }

    private static void setParamsToRedis(Configure configure, byte[] symKey, Service myService, DiskParams myServiceParams) {
        try(Jedis jedis = jedisPool.getResource()) {
            jedis.hset(FieldNames.DISK_INFO, SID,myService.getSid());
            jedis.hset(FieldNames.DISK_INFO, CONFIG_FILE_PATH,Configure.getConfDir());
            jedis.hset(FieldNames.DISK_INFO,LOCAL_DATA_PATH, Settings.getLocalDataDir(myService.getSid()));
            myServiceParams.setParamsToRedis(Settings.addSidBriefToName(sid,Strings.PARAMS), jedis);
            Map<String, String> nPrice = jedis.hgetAll(Settings.addSidBriefToName(sid,Strings.N_PRICE));
            if (nPrice == null) setNPrices(br, jedis);
            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),ES_ACCOUNT_ID, setter.getEsAccountId());
            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),WINDOW_TIME, String.valueOf(setter.getWindowTime()));
            EccAesDataByte eccDateBytes = EccAes256K1P7.encryptWithPassword(symKey, Hex.fromHex(configure.getNonce()));
            if(eccDateBytes==null)
                throw new RuntimeException("Failed to encrypt symKey.");
            String symKeyCipher = eccDateBytes.toNiceJson();
            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),INIT_SYM_KEY_CIPHER,symKeyCipher);
        }
    }

    private static void recreateAllIndices(ElasticsearchClient esClient,BufferedReader br) {
        if(!Inputer.askIfYes(br,"Recreate the diskItem, order, balance, reward indices? y/n"))return;
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


    private static void setNPrices(BufferedReader br, Jedis jedis) {
        for(String api :ApiNames.FreeDiskAPIs){
            String ask = "Set the price multiplier for " + api + "?y/n Enter to leave default 1:";
            int input = Inputer.inputInteger(br, ask,0);
            if(input==0)input=1;
            jedis.hset(Settings.addSidBriefToName(sid,Strings.N_PRICE),api, String.valueOf(input));
        }
    }

    private static void resetNPrices(BufferedReader br) {
        try(Jedis jedis = jedisPool.getResource()) {
            Map<String, String> nPriceMap = jedis.hgetAll(Settings.addSidBriefToName(sid,Strings.N_PRICE));
            for (String name : nPriceMap.keySet()) {
                String ask = "The price multiplier of " + name + " is " + nPriceMap.get(name) + ". Reset it? y/n";
                int input = Inputer.inputInteger(br, ask, 0);
                if (input != 0)
                    jedis.hset(Settings.addSidBriefToName(sid,Strings.N_PRICE), name, String.valueOf(input));
            }
        }
    }
}
