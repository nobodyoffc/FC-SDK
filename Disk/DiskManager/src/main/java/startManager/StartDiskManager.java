package startManager;

import clients.apipClient.ApipClient;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.DiskParams;
import appTools.Inputer;
import appTools.Menu;
import clients.diskClient.DiskDataInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.ApiAccount;
import config.ApiType;
import config.Configure;
import constants.*;
import crypto.eccAes256K1.EccAes256K1P7;
import crypto.CryptoDataByte;
import clients.esClient.EsTools;
import javaTools.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;

public class StartDiskManager {
    final static Logger log = LoggerFactory.getLogger(StartDiskManager.class);
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

        Service myService = configure.getMyService(symKey, ApiType.DISK);
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

        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> counter.run();
                case 2 -> diskManager.manageService(br, symKey);
                case 3 -> resetNPrices(br);
                case 4 -> recreateAllIndices(esClient, br);
                case 5 -> rewardManager.menu(params.getConsumeViaShare(), params.getOrderViaShare());

                case 6 -> setter.setting(symKey, br);
                case 0 -> {
                    setter.close();
                    return;
                }
            }
        }
    }

    private static void setParamsToRedis(Configure configure, byte[] symKey, Service myService, DiskParams myServiceParams) {
        try(Jedis jedis = jedisPool.getResource()) {
            jedis.hset(FieldNames.DISK_INFO, SID,myService.getSid());
            jedis.hset(FieldNames.DISK_INFO, CONFIG_FILE_PATH,Configure.getConfDir());
            jedis.hset(FieldNames.DISK_INFO,LOCAL_DATA_PATH, Settings.getLocalDataDir(myService.getSid()));

            myServiceParams.writeParamsToRedis(Settings.addSidBriefToName(sid,Strings.PARAMS), jedis);

            Map<String, String> nPrice = jedis.hgetAll(Settings.addSidBriefToName(sid,Strings.N_PRICE));
            if (nPrice == null) setNPrices(br, jedis);

            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),ES_ACCOUNT_ID, setter.getEsAccountId());
            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),WINDOW_TIME, String.valueOf(setter.getWindowTime()));

            CryptoDataByte eccDateBytes = EccAes256K1P7.encryptWithPassword(symKey, Hex.fromHex(configure.getNonce()));
            if(eccDateBytes==null)
                throw new RuntimeException("Failed to encrypt symKey.");
            String symKeyCipher = eccDateBytes.toNiceJson();
            jedis.hset(Settings.addSidBriefToName(sid,SETTINGS),INIT_SYM_KEY_CIPHER,symKeyCipher);

            if(!jedis.exists(Settings.addSidBriefToName(sid, Strings.N_PRICE)))
                setNPrices(br, ApiNames.diskApiList);
        }
    }

    public static void setNPrices(BufferedReader br, ArrayList<String> diskApiList) {
        Map<Integer, String> apiMap = apiListToMap(diskApiList);
        showAllAPIs(apiMap);
        while (true) {
            System.out.println("""
                    Set nPrices:
                    \t'a' to set all nPrices,
                    \t'one' to set all nPrices by 1,
                    \t'zero' to set all nPrices by 0,
                    \tan integer to set the corresponding API,
                    \tor 'q' to quit.\s""");
            String str = null;
            try {
                str = br.readLine();
                if ("".equals(str)) str = br.readLine();
                if (str.equals("q")) return;
                if (str.equals("a")) {
                    setAllNPrices(apiMap, br);
                    System.out.println("Done.");
                    return;
                }
            }catch (Exception e){
                log.error("Set nPrice wrong. ",e);
            }
            if(str==null){
                log.error("Set nPrice failed. ");
            }
            try(Jedis jedis = jedisPool.getResource()) {
                if (str.equals("one")) {
                    for (int i = 0; i < apiMap.size(); i++) {
                        jedis.hset(Settings.addSidBriefToName(sid,Strings.N_PRICE), apiMap.get(i + 1), "1");
                    }
                    System.out.println("Done.");
                    return;
                }
                if (str.equals("zero")) {
                    for (int i = 0; i < apiMap.size(); i++) {
                        jedis.hset(Settings.addSidBriefToName(sid,Strings.N_PRICE), apiMap.get(i + 1), "0");
                    }
                    System.out.println("Done.");
                    return;
                }
                try {
                    int i = Integer.parseInt(str);
                    if (i > apiMap.size()) {
                        System.out.println("The integer should be no bigger than " + apiMap.size());
                    } else {
                        setNPrice(i, apiMap, br);
                        System.out.println("Done.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Wrong input.");
                }
            }
        }
    }

    private static void setAllNPrices(Map<Integer, String> apiMap,  BufferedReader br) throws IOException {
        for (int i : apiMap.keySet()) {
            setNPrice(i, apiMap,  br);
        }
    }

    private static void setNPrice(int i, Map<Integer, String> apiMap, BufferedReader br) throws IOException {
        String apiName = apiMap.get(i);
        while (true) {
            System.out.println("Input the multiple number of API " + apiName + ":");
            String str = br.readLine();
            try(Jedis jedis = jedisPool.getResource()) {
                int n = Integer.parseInt(str);
                jedis.hset(Settings.addSidBriefToName(sid,Strings.N_PRICE), apiName, String.valueOf(n));
                return;
            } catch (Exception e) {
                System.out.println("Wong input.");
            }
        }
    }

    private static void showAllAPIs(Map<Integer, String> apiMap) {
        System.out.println("API list:");
        for (int i = 1; i <= apiMap.size(); i++) {
            System.out.println(i + ". " + apiMap.get(i));
        }
    }

    private static Map<Integer, String> apiListToMap(ArrayList<String> apiList) {

        Map<Integer, String> apiMap = new HashMap<>();
        for (int i = 0; i < apiList.size(); i++) apiMap.put(i + 1, apiList.get(i));
        return apiMap;
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
