package server;

import apip.apipData.CidInfo;
import apip.apipData.Fcdsl;
import clients.apipClient.ApipClient;
import clients.apipClient.DataGetter;
import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.ApiProvider;
import config.ApiType;
import constants.IndicesNames;
import crypto.KeyTools;
import fch.FchMainNetwork;
import feip.feipData.Service;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import config.ApiAccount;
import config.Configure;
import constants.FieldNames;
import constants.Strings;
import constants.Values;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import fcData.AlgorithmId;
import feip.feipData.serviceParams.DiskParams;
import feip.feipData.serviceParams.Params;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static appTools.Inputer.askIfYes;
import static appTools.Inputer.chooseOne;
import static config.Configure.getSymKeyFromPasswordAndNonce;
import static constants.Strings.*;

public abstract class Settings {
    final static Logger log = LoggerFactory.getLogger(Settings.class);
    protected String sid;
    protected transient Configure config;
    protected transient BufferedReader br;
    protected String mainFid;
    protected String mainFidPriKeyCipher;
    protected String listenPath;
    protected boolean fromWebhook;
    protected transient ApiAccount apipAccount;
    protected transient ApiAccount esAccount;
    protected transient ApiAccount redisAccount;
    protected String apipAccountId;
    protected String esAccountId;
    protected String redisAccountId;
    private transient JedisPool jedisPool;
    private static String fileName;
    public static String SETTINGS_DOT_JSON = "settings.json";
    protected String nasaAccountId;
    protected transient ApiAccount nasaAccount;
    protected Map<String,Long> bestHeightMap;
    public static List<String> freeApipUrlList;

    public Settings(Configure config, BufferedReader br, JedisPool jedisPool) {
        this.config = config;
        this.br = br;
        this.jedisPool = jedisPool;
        freeApipUrlList = config.getFreeApipUrlList();
    }

    public Settings(Configure config, BufferedReader br) {
        this.config = config;
        this.br = br;
    }

    public Settings() {}

    public static ApipClient getFreeApipClient(){
        return getFreeApipClient(null);
    }
    public static ApipClient getFreeApipClient(BufferedReader br){
        ApipClient apipClient = new ApipClient();
        ApiAccount apipAccount = new ApiAccount();
        for(String url : freeApipUrlList){
            apipAccount.setApiUrl(url);
            apipClient.setApiAccount(apipAccount);
            if(apipClient.pingFree(ApiType.APIP))
                return apipClient;
        }
        if(br !=null) {
            if (askIfYes(br, "Failed to get free APIP service. Add new?")) {
                do {
                    String url = fch.Inputer.inputString(br, "Input the urlHead of the APIP service:");
                    apipAccount.setApiUrl(url);
                    apipClient.setApiAccount(apipAccount);
                    if (apipClient.pingFree(ApiType.APIP)) {
                        freeApipUrlList.add(url);
                        return apipClient;
                    }
                } while (askIfYes(br, "Failed to ping this APIP Service. Try more?"));
            }
        }
        return null;
    }
    public static String makeFileName(String id){
        if(id==null)return SETTINGS_DOT_JSON;
        return id.substring(0,6)+"_" +SETTINGS_DOT_JSON;
    }


    public static String getLocalDataDir(String sid){
        return System.getProperty("user.dir")+"/"+ addSidBriefToName(sid,DATA)+"/";
    }

    public static String addSidBriefToName(String sid, String name) {
        String finalName;
        finalName = (sid.substring(0,6) + "_" + name);
        return finalName;
    }

    public static void setNPrices(String sid, String[] ApiNames, Jedis jedis, BufferedReader br) {
        for(String api : ApiNames){
            String ask = "Set the price multiplier for " + api + "?y/n Enter to leave default 1:";
            int input = Inputer.inputInteger(br, ask,0);
            if(input==0)input=1;
            jedis.hset(addSidBriefToName(sid,Strings.N_PRICE),api, String.valueOf(input));
        }
    }

