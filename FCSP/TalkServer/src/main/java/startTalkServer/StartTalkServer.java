package startTalkServer;

import appTools.Inputer;
import appTools.Menu;
import clients.apipClient.ApipClient;
import clients.esClient.EsTools;
import clients.fcspClient.TalkItem;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.ServiceType;
import config.Configure;
import constants.ApiNames;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import feip.feipData.Service;
import feip.feipData.serviceParams.TalkParams;
import redis.clients.jedis.JedisPool;
import server.Counter;
import server.Settings;
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

public class StartTalkServer {
    public static final String STORAGE_DIR = System.getProperty("user.home")+"/disk_data";
    private static TalkServerSettings settings;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static TalkParams params;
    public static Counter counter;


    public static String sid;

    public static void main(String[] args) throws IOException {
        ServiceType serviceType = ServiceType.TALK;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        //Load config info from the file
        Configure configure = Configure.loadConfig(br);
        byte[] symKey = configure.checkPassword(configure);

        sid = configure.chooseSid(symKey);
        //Load the local settings from the file of localSettings.json
        settings = TalkServerSettings.loadFromFile(sid, TalkServerSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new TalkServerSettings(configure);
        service = settings.initiateServer(sid,symKey,configure, br);
        if(service==null){
            System.out.println("Failed to initiate.");
            close();
            return;
        }

        sid = service.getSid();
        params = (TalkParams) service.getParams();

        //Prepare API clients
        apipClient = (ApipClient) settings.getApipAccount().getClient();
        esClient = (ElasticsearchClient) settings.getEsAccount().getClient();
        JedisPool jedisPool = (JedisPool) settings.getRedisAccount().getClient();

        Configure.checkWebConfig(sid,configure, settings,symKey, serviceType, jedisPool, br);

        //Set params to redis
//        setParamsToRedis(configure, symKey, service, params);

        //Check indices in ES
        checkEsIndices(esClient);

        //Check API prices
        Order.setNPrices(sid, ApiNames.ApipApiList, jedisPool, br,false);

        //Check user balance
        Counter.checkUserBalance(sid, jedisPool, esClient, br);

        //Check webhooks for new orders.
        if(settings.getFromWebhook()!=null && settings.getFromWebhook().equals(Boolean.TRUE))
            if (!Order.checkWebhook(ApiNames.NewCashByFids, sid, params, apipClient.getApiAccount(), br, jedisPool)){
                close();
                return;
            }

        Rewarder.checkRewarderParams(sid, params, jedisPool, br);

        startCounterThread(symKey);

        byte[] waiterPriKey;
        CryptoDataByte result = new Decryptor().decryptJsonBySymKey(settings.getMainFidPriKeyCipher(), symKey);
        if(result.getCode()!=0 || result.getData()==null){
            System.out.println("Failed to decrypt the waiter's priKey.");
            return;
        }
        waiterPriKey = result.getData();

        //Show the main menu
        Menu menu = new Menu();
        menu.setName("Start server");
        menu.setName("Talk Manager");
        menu.add("Manage the service");
        menu.add("Reset the price multipliers(nPrice)");
        menu.add("Recreate all indices");
        menu.add("Manage the rewards");
        menu.add("Settings");

        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> new TalkServer(sid, waiterPriKey, apipClient,jedisPool).start();
                case 2 -> new DiskManager(service, settings.getApipAccount(), br,symKey, TalkParams.class).menu();
                case 3 -> Order.resetNPrices(br, sid, jedisPool);
                case 4 -> recreateAllIndices(esClient, br);
                case 5 -> new RewardManager(sid,params.getAccount(),apipClient,esClient,null, jedisPool, br)
                        .menu(params.getConsumeViaShare(), params.getOrderViaShare());
                case 6 -> settings.setting(symKey, br);
                case 0 -> {
                    if (counter != null) counter.close();
                    close();
                    System.out.println("Exited, see you again.");
                    System.exit(0);
                }
            }
        }
    }

    private static void startCounterThread(byte[] symKey) {
        counter = new Counter(settings, params,symKey);
        Thread thread = new Thread(counter);
        thread.start();
    }

    private static void close() throws IOException {
        settings.close();
    }

    private static void recreateAllIndices(ElasticsearchClient esClient,BufferedReader br) {
        if(!Inputer.askIfYes(br,"Recreate the disk data, order, balance, reward indices?"))return;
        try {
            EsTools.recreateIndex(Settings.addSidBriefToName(sid,DATA), TalkItem.MAPPINGS,esClient);
            EsTools.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient);
            EsTools.recreateIndex(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS,esClient);
            EsTools.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkEsIndices(ElasticsearchClient esClient) {
        Map<String,String> nameMappingList = new HashMap<>();
        nameMappingList.put(Settings.addSidBriefToName(sid,DATA), TalkItem.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
        EsTools.checkEsIndices(esClient,nameMappingList);
    }

}
