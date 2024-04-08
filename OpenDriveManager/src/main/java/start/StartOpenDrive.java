package start;

import APIP.apipClient.ApipClient;
import FEIP.feipData.Service;
import appTools.Inputer;
import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.ConfigOpenDrive;
import redis.clients.jedis.JedisPool;
import server.*;
import server.serviceManagers.ServiceManager;
import server.setter.Setter;
import server.setter.SetterOpenDrive;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class StartOpenDrive {
    private static byte[] symKey;
    private static ApipClient apipClient;
    private static JedisPool jedisPool;
    private static ElasticsearchClient esClient;

    private static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) {

        Starter starter = new Starter(br);
        starter.loadConfig();
        symKey = starter.checkPassword();
        starter.checkConfig(symKey);

        ConfigOpenDrive config = new ConfigOpenDrive();

        SetterOpenDrive setter = new SetterOpenDrive(config, br);
        checkApis(setter);

        Service myService = starter.loadMyService(symKey);
        assert myService!=null;
        OpenDriveParams openDriveParams = OpenDriveParams.getParamsFromService(myService);
        assert openDriveParams!=null;

        Counter counter = new Counter(config.getListenPath(),config.getAccount(), config.getMinPayment(),false,esClient,apipClient,jedisPool);
        if(config.getListenPath()==null && !counter.isFromWebhook()) {
            String path = Inputer.inputPath(br);
            config.setListenPath(path);
        }
        config.setAccount(openDriveParams.getAccount());
        config.setMinPayment(openDriveParams.getMinPayment());

        ServiceManager serviceManager = new OpenDriveManager(config.getInitApipAccount(), OpenDriveParams.class);
        counter = new Counter(config.getListenPath(), openDriveParams.getAccount(), config.getMinPayment(),false,esClient,apipClient,jedisPool);
        Menu menu = new Menu();

        menu.add("Start the counter");
        menu.add("Manage the service");
        menu.add("Setting");

        menu.show();
        int choice = menu.choose(br);
        switch (choice){
            case 1 -> counter.run();
            case 2 -> serviceManager.manageService(br,symKey);
            case 3 -> setter.setting(symKey,br);
        }
    }

    private static void checkApis(SetterOpenDrive setterOpenDrive) {
        while(true) {
            apipClient = Starter.initApipClient;
            jedisPool = Starter.jedisPool;
            esClient = Starter.esClient;
            if (apipClient == null || jedisPool == null || esClient == null) {
                if( Inputer.askIfYes(br," ApipClient, jedisPool, and esClient is necessary. Reset them? y/n")) {
                    setterOpenDrive.resetDefaultApi(symKey);
                }else System.exit(0);
            }else break;
        }
    }
}
