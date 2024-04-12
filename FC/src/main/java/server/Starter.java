package server;

import APIP.apipClient.ApipClient;
import APIP.apipClient.ApipDataGetter;
import APIP.apipData.BlockInfo;
import APIP.apipData.Fcdsl;
import FCH.FchMainNetwork;
import FEIP.feipData.Cid;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.SwapParams;
import NaSa.NaSaRpcClient;
import appTools.Inputer;
import appTools.Shower;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.google.gson.Gson;
import config.*;
import constants.FieldNames;
import constants.Strings;
import constants.UpStrings;
import constants.Values;
import crypto.cryptoTools.Hash;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.HttpMethods;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.serviceManagers.SwapManager;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static config.Config.CONFIG_DOT_JSON;
import static constants.Constants.COIN_TO_SATOSHI;

public class Starter {
    final static Logger log = LoggerFactory.getLogger(Starter.class);
    private Config config;
    private BufferedReader br;
    private Service myService;
    private ApipClient initApipClient;
    public static String sidBrief;
    private JedisPool jedisPool;
    private ElasticsearchClient esClient;
    private NaSaRpcClient naSaRpcClient;
    private static String MY_SERVICE_DOT_JSON = "myService.json";
    public Starter(BufferedReader br) {
        this.br =br;
    }

    public static void main(String[] args) {
        Starter starter = new Starter(new BufferedReader(new InputStreamReader(System.in)));
        //Load config
        starter.config=starter.loadConfig();

        //Set password
        byte[] symKey = starter.checkPassword();

//        Setting setting = new Setting(config,br);
//        symKey = setting.resetPassword();
//        setting.updateApiProvider();
//        setting.updateApiAccount(setting.choseApiProvider(),symKey);
//        setting.deleteApiProvider();
//        setting.deleteApiAccount(symKey);


        //Set config, add Api providers, connect APIP service
        boolean done = starter.checkConfig(symKey);
        if(!done)return;
        starter.config.showApiProviders(starter.config.getApiProviderMap());
        starter.config.showAccounts(starter.config.getApiAccountMap());

        //test initial APIP. Load my service from APIP
        if(starter.initApipClient==null)return;
        starter.myService = starter.loadMyService(symKey, new String[]{UpStrings.SWAP, Values.SWAP});
        if(starter.myService==null)
            new SwapManager(starter.initApipClient.getApiAccount(), SwapParams.class).publishService(symKey,starter.br);

        SwapParams params = parseMyServiceParams(starter.myService, SwapParams.class);
        starter.myService.setParams(params);
        JsonTools.gsonPrint(starter.config);
        JsonTools.gsonPrint(starter.myService);
        starter.showInitApipBalance();
        Map<String, BlockInfo> block = starter.initApipClient.blockByHeights(new String[]{"2"}, HttpMethods.POST);
        JsonTools.gsonPrint(block.get("2"));

        //test order scanner

        //test es
        try {
            ApiAccount apiAccount = starter.config.getApiAccountMap().get(starter.config.getEsAccountId());
            ElasticsearchClient esClient = (ElasticsearchClient) apiAccount.getClient();
            GetResponse<Cid> cidResult = esClient.get(g -> g.index("cid").id("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7"), Cid.class);
            assert cidResult.source() != null;
            JsonTools.gsonPrint(cidResult.source());
            apiAccount.closeEsClient();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //test jedis
        ApiAccount apiAccount = starter.config.getApiAccountMap().get(starter.config.getRedisAccountId());
        JedisPool jedisPool = (JedisPool) apiAccount.getClient();
        try(Jedis jedis = jedisPool.getResource()){
            jedis.set("testStarter","good jedis");
            System.out.println(jedis.get("testStarter"));
        }

        //test NaSa node
        ApiAccount apiAccount1 = starter.config.getApiAccountMap().get(starter.config.getNaSaNodeAccountId());
        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) apiAccount1.getClient();
        JsonTools.gsonPrint(naSaRpcClient.getBlockchainInfo());
    }

    public static  <T> T parseMyServiceParams(Service myService, Class<T> tClass){
        Gson gson = new Gson();
        T params = gson.fromJson(gson.toJson(myService.getParams()), tClass);
        myService.setParams(params);
        return params;
    }

    public static String addSidBriefToName(String name) {
        String finalName;
        finalName = (sidBrief + "_" + name);
        return finalName;
    }

