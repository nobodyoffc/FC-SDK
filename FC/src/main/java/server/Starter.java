package server;

import APIP.apipClient.ApipClient;
import APIP.apipClient.ApipDataGetter;
import APIP.apipData.Fcdsl;
import FEIP.feipData.Cid;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.SwapParams;
import NaSa.NaSaRpcClient;
import appTools.Inputer;
import appTools.Shower;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import constants.FieldNames;
import constants.Strings;
import constants.UpStrings;
import constants.Values;
import crypto.cryptoTools.Hash;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static constants.Constants.COIN_TO_SATOSHI;

public class Starter {
    final static Logger log = LoggerFactory.getLogger(Starter.class);
    public static Config config;
    public static BufferedReader br;
    public static Service myService;
    public static ApipClient initApipClient;

    public Starter(BufferedReader br) {
        this.br =br;
    }

    public static void main(String[] args) {
        Starter starter = new Starter(new BufferedReader(new InputStreamReader(System.in)));
        //Load config
        starter.loadConfig();

        //Set password
        byte[] symKey = starter.checkPassword();

        Setting setting = new Setting(config,br);
//        symKey = setting.resetPassword();
//        setting.updateApiProvider();
//        setting.updateApiAccount(setting.choseApiProvider(),symKey);
//        setting.deleteApiProvider();
        setting.deleteApiAccount(symKey);


        //Set config, add Api providers, connect APIP service
        boolean done = starter.checkConfig(symKey);
        if(!done)return;
        config.showApiProviders(config.getApiProviderMap());
        config.showAccounts(config.getApiAccountMap());

        //test initial APIP. Load my service from APIP
        if(initApipClient==null)return;
        myService = starter.loadMyService(symKey);
        SwapParams params = parseMyServiceParams(myService, SwapParams.class);
        myService.setParams(params);
        JsonTools.gsonPrint(config);
        JsonTools.gsonPrint(myService);
        showInitApipBalance();

        //test es
        try {
            ApiAccount apiAccount = config.getApiAccountMap().get(config.getMainDatabaseAccountId());
            ElasticsearchClient esClient = (ElasticsearchClient) apiAccount.getClient();
            GetResponse<Cid> cidResult = esClient.get(g -> g.index("cid").id("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7"), Cid.class);
            assert cidResult.source() != null;
            JsonTools.gsonPrint(cidResult.source());
            apiAccount.closeEsClient();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //test jedis
        ApiAccount apiAccount = config.getApiAccountMap().get(config.getMemDatabaseAccountId());
        JedisPool jedisPool = (JedisPool) apiAccount.getClient();
        try(Jedis jedis = jedisPool.getResource()){
            jedis.set("testStarter","good jedis");
            System.out.println(jedis.get("testStarter"));
        }

        //test NaSa node
        ApiAccount apiAccount1 = config.getApiAccountMap().get(config.getNaSaNodeAccountId());
        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) apiAccount1.getClient();
        JsonTools.gsonPrint(naSaRpcClient.getBlockchainInfo());
    }

    public static  <T> T parseMyServiceParams(Service myService, Class<T> tClass){
        Gson gson = new Gson();
        T params = gson.fromJson(gson.toJson(myService.getParams()), tClass);
        myService.setParams(params);
        return params;
    }

    public void loadConfig(){
        config = Config.loadConfig();
        if(config==null){
            log.debug("Failed to load config from "+Config.CONFIG_DOT_JSON);
            config = new Config();
        }
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
                    if (config == null) config = new Config();
                    config.setNonce(Hex.toHex(randomBytes));
                    String nonceCipher = EccAes256K1P7.encryptWithSymKey(Hex.fromHex(config.getNonce()), symKey);
                    if (nonceCipher.contains("Error")) {
                        System.out.println("Failed to encrypt the nonce with the symKey. Try again.");
                        continue;
                    }
                    config.setNonceCipher(nonceCipher);
                    config.saveConfig();
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
            loadApiClient(config.getInitApipAccountId(),symKey);
            initApipClient = (ApipClient) config.getApiAccountMap().get(config.getInitApipAccountId()).getClient();
        }

        if(config.getNaSaNodeAccountId()!=null) loadApiClient(config.getNaSaNodeAccountId(), symKey);

        if(config.getMainDatabaseAccountId()!=null) loadApiClient(config.getMainDatabaseAccountId(), symKey);

