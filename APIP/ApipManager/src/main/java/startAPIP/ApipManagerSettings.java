package startAPIP;

import appTools.Menu;
import clients.apipClient.ApipClient;
import clients.redisClient.RedisTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.ApiAccount;
import config.ApiType;
import config.Configure;
import constants.ApiNames;
import constants.FieldNames;
import constants.Strings;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static appTools.Inputer.chooseOne;
import static constants.Constants.UserDir;
import static constants.Constants.UserHome;
import static constants.FieldNames.SETTINGS;

public class ApipManagerSettings extends Settings {
    public static final int DEFAULT_WINDOW_TIME = 1000 * 60 * 5;
    private long windowTime;
    private boolean forbidFreeApi;
    protected boolean scanMempool = true;
    private String avatarElementsPath = System.getProperty(UserDir)+"/avatar/elements";
    private String avatarPngPath = System.getProperty(UserDir)+"/avatar/png";
    private boolean ignoreOpReturn;

    public ApipManagerSettings(Configure config, BufferedReader br, JedisPool jedisPool) {
        super(config, br, jedisPool);
    }
    public ApipManagerSettings() {
    }

    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        System.out.println("Initiating service settings...");
        setInitForServer(sid, config, br);

        esAccount = checkEsAccount(esAccountId, config,symKey,null);
        if(esAccount.getClient()!=null)esAccountId = esAccount.getId();
        else System.out.println("No ES service.");

        nasaAccount = checkNasaRPC(nasaAccountId, config, symKey,null);
        if(nasaAccount.getClient()!=null)nasaAccountId=nasaAccount.getId();
        else System.out.println("No Nasa node RPC service.");

        redisAccount = checkRedisAccount(redisAccountId,config,symKey,null);
        if(redisAccount.getClient()!=null)redisAccountId = redisAccount.getId();
        else System.out.println("No Redis service.");

        ElasticsearchClient esClient = (ElasticsearchClient) esAccount.getClient();
        Service service = getMyService(sid, symKey, config, br, esClient, ApipParams.class, ApiType.APIP);

        config.saveConfig();
        saveSettings(mainFid);
        System.out.println("Initiated.");
        return service;
    }


    @Override
    public String initiateClient(String fid, byte[] symKey, Configure config, BufferedReader br) {
        return null;
    }

    @Override
    public void resetLocalSettings(byte[] symKey) {
        Menu menu = new Menu();
        menu.setName("Settings of Disk Manager");
        menu.add("Reset listenPath");
        menu.add("Reset fromWebhook switch");
        menu.add("Reset forbidFreeApi switch");
        menu.add("Reset window time");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> updateListenPath(br);
                case 2 -> updateFromWebhook(br);
                case 3 -> updateForbidFreeApi(br);
                case 4 -> updateWindowTime(br);
                case 0 -> {
                    System.out.println("Restart is necessary to active new settings.");
                    return;
                }
            }
        }
    }

    @Override
    public void resetApis(byte[] symKey,JedisPool jedisPool,ApipClient apipClient){
        Menu menu = new Menu();
        menu.setName("Reset APIs for APIP manager");
        menu.add("Reset APIP");
        menu.add("Reset ES");
        menu.add("Reset Redis");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> resetApi(symKey, apipClient, ApiType.APIP);
                case 2 -> resetApi(symKey, apipClient, ApiType.ES);
                case 3 -> resetApi(symKey, apipClient, ApiType.REDIS);
                default -> {
                    return;
                }
            }
        }
    }

    @Override
    public void close() {
        JedisPool jedisPool = (JedisPool)redisAccount.getClient();
        jedisPool.close();
        esAccount.closeEs();
    }
