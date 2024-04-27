package startManager;

import appTools.Menu;
import config.ApiAccount;
import config.ApiProvider;
import constants.FieldNames;
import clients.redisClient.RedisTools;
import constants.Strings;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;
import config.Configure;

import java.io.BufferedReader;
import java.io.IOException;

import static constants.FieldNames.SETTINGS;

public class DiskManagerSettings extends Settings {
    private long windowTime;
    private boolean forbidFreeApi;

    public DiskManagerSettings(Configure config, BufferedReader br, JedisPool jedisPool) {
        super(config, br, jedisPool);
    }
    public DiskManagerSettings(Configure config, BufferedReader br) {
        super(config, br);
    }
    public DiskManagerSettings() {
        super();
    }


    public void initiate(byte[] symKey, Configure config) {
        System.out.println("Initiating service settings...");
        this.config=config;
        if(apipAccountId==null) {
            apipAccount = config.checkAPI(apipAccountId,"APIP",symKey);
            apipAccountId=apipAccount.getId();
        }else{
            apipAccount = config.getApiAccountMap().get(apipAccountId);
            apipAccount.connectApi(config.getApiProviderMap().get(apipAccount.getSid()),symKey);
        }
        if(redisAccountId==null) {
            redisAccount = config.checkAPI(redisAccountId, "redis", symKey);
            redisAccountId = redisAccount.getId();
        }else {
            redisAccount = config.getApiAccountMap().get(redisAccountId);
            redisAccount.connectApi(config.getApiProviderMap().get(redisAccount.getSid()),symKey);
        }
        if(esAccountId==null) {
            esAccount = config.checkAPI(esAccountId,"ES",symKey);
            esAccountId = esAccount.getId();
        }else {
            esAccount = config.getApiAccountMap().get(esAccountId);
            esAccount.connectApi(config.getApiProviderMap().get(esAccount.getSid()),symKey);
        }
        saveSettings();
        System.out.println("Service settings initiated.");
    }
    @Override
    public void resetLocalSettings(byte[] symKey) {
        Menu menu = new Menu();
        menu.add("Reset listenPath");
        menu.add("Reset fromWebhook switch");
        menu.add("Reset forbidFreeApi switch");
        menu.add("Reset window time");
        int choice = menu.choose(br);
        menu.show();
        switch (choice){
            case 1 -> updateListenPath(br);
            case 2 -> updateFromWebhook(br);
            case 3 -> updateForbidFreeApi(br);
            case 4 -> updateWindowTime(br);
        }
    }

    @Override
    public void resetApis(byte[] symKey,JedisPool jedisPool){
        Menu menu = new Menu();
        menu.add("Reset APIP");
        menu.add("Reset ES");
        menu.add("Reset Redis");
        while (true) {
            System.out.println("Reset default API service...");
            ApiProvider apiProvider = config.chooseApiProviderOrAdd();
            ApiAccount apiAccount = config.chooseApiProvidersAccount(apiProvider, symKey);

            if (apiAccount != null) {
                Object client = apiAccount.connectApi(config.getApiProviderMap().get(apiAccount.getSid()), symKey, br, null);
                if (client != null) {
                    menu.show();
                    int choice = menu.choose(br);
                    switch (choice) {
                        case 1 -> apipAccountId=apiAccount.getId();
                        case 2 -> esAccountId=apiAccount.getId();
                        case 3 -> redisAccountId=apiAccount.getId();
                        default -> {
                            return;
                        }
                    }
                    config.saveConfig();
                    System.out.println("Done.");
                } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
            } else System.out.println("Failed to get the apiAccount.");
        }
    }

    @Override
    public void close() {
        JedisPool jedisPool = (JedisPool)redisAccount.getClient();
        jedisPool.close();
        esAccount.closeEs();
    }

