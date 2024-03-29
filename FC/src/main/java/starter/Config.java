package starter;

import FEIP.feipData.Service;
import appTools.Inputer;
import appTools.Shower;
import config.ApiAccount;
import config.ApiProvider;

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

    private String memDatabaseSid;
    private String mainDatabaseSid;
    private String chainDatabaseSid;
    public static String CONFIG_DOT_JSON = "config.json";
    public ApiProvider addApiProvider(BufferedReader br, ApiProvider.ApiType apiType) {
        ApiProvider apiProvider = new ApiProvider();
        apiProvider.inputAll(br,apiType);
        if(apiProviderMap==null)apiProviderMap= new HashMap<>();
        apiProviderMap.put(apiProvider.getSid(),apiProvider);
        System.out.println(apiProvider.getSid()+" on "+apiProvider.getApiUrl() + " added.");
        saveConfig();
        return apiProvider;
    }

    public ApiAccount addApiAccount(ApiProvider apiProvider, byte[] symKey, BufferedReader br) {
        System.out.println("Add API account...");
        if(apiAccountMap==null)apiAccountMap = new HashMap<>();
        ApiAccount apiAccount = new ApiAccount();
        while(true) {
            apiAccount.inputAll(symKey,apiProvider,br);
            if (apiAccountMap.get(apiAccount.getId()) != null) {
                ApiAccount apiAccount1 = apiAccountMap.get(apiAccount.getId());
                if (!Inputer.askIfYes(br, "There has an account for user " + apiAccount1.getUserName() + " on SID " + apiAccount1.getSid() + ". Cover it? y/n")) {
                    System.out.println("Add again.");
                    continue;
                }
            }
            apiAccountMap.put(apiAccount.getId(), apiAccount);
            break;
        }
        return apiAccount;
    }

    public void showApiProviders(ApiProvider[] apiProviders) {
        if(apiProviders==null || apiProviders.length==0)return;
        String[] fields = {"", "id", "type","URL", "ticks"};
        int[] widths = {2,64,10, 32, 24};
        List<List<Object>> valueListList = new ArrayList<>();
        int i = 1;
        for (ApiProvider apiProvider : apiProviders) {
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

    public String getMemDatabaseSid() {
        return memDatabaseSid;
    }

    public void setMemDatabaseAccountId(String memDatabaseSid) {
        this.memDatabaseSid = memDatabaseSid;
    }

    public String getMainDatabaseSid() {
        return mainDatabaseSid;
    }

    public void setMainDatabaseAccountId(String mainDatabaseSid) {
        this.mainDatabaseSid = mainDatabaseSid;
    }

    public String getChainDatabaseSid() {
        return chainDatabaseSid;
    }

    public void setChainDatabaseSid(String chainDatabaseSid) {
        this.chainDatabaseSid = chainDatabaseSid;
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
}
