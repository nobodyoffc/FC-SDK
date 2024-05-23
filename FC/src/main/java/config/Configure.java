package config;

import FCH.FchMainNetwork;
import FEIP.feipData.serviceParams.ChatParams;
import FEIP.feipData.serviceParams.DiskParams;
import FEIP.feipData.serviceParams.SwapParams;
import appTools.Inputer;
import clients.apipClient.ApipClient;
import FEIP.feipData.Service;
import appTools.Shower;
import com.google.gson.Gson;
import constants.FieldNames;
import crypto.*;
import fcData.AlgorithmType;
import javaTools.BytesTools;
import javaTools.FileTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServiceType;
import server.serviceManagers.ChatManager;
import server.serviceManagers.DiskManager;
import server.serviceManagers.SwapManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static constants.Strings.CONFIG;

public class Configure {
    protected String nonce;
    protected String owner;
    protected List<String> users;
    private String nonceCipher;
    private String initApipAccountId;
//    private String initRedisAccountId;
    private Set<String> myServiceIdSet;
    private Map<String, ApiProvider> apiProviderMap;
    private Map<String, ApiAccount> apiAccountMap;

    final static Logger log = LoggerFactory.getLogger(Configure.class);
    public static String CONFIG_DOT_JSON = "config.json";
//    private Setter setter;
    private static BufferedReader br;
    private static ApipClient initApipClient;
//    private static JedisPool jedisPool;

    public Configure(BufferedReader br) {
        Configure.br =br;
    }

    public Configure() {
    }

    public void initiate(byte[] symKey){
        System.out.println("Initiating config...");
        if(apiProviderMap==null)apiProviderMap = new HashMap<>();
        if(apiAccountMap == null)apiAccountMap = new HashMap<>();

        ApiAccount initApipAccount;
        initApipAccount = checkApiClient(symKey, initApipAccountId, ApiType.APIP, initApipClient);

        if(initApipAccount==null) {
            System.out.println("Failed to initiate APIP service.");
            return;
        }
        if (initApipAccount.getClient() == null) {
            System.out.println("Failed to initiate APIP service.");
            return;
        }
        initApipClient = (ApipClient) initApipAccount.getClient();
        initApipAccountId=initApipAccount.getId();
    }

    @Nullable
    private ApiAccount checkApiClient(byte[] symKey, String apiAccountId, ApiType type, ApipClient apiClient) {
        ApiAccount apiAccount = null;
        if(apiAccountId !=null){
            apiAccount = apiAccountMap.get(apiAccountId);
            if(!Inputer.askIfYes(br,"Current ID is: "+apiAccount.getUserId()+".\nChange to another one?")) {
                System.out.println("Connect by "+apiAccount.getUserId()+"...");
                apiAccount.connectApip(apiProviderMap.get(apiAccount.getSid()), symKey, br);
            }else {
                System.out.println("Connect by other ID...");
                List<String> accountUserIdUrlList = new ArrayList<>();
                Map<String,String> map = new HashMap<>();
                for(String id:apiAccountMap.keySet()){
                    apiAccount = apiAccountMap.get(id);
                    ApiProvider apiProvider = apiProviderMap.get(apiAccount.getSid());
                    if(type.equals(apiProvider.getType())) {
                        String tempId = apiAccount.getUserId() + "@" + apiAccount.getApiUrl();
                        accountUserIdUrlList.add(tempId);
                        map.put(tempId, apiAccount.getId());
                    }
                }

                if(!map.isEmpty()) {
                    String choice = (String) Inputer.chooseOne(accountUserIdUrlList.toArray(), "Choose your account", br);
                    if(choice!=null) {
                        apiAccountId = map.get(choice);
                        apiAccount=apiAccountMap.get(apiAccountId);
                        apiAccount.connectApip(apiProviderMap.get(apiAccount.getSid()), symKey, br);
                    }
                }

                if(Inputer.askIfYes(br,"Use current ID "+apiAccount.getUserId()+"?")){
                    apiClient = apiAccount.connectApip(apiProviderMap.get(apiAccount.getSid()), symKey, br);
                }else {
                    apiAccount = chooseApi(symKey, type);
                }
            }
        }else{
            ApiProvider apiProvider = chooseApiProvider(apiProviderMap,type);
            if(apiProvider==null)apiProvider = addApiProvider(type);
            if(apiProvider==null) return null;
            apiAccount = chooseApiProvidersAccount(apiProvider,symKey);
            apiAccount.connectApi(apiProvider, symKey, br, initApipClient);
        }
        if(apiAccount!=null)
            apiAccount.setClient(apiClient);
        return apiAccount;
    }