    public Config loadConfig(){
        Config config1;
        try {

            config1 = JsonTools.readObjectFromJsonFile(null, CONFIG_DOT_JSON, Config.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(config1==null){
            log.debug("Failed to load config from "+ CONFIG_DOT_JSON);
            config1 = new ConfigSwap();
        }
        config = config1;
        return config1;
    }

    public byte[] checkPassword(){
        byte[] symKey;
        byte[] randomBytes;
        byte[] passwordBytes;

        if (config == null || config.getNonce() == null) {
            while (true) {
                passwordBytes = Inputer.resetNewPassword(br);
                if (passwordBytes == null){
                    System.out.println("Input wrong. Try again.");
                    continue;
                }
                randomBytes = BytesTools.getRandomBytes(16);
                symKey = Hash.Sha256x2(BytesTools.bytesMerger(passwordBytes, randomBytes));
                if (config == null) config = new ConfigSwap();
                config.setNonce(Hex.toHex(randomBytes));
                String nonceCipher = EccAes256K1P7.encryptWithSymKey(Hex.fromHex(config.getNonce()), symKey);
                if (nonceCipher.contains("Error")) {
                    System.out.println("Failed to encrypt the nonce with the symKey. Try again.");
                    continue;
                }
                config.setNonceCipher(nonceCipher);
                config.saveConfig(jedisPool);
                return symKey;
            }
        } else {
            while(true) {
                randomBytes = Hex.fromHex(config.getNonce());
                passwordBytes = Inputer.getPasswordBytes(br);
                symKey = Hash.Sha256x2(BytesTools.bytesMerger(passwordBytes, randomBytes));
                byte[] result = EccAes256K1P7.decryptJsonBytes(config.getNonceCipher(), symKey);
                if (result==null || ! config.getNonce().equals(Hex.toHex(result))) {
                    System.out.println("Password wrong. Input it again.");
                    continue;
                }
                BytesTools.clearByteArray(passwordBytes);
                return symKey;
            }
        }
    }

    public boolean checkConfig(byte[] symKey){
        System.out.println("Check the config...");
        if(config==null)loadConfig();

        if(config.getOwner()==null) initApiAccounts(symKey);

        if(config.getInitApipAccountId()!=null) {
            if(!loadApiClient(config.getInitApipAccountId(),symKey)){
                config.setInitApipAccountId(null);
                config.saveConfig(jedisPool);
                return false;
            }
        }

        if(config.getNaSaNodeAccountId()!=null) {
            if(!loadApiClient(config.getNaSaNodeAccountId(), symKey)){
                config.setNaSaNodeAccountId(null);
                config.saveConfig(jedisPool);
                return false;
            }
        }

        if(config.getEsAccountId()!=null) {
            if(!loadApiClient(config.getEsAccountId(), symKey)) {
                config.setEsAccountId(null);
                config.saveConfig(jedisPool);
                return false;
            }
        }

        if(config.getRedisAccountId()!=null) {
            if(!loadApiClient(config.getRedisAccountId(), symKey)){
                config.setRedisAccountId(null);
                config.saveConfig(jedisPool);
                return false;
            }
        }

        config.saveConfig(jedisPool);
        System.out.println("Config done. You can reset it in the Setting of the menu.");
        return true;
    }
    public void closeClients(){
        jedisPool.close();
        closeEs();
    }
    public void closeEs(){
        if(esClient==null){
            System.out.println("No ES esClient running.");
            return;
        }
        if(config.getEsAccountId()==null){
            System.out.println("No ES account ID was set.");
            return;
        }
        ApiAccount apiAccount = config.getApiAccountMap().get(config.getEsAccountId());
        if(apiAccount==null) {
            System.out.println("The ES account isn't found.");
            return;
        }
        apiAccount.closeEsClient();
    }
    private boolean loadApiClient(String id, byte[] symKey) {
        ApiAccount apiAccount;
        ApiProvider apiProvider;
        try {
            apiAccount = config.getApiAccountMap().get(id);
            apiProvider = config.getApiProviderMap().get(apiAccount.getSid());
            apiAccount.connectApi(apiProvider, symKey, br);
        } catch (Exception e) {
            System.out.println("Failed to load :"+id);
            return false;
        }

        switch (apiProvider.getType()){
            case APIP -> initApipClient=(ApipClient) apiAccount.getClient();
            case ES -> esClient = (ElasticsearchClient) apiAccount.getClient();
            case NaSaRPC -> naSaRpcClient = (NaSaRpcClient) apiAccount.getClient();
            case Redis -> jedisPool = (JedisPool) apiAccount.getClient();
        }
        if(apiProvider.getType()== ApiProvider.ApiType.Redis){
            jedisPool = (JedisPool) apiAccount.getClient();
        }
        return true;
    }

    private void initApiAccounts(byte[] symKey) {
        System.out.println("Initial the API accounts...");

        String input = FCH.Inputer.inputGoodFid(br,"Set the owner FID. Enter to create a new one:");
        if("".equals(input)){
            ECKey ecKey = KeyTools.genNewFid(br);
            input = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
        }
        config.setOwner(input);

        if(config.getInitApipAccountId()==null){
            if(Inputer.askIfYes(br,"No APIP service provider yet. Add it? y/n: ")) {
                String accountId = setApiService(symKey, ApiProvider.ApiType.APIP);
                config.setInitApipAccountId(accountId);
                initApipClient = (ApipClient) config.getApiAccountMap().get(accountId).getClient();
                config.saveConfig(jedisPool);
            }
        }

        if(config.getNaSaNodeAccountId()==null){
            if(Inputer.askIfYes(br,"No NaSa node yet. Add it? y/n: ")){
                String id = setApiService(symKey, ApiProvider.ApiType.NaSaRPC);
                config.setNaSaNodeAccountId(id);
                config.saveConfig(jedisPool);
            }
        }

        if(config.getEsAccountId()==null){
            if(Inputer.askIfYes(br,"No ElasticSearch service provider yet. Add it? y/n: ")){
                String id = setApiService(symKey,null);
                config.setEsAccountId(id);
                config.saveConfig(jedisPool);
            }
        }

        if(config.getRedisAccountId()==null){
            if(Inputer.askIfYes(br,"No Redis provider yet. Add it? y/n: ")) {
                String id = setApiService(symKey,null);
                config.setRedisAccountId(id);
                config.saveConfig(jedisPool);
            }
            while (Inputer.askIfYes(br,"Add more API service? y/n")) {
                setApiService(symKey,null);
            }
        }
    }

    private String setApiService(byte[] symKey, ApiProvider.ApiType apiType) {
        ApiProvider apiProvider =
                config.addApiProvider(br,apiType,jedisPool);
        ApiAccount apiAccount =
                config.addApiAccount(apiProvider, symKey,jedisPool,br);
        if(apiAccount.getClient()!=null) config.saveConfig(jedisPool);
        return apiAccount.getId();
    }


    public Service loadMyService(byte[] symKey, String[] types){
//        if(config.getMyService()!=null)return config.getMyService();

        System.out.println("Load my services from APIP...");

        List<Service> serviceList = getMyServiceList(true, types);

        if (serviceList == null) {
            System.out.println("Load swap services wrong.");
            return null;
        }
        Service myService = selectMyService(serviceList,symKey);
        if(myService==null)return null;
        sidBrief = myService.getSid().substring(0,6);
        return myService;
    }

    public void showInitApipBalance(){
        ApiAccount apipAccount = config.getApiAccountMap().get(config.getInitApipAccountId());
        System.out.println("APIP balance: "+(double) apipAccount.getBalance()/ COIN_TO_SATOSHI + " F");
        System.out.println("Rest request: "+(long)((apipAccount.getBalance())/(Double.parseDouble(apipAccount.getApipParams().getPricePerKBytes())* COIN_TO_SATOSHI))+" times");
    }

    private List<Service> getMyServiceList(boolean onlyActive, String[] types) {

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPES).setValues(types);
        fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.OWNER).setValues(config.getOwner());
        if(onlyActive)fcdsl.addNewExcept().addNewTerms().addNewFields(Strings.ACTIVE).addNewValues(Values.FALSE);
        fcdsl.addSize(100);

        fcdsl.addNewSort(FieldNames.LAST_HEIGHT,Strings.DESC);
        initApipClient.serviceSearch(fcdsl,HttpMethods.POST);
        Object result = initApipClient.checkApipResult("get service");
        return ApipDataGetter.getServiceList(result);
    }

    public Service selectMyService(List<Service> serviceList, byte[] symKey){
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

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public BufferedReader getBr() {
        return br;
    }

    public void setBr(BufferedReader br) {
        this.br = br;
    }

    public Service getMyService() {
        return myService;
    }

    public void setMyService(Service myService) {
        this.myService = myService;
    }

    public ApipClient getInitApipClient() {
        return initApipClient;
    }

    public void setInitApipClient(ApipClient initApipClient) {
        this.initApipClient = initApipClient;
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public ElasticsearchClient getEsClient() {
        return esClient;
    }

    public void setEsClient(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public NaSaRpcClient getNaSaRpcClient() {
        return naSaRpcClient;
    }

    public void setNaSaRpcClient(NaSaRpcClient naSaRpcClient) {
        this.naSaRpcClient = naSaRpcClient;
    }
}