    public CidInfo checkFidInfo(ApipClient apipClient, BufferedReader br) {
        CidInfo fidInfo = getCidInfo(mainFid,apipClient);

        if(fidInfo!=null) {
            long bestHeight = apipClient.getClientData().getResponseBody().getBestHeight();

            bestHeightMap.put(IndicesNames.CID,bestHeight);
            System.out.println("My information:\n" + JsonTools.getNiceString(fidInfo));
            Menu.anyKeyToContinue(br);
            if(fidInfo.getBalance()==0){
                System.out.println("No fch yet. Send some fch to "+mainFid);
                Menu.anyKeyToContinue(br);
            }
        }else{
            System.out.println("New FID. Send some fch to it: "+mainFid);
            Menu.anyKeyToContinue(br);
        }
        return fidInfo;
    }

    @Nullable
    public static CidInfo getCidInfo(String fid,ApipClient apipClient) {
        System.out.println("Get CID information...");
        apipClient.cidInfoByIds(new String[]{fid});
        Object data = apipClient.checkResult();
        if(data==null) {
            apipClient.getClientData().getResponseBody().printCodeMessage();
            return null;
        }
        Map<String,CidInfo> cidInfoMap = DataGetter.getCidInfoMap(data);
        CidInfo fidInfo = cidInfoMap.get(fid);
        if(fidInfo==null){
            System.out.println("Fid is not found on chain.");
            return null;
        }
        return fidInfo;
    }

    protected void setInitForClient(String fid, Configure config, BufferedReader br) {
        this.config= config;
        this.br= br;
        freeApipUrlList = config.getFreeApipUrlList();
        this.mainFid = fid;
        if(bestHeightMap==null)bestHeightMap=new HashMap<>();
    }