//    public Object resetDefaultApi(byte[] symKey, ApiType apiType) {
//        System.out.println("Reset API service...");
//        ApiProvider apiProvider = config.chooseApiProviderOrAdd();
//        ApiAccount apiAccount = config.chooseApiProvidersAccount(apiProvider, symKey);
//        Object client = null;
//        if (apiAccount != null) {
//            client = apiAccount.connectApi(apiProvider, symKey, br, null);
//            if (client != null) {
//                switch (apiType) {
//                    case APIP -> apipAccountId=apiAccount.getId();
//                    case ES -> esAccountId=apiAccount.getId();
//                    case REDIS -> redisAccountId=apiAccount.getId();
//                    default -> {
//                        return client;
//                    }
//                }
//                System.out.println("Done.");
//            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
//        } else System.out.println("Failed to get the apiAccount.");
//        return client;
//    }

    public static ApipManagerSettings loadMySettings(String sid, BufferedReader br, JedisPool jedisPool){
        ApipManagerSettings apipManagerSettings = ApipManagerSettings.loadFromFile(sid,ApipManagerSettings.class);
        if(apipManagerSettings ==null){
            apipManagerSettings = new ApipManagerSettings();
            apipManagerSettings.setSid(sid);
            apipManagerSettings.br = br;
            apipManagerSettings.inputAll(br);
            if(jedisPool!=null)apipManagerSettings.saveSettings(sid);
            return apipManagerSettings;
        }else {
            apipManagerSettings.br = br;
            if (apipManagerSettings.getWindowTime() == 0)
                apipManagerSettings.setWindowTime(DEFAULT_WINDOW_TIME);
        }
        if(jedisPool!=null)apipManagerSettings.saveSettings(jedisPool);
        apipManagerSettings.setSid(sid);
        return apipManagerSettings;
    }
    public static ApipManagerSettings loadMySettings(String sid,BufferedReader br){
        System.out.println("Load local settings...");
        return loadMySettings(sid,br,null);
    }

    public void inputListenPath(BufferedReader br){
        while(true) {
            try {
                listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH, this.listenPath);
                if(listenPath!=null){
                    if(new File(listenPath).exists())return;
                }
                System.out.println("A listenPath is necessary to wake up the order scanning. \nGenerally it can be set to the blocks path.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void inputAll(BufferedReader br){
        try {
            listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.listenPath);
            inputForbidFreeApi(br);
            inputWindowTime(br);
            saveSettings(mainFid);
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
    public void saveSettings(String mainFid){
        writeToFile(mainFid);
        if(redisAccount!=null) {
            JedisPool jedisPool = (JedisPool) redisAccount.getClient();
            try (Jedis jedis = jedisPool.getResource()) {
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, ApipManagerSettings.class);
            }
        }
    }

    public void saveSettings(JedisPool jedisPool){
        writeToFile(mainFid);
        try (Jedis jedis = jedisPool.getResource()) {
            RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, ApipManagerSettings.class);
        }
    }
    private void inputForbidFreeApi(BufferedReader br) {
        try {
            forbidFreeApi = appTools.Inputer.promptAndSet(br, FieldNames.FORBID_FREE_API,this.forbidFreeApi);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void updateForbidFreeApi(BufferedReader br) {
        try {
            forbidFreeApi = appTools.Inputer.promptAndSet(br, FieldNames.FORBID_FREE_API,this.forbidFreeApi);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        saveSettings(mainFid);
        System.out.println("It's '"+forbidFreeApi+"' now.");
        Menu.anyKeyToContinue(br);
    }

    private void inputWindowTime(BufferedReader br) {
        try {
            if(windowTime==0)windowTime = 3000;
            windowTime = appTools.Inputer.promptAndUpdate(br, Strings.WINDOW_TIME,this.windowTime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void updateWindowTime(BufferedReader br) {
        try {
            if(windowTime==0)windowTime = 3000;
            windowTime = appTools.Inputer.promptAndUpdate(br, Strings.WINDOW_TIME,this.windowTime);
            saveSettings(mainFid);
            System.out.println("It's '"+windowTime+"' now.");
            Menu.anyKeyToContinue(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateListenPath(BufferedReader br) {
        try {
            listenPath = appTools.Inputer.promptAndUpdate(br, FieldNames.LISTEN_PATH,this.listenPath);
            saveSettings(mainFid);
            System.out.println("It's '"+listenPath+"' now.");
            Menu.anyKeyToContinue(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateFromWebhook(BufferedReader br)  {
        try {
            fromWebhook = appTools.Inputer.promptAndSet(br, FieldNames.FROM_WEBHOOK,this.fromWebhook);
            if(fromWebhook)listenPath = System.getProperty(UserHome) + "/" + ApiNames.NewCashByFidsAPI;
            saveSettings(mainFid);
            System.out.println("It's '"+fromWebhook +"' now.");
            Menu.anyKeyToContinue(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public long getWindowTime() {
        return windowTime;
    }

    public void setWindowTime(long windowTime) {
        this.windowTime = windowTime;
    }

    public ApiAccount getNasaAccount() {
        return nasaAccount;
    }

    public void setNasaAccount(ApiAccount nasaAccount) {
        this.nasaAccount = nasaAccount;
    }

    public String getNasaAccountId() {
        return nasaAccountId;
    }

    public void setNasaAccountId(String nasaAccountId) {
        this.nasaAccountId = nasaAccountId;
    }

    public boolean isScanMempool() {
        return scanMempool;
    }

    public void setScanMempool(boolean scanMempool) {
        this.scanMempool = scanMempool;
    }

    public String getAvatarElementsPath() {
        return avatarElementsPath;
    }

    public void setAvatarElementsPath(String avatarElementsPath) {
        this.avatarElementsPath = avatarElementsPath;
    }

    public String getAvatarPngPath() {
        return avatarPngPath;
    }

    public void setAvatarPngPath(String avatarPngPath) {
        this.avatarPngPath = avatarPngPath;
    }

    public boolean isIgnoreOpReturn() {
        return ignoreOpReturn;
    }

    public void setIgnoreOpReturn(boolean ignoreOpReturn) {
        this.ignoreOpReturn = ignoreOpReturn;
    }
}