        if(config.getMemDatabaseAccountId()!=null) loadApiClient(config.getMemDatabaseAccountId(), symKey);

        config.saveConfig();
        System.out.println("Config done. You can reset it in the Setting of the menu.");
        return true;
    }

    private void loadApiClient(String id, byte[] symKey) {
        ApiAccount apiAccount = config.getApiAccountMap().get(id);
        ApiProvider apiProvider = config.getApiProviderMap().get(apiAccount.getSid());
        apiAccount.connectApi(apiProvider, symKey, br);
    }


    private void initApiAccounts(byte[] symKey) {
        System.out.println("Initial the API accounts...");
        System.out.println("Set the owner FID:");
        String input = Inputer.inputString(br);
        config.setOwner(input);

        if(config.getInitApipAccountId()==null){
            if(Inputer.askIfYes(br,"No APIP service provider yet. Add it? y/n: ")) {
                String accountId = setApiService(symKey, ApiProvider.ApiType.APIP);
                config.setInitApipAccountId(accountId);
                initApipClient = (ApipClient) config.getApiAccountMap().get(accountId).getClient();
                config.saveConfig();
            }
        }

        if(config.getNaSaNodeAccountId()==null){
            if(Inputer.askIfYes(br,"No NaSa node yet. Add it? y/n: ")){
                String id = setApiService(symKey, ApiProvider.ApiType.NaSaRPC);
                config.setNaSaNodeAccountId(id);
                config.saveConfig();
            }
        }

        if(config.getMainDatabaseAccountId()==null){
            if(Inputer.askIfYes(br,"No main database service provider yet. Add it? y/n: ")){
                String id = setApiService(symKey,null);
                config.setMainDatabaseAccountId(id);
                config.saveConfig();
            }
        }

        if(config.getMemDatabaseAccountId()==null){
            if(Inputer.askIfYes(br,"No memory database service provider yet. Add it? y/n: ")) {
                String id = setApiService(symKey,null);
                config.setMemDatabaseAccountId(id);
                config.saveConfig();
            }
            while (Inputer.askIfYes(br,"Add more API service? y/n")) {
                setApiService(symKey,null);
            }
        }
    }

    private String setApiService(byte[] symKey, ApiProvider.ApiType apiType) {
        ApiProvider apiProvider =
                config.addApiProvider(br,apiType);
        ApiAccount apiAccount =
                config.addApiAccount(apiProvider, symKey,br);
        Object obj = apiAccount.connectApi(apiProvider, symKey, br);
        if(obj!=null) config.saveConfig();
        return apiAccount.getId();
    }

    public Service loadMyService(byte[] symKey){
        if(config.getMyService()!=null)return config.getMyService();

        System.out.println("Load my services from APIP...");

        List<Service> serviceList = getMyServiceList(symKey,true);

        if (serviceList == null) {
            System.out.println("Load swap services wrong.");
            return null;
        }

        return selectMyService(serviceList,symKey);
    }

    public static void showInitApipBalance(){
        ApiAccount apipAccount = config.getApiAccountMap().get(config.getInitApipAccountId());
        System.out.println("APIP balance: "+(double) apipAccount.getBalance()/ COIN_TO_SATOSHI + " F");
        System.out.println("Rest request: "+(long)((apipAccount.getBalance())/(Double.parseDouble(apipAccount.getApipParams().getPricePerKBytes())* COIN_TO_SATOSHI))+" times");
    }

    private static List<Service> getMyServiceList(byte[] symKey,boolean onlyActive) {

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPES).setValues(UpStrings.SWAP, Values.SWAP);
        fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.OWNER).setValues(config.getOwner());
        if(onlyActive)fcdsl.addNewExcept().addNewTerms().addNewFields(Strings.ACTIVE).addNewValues(Values.FALSE);
        fcdsl.addSize(100);

        fcdsl.addNewSort(FieldNames.LAST_HEIGHT,Strings.DESC);
        initApipClient.serviceSearch(fcdsl,HttpMethods.POST);
        Object result = initApipClient.checkApipResult(symKey, "get service");
        return ApipDataGetter.getServiceList(result);
    }

    public Service selectMyService(List<Service> serviceList, byte[] symKey){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServiceList(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            if(Inputer.askIfYes(br,"Publish a new service? y/n")){
                new ServiceManager(initApipClient.getApiAccount(), SwapParams.class).publishService(symKey,br);
                System.out.println("Wait for a few minutes and try to start again.");
                System.exit(0);
            }
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

}