    public Service getMyService(byte[] symKey, ApiType type){
        if(owner==null)
            owner = inputOwner();
        System.out.println();
        System.out.println("The owner: "+owner);
        System.out.println();
        Service service = chooseOwnerService(symKey,type);

        saveConfig();
        System.out.println("Config initiated.");
        return service;
    }
    private Service chooseOwnerService(byte[] symKey) {
        return chooseOwnerService(symKey,null);
    }
    @Nullable
    private Service chooseOwnerService(byte[] symKey, ApiType type) {
        List<Service> serviceList;
        if(myServiceIdSet ==null){
            myServiceIdSet = new HashSet<>();
            serviceList = initApipClient.getServiceListByOwner(owner);
        }else {
            String[] ids = myServiceIdSet.toArray(new String[0]);
            Map<String, Service> serviceMap = initApipClient.serviceMapByIds(ids);
            serviceList = new ArrayList<>(serviceMap.values());
        }

        if(serviceList.isEmpty()){
            System.out.println("No any service on chain of the owner.");
            return null;
        }

        if(type!=null)
            filterServiceByType(type, serviceList);

        Service service;
        if(symKey!=null)service = selectService(serviceList, symKey, apiAccountMap.get(initApipAccountId));
        else service = selectService(serviceList);
        if(service==null) System.out.println("Failed to get the service.");
        else {
            myServiceIdSet.add(service.getSid());
        }
        return service;
    }

    private static void filterServiceByType(ApiType type, List<Service> serviceList) {
        Iterator<Service> iter = serviceList.iterator();
        while (iter.hasNext()) {
            Service service = iter.next();
            String[] types = service.getTypes();
            if (types != null && Stream.of(types).anyMatch(s -> s.equalsIgnoreCase(type.name())))
                continue;
            iter.remove();
        }
    }

    private Service chooseTypeService(String type) {
        List<Service> serviceList;
        if(myServiceIdSet ==null){
            myServiceIdSet = new HashSet<>();
            serviceList = initApipClient.getServiceListByType(type);
        }else {
            String[] ids = myServiceIdSet.toArray(new String[0]);
            Map<String, Service> serviceMap = initApipClient.serviceMapByIds(ids);
            serviceList = new ArrayList<>(serviceMap.values());
        }

        Service service;
        service = selectService(serviceList);
        if(service==null) System.out.println("Failed to get the service.");
        else {
            myServiceIdSet.add(service.getSid());
        }
        return service;
    }
    private Service chooseOwnerService(ApiType type) {
        return chooseOwnerService(null, type);
    }

    public static Service selectService(List<Service> serviceList,byte[] symKey,ApiAccount apipAccount){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServices(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            if(Inputer.askIfYes(br,"Publish a new service?")){
                ServiceType type = Inputer.chooseOne(ServiceType.values(), "Choose a type", br);
                switch (type){
                    case DISK -> new DiskManager(apipAccount, DiskParams.class).publishService(symKey,br,initApipClient);
                    case SWAP -> new SwapManager(apipAccount, SwapParams.class).publishService(symKey,br,initApipClient);
                    case CHAT -> new ChatManager(apipAccount, ChatParams.class).publishService(symKey,br,initApipClient);
                }
                System.out.println("Wait for a few minutes and try to start again.");
                System.exit(0);
            }
        }
        return serviceList.get(choice-1);
    }

    public static Service selectService(List<Service> serviceList){
        if(serviceList==null||serviceList.isEmpty())return null;
        showServices(serviceList);
        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0)return null;
        return serviceList.get(choice-1);
    }
    public static void showServices(List<Service> serviceList) {
        String title = "Services";
        String[] fields = new String[]{"",FieldNames.STD_NAME,FieldNames.TYPES,FieldNames.SID};
        int[] widths = new int[]{2,24,24,64};
        List<List<Object>> valueListList = new ArrayList<>();
        int i=1;
        for(Service service : serviceList){
            List<Object> valueList = new ArrayList<>();
            valueList.add(i);
            valueList.add(service.getStdName());
            StringBuilder sb = new StringBuilder();
            for(String type:service.getTypes()){
                sb.append(type);
                sb.append(",");
            }
            if(sb.length()>1)sb.deleteCharAt(sb.lastIndexOf(","));
            valueList.add(sb.toString());
            valueList.add(service.getSid());
            valueListList.add(valueList);
            i++;
        }
        Shower.showDataTable(title,fields,widths,valueListList);
    }
    private ApiAccount chooseApi(byte[] symKey, ApiType type) {
        System.out.println("The " + type.name() + " is not ready. Set it...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,type);
        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider, symKey);
        if(apiAccount.getClient()==null) {
            System.err.println("Failed to create " + type.name() + ".");
            return null;
        }
        return apiAccount;
    }

    //
