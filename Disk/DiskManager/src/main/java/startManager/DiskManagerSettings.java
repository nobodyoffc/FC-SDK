package startManager;

import appTools.Menu;
import clients.apipClient.ApipClient;
import config.ApiType;
import constants.ApiNames;
import constants.FieldNames;
import clients.redisClient.RedisTools;
import constants.Strings;
import feip.feipData.Service;
import feip.feipData.serviceParams.DiskParams;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;
import config.Configure;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static appTools.Inputer.chooseOne;
import static constants.Constants.UserHome;
import static constants.FieldNames.SETTINGS;

public class DiskManagerSettings extends Settings {
    public static final int DEFAULT_WINDOW_TIME = 1000 * 60 * 5;
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

    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        System.out.println("Initiating service settings...");
        setInitForServer(sid, config, br);

        apipAccount = config.checkAPI(apipAccountId,ApiType.APIP,symKey);//checkApiAccount(apipAccountId,ApiType.APIP, config, symKey, null);
        checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount);
        if(apipAccount!=null)apipAccountId=apipAccount.getId();
        else System.out.println("No APIP service.");

        esAccount =  config.checkAPI(esAccountId,ApiType.ES,symKey);//checkApiAccount(esAccountId, ApiType.ES, config, symKey, null);
        if(esAccount!=null)esAccountId = esAccount.getId();
        else System.out.println("No ES service.");

        redisAccount =  config.checkAPI(redisAccountId,ApiType.REDIS,symKey);//checkApiAccount(redisAccountId,ApiType.REDIS,config,symKey,null);
        if(redisAccount!=null)redisAccountId = redisAccount.getId();
        else System.out.println("No Redis service.");

        ApipClient apipClient = (ApipClient) apipAccount.getClient();
        Service service = getMyService(sid, symKey, config, br, apipClient, DiskParams.class, ApiType.DISK);

        writeParamsToRedis(service.getSid(), (DiskParams) service.getParams(),(JedisPool)redisAccount.getClient(), DiskParams.class);

        apipAccount = checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount);
        apipAccountId = apipAccount.getId();

        config.saveConfig();
        saveSettings(service.getSid());
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
        menu.setName("Reset APIs for Disk manager");
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
    public void close() throws IOException {
        JedisPool jedisPool = (JedisPool)redisAccount.getClient();
        jedisPool.close();
        esAccount.closeEs();
        br.close();
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

    public static DiskManagerSettings loadMySettings(String sid, BufferedReader br, JedisPool jedisPool){
        DiskManagerSettings diskManagerSettings = DiskManagerSettings.loadFromFile(sid,DiskManagerSettings.class);
        if(diskManagerSettings ==null){
            diskManagerSettings = new DiskManagerSettings();
            diskManagerSettings.setSid(sid);
            diskManagerSettings.br = br;
            diskManagerSettings.inputAll(br);
            if(jedisPool!=null)diskManagerSettings.saveSettings(sid);
            return diskManagerSettings;
        }else {
            diskManagerSettings.br = br;
            diskManagerSettings.updateFromWebhook(br);
            if (!diskManagerSettings.fromWebhook && diskManagerSettings.getListenPath() == null)
                diskManagerSettings.inputListenPath(br);
            if (diskManagerSettings.getWindowTime() == 0) diskManagerSettings.setWindowTime(DEFAULT_WINDOW_TIME);
        }
        if(jedisPool!=null)diskManagerSettings.saveSettings(jedisPool);
        diskManagerSettings.setSid(sid);
        return diskManagerSettings;
    }
    public static DiskManagerSettings loadMySettings(String sid,BufferedReader br){
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
                if(fromWebhook)return;
                System.out.println("A listenPath is necessary to wake up the order scanning. \nGenerally it can be set to the blocks path.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void inputAll(BufferedReader br){
        try {
            fromWebhook = appTools.Inputer.promptAndSet(br, FieldNames.FROM_WEBHOOK,this.fromWebhook);
            if(fromWebhook){
                listenPath = System.getProperty(UserHome) + "/" + ApiNames.NewCashByFids;
            }else listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.listenPath);
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
    public void saveSettings(String id){
        writeToFile(id);
        if(redisAccount!=null) {
            JedisPool jedisPool = (JedisPool) redisAccount.getClient();
            try (Jedis jedis = jedisPool.getResource()) {
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, DiskManagerSettings.class);
            }
        }
    }

    public void saveSettings(JedisPool jedisPool){
        writeToFile(mainFid);
        try (Jedis jedis = jedisPool.getResource()) {
            RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, DiskManagerSettings.class);
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
            if(fromWebhook)listenPath = System.getProperty(UserHome) + "/" + ApiNames.NewCashByFids;
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

    public Long getWindowTime() {
        return windowTime;
    }

    public void setWindowTime(long windowTime) {
        this.windowTime = windowTime;
    }


}
