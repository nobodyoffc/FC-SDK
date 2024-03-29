package starter;

import APIP.apipClient.ApipClient;
import APIP.apipClient.ApipDataGetter;
import APIP.apipData.Fcdsl;
import FCH.fchData.Block;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.SwapParams;
import appTools.Inputer;
import appTools.Shower;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import constants.FieldNames;
import constants.Strings;
import constants.UpStrings;
import constants.Values;
import crypto.cryptoTools.Hash;
import crypto.eccAes256K1P7.EccAes256K1P7;
import database.esTools.EsTools;
import database.esTools.NewEsClient;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.HttpMethods;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static constants.Constants.COIN_TO_SATOSHI;

public class Starter {
    /*
    1. 身份验证：symKey = Hash(hand inputted password + nouns in file). symKey encrypted priKey, password or sessionKey.
    2. config.json，读取file，检查初始化，选择数据库
    3. 加载数据库 API：APIP, 链数据库（fchRPC），主数据库：ES，内存数据库：redis
    4. 进入菜单
     */

    public static Config config;
    public static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    public static Service myService;
    public static ApiAccount initApipAccount;

    public static void main(String[] args) {
        Starter starter = new Starter();
        starter.loadConfig();
        //Set password
        byte[] symKey = starter.checkPassword();

        //Set config, add Api providers, connect APIP service

        boolean done = starter.checkConfig(symKey);
        if(!done)return;

        if(config.getInitApipAccountId()!=null){
            ApiAccount apiAccount = config.getApiAccountMap().get(config.getInitApipAccountId());
            ApiProvider apiProvider = config.getApiProviderMap().get(apiAccount.getSid());
            ApipClient apipClient = new ApipClient();
            apipClient.setApiAccount(apiAccount);
            apipClient.setApiProvider(apiProvider);
            apipClient.totals(HttpMethods.POST);
        }

        //Set my service
        myService = starter.loadMyService(symKey);
        SwapParams params = parseMyServiceParams(myService, SwapParams.class);
        myService.setParams(params);
        JsonTools.gsonPrint(config);
        JsonTools.gsonPrint(myService);

        //Connect elasticsearch
        NewEsClient newEsClient = new NewEsClient();
        ElasticsearchClient esClient;
        try {
            esClient =newEsClient.getClientHttps("127.0.0.1",9200,"elastic","password");
            Block block = EsTools.getBestOne(esClient, "block", "height", SortOrder.Desc, Block.class);
            assert block != null;
            JsonTools.gsonPrint(block);
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }

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
            System.out.println("Failed to load config from "+Config.CONFIG_DOT_JSON);
            config = new Config();
        }
    }
    public byte[] checkPassword(){
        byte[] symKey;
        byte[] randomBytes;
        byte[] passwordBytes;
        while(true) {
            if (config == null || config.getNonce() == null) {
                passwordBytes = Inputer.resetNewPassword(br);
                randomBytes = BytesTools.getRandomBytes(16);
                if (config == null) config = new Config();
                config.setNonce(Hex.toHex(randomBytes));
                config.saveConfig();
            } else {
                passwordBytes = Inputer.getPasswordBytes(br);
                randomBytes = Hex.fromHex(config.getNonce());
            }
            if (passwordBytes == null) return null;
            symKey = Hash.Sha256x2(BytesTools.bytesMerger(passwordBytes, randomBytes));
            if(config.getNonceCipher()==null){
                String nonceCipher = EccAes256K1P7.encryptWithSymKey(Hex.fromHex(config.getNonce()), symKey);
                if(nonceCipher.contains("Error")){
                    System.out.println("Failed to encrypt the nonce with the symKey.");
                    return null;
                }
                config.setNonceCipher(nonceCipher);
                config.saveConfig();
            }

            byte[] result = EccAes256K1P7.decryptJsonBytes(config.getNonceCipher(), symKey);
            if (result==null || ! config.getNonce().equals(Hex.toHex(result))) {
                System.out.println("Password wrong. Reset it.");
                config.setNonce(null);
                continue;
            }
            BytesTools.clearByteArray(passwordBytes);
            return symKey;
        }
    }

    public boolean checkConfig(byte[] symKey){
        if(config==null)loadConfig();
        if(config==null)return false;

        if(config.getOwner()==null){
            System.out.println("Set the owner FID:");
            String input = Inputer.inputString(br);
            config.setOwner(input);
        }

        if(config.getInitApipAccountId()==null){
            if(Inputer.askIfYes(br,"No APIP service provider yet. Add it? y/n: ")) {
                String accountId = setApiService(symKey, ApiProvider.ApiType.APIP);
                config.setInitApipAccountId(accountId);
                config.saveConfig();
            }
        }else {
            ApiAccount apiAccount = config.getApiAccountMap().get(config.getInitApipAccountId());
            ApiProvider apiProvider = config.getApiProviderMap().get(apiAccount.getSid());
            apiAccount.connectApi(apiProvider, symKey, br);
        }

        if(config.getMainDatabaseSid()==null){
            if(Inputer.askIfYes(br,"No main database service provider yet. Add it? y/n: ")){
                String id = setApiService(symKey,null);
                config.setMainDatabaseAccountId(id);
                config.saveConfig();
            }
        }

        if(config.getMemDatabaseSid()==null){
            if(Inputer.askIfYes(br,"No memory database service provider yet. Add it? y/n: ")) {
                String id = setApiService(symKey,null);
                config.setMemDatabaseAccountId(id);
                config.saveConfig();
            }
        }

        while (Inputer.askIfYes(br,"Add more API service? y/n")) {
            setApiService(symKey,null);
        }

        System.out.println("Config done. You can reset it in the Setting of the menu.");
        return true;
    }

    private static String setApiService(byte[] symKey, ApiProvider.ApiType apiType) {
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

        System.out.println("Load services from APIP...");

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
        ApipClient apipClient = new ApipClient(initApipAccount);
        apipClient.serviceSearch(fcdsl,HttpMethods.POST);
        Object result = apipClient.checkResult(symKey, "get service");
        return ApipDataGetter.getServiceList(result);
    }

    public static Service selectMyService(List<Service> serviceList, byte[] symKey){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServiceList(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            if(Inputer.askIfYes(br,"Publish a new service? y/n")){
                new ServiceManager(initApipAccount, SwapParams.class).publishService(symKey,br);
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