//    public static void main(String[] args) {
//        Starter starter = new Starter(new BufferedReader(new InputStreamReader(System.in)));
//        //Load config
//        starter.config= Config.loadConfig(br);
//
//        //Set password
//        byte[] symKey = starter.checkPassword();
//        Setter setter;
//        setter = Setter.loadFromFile(SwapSetter.class);
//        setter.config.updateApiProvider(setter);
//        setter.config.updateApiAccount(setter.config.chooseApiProvider(setter),symKey, setter);
//        setter.config.deleteApiProvider(setter);
//        setter.config.deleteApiAccount(symKey, setter);
//
//
//        //Set config, add Api providers, connect APIP service
//        starter.config = Config.loadConfig(br);
//        if(starter.config==null)return;
//        starter.config.showApiProviders(starter.config.getApiProviderMap());
//        starter.config.showAccounts(starter.config.getApiAccountMap());
//
//        //test initial APIP. Load my service from APIP
//        if(starter.initApipClient==null)return;
//        starter.myService = starter.loadMyService(symKey, new String[]{UpStrings.SWAP, Values.SWAP});
//        if(starter.myService==null)
//            new SwapManager(starter.initApipClient.getApiAccount(), SwapParams.class).publishService(symKey,starter.br);
//
//        SwapParams params = parseMyServiceParams(starter.myService, SwapParams.class);
//        starter.myService.setParams(params);
//        JsonTools.gsonPrint(starter.config);
//        JsonTools.gsonPrint(starter.myService);
////        starter.showInitApipBalance();
//        Map<String, BlockInfo> block = starter.initApipClient.blockByHeights(new String[]{"2"});
//        JsonTools.gsonPrint(block.get("2"));
//
//        //test order scanner
//
//        //test es
////        try {
////            ApiAccount apiAccount = starter.config.getApiAccountMap().get(starter.setter.getEsAccountId());
////            ElasticsearchClient esClient = (ElasticsearchClient) apiAccount.getClient();
////            GetResponse<Cid> cidResult = esClient.get(g -> g.index("cid").id("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7"), Cid.class);
////            assert cidResult.source() != null;
////            JsonTools.gsonPrint(cidResult.source());
////            apiAccount.closeEsClient();
////        } catch (IOException e) {
////            throw new RuntimeException(e);
////        }
////
////        //test jedis
////        ApiAccount apiAccount = starter.config.getApiAccountMap().get(starter.setter.getRedisAccountId());
////        JedisPool jedisPool = (JedisPool) apiAccount.getClient();
////        try(Jedis jedis = jedisPool.getResource()){
////            jedis.set("testStarter","good jedis");
////            System.out.println(jedis.get("testStarter"));
////        }
////
////        //test NaSa node
////        ApiAccount apiAccount1 = starter.config.getApiAccountMap().get(starter.setter.getNaSaNodeAccountId());
////        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) apiAccount1.getClient();
////        JsonTools.gsonPrint(naSaRpcClient.getBlockchainInfo());
//    }
    public ApiAccount addApiAccount(@NotNull ApiProvider apiProvider, byte[] symKey) {
        System.out.println("Add API account for provider "+ apiProvider.getSid()+"...");
        if(apiAccountMap==null)apiAccountMap = new HashMap<>();
        ApiAccount apiAccount;
        while(true) {
            apiAccount = new ApiAccount();
            apiAccount.inputAll(symKey,apiProvider,br);
            if(initApipClient!=null)apiAccount.setApipClient(initApipClient);
            if (apiAccountMap.get(apiAccount.getId()) != null) {
                ApiAccount apiAccount1 = apiAccountMap.get(apiAccount.getId());
                if (!Inputer.askIfYes(br, "There has an account for user " + apiAccount1.getUserName() + " on SID " + apiAccount1.getSid() + ". Cover it?")) {
                    System.out.println("Add again.");
                    continue;
                }
            }
            saveConfig();
            try {
                Object client = apiAccount.connectApi(apiProvider, symKey, br, initApipClient);
                if(client==null) {
                    System.out.println("This account can't connect withe the API. Reset again.");
                    continue;
                }
            }catch (Exception e){
                System.out.println("Can't connect the API provider of "+apiProvider.getSid());
                if(Inputer.askIfYes(br,"Do you want to revise the API provider?")){
                    apiProvider.updateAll(br);
                    saveConfig();
                    continue;
                }else return null;
            }
            apiAccountMap.put(apiAccount.getId(), apiAccount);
            saveConfig();
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
        saveConfigToFile();
        javaTools.JsonTools.writeObjectToJsonFile(this, Configure.getConfDir()+ Configure.CONFIG_DOT_JSON,false);
    }

    public void saveConfigToFile() {
        String confDir = Configure.getConfDir();
        File file = new File(confDir, Configure.CONFIG_DOT_JSON);
        if(!file.exists()) {
            FileTools.createFileWithDirectories(confDir+ Configure.CONFIG_DOT_JSON);
        }
    }

//    public void saveConfigToJedis(JedisPool jedisPool) {
//        if(jedisPool !=null)try(Jedis jedis = jedisPool.getResource()){
//            String configStr = JsonTools.getNiceString(this);
//            String key = owner.substring(30,34)+CONFIG;
//            jedis.set(key, configStr);
//        }
//    }
//
//
//    public String getRedisAccountId() {
//        return redisAccountId;
//    }
//
//    public void setRedisAccountId(String memDatabaseSid) {
//        this.redisAccountId = memDatabaseSid;
//    }
//
//    public String getEsAccountId() {
//        return esAccountId;
//    }
//
//    public void setEsAccountId(String mainDatabaseSid) {
//        this.esAccountId = mainDatabaseSid;
//    }
//
//    public String getApipAccountId() {
//        return apipAccountId;
//    }
//
//    public void setApipAccountId(String apipAccountId) {
//        this.apipAccountId = apipAccountId;
//    }

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

    public String getInitApipAccountId() {
        return initApipAccountId;
    }

    public void setInitApipAccountId(String initApipAccountId) {
        this.initApipAccountId = initApipAccountId;
    }

    public void addApiAccount(byte[] symKey){
        System.out.println("Add API accounts...");
        ApiProvider apiProvider = chooseApiProviderOrAdd();
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, symKey);
            saveConfig();
            System.out.println("Add API account "+apiAccount.getId()+" is added.");
        }
    }
    public String addApiProviderAndConnect(byte[] symKey){
        return addApiProviderAndConnect(symKey,null);
    }
    public String addApiProviderAndConnect(byte[] symKey, ApiType apiType){
        System.out.println("Add API providers...");
        ApiProvider apiProvider = addApiProvider(apiType);
        String apiAccountId = null;
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, symKey);
            if(apiAccount!=null) {
                apiAccount.connectApi(apiProvider, symKey, br, null);
                apiAccountId = apiAccount.getId();
                saveConfig();
            }else return null;
        }
        System.out.println("Add API provider "+apiProvider.getSid()+" is added.");
        return apiAccountId;
    }

    public ApiProvider addApiProvider(ApiType apiType) {
        ApiProvider apiProvider = new ApiProvider();
        apiProvider.makeApiProvider(br,apiType,initApipClient);
        if(apiProviderMap==null)apiProviderMap= new HashMap<>();
        apiProviderMap.put(apiProvider.getSid(),apiProvider);
        System.out.println(apiProvider.getSid()+" on "+apiProvider.getApiUrl() + " added.");
        saveConfig();
        return apiProvider;
    }

    public ApiProvider addApiProvider() {
        return addApiProvider(null);
    }

    public void updateApiAccount(ApiProvider apiProvider, byte[] symKey){
        System.out.println("Update API accounts...");
        ApiAccount apiAccount;
        if(apiProvider==null)apiAccount = chooseApiAccount(symKey);
        else apiAccount = chooseApiProvidersAccount(apiProvider, symKey);
        if(apiAccount!=null) {
            System.out.println("Update API account: "+apiAccount.getSid()+"...");
            apiAccount.updateAll(symKey, apiProvider,br);
            getApiAccountMap().put(apiAccount.getId(), apiAccount);
            saveConfig();
        }
        System.out.println("Api account "+apiAccount.getId()+" is updated.");
    }

    public void updateApiProvider(byte[] symKey){
        System.out.println("Update API providers...");
        ApiProvider apiProvider = chooseApiProviderOrAdd();
        if(apiProvider!=null) {
            apiProvider.updateAll(br);
            getApiProviderMap().put(apiProvider.getSid(), apiProvider);
            saveConfig();
            System.out.println("Api provider "+apiProvider.getSid()+" is updated.");
        }
    }

    public void deleteApiProvider(byte[] symKey){
        System.out.println("Deleting API provider...");
        ApiProvider apiProvider = chooseApiProviderOrAdd();
        if(apiProvider==null) return;
        for(ApiAccount apiAccount: getApiAccountMap().values()){
            if(apiAccount.getSid().equals(apiProvider.getSid())){
                if(Inputer.askIfYes(br,"There is the API account "+apiAccount.getId()+" of "+apiProvider.getSid()+". \nDelete it?")){
                    getApiAccountMap().remove(apiAccount.getId());
                    System.out.println("Api account "+apiAccount.getId()+" is deleted.");
                    saveConfig();
                }
            }
        }
        if(Inputer.askIfYes(br,"Delete API provider "+apiProvider.getSid()+"?")){
            getApiProviderMap().remove(apiProvider.getSid());
            System.out.println("Api provider " + apiProvider.getSid() + " is deleted.");
            saveConfig();
        }
    }

    public void deleteApiAccount(byte[] symKey){
        System.out.println("Deleting API Account...");
        ApiAccount apiAccount = chooseApiAccount(symKey);
        if(apiAccount==null) return;
        if(Inputer.askIfYes(br,"Delete API account "+apiAccount.getId()+"?")) {
            getApiAccountMap().remove(apiAccount.getId());
            System.out.println("Api account " + apiAccount.getId() + " is deleted.");
            saveConfig();
        }
    }

    public ApiAccount chooseApiAccount(byte[] symKey){
        ApiAccount apiAccount = null;
        showAccounts(getApiAccountMap());
        int input = Inputer.inputInteger(br, "Input the number of the account you want. Enter to add a new one:", getApiAccountMap().size());
        if (input == 0) {
            if(Inputer.askIfYes(br,"Add a new API account?")) {
                ApiProvider apiProvider = chooseApiProviderOrAdd();
                apiAccount = addApiAccount(apiProvider, symKey);
            }
        } else {
            apiAccount = (ApiAccount) getApiAccountMap().values().toArray()[input - 1];
        }
        return apiAccount;
    }
    public ApiProvider chooseApiProviderOrAdd(){
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap);
        if(apiProvider==null){
            ApiType apiType = Inputer.chooseOne(ApiType.values(), "Choose the type of the API:", br);
            apiProvider = addApiProvider(apiType);
        }
        return apiProvider;
    }

    public ApiProvider chooseApiProviderOrAdd(Map<String, ApiProvider> apiProviderMap, ApiType apiType){
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap,apiType);
        if(apiProvider==null){
            apiProvider = addApiProvider(apiType);
        }
        return apiProvider;
    }

    public ApiProvider chooseApiProvider(Map<String, ApiProvider> apiProviderMap, ApiType apiType){
        Map<String, ApiProvider> map = new HashMap<>();
        for(String id : apiProviderMap.keySet()){
            ApiProvider apiProvider = apiProviderMap.get(id);
            if(apiProvider.getType().equals(apiType))
                map.put(id,apiProvider);
        }
        return chooseApiProvider(map);
    }

    public ApiProvider chooseApiProvider(Map<String, ApiProvider> apiProviderMap){
        ApiProvider apiProvider;
        if (apiProviderMap == null) {
            setApiProviderMap(new HashMap<>());
        }
        if (apiProviderMap.size() == 0) {
            System.out.println("No any API provider yet.");
            return null;
        } else {
            showApiProviders(apiProviderMap);
            int input = Inputer.inputInteger( br,"Input the number of the API provider you want. Enter to add new one:", apiProviderMap.size());
            if (input == 0) {
                return null;
            } else apiProvider = (ApiProvider) apiProviderMap.values().toArray()[input - 1];
        }
        return apiProvider;
    }

    public ApiAccount initFcAccount(ApipClient initApipClient, ApiType apiType, byte[]symKey) {
        this.setInitApipClient(initApipClient);
        ApiProvider apiProvider = selectFcApiProvider(initApipClient,apiType);
        if(apiProvider==null)return null;
        if(apiProviderMap==null)apiProviderMap=new HashMap<>();
        apiProviderMap.put(apiProvider.getSid(),apiProvider);
        apiProvider.setType(apiType);
        return chooseApiProvidersAccount(apiProvider,symKey);
    }

    private ApiProvider selectFcApiProvider(ApipClient initApipClient, ApiType apiType) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap,apiType);
        if(apiProvider==null) apiProvider= ApiProvider.searchFcApiProvider(initApipClient,apiType);
        return apiProvider;
    }

    public ApiAccount chooseApiProvidersAccount(ApiProvider apiProvider, byte[] symKey) {
        ApiAccount apiAccount = null;
        Map<String, ApiAccount> hitApiAccountMap = new HashMap<>();
        if (getApiAccountMap() == null) setApiAccountMap(new HashMap<>());
        if (getApiAccountMap().size() == 0) {
            System.out.println("No API accounts yet. Add new one...");
            apiAccount = addApiAccount(apiProvider, symKey);
        } else {
            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
                if (apiAccount1.getSid().equals(apiProvider.getSid()))
                    hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
            }
            if (hitApiAccountMap.size() == 0) {
                apiAccount = addApiAccount(apiProvider, symKey);
            } else {
                showAccounts(hitApiAccountMap);
                int input = Inputer.inputInteger( br,"Input the number of the account you want. Enter to add new one:", hitApiAccountMap.size());
                if (input == 0) {
                    apiAccount = addApiAccount(apiProvider, symKey);
                } else {
                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
                    apiAccount.setApipClient(initApipClient);
                    if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symKey);
                }
            }
        }
        return apiAccount;
    }

    public ApiAccount checkAPI(@Nullable String apipAccountId, String type, byte[] symKey) {
        ApiAccount apiAccount;
        while (true) {
            if (apipAccountId == null) {
                System.out.println("No " + type + " service yet. Add it.");
                ApiType apiType;
                try{
                    apiType = ApiType.valueOf(type.toUpperCase());
                    apipAccountId = setApiService(symKey,apiType);
                }catch (Exception ignore){
                    apipAccountId = setApiService(symKey);
                }
            }
            if (apipAccountId == null) {
                System.out.println("Failed to get API account. Try again.");
                continue;
            }
            apiAccount = getApiAccountMap().get(apipAccountId);
            if (apiAccount == null) continue;
            if (apiAccount.getClient() == null) {
                Object apiClient = apiAccount.connectApi(getApiProviderMap().get(apiAccount.getSid()), symKey, br, null);
                if (apiClient == null) {
                    System.out.println("Failed to connect " + apiAccount.getApiUrl() + ". Try again.");
                    continue;
                }
            }
            return apiAccount;
        }
    }

    public String setApiService(byte[] symKey) {
        ApiProvider apiProvider = chooseApiProviderOrAdd();
        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider,symKey);
        if(apiAccount.getClient()!=null) saveConfig();
        return apiAccount.getId();
    }

    public String setApiService(byte[] symKey, ApiType apiType) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap,apiType);
        if(apiProvider==null) apiProvider = addApiProvider(apiType);
        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider,symKey);
        if(apiAccount==null) apiAccount = addApiAccount(apiProvider, symKey);
        if(apiAccount.getClient()!=null) saveConfig();
        return apiAccount.getId();
    }

    public byte[] checkPassword(Configure configure){
        byte[] symKey;
        byte[] nonceBytes;
        byte[] passwordBytes;

        if (nonce == null) {
            while (true) {
                passwordBytes = Inputer.resetNewPassword(br);
                if (passwordBytes == null){
                    System.out.println("Input wrong. Try again.");
                    continue;
                }
                nonceBytes = BytesTools.getRandomBytes(16);
                symKey = getSymKeyFromPasswordAndNonce(nonceBytes, passwordBytes);
                nonce = Hex.toHex(nonceBytes);

                EncryptorSym encryptorSym = new EncryptorSym(AlgorithmType.FC_Aes256Cbc_No1_NrC7);
                CryptoDataByte cryptoDataByte = encryptorSym.encryptBySymKey(nonceBytes,symKey);
                if(cryptoDataByte.getCode()!=0){
                    System.out.println(cryptoDataByte.getMessage());
                    return null;
                }
                nonceCipher = cryptoDataByte.toJson();

                saveConfig();
                return symKey;
            }
        } else {
            while(true) {
                passwordBytes = Inputer.getPasswordBytes(br);
                symKey = getSymKeyFromPasswordAndNonce(Hex.fromHex(nonce), passwordBytes);
                DecryptorSym decryptorSym = new DecryptorSym();
                CryptoDataByte cryptoDataByte = decryptorSym.decryptJsonBySymKey(getNonceCipher(),symKey);
                if(cryptoDataByte.getCode()!=0){
                    System.out.println(cryptoDataByte.getMessage());
                    return null;
                }
                byte[] result = cryptoDataByte.getData();
                if (result==null || ! getNonce().equals(Hex.toHex(result))) {
                    System.out.println("Password wrong. Input it again.");
                    continue;
                }
                BytesTools.clearByteArray(passwordBytes);
                return symKey;
            }
        }
    }

    public static byte[] getSymKeyFromPasswordAndNonce(byte[] nonce, byte[] passwordBytes) {
        return Hash.sha256x2(BytesTools.bytesMerger(passwordBytes, nonce));
    }

    public static  <T> T parseMyServiceParams(Service myService, Class<T> tClass){
        Gson gson = new Gson();
        T params = gson.fromJson(gson.toJson(myService.getParams()), tClass);
        myService.setParams(params);
        return params;
    }

    public static String getConfDir(){
        return System.getProperty("user.dir")+"/"+ CONFIG +"/";
    }

    public static Configure loadConfig(String path,BufferedReader br){
        if(path==null)path = getConfDir();
        Configure config;
        try {
            config = JsonTools.readObjectFromJsonFile(path, CONFIG_DOT_JSON, Configure.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(config==null){
            log.debug("Failed to load config from "+ CONFIG_DOT_JSON+". It will be create.");
            if(br==null)return new Configure();
            else return new Configure(br);
        }
        config.setBr(br);
        return config;
    }

    public static Configure loadConfig(){
        return loadConfig(null,null);
    }
    public static Configure loadConfig(String path){
        return loadConfig(path,null);
    }

    public static Configure loadConfig(BufferedReader br){
        return loadConfig(null,br);
    }

    @Nullable
    private static String inputOwner() {
        String input = FCH.Inputer.inputGoodFid(br, "Set the owner FID. Enter to create a new one:");
        if ("".equals(input)) {
            ECKey ecKey = KeyTools.genNewFid(br);
            input = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
        }
        return input;
    }


    //
//    public boolean checkInitialApis(byte[] symKey){
//
//        if(config.getOwner()==null) initApiAccounts(symKey);
//
//        if(setter.getApipAccountId()!=null) {
//            if(!loadApiClient(setter.getApipAccountId(),symKey)){
//                setter.setApipAccountId(null);
//                config.saveConfig(jedisPool);
//                return false;
//            }
//        }
//
//        if(setter.getNaSaNodeAccountId()!=null) {
//            if(!loadApiClient(setter.getNaSaNodeAccountId(), symKey)){
//                setter.setNaSaNodeAccountId(null);
//                config.saveConfig(jedisPool);
//                return false;
//            }
//        }
//
//        if(setter.getEsAccountId()!=null) {
//            if(!loadApiClient(setter.getEsAccountId(), symKey)) {
//                setter.setEsAccountId(null);
//                config.saveConfig(jedisPool);
//                return false;
//            }
//        }
//
//        if(setter.getRedisAccountId()!=null) {
//            if(!loadApiClient(setter.getRedisAccountId(), symKey)){
//                setter.setRedisAccountId(null);
//                config.saveConfig(jedisPool);
//                return false;
//            }
//        }
//        config.saveConfig(jedisPool);
//        return true;
//    }

//    public boolean loadConfig(byte[] symKey){
//        System.out.println("Check the config...");
//        if(config==null)loadConfig();
//        System.out.println("Config done. You can reset it in the Setting of the menu.");
//        return true;
//    }
//    public void closeClients(){
//        jedisPool.close();
//    }

//    private boolean loadApiClient(String id, byte[] symKey) {
//        ApiAccount apiAccount;
//        ApiProvider apiProvider;
//        try {
//            apiAccount = config.getApiAccountMap().get(id);
//            apiProvider = config.getApiProviderMap().get(apiAccount.getSid());
//            apiAccount.connectApi(apiProvider, symKey, br);
//        } catch (Exception e) {
//            System.out.println("Failed to load :"+id);
//            return false;
//        }
//
//        switch (apiProvider.getType()){
//            case APIP -> initApipClient=(ApipClient) apiAccount.getClient();
////            case ES -> esClient = (ElasticsearchClient) apiAccount.getClient();
////            case NaSaRPC -> naSaRpcClient = (NaSaRpcClient) apiAccount.getClient();
//            case Redis -> jedisPool = (JedisPool) apiAccount.getClient();
//        }
//        if(apiProvider.getType()== ApiProvider.ApiType.Redis){
//            jedisPool = (JedisPool) apiAccount.getClient();
//        }
//        return true;
//    }

//    private void initApiAccounts(byte[] symKey) {
//        System.out.println("Initial the API accounts...");
//
//        String input = FCH.Inputer.inputGoodFid(br,"Set the owner FID. Enter to create a new one:");
//        if("".equals(input)){
//            ECKey ecKey = KeyTools.genNewFid(br);
//            input = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
//        }
//        config.setOwner(input);
//
//        if(setter.getApipAccountId()==null){
//            if(Inputer.askIfYes(br,"No APIP service provider yet. Add it? y/n: ")) {
//                String accountId = setApiService(symKey, ApiProvider.ApiType.APIP);
//                setter.setApipAccountId(accountId);
//                initApipClient = (ApipClient) config.getApiAccountMap().get(accountId).getClient();
//                config.saveConfig(jedisPool);
//            }
//        }
//
//        if(setter.getNaSaNodeAccountId()==null){
//            if(Inputer.askIfYes(br,"No NaSa node yet. Add it? y/n: ")){
//                String id = setApiService(symKey, ApiProvider.ApiType.NaSaRPC);
//                setter.setNaSaNodeAccountId(id);
//                config.saveConfig(jedisPool);
//            }
//        }
//
//        if(setter.getEsAccountId()==null){
//            if(Inputer.askIfYes(br,"No ElasticSearch service provider yet. Add it? y/n: ")){
//                String id = setApiService(symKey,null);
//                setter.setEsAccountId(id);
//                config.saveConfig(jedisPool);
//            }
//        }
//
//        if(setter.getRedisAccountId()==null){
//            if(Inputer.askIfYes(br,"No Redis provider yet. Add it? y/n: ")) {
//                String id = setApiService(symKey,null);
//                setter.setRedisAccountId(id);
//                config.saveConfig(jedisPool);
//            }
//            while (Inputer.askIfYes(br,"Add more API service? y/n")) {
//                setApiService(symKey,null);
//            }
//        }
//    }


//    public void showInitApipBalance(){
//        ApiAccount apipAccount = config.getApiAccountMap().get(setter.getApipAccountId());
//        System.out.println("APIP balance: "+(double) apipAccount.getBalance()/ COIN_TO_SATOSHI + " F");
//        System.out.println("Rest request: "+(long)((apipAccount.getBalance())/(Double.parseDouble(apipAccount.getApipParams().getPricePerKBytes())* COIN_TO_SATOSHI))+" times");
//    }

    public BufferedReader getBr() {
        return br;
    }

    public void setBr(BufferedReader br) {
        this.br = br;
    }

    public ApipClient getInitApipClient() {
        return initApipClient;
    }

    public void setInitApipClient(ApipClient initApipClient) {
        this.initApipClient = initApipClient;
    }

//    public JedisPool getJedisPool() {
//        return jedisPool;
//    }
//
//    public void setJedisPool(JedisPool jedisPool) {
//        this.jedisPool = jedisPool;
//    }

    public Set<String> getMyServiceIdSet() {
        return myServiceIdSet;
    }

    public void setMyServiceIdSet(Set<String> myServiceIdSet) {
        this.myServiceIdSet = myServiceIdSet;
    }

    public static String getConfigDotJson() {
        return CONFIG_DOT_JSON;
    }

    public static void setConfigDotJson(String configDotJson) {
        CONFIG_DOT_JSON = configDotJson;
    }

//    public String getInitRedisAccountId() {
//        return initRedisAccountId;
//    }
//
//    public void setInitRedisAccountId(String initRedisAccountId) {
//        this.initRedisAccountId = initRedisAccountId;
//    }
    //    public ElasticsearchClient getEsClient() {
//        return esClient;
//    }
//
//    public void setEsClient(ElasticsearchClient esClient) {
//        this.esClient = esClient;
//    }
//
//    public NaSaRpcClient getNaSaRpcClient() {
//        return naSaRpcClient;
//    }
//
//    public void setNaSaRpcClient(NaSaRpcClient naSaRpcClient) {
//        this.naSaRpcClient = naSaRpcClient;
//    }

    public List<String> getUsers() {
        return users;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }
}