    //    private static void checkApis(Starter starter) {
//        FreeDiskSetter freeDiskSetter = (FreeDiskSetter) starter.getSetter();
//        do {
//            if (apipClient == null) {
//                if (Inputer.askIfYes(br, "ApipClient is null, but its necessary. Set it now? y/n")) {
//                    apipClient = (ApipClient) freeDiskSetter.resetDefaultApi(symKey, ApiProvider.ApiType.APIP);
//                } else System.exit(0);
//            }
//
//            if (jedisPool == null) {
//                if (Inputer.askIfYes(br, "JedisPool is null, but its necessary. Set it them? y/n")) {
//                    jedisPool = (JedisPool) freeDiskSetter.resetDefaultApi(symKey, ApiProvider.ApiType.Redis);
//                } else System.exit(0);
//            }
//
//            if (esClient == null) {
//                if (Inputer.askIfYes(br, "EsClient is null, but its necessary. Set it them? y/n")) {
//                    esClient = (ElasticsearchClient) freeDiskSetter.resetDefaultApi(symKey, ApiProvider.ApiType.ES);
//                } else System.exit(0);
//            }
//        } while (apipClient == null || jedisPool == null || esClient == null);
//        starter.config.saveConfig(jedisPool);
//    }
    public Object resetDefaultApi(byte[] symKey, ApiProvider.ApiType apiType) {
        System.out.println("Reset API service...");
        ApiProvider apiProvider = config.chooseApiProviderOrAdd();
        ApiAccount apiAccount = config.chooseApiProvidersAccount(apiProvider, symKey);
        Object client = null;
        if (apiAccount != null) {
            client = apiAccount.connectApi(apiProvider, symKey, br, null);
            if (client != null) {
                switch (apiType) {
                    case APIP -> apipAccountId=apiAccount.getId();
                    case ES -> esAccountId=apiAccount.getId();
                    case Redis -> redisAccountId=apiAccount.getId();
                    default -> {
                        return client;
                    }
                }
                System.out.println("Done.");
            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
        } else System.out.println("Failed to get the apiAccount.");
        return client;
    }

    public static DiskManagerSettings loadMySettings(String sid, BufferedReader br, JedisPool jedisPool){
        DiskManagerSettings diskManagerSettings = DiskManagerSettings.loadFromFile(sid,DiskManagerSettings.class);

        if(diskManagerSettings ==null){
            diskManagerSettings = new DiskManagerSettings();
            diskManagerSettings.br = br;
            diskManagerSettings.inputAll(br);
            if(jedisPool!=null)diskManagerSettings.saveSettings();
            return diskManagerSettings;
        }else diskManagerSettings.br = br;
        if(diskManagerSettings.getListenPath()==null) diskManagerSettings.inputListenPath(br);
        if(diskManagerSettings.getWindowTime()==0) diskManagerSettings.setWindowTime(1000*60*5);
        if(jedisPool!=null)diskManagerSettings.saveSettings(jedisPool);
        diskManagerSettings.setSid(sid);
        return diskManagerSettings;
    }
    public static DiskManagerSettings loadMySettings(String sid,BufferedReader br){
        return loadMySettings(sid,br,null);
    }

    public void inputListenPath(BufferedReader br){
        try {
            listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.listenPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void inputAll(BufferedReader br){
        try {
            fromWebhook = appTools.Inputer.promptAndSet(br, FieldNames.FROM_WEBHOOK,this.fromWebhook);
            if(!fromWebhook)listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.listenPath);
            updateForbidFreeApi(br);
            updateWindowTime(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateAll(BufferedReader br){
        updateFromWebhook(br);
        if(!fromWebhook) updateListenPath(br);
        updateForbidFreeApi(br);
        updateWindowTime(br);
    }

    @Override
    public void saveSettings(){
        writeToFile();
        if(redisAccount!=null) {
            JedisPool jedisPool = (JedisPool) redisAccount.getClient();
            try (Jedis jedis = jedisPool.getResource()) {
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, DiskManagerSettings.class);
            }
        }
    }

    public void saveSettings(JedisPool jedisPool){
        writeToFile();
        try (Jedis jedis = jedisPool.getResource()) {
            RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, DiskManagerSettings.class);
        }
    }

    private void updateForbidFreeApi(BufferedReader br) {
        try {
            forbidFreeApi = appTools.Inputer.promptAndSet(br, FieldNames.FORBID_FREE_API,this.forbidFreeApi);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void updateWindowTime(BufferedReader br) {
        try {
            if(windowTime==0)windowTime=300;
            windowTime = appTools.Inputer.promptAndUpdate(br, Strings.WINDOW_TIME,this.windowTime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateListenPath(BufferedReader br) {
        try {
            listenPath = appTools.Inputer.promptAndUpdate(br, FieldNames.LISTEN_PATH,this.listenPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateFromWebhook(BufferedReader br)  {
        try {
            fromWebhook = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.fromWebhook);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public String getListenPath() {
        return listenPath;
    }

    public void setListenPath(String listenPath) {
        this.listenPath = listenPath;
    }

    public boolean isFromWebhook() {
        return fromWebhook;
    }

    public void setFromWebhook(boolean fromWebhook) {
        this.fromWebhook = fromWebhook;
    }

    public boolean isForbidFreeApi() {
        return forbidFreeApi;
    }

    public void setForbidFreeApi(boolean forbidFreeApi) {
        this.forbidFreeApi = forbidFreeApi;
    }

    public String getRedisAccountId() {
        return redisAccountId;
    }

    public void setRedisAccountId(String redisAccountId) {
        this.redisAccountId = redisAccountId;
    }

    public String getEsAccountId() {
        return esAccountId;
    }

    public void setEsAccountId(String esAccountId) {
        this.esAccountId = esAccountId;
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public ApiAccount getApipAccount() {
        return apipAccount;
    }

    public void setApipAccount(ApiAccount apipAccount) {
        this.apipAccount = apipAccount;
    }

    public ApiAccount getEsAccount() {
        return esAccount;
    }

    public void setEsAccount(ApiAccount esAccount) {
        this.esAccount = esAccount;
    }

    public ApiAccount getRedisAccount() {
        return redisAccount;
    }

    public void setRedisAccount(ApiAccount redisAccount) {
        this.redisAccount = redisAccount;
    }

    public long getWindowTime() {
        return windowTime;
    }

    public void setWindowTime(long windowTime) {
        this.windowTime = windowTime;
    }


}
