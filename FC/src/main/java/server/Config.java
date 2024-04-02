package server;

import FEIP.feipData.Service;
import appTools.Inputer;
import appTools.Shower;
import config.ApiAccount;
import config.ApiProvider;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Config {
    private String nonce;
    private String nonceCipher;
    private String owner;
    private Service myService;
    private String initApipAccountId;
    private Map<String, ApiProvider> apiProviderMap;
    private Map<String, ApiAccount> apiAccountMap;
    private String memDatabaseAccountId;
    private String mainDatabaseAccountId;
    private String chainDatabaseAccountId;
    private String naSaNodeAccountId;
    public static String CONFIG_DOT_JSON = "config.json";

//    public ApipClient initialApipClient(byte[] symKey) {
//        ApipClient apipClient = new ApipClient();
//        ApiAccount apiAccount = apiAccountMap.get(initApipAccountId);
//        if(apiAccount.getSessionKeyCipher()!=null) {
//            byte[] sessionKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getSessionKeyCipher(), symKey);
//            apiAccount.setSessionKey(sessionKey);
//        }else {
//            apiAccount.freshApipSessionKey(symKey,null);
//        }
//        ApiProvider apiProvider = apiProviderMap.get(apiAccount.getSid());
//        apipClient.setApiAccount(apiAccount);
//        apipClient.setApiProvider(apiProvider);
//        ApipClientData result = apipClient.totals(HttpMethods.POST);
//        if(result.isBadResponse("get totals"))return null;
//        System.out.println("Initial Apip Client is created.");
//        return apipClient;
//    }

    public ApiProvider addApiProvider(BufferedReader br, ApiProvider.ApiType apiType) {
        ApiProvider apiProvider = new ApiProvider();
        apiProvider.inputAll(br,apiType);
        if(apiProviderMap==null)apiProviderMap= new HashMap<>();
        apiProviderMap.put(apiProvider.getSid(),apiProvider);
        System.out.println(apiProvider.getSid()+" on "+apiProvider.getApiUrl() + " added.");
        saveConfig();
        return apiProvider;
    }

    public ApiAccount addApiAccount(@NotNull ApiProvider apiProvider, byte[] symKey, BufferedReader br) {
        System.out.println("Add API account for provider "+ apiProvider.getSid()+"...");
        if(apiAccountMap==null)apiAccountMap = new HashMap<>();
        ApiAccount apiAccount;
        while(true) {
            apiAccount = new ApiAccount();
            apiAccount.inputAll(symKey,apiProvider,br);
            if (apiAccountMap.get(apiAccount.getId()) != null) {
                ApiAccount apiAccount1 = apiAccountMap.get(apiAccount.getId());
                if (!Inputer.askIfYes(br, "There has an account for user " + apiAccount1.getUserName() + " on SID " + apiAccount1.getSid() + ". Cover it? y/n")) {
                    System.out.println("Add again.");
                    continue;
                }
            }
            Object client = apiAccount.connectApi(apiProvider, symKey, br);
            if(client==null) {
                System.out.println("This account can't connect withe the API. Reset again.");
                continue;
            }
            apiAccountMap.put(apiAccount.getId(), apiAccount);
            break;
        }
        return apiAccount;
    }

    public void showApiProviders(Map<String, ApiProvider> apiProviderMap) {
        if(apiProviderMap==null || apiProviderMap.size()==0)return;
        String[] fields = {"", "sid", "type","url", "ticks"};
        int[] widths = {2,16,10, 32, 24};
        List<List<Object>> valueListList = new ArrayList<>();
        int i = 1;
        for (ApiProvider apiProvider : apiProviderMap.values()) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(i++);
            valueList.add(apiProvider.getSid());
            valueList.add(apiProvider.getType());
            valueList.add(apiProvider.getApiUrl());
            valueList.add(Arrays.toString(apiProvider.getTicks()));
            valueListList.add(valueList);
        }
        Shower.showDataTable("API providers", fields, widths, valueListList);
    }

    public void showAccounts(Map<String, ApiAccount> apiAccountMap) {
        if(apiAccountMap==null || apiAccountMap.size()==0)return;
        String[] fields = {"", "id","userName","userId", "url", "sid"};
        int[] widths = {2,16,16,16, 32, 16};
        List<List<Object>> valueListList = new ArrayList<>();
        int i = 1;
        for (ApiAccount apiAccount : apiAccountMap.values()) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(i++);
            valueList.add(apiAccount.getId());
            valueList.add(apiAccount.getUserName());
            valueList.add(apiAccount.getUserId());
            valueList.add(apiAccount.getApiUrl());
            valueList.add(apiAccount.getSid());
            valueListList.add(valueList);
        }
        Shower.showDataTable("API accounts", fields, widths, valueListList);
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Map<String, ApiProvider> getApiProviderMap() {
        return apiProviderMap;
    }

    public void setApiProviderMap(Map<String, ApiProvider> apiProviderMap) {
        this.apiProviderMap = apiProviderMap;
    }

    public Map<String, ApiAccount> getApiAccountMap() {
        return apiAccountMap;
    }

    public void setApiAccountMap(Map<String, ApiAccount> apiAccountMap) {
        this.apiAccountMap = apiAccountMap;
    }

    public void saveConfig() {
        File file = new File(CONFIG_DOT_JSON);
        if(!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        javaTools.JsonTools.writeObjectToJsonFile(this,CONFIG_DOT_JSON,false);
    }
    public static Config loadConfig(){
        try {
            return javaTools.JsonTools.readObjectFromJsonFile(null,CONFIG_DOT_JSON, Config.class);
        } catch (IOException e) {
            return null;
        }
    }

    public String getMemDatabaseAccountId() {
        return memDatabaseAccountId;
    }

    public void setMemDatabaseAccountId(String memDatabaseSid) {
        this.memDatabaseAccountId = memDatabaseSid;
    }

    public String getMainDatabaseAccountId() {
        return mainDatabaseAccountId;
    }

    public void setMainDatabaseAccountId(String mainDatabaseSid) {
        this.mainDatabaseAccountId = mainDatabaseSid;
    }

    public String getChainDatabaseAccountId() {
        return chainDatabaseAccountId;
    }

    public void setChainDatabaseAccountId(String chainDatabaseAccountId) {
        this.chainDatabaseAccountId = chainDatabaseAccountId;
    }

    public Service getMyService() {
        return myService;
    }

    public void setMyService(Service myService) {
        this.myService = myService;
    }

    public String getInitApipAccountId() {
        return initApipAccountId;
    }

    public void setInitApipAccountId(String initApipAccountId) {
        this.initApipAccountId = initApipAccountId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getNonceCipher() {
        return nonceCipher;
    }

    public void setNonceCipher(String nonceCipher) {
        this.nonceCipher = nonceCipher;
    }

    public String getNaSaNodeAccountId() {
        return naSaNodeAccountId;
    }

    public void setNaSaNodeAccountId(String naSaNodeAccountId) {
        this.naSaNodeAccountId = naSaNodeAccountId;
    }
}