    public abstract Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br);
    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ApipClient apipClient, Class<?> paramsClass, ApiType apiType) {
        return getMyService(sid, symKey, config, br, apipClient,null,DiskParams .class, ApiType.DISK);
    }
    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ElasticsearchClient esClient,  Class<?> paramsClass, ApiType apiType) {
        return getMyService(sid, symKey, config, br, null,esClient,DiskParams .class, ApiType.DISK);
    }
    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ApipClient apipClient, ElasticsearchClient esClient, Class<?> paramsClass, ApiType apiType) {
        Service service = null;
        if(sid ==null) {
            String owner = chooseOne(config.getOwnerList(), "Choose the owner:", br);
            if (owner == null)
                owner = config.addOwner(br);
            service = config.chooseOwnerService(owner, symKey, apiType, esClient, apipClient);
        }else {
            try {
                if(esClient !=null)
                    service = EsTools.getById(esClient, IndicesNames.SERVICE, sid,Service.class);
                else if(apipClient !=null){
                    service = apipClient.serviceById(sid);
                }
            } catch (IOException e) {
                System.out.println("Failed to get service from ES.");
                return null;
            }
        }

        if(service==null)return null;
        Params params = (Params) Params.getParamsFromService(service, paramsClass);
        if(params==null)return service;
        service.setParams(params);
        this.sid = service.getSid();
        mainFid = params.getAccount();
        if(config.getFidCipherMap().get(mainFid)==null)
            config.addUser(mainFid, symKey);
        mainFidPriKeyCipher = config.getFidCipherMap().get(mainFid);
        return service;
    }

    protected void setInitForServer(String sid, Configure config, BufferedReader br) {
        this.config= config;
        this.br = br;
        this.sid = sid;
        freeApipUrlList = config.getFreeApipUrlList();
    }

    public abstract String initiateClient(String fid, byte[] symKey, Configure config, BufferedReader br);

    public Service loadMyService(String owner, String[] types){
        System.out.println("Load my services from APIP...");

        List<Service> serviceList = getMyServiceList(owner,true, types,(ApipClient)apipAccount.getClient());

        if (serviceList == null) {
            System.out.println("Load swap services wrong.");
            return null;
        }
        return selectMyService(serviceList);
    }

    private List<Service> getMyServiceList(String owner, boolean onlyActive, String[] types, ApipClient apipClient) {

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPES).setValues(types);
        fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.OWNER).setValues(owner);
        if(onlyActive)fcdsl.addNewExcept().addNewTerms().addNewFields(Strings.ACTIVE).addNewValues(Values.FALSE);
        fcdsl.addSize(100);

        fcdsl.addNewSort(FieldNames.LAST_HEIGHT,Strings.DESC);
        return apipClient.serviceSearch(fcdsl);
    }

    public Service selectMyService(List<Service> serviceList){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServiceList(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            return null;
        }
        return serviceList.get(choice-1);
    }
    public static void showServiceList(List<Service> serviceList) {
        String title = "Services";
        String[] fields = new String[]{"",FieldNames.STD_NAME,FieldNames.SID};
        int[] widths = new int[]{2,24,64};
        List<List<Object>> valueListList = new ArrayList<>();
        int i=1;
        for(Service service : serviceList){
            List<Object> valueList = new ArrayList<>();
            valueList.add(i);
            valueList.add(service.getStdName());
            valueList.add(service.getSid());
            valueListList.add(valueList);
            i++;
        }
        Shower.showDataTable(title,fields,widths,valueListList);
    }

    private static String inputOwner(BufferedReader br) {
        String input = fch.Inputer.inputGoodFid(br, "Set the owner FID. Enter to create a new one:");
        if ("".equals(input)) {
            ECKey ecKey = KeyTools.genNewFid(br);
            input = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
        }
        return input;
    }

    public static <T> T loadFromFile(String id,Class<T> tClass){
        if(id==null)return null;
        try {
            fileName = makeFileName(id);
            return JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, tClass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public ApiAccount checkFcAccount(String accountId, ApiType apiType, Configure config, byte[] symKey,ApipClient apipClient) {
        ApiAccount fcApiAccount;
        if(accountId ==null) {
            ApiProvider apiProvider = config.selectFcApiProvider((ApipClient)apipAccount.getClient(),apiType);
            if(apiProvider==null)return null;
            config.getApiProviderMap().put(apiProvider.getSid(),apiProvider);
            apiProvider.setType(apiType);
            fcApiAccount = config.chooseApiProvidersAccount(apiProvider,symKey,apipClient);
        }else {
            fcApiAccount = config.getApiAccountMap().get(accountId);
            fcApiAccount.setApipClient((ApipClient) apipAccount.getClient());
            Object result = fcApiAccount.connectApi(config.getApiProviderMap().get(fcApiAccount.getSid()), symKey);
            if(result==null) {
                if( Inputer.askIfYes(this.br,"Failed to connected the "+apiType+" server. Continue?")){
                    return null;
                }else {
                    System.exit(0);
                }
            }
            fcApiAccount.setClient(result);
        }
        return checkIfMainFidIsApiAccountUser(symKey,config,br,fcApiAccount);
    }





    public ApiAccount checkApipAccount(String apipAccountId, Configure config, byte[] symKey,ApipClient apipClient) {
        ApiAccount apipAccount;
        if (apipAccountId == null) {
            apipAccount = config.checkAPI(apipAccountId, ApiType.APIP, symKey,apipClient);
        }else {
            apipAccount = config.getApiAccountMap().get(apipAccountId);
            Object client = apipAccount.connectApi(config.getApiProviderMap().get(apipAccount.getSid()), symKey);
            apipAccount.setApipClient((ApipClient) client);
        }

        return checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount);
    }

    public ApiAccount checkEsAccount(String esAccountId, Configure config, byte[] symKey,ApipClient apipClient) {
        ApiAccount esAccount = null;
        if(esAccountId==null) {
            esAccount = config.checkAPI(esAccountId, ApiType.ES, symKey,null);
        }else {
            esAccount = config.getApiAccountMap().get(esAccountId);
            Object client = esAccount.connectApi(config.getApiProviderMap().get(esAccount.getSid()), symKey);
            esAccount.setClient(client);
        }
        return esAccount;
    }

    public ApiAccount checkRedisAccount(String redisAccountId, Configure config, byte[] symKey,ApipClient apipClient) {
        ApiAccount redisAccount = null;
        if(redisAccountId==null) {
                redisAccount = config.checkAPI(redisAccountId, ApiType.REDIS, symKey,apipClient);
        }else {
            redisAccount = config.getApiAccountMap().get(redisAccountId);
            Object client = redisAccount.connectApi(config.getApiProviderMap().get(redisAccount.getSid()), symKey);
            redisAccount.setClient(client);
        }
        return redisAccount;
    }

    public ApiAccount checkNasaRPC(String nasaAccountId, Configure config, byte[] symKey,ApipClient apipClient) {
        ApiAccount apiAccount = null;
        if(nasaAccountId==null) {
            apiAccount = config.checkAPI(nasaAccountId, ApiType.NASA_RPC, symKey,apipClient);
        }else {
            apiAccount = config.getApiAccountMap().get(nasaAccountId);
            Object result = apiAccount.connectApi(config.getApiProviderMap().get(apiAccount.getSid()), symKey);
            if(result==null) {
                if( Inputer.askIfYes(this.br,"Failed to connected the Nasa node RPC. Continue?")){
                    return null;
                }else {
                    System.exit(0);
                }
            }
            apiAccount.setClient(result);
        }
        return apiAccount;
    }

    public String chooseFid(Configure config, BufferedReader br, byte[] symKey) {
        String fid = fch.Inputer.chooseOne(config.getFidCipherMap().keySet().toArray(new String[0]), "Choose fid:",br);
        if(fid==null)fid =config.addUser(symKey);
        return fid;
    }

    public abstract void inputAll(BufferedReader br);

    public abstract void updateAll(BufferedReader br);

    public void writeToFile(String id){
        fileName = makeFileName(id);
        JsonTools.writeObjectToJsonFile(this,Configure.getConfDir(),fileName,false);
    }

    public abstract void saveSettings(String mainFid);

    public void setting( byte[] symKey, BufferedReader br) {
        System.out.println("Setting...");
        while (true) {
            Menu menu = new Menu();
            menu.setName("Settings");
            menu.add("Reset password",
                    "Add API provider",
                    "Add API account",
                    "Update API provider",
                    "Update API account",
                    "Delete API provider",
                    "Delete API account",
                    "Reset my local settings",
                    "Reset Default APIs"
            );
            menu.show();
            int choice = menu.choose(br);
            ApipClient apipClient = (ApipClient) apipAccount.getClient();
            switch (choice) {
                case 1 -> {
                    byte[] newSymKey=resetPassword();
                    if(newSymKey==null)break;
                    symKey = newSymKey;
                }
                case 2 -> config.addApiProviderAndConnect(symKey,apipClient);
                case 3 -> config.addApiAccount(symKey, apipClient);
                case 4 -> config.updateApiProvider(symKey,apipClient);
                case 5 -> config.updateApiAccount(config.chooseApiProviderOrAdd(config.getApiProviderMap(), apipClient),symKey,apipClient);
                case 6 -> config.deleteApiProvider(symKey,apipClient);
                case 7 -> config.deleteApiAccount(symKey,apipClient);
                case 8 -> resetLocalSettings(symKey);
                case 9 -> resetApis(symKey,jedisPool,apipClient);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public byte[] resetPassword(){
        System.out.println("Reset password...");
        byte[] oldSymKey;
        byte[] oldNonceBytes;
        byte[] oldPasswordBytes;
        while(true) {
            oldPasswordBytes = Inputer.getPasswordBytes(br);
            oldNonceBytes = Hex.fromHex(config.getNonce());
            oldSymKey = getSymKeyFromPasswordAndNonce(oldPasswordBytes, oldNonceBytes);//Hash.sha256x2(BytesTools.bytesMerger(oldPasswordBytes, oldNonceBytes));
            CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymKey(config.getNonceCipher(), oldSymKey);
            if(cryptoDataByte.getCode()!=0){
                System.out.println("Wrong password. Try again.");
                continue;
            }
            byte[] oldNonce = cryptoDataByte.getData();
            if (oldNonce==null || ! config.getNonce().equals(Hex.toHex(oldNonce))) {
                System.out.println("Password wrong. Reset it.");
                config.setNonce(null);
                continue;
            }

            byte[] newPasswordBytes = Inputer.resetNewPassword(br);
            if(newPasswordBytes==null)return null;
            byte[] newNonce = BytesTools.getRandomBytes(16);
            byte[] newSymKey =  getSymKeyFromPasswordAndNonce(newPasswordBytes, newNonce);

            CryptoDataByte cryptoDataByte1 = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7).encryptBySymKey(newNonce, newSymKey);
            if(cryptoDataByte1.getCode()!=0){
                System.out.println("Failed to encrypt with new symKey.");
                continue;
            }
            String newNonceCipher = cryptoDataByte1.toJson();
            config.setNonce(Hex.toHex(newNonce));
            config.setNonceCipher(newNonceCipher);

            if(config.getApiAccountMap()==null||config.getApiAccountMap().isEmpty())return newSymKey;
            for(ApiAccount apiAccount : config.getApiAccountMap().values()){
                if(apiAccount.getPasswordCipher()!=null){
                    String cipher = apiAccount.getPasswordCipher();
                    String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                    apiAccount.setPasswordCipher(newCipher);
                }
                if(apiAccount.getUserPriKeyCipher()!=null){
                    String cipher = apiAccount.getUserPriKeyCipher();
                    String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                    apiAccount.setUserPriKeyCipher(newCipher);
                    apiAccount.setUserPubKey(ApiAccount.makePubKey(newCipher,newSymKey));
                }
                if(apiAccount.getSession()!=null && apiAccount.getSession().getSessionKeyCipher()!=null){
                    String cipher = apiAccount.getSession().getSessionKeyCipher();
                    String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                    apiAccount.getSession().setSessionKeyCipher(newCipher);
                }
            }

            if(jedisPool!=null){
                try(Jedis jedis = jedisPool.getResource()){
                    String oldSymKeyCipher = jedis.hget(addSidBriefToName(sid,CONFIG),INIT_SYM_KEY_CIPHER);
                    if(oldSymKeyCipher!=null) {
                        String newCipher = replaceCipher(oldSymKeyCipher, oldNonce, newNonce);
                        jedis.hset(addSidBriefToName(sid,CONFIG), INIT_SYM_KEY_CIPHER, newCipher);
                    }
                }
            }
            config.saveConfig();

            BytesTools.clearByteArray(oldPasswordBytes);
            BytesTools.clearByteArray(newPasswordBytes);
            BytesTools.clearByteArray(oldSymKey);

            return newSymKey;
        }
    }

    private String replaceCipher(String oldCipher, byte[] oldSymKey, byte[] newSymKey) {
        byte[] data = new Decryptor().decryptJsonBySymKey(oldCipher, oldSymKey).getData();
        return new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7).encryptBySymKey(data,newSymKey).toJson();
    }

//    public void inputCounterPriKeyCipher(String account, byte[] symKey, BufferedReader br) {
//        while(true) {
//            System.out.println("Input the priKey cipher of your account");
//            byte[] priKey32 = KeyTools.inputCipherGetPriKey(br);
//            String fid = KeyTools.priKeyToFid(priKey32);
//            if (fid.equals(account)){
//                accountPriKeyCipher = fch.Inputer.makePriKeyCipher(priKey32, symKey);
//                break;
//            }
//            System.out.println("You inputted the priKey of fid. But your service account is:"+ account +". Try again.");
//        }
//    }

    public ApiAccount checkIfMainFidIsApiAccountUser(byte[] symKey, Configure config, BufferedReader br, ApiAccount apiAccount) {
        String apiAccountId = apiAccount.getId();
        if(mainFid==null)return apiAccount;

        if(mainFid.equals(apiAccount.getUserId())) {
            mainFidPriKeyCipher = apiAccount.getUserPriKeyCipher();
            config.getFidCipherMap().put(mainFid,mainFidPriKeyCipher);
        }else{
            if(askIfYes(br,"Your service account "+mainFid+" is not the user of the API account "+apiAccount.getUserId()+". \nReset API account?")){
                while(true) {
                    apiAccount = config.setApiService(symKey, ApiType.APIP,null);
                    if(mainFid.equals(apiAccount.getUserId())){
                        mainFidPriKeyCipher = config.getApiAccountMap().get(apiAccountId).getUserPriKeyCipher();
                        break;
                    }
                    System.out.println("The API user is still not your service account. Reset API account again.");
                }
            }else {
                config.addUser(mainFid, symKey);
                mainFidPriKeyCipher = config.getFidCipherMap().get(mainFid);
            }
        }

        return apiAccount;
    }

    public abstract void resetLocalSettings(byte[] symKey);

//    public abstract Object resetDefaultApi(byte[] symKey, ApiType apiType);
    public abstract void resetApis(byte[] symKey,JedisPool jedisPool,ApipClient apipClient);

    public void resetApi(byte[] symKey, ApipClient apipClient, ApiType type) {
        System.out.println("Reset default API service...");
        ApiProvider apiProvider = config.chooseApiProviderOrAdd(config.getApiProviderMap(), type, apipClient);
        ApiAccount apiAccount = config.chooseApiProvidersAccount(apiProvider, symKey, apipClient);
        if (apiAccount != null) {
            Object client = apiAccount.connectApi(config.getApiProviderMap().get(apiAccount.getSid()), symKey, br, null);
            if (client != null) {
                config.saveConfig();
                System.out.println("Done.");
            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
        } else System.out.println("Failed to get the apiAccount.");
        switch (type){
            case APIP -> apipAccountId=apiAccount.getId();
            case ES -> esAccountId=apiAccount.getId();
            case REDIS -> redisAccountId=apiAccount.getId();
        }
    }

    public abstract void close() throws IOException;

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public Configure getConfig() {
        return config;
    }

    public void setConfig(Configure config) {
        this.config = config;
    }

    public BufferedReader getBr() {
        return br;
    }

    public void setBr(BufferedReader br) {
        this.br = br;
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public String getNasaAccountId() {
        return nasaAccountId;
    }

    public void setNasaAccountId(String nasaAccountId) {
        this.nasaAccountId = nasaAccountId;
    }

    public ApiAccount getNasaAccount() {
        return nasaAccount;
    }

    public void setNasaAccount(ApiAccount nasaAccount) {
        this.nasaAccount = nasaAccount;
    }

    public String getMainFid() {
        return mainFid;
    }

    public void setMainFid(String mainFid) {
        this.mainFid = mainFid;
    }

    public String getMainFidPriKeyCipher() {
        return mainFidPriKeyCipher;
    }

    public void setMainFidPriKeyCipher(String mainFidPriKeyCipher) {
        this.mainFidPriKeyCipher = mainFidPriKeyCipher;
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public String getEsAccountId() {
        return esAccountId;
    }

    public void setEsAccountId(String esAccountId) {
        this.esAccountId = esAccountId;
    }

    public String getRedisAccountId() {
        return redisAccountId;
    }

    public void setRedisAccountId(String redisAccountId) {
        this.redisAccountId = redisAccountId;
    }

    public Map<String, Long> getBestHeightMap() {
        return bestHeightMap;
    }

    public void setBestHeightMap(Map<String, Long> bestHeightMap) {
        this.bestHeightMap = bestHeightMap;
    }
}
