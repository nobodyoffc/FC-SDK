package start;

import APIP.apipClient.ApipClient;
import FEIP.feipData.Service;
import appTools.Inputer;
import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.ApiProvider;
import config.Config;
import config.MySettings;
import constants.UpStrings;
import constants.Values;
import redis.clients.jedis.JedisPool;
import server.*;
import server.serviceManagers.ServiceManager;
import server.setter.SetterOpenDrive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class StartOpenDrive {
    private static byte[] symKey;
    private static ApipClient apipClient;
    private static JedisPool jedisPool;
    private static ElasticsearchClient esClient;
    private static MySettings mySettings;
    private static Config config;
    private static Service myService;
    private static OpenDriveParams myServiceParams;

    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) throws IOException {
        //Load config info from the file of config.json
        Starter starter = new Starter(br);
        config = starter.loadConfig();
        symKey = starter.checkPassword();
        starter.checkConfig(symKey);

        //Check necessary APIs and set them if anyone can't be connected.
        SetterOpenDrive setter = new SetterOpenDrive(config,mySettings,br,jedisPool);
        checkApis(starter,setter);

        //Load my service info on chain from APIP
        myService = starter.loadMyService(symKey, new String[]{UpStrings.DISK, Values.DISK,UpStrings.DRIVE, Values.DRIVE});
        OpenDriveManager openDriveManager = new OpenDriveManager(config.getInitApipAccount(), OpenDriveParams.class);
        if(myService==null){
            openDriveManager.publishService(symKey,br);
        }

        myServiceParams = OpenDriveParams.getParamsFromService(myService);

        if(myServiceParams.getAccount()==null){
            System.out.println("It's not an Open Drive service. Check it.");
            br.close();
            starter.closeClients();
            return;
        }

        //Load the local settings from the file of localSettings.json
        mySettings = MySettings.checkMySettings(br);

        //Prepare the counter who scan the orders, update the user balances and do distribution.
        Counter counter = new Counter(myService, mySettings.getListenPath(), myServiceParams.getAccount(), myServiceParams.getMinPayment(), mySettings.isFromWebhook(), esClient,apipClient,jedisPool);

        //Show the main menu
        Menu menu = new Menu();

        menu.add("Start the counter");
        menu.add("Manage the service");
        menu.add("Settings");

        menu.show();
        int choice = menu.choose(br);
        switch (choice){
            case 1 -> counter.run();
            case 2 -> openDriveManager.manageService(br,symKey);
            case 3 -> setter.setting(symKey,br);
        }
    }

    private static void checkApis(Starter starter, SetterOpenDrive setterOpenDrive) {
        while (true) {
            apipClient = starter.getInitApipClient();
            jedisPool = starter.getJedisPool();
            esClient = starter.getEsClient();

            if (apipClient == null) {
                if (Inputer.askIfYes(br, "ApipClient is null, but its necessary. Set it now? y/n")) {
                    apipClient = (ApipClient) setterOpenDrive.resetDefaultApi(symKey, ApiProvider.ApiType.APIP);
                } else System.exit(0);
            }

            if (jedisPool == null) {
                if (Inputer.askIfYes(br, "JedisPool is null, but its necessary. Set it them? y/n")) {
                    jedisPool = (JedisPool) setterOpenDrive.resetDefaultApi(symKey, ApiProvider.ApiType.Redis);
                } else System.exit(0);
            }

            if (esClient == null) {
                if (Inputer.askIfYes(br, "EsClient is null, but its necessary. Set it them? y/n")) {
                    esClient = (ElasticsearchClient) setterOpenDrive.resetDefaultApi(symKey, ApiProvider.ApiType.ES);
                } else System.exit(0);
            }
            if(apipClient!=null && jedisPool!=null && esClient!=null)break;
        }
    }
}
