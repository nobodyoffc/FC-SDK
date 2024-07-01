package config;

import appTools.Menu;
import feip.feipData.serviceParams.*;
import appTools.Inputer;
import clients.apipClient.ApipClient;
import feip.feipData.Service;
import appTools.Shower;
import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.gson.Gson;
import constants.FieldNames;
import constants.IndicesNames;
import crypto.*;
import fcData.AlgorithmId;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FreeApi;
import server.ServiceType;
import server.Settings;
import server.serviceManagers.ChatManager;
import server.serviceManagers.DiskManager;
import server.serviceManagers.SwapManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static appTools.Inputer.askIfYes;
import static constants.FieldNames.FORBID_FREE_API;
import static constants.FieldNames.OWNER;
import static constants.Strings.*;
import static fch.Inputer.inputPriKey;
import static fch.Inputer.makePriKeyCipher;
import static server.Settings.addSidBriefToName;

public class Configure {
    protected String nonce;
    private String nonceCipher;
    protected List<String> ownerList;  //Owners for servers.
    protected Map<String,String> fidCipherMap; //Users for clients or accounts for servers.
    private String apipAccountId;
    private String esAccountId;
//    private ApiAccount esAccount;
//    private ApiAccount apipAccount;
    private Map<String,String> myServiceIdMap;
    private Map<String, ApiProvider> apiProviderMap;
    private Map<String, ApiAccount> apiAccountMap;

    final static Logger log = LoggerFactory.getLogger(Configure.class);
    public static String CONFIG_DOT_JSON = "config.json";

    private static BufferedReader br;
    private List<FreeApi> freeApipUrlList;
//    public static List<String> freeApipUrls;

    public Configure(BufferedReader br) {
        Configure.br =br;
        initiateFreeApipServices();
    }

    public Configure() {
        initiateFreeApipServices();
    }

    public static void makeWebConfig(String sid, Configure configure,Settings settings, byte[] symKey, ApiType apiType, JedisPool jedisPool,BufferedReader br) {
        String fileName = apiType.name() + "_" + CONFIG + DOT_JSON;
        if (!new File(fileName).exists()) {
            if(askIfYes(br,"Creat config file for the web server?")) {
                makeWebConfig(sid, configure, settings,symKey, apiType, jedisPool);
                Menu.anyKeyToContinue(br);
            }
        }
    }
    public static void makeWebConfig(String sid, Configure configure,Settings settings, byte[] symKey, ApiType apiType, JedisPool jedisPool) {
        String fileName = apiType.name()+"_"+CONFIG+DOT_JSON;

        WebServerConfig webServerConfig = new WebServerConfig();
        webServerConfig.setSid(sid);
        String confDir = getConfDir();
        webServerConfig.setConfigPath(Path.of(confDir,CONFIG+DOT_JSON).toString());
        webServerConfig.setSettingPath(Path.of(confDir,addSidBriefToName(sid,SETTINGS+DOT_JSON)).toString());
        webServerConfig.setDataPath(Settings.getLocalDataDir(sid));
        CryptoDataByte result = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7).encryptByPassword(symKey, configure.getNonce().toCharArray());
        if(result.getCode()!=0){
            System.out.println("Failed to encrypt symKey for web server.");
            return;
        }
        String symKeyCipher = result.toJson();
        try(Jedis jedis = jedisPool.getResource()){
            jedis.hset(addSidBriefToName(sid,WEB_PARAMS),SYM_KEY_CIPHER,symKeyCipher);
            jedis.hset(addSidBriefToName(sid,WEB_PARAMS),FORBID_FREE_API, String.valueOf(settings.getForbidFreeApi()));
        }
        JsonTools.writeObjectToJsonFile(webServerConfig,fileName,false);

        System.out.println("Copy the file of '"+fileName+"' to the bin directory of Tomcat.");
    }

    public void initiateFreeApipServices(){
        if(freeApipUrlList==null){
            freeApipUrlList = new ArrayList<>();
            FreeApi freeApiHelp = new FreeApi("https://help.cash/APIP",true);
            FreeApi freeApiApip = new FreeApi("https://apip.cash/APIP",true);
            freeApipUrlList.add(freeApiHelp);
            freeApipUrlList.add(freeApiApip);
        }
    }

    public String initiateClient(byte[] symKey) {
        System.out.println("Initiating config...");
        String fid;
        if (apiProviderMap == null) apiProviderMap = new HashMap<>();
        if (apiAccountMap == null) apiAccountMap = new HashMap<>();
        if (fidCipherMap == null) {
            fidCipherMap = new HashMap<>();
            addUser(symKey);
        }

        if(fidCipherMap ==null || fidCipherMap.isEmpty())
            return null;
        fid = (String) Inputer.chooseOne(fidCipherMap.keySet().toArray(), "Choose a user:", br);
        if(fid==null)fid = addUser(symKey);
        saveConfig();
        return fid;
    }

    public String addUser(byte[] symKey) {
        return addUser(null,symKey);

    }

    public String addUser(String fid,byte[] symKey) {
        if(fidCipherMap.get(fid)!=null){
            if(!Inputer.askIfYes(br,fid +" exists. Replace it?"))return fid;
        }

        if(fid==null)System.out.println("Add new user...");
        else System.out.println("Add "+fid+" to users...");
        byte[] priKeyBytes;
        if(askIfYes(br,"Add a watch FID without private Key?")) {
            if(fid==null)
                fid = fch.Inputer.inputGoodFid(br, "Input the FID for watching");
            fidCipherMap.put(fid,"");
        }else {
            while(true) {
                priKeyBytes = inputPriKey(br);
                String newFid = KeyTools.priKeyToFid(priKeyBytes);
                if(fid==null) {
                        fid = newFid;
                        break;
                }
                if (newFid.equals(fid)) break;
                System.out.println("The cipher is of "+newFid+" instead of "+fid+". \nTry again.");
            }
            String cipher = makePriKeyCipher(priKeyBytes, symKey);
            fidCipherMap.put(fid, cipher);
        }
        return fid;
    }

    private Service chooseOwnerService(String owner,byte[] symKey, ApiType apiType, ApipClient apipClient) {
        return chooseOwnerService(owner,symKey,apiType,null, apipClient);
    }

    private Service chooseOwnerService(String owner,byte[] symKey, ApiType apiType, ElasticsearchClient esClient) {
        return chooseOwnerService(owner,symKey,apiType,esClient,null);
    }

    public List<Service> getServiceListByOwnerAndTypeFromEs(String owner, @Nullable ApiType type, ElasticsearchClient esClient) {
        List<Service> serviceList;

        SearchRequest.Builder sb = new SearchRequest.Builder();
        sb.index(IndicesNames.SERVICE);

        BoolQuery.Builder bb = QueryBuilders.bool();
        bb.must(b->b.term(t->t.field(OWNER).value(owner)));
        bb.must(m->m.term(t1->t1.field(ACTIVE).value(true)));
        if(type!=null)bb.must(m2->m2.match(m3->m3.field(TYPES).query(type.name())));
        BoolQuery boolQuery = bb.build();
        sb.query(q->q.bool(boolQuery));
        sb.size(EsTools.READ_MAX);
        SearchResponse<Service> result;
        try {
            result = esClient.search(sb.build(), Service.class);
        } catch (IOException e) {
            return null;
        }
        serviceList = new ArrayList<>();
        if(result==null || result.hits()==null)return null;
        for(Hit<Service> hit : result.hits().hits()){
            serviceList.add(hit.source());
        }
        return serviceList;
    }
    @Nullable
    public Service chooseOwnerService(String owner,byte[] symKey, ApiType type, ElasticsearchClient esClient, ApipClient apipClient) {
        List<Service> serviceList;

        if(esClient==null)
            serviceList = apipClient.getServiceListByOwnerAndType(owner,type);
        else serviceList = getServiceListByOwnerAndTypeFromEs(owner,type,esClient);

        if(serviceList.isEmpty()){
            System.out.println("No any service on chain of the owner.");
            return null;
        }

        Service service;
        if(symKey!=null)service = selectService(serviceList, symKey, apiAccountMap.get(apipAccountId));
        else service = selectService(serviceList);
        if(service==null) System.out.println("Failed to get the service.");
        else {
            myServiceIdMap.put(service.getSid(),service.getStdName());
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

//    private Service chooseTypeService(String type) {
//        List<Service> serviceList;
//        if(myServiceIdSet ==null){
//            myServiceIdSet = new HashSet<>();
//            serviceList = initApipClient.getServiceListByType(type);
//        }else {
//            String[] ids = myServiceIdSet.toArray(new String[0]);
//            Map<String, Service> serviceMap = initApipClient.serviceMapByIds(ids);
//            serviceList = new ArrayList<>(serviceMap.values());
//        }
//
//        Service service;
//        service = selectService(serviceList);
//        if(service==null) System.out.println("Failed to get the service.");
//        else {
//            myServiceIdSet.add(service.getSid());
//        }
//        return service;
//    }
//    private Service chooseOwnerService(ApiType type) {
//        return chooseOwnerService(null, type, apipClient);
//    }

    public static Service selectService(List<Service> serviceList,byte[] symKey,ApiAccount apipAccount){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServices(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            if(Inputer.askIfYes(br,"Publish a new service?")){
                ServiceType type = Inputer.chooseOne(ServiceType.values(), "Choose a type", br);
                switch (type){
                    case DISK -> new DiskManager(null,apipAccount,br,symKey,DiskParams.class).publishService();
                    case SWAP -> new SwapManager(null,apipAccount,br,symKey,SwapParams.class).publishService();
                    case CHAT -> new ChatManager(null,apipAccount,br,symKey,ChatParams.class).publishService();
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
        String[] fields = new String[]{FieldNames.STD_NAME,FieldNames.TYPES,FieldNames.SID};
        int[] widths = new int[]{24,24,64};
        List<List<Object>> valueListList = new ArrayList<>();
        for(Service service : serviceList){
            List<Object> valueList = new ArrayList<>();
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
        }
        Shower.showDataTable(title,fields,widths,valueListList);
    }
    private ApiAccount chooseApi(byte[] symKey, ApiType type,ApipClient apipClient) {
        System.out.println("The " + type.name() + " is not ready. Set it...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,type,apipClient);
        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider, symKey,apipClient);
        if(apiAccount.getClient()==null) {
            System.err.println("Failed to create " + type.name() + ".");
            return null;
        }
        return apiAccount;
    }
    public ApiAccount addApiAccount(@NotNull ApiProvider apiProvider, byte[] symKey, ApipClient initApipClient) {
        System.out.println("Add API account for provider "+ apiProvider.getId()+"...");
        if(apiAccountMap==null)apiAccountMap = new HashMap<>();
        ApiAccount apiAccount;
        while(true) {
            apiAccount = new ApiAccount();
            apiAccount.inputAll(symKey,apiProvider,br);
//            if(initApipClient !=null)apiAccount.setApipClient(initApipClient);
            if (apiAccountMap.get(apiAccount.getId()) != null) {
                ApiAccount apiAccount1 = apiAccountMap.get(apiAccount.getId());
                if (!Inputer.askIfYes(br, "There has an account for user " + apiAccount1.getUserName() + " on SID " + apiAccount1.getProviderId() + ".\n Cover it?")) {
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
                System.out.println("Can't connect the API provider of "+apiProvider.getId());
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
        String[] fields = {"sid", "type","url", "ticks"};
        int[] widths = {16,10, 32, 24};
        List<List<Object>> valueListList = new ArrayList<>();
        for (ApiProvider apiProvider : apiProviderMap.values()) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(apiProvider.getId());
            valueList.add(apiProvider.getType());
            valueList.add(apiProvider.getApiUrl());
            valueList.add(Arrays.toString(apiProvider.getTicks()));
            valueListList.add(valueList);
        }
        Shower.showDataTable("API providers", fields, widths, valueListList);
    }

    public void showAccounts(Map<String, ApiAccount> apiAccountMap) {
        if(apiAccountMap==null || apiAccountMap.size()==0)return;
        String[] fields = {"id","userName","userId", "url", "sid"};
        int[] widths = {16,16,16, 32, 16};
        List<List<Object>> valueListList = new ArrayList<>();
        for (ApiAccount apiAccount : apiAccountMap.values()) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(apiAccount.getId());
            valueList.add(apiAccount.getUserName());
            valueList.add(apiAccount.getUserId());
            valueList.add(apiAccount.getApiUrl());
            valueList.add(apiAccount.getProviderId());
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
//        String confDir = Configure.getConfDir();
//        File file = new File(confDir, Configure.CONFIG_DOT_JSON);
//        if(!file.exists()) {
//            FileTools.createFileWithDirectories(confDir+ Configure.CONFIG_DOT_JSON);
//        }
        javaTools.JsonTools.writeObjectToJsonFile(this, Configure.getConfDir()+ Configure.CONFIG_DOT_JSON,false);
    }


    public List<String> getOwnerList() {
        return ownerList;
    }

    public void setOwnerList(List<String> ownerList) {
        this.ownerList = ownerList;
    }

    public String getNonceCipher() {
        return nonceCipher;
    }

    public void setNonceCipher(String nonceCipher) {
        this.nonceCipher = nonceCipher;
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public void addApiAccount(byte[] symKey,ApipClient initApipClient){
        System.out.println("Add API accounts...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,  initApipClient);
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, symKey, initApipClient);
            saveConfig();
            System.out.println("Add API account "+apiAccount.getId()+" is added.");
        }
    }
    public String addApiProviderAndConnect(byte[] symKey,ApipClient initApipClient){
        return addApiProviderAndConnect(symKey,null, initApipClient);
    }
    public String addApiProviderAndConnect(byte[] symKey, ApiType apiType,ApipClient initApipClient){
        System.out.println("Add API providers...");
        ApiProvider apiProvider = addApiProvider(apiType,initApipClient);
        String apiAccountId = null;
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, symKey, initApipClient);
            if(apiAccount!=null) {
                apiAccount.connectApi(apiProvider, symKey, br, null);
                apiAccountId = apiAccount.getId();
                saveConfig();
            }else return null;
        }
        System.out.println("Add API provider "+apiProvider.getId()+" is added.");
        return apiAccountId;
    }

    public ApiProvider addApiProvider(ApiType apiType, ApipClient apipClient) {
        ApiProvider apiProvider = new ApiProvider();
        apiProvider.makeApiProvider(br,apiType,apipClient);
        if(apiProviderMap==null)apiProviderMap= new HashMap<>();
        apiProviderMap.put(apiProvider.getId(),apiProvider);
        System.out.println(apiProvider.getId()+" on "+apiProvider.getApiUrl() + " added.");
        saveConfig();
        return apiProvider;
    }


    public void updateApiAccount(ApiProvider apiProvider, byte[] symKey,ApipClient initApipClient){
        System.out.println("Update API accounts...");
        ApiAccount apiAccount;
        if(apiProvider==null)apiAccount = chooseApiAccount(symKey,initApipClient);
        else apiAccount = chooseApiProvidersAccount(apiProvider, symKey,initApipClient);
        if(apiAccount!=null) {
            System.out.println("Update API account: "+apiAccount.getProviderId()+"...");
            apiAccount.updateAll(symKey, apiProvider,br);
            getApiAccountMap().put(apiAccount.getId(), apiAccount);
            saveConfig();
        }
        System.out.println("Api account "+apiAccount.getId()+" is updated.");
    }

    public void updateApiProvider(byte[] symKey,ApipClient apipClient){
        System.out.println("Update API providers...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, apipClient);
        if(apiProvider!=null) {
            apiProvider.updateAll(br);
            getApiProviderMap().put(apiProvider.getId(), apiProvider);
            saveConfig();
            System.out.println("Api provider "+apiProvider.getId()+" is updated.");
        }
    }

    public void deleteApiProvider(byte[] symKey,ApipClient apipClient){
        System.out.println("Deleting API provider...");
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap);
        if(apiProvider==null) return;
        for(ApiAccount apiAccount: getApiAccountMap().values()){
            if(apiAccount.getProviderId().equals(apiProvider.getId())){
                if(Inputer.askIfYes(br,"There is the API account "+apiAccount.getId()+" of "+apiProvider.getId()+". \nDelete it?")){
                    getApiAccountMap().remove(apiAccount.getId());
                    System.out.println("Api account "+apiAccount.getId()+" is deleted.");
                    saveConfig();
                }
            }
        }
        if(Inputer.askIfYes(br,"Delete API provider "+apiProvider.getId()+"?")){
            getApiProviderMap().remove(apiProvider.getId());
            System.out.println("Api provider " + apiProvider.getId() + " is deleted.");
            saveConfig();
        }
    }

    public void deleteApiAccount(byte[] symKey,ApipClient initApipClient){
        System.out.println("Deleting API Account...");
        ApiAccount apiAccount = chooseApiAccount(symKey,initApipClient);
        if(apiAccount==null) return;
        if(Inputer.askIfYes(br,"Delete API account "+apiAccount.getId()+"?")) {
            getApiAccountMap().remove(apiAccount.getId());
            System.out.println("Api account " + apiAccount.getId() + " is deleted.");
            saveConfig();
        }
    }

    public ApiAccount chooseApiAccount(byte[] symKey,ApipClient initApipClient){
        ApiAccount apiAccount = null;
        showAccounts(getApiAccountMap());
        int input = Inputer.inputInteger(br, "Input the number of the account you want. Enter to add a new one:", getApiAccountMap().size());
        if (input == 0) {
            if(Inputer.askIfYes(br,"Add a new API account?")) {
                ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, initApipClient);
                apiAccount = addApiAccount(apiProvider, symKey, initApipClient);
            }
        } else {
            apiAccount = (ApiAccount) getApiAccountMap().values().toArray()[input - 1];
        }
        return apiAccount;
    }
//    public ApiProvider chooseApiProviderOrAdd(Map<String, ApiProvider> apiProviderMap, ApiType type, ApipClient apipClient,BufferedReader br){
//        ApiProvider apiProvider = chooseApiProvider(this.apiProviderMap);
//        if(apiProvider==null){
//            ApiType apiType = Inputer.chooseOne(ApiType.values(), "Choose the type of the API:", br);
//            apiProvider = addApiProvider(apiType,apipClient);
//        }
//        return apiProvider;
//    }
public ApiProvider chooseApiProviderOrAdd(Map<String, ApiProvider> apiProviderMap, ApipClient apipClient){
        return chooseApiProviderOrAdd(apiProviderMap,null,apipClient);
}
    public ApiProvider chooseApiProviderOrAdd(Map<String, ApiProvider> apiProviderMap, ApiType apiType, ApipClient apipClient){
        if(apiType==null)
            apiType = fch.Inputer.chooseOne(ApiType.values(),"Choose the API type:",br);
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap,apiType);
        if(apiProvider==null){
            apiProvider = addApiProvider(apiType,apipClient);
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

    public ApiProvider selectFcApiProvider(ApipClient initApipClient, ApiType apiType) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap,apiType);
        if(apiProvider==null) apiProvider= ApiProvider.searchFcApiProvider(initApipClient,apiType);
        return apiProvider;
    }

    public ApiAccount chooseApiProvidersAccount(ApiProvider apiProvider, byte[] symKey,ApipClient initApipClient) {
        ApiAccount apiAccount = null;
        Map<String, ApiAccount> hitApiAccountMap = new HashMap<>();
        if (getApiAccountMap() == null) setApiAccountMap(new HashMap<>());
        if (getApiAccountMap().size() == 0) {
            System.out.println("No API accounts yet. Add new one...");
            apiAccount = addApiAccount(apiProvider, symKey, initApipClient);
        } else {
            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
                if (apiAccount1.getProviderId().equals(apiProvider.getId()))
                    hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
            }
            if (hitApiAccountMap.size() == 0) {
                apiAccount = addApiAccount(apiProvider, symKey, initApipClient);
            } else {
                showAccounts(hitApiAccountMap);
                int input = Inputer.inputInteger( br,"Input the number of the account you want. Enter to add new one:", hitApiAccountMap.size());
                if (input == 0) {
                    apiAccount = addApiAccount(apiProvider, symKey,initApipClient );
                } else {
                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
                    apiAccount.setApipClient(initApipClient);
                    if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symKey);
                }
            }
        }
        return apiAccount;
    }
    public ApiAccount checkAPI(@Nullable String apiAccountId, ApiType apiType, byte[] symKey) {
        return checkAPI(apiAccountId,apiType,symKey,null);
    }
    public ApiAccount checkAPI(@Nullable String apiAccountId, ApiType apiType, byte[] symKey,ApipClient apipClient) {
        ApiAccount apiAccount = null;
        while (true) {
            if (apiAccountId == null) {
                System.out.println("No " + apiType + " service set yet. Add it.");
                try{
                    apiAccount = setApiService(symKey,apiType, apipClient);
                }catch (Exception e){
                    return null;//apiAccount = setApiService(symKey,apipClient);
                }
            }
            if (apiAccount == null) {
                System.out.println("Failed to get API account. Try again.");
                continue;
            }
            if (apiAccount.getClient() == null) {
                Object apiClient = apiAccount.connectApi(getApiProviderMap().get(apiAccount.getProviderId()), symKey, br, null);
                if (apiClient == null) {
                    System.out.println("Failed to connect " + apiAccount.getApiUrl() + ". Try again.");
                    continue;
                }
            }
            return apiAccount;
        }
    }

    public ApiAccount setApiService(byte[] symKey,ApipClient apipClient) {
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, apipClient);
        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider,symKey,apipClient);
        if(apiAccount.getClient()!=null) saveConfig();
        return apiAccount;
    }

    public ApiAccount setApiService(byte[] symKey, ApiType apiType,ApipClient apipClient) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap,apiType);
        if(apiProvider==null) apiProvider = addApiProvider(apiType,apipClient);
        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider,symKey,apipClient);
        if(apiAccount==null) apiAccount = addApiAccount(apiProvider, symKey,apipClient );
        if(apiAccount.getClient()!=null) saveConfig();
        return apiAccount;
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
                symKey = getSymKeyFromPasswordAndNonce(passwordBytes, nonceBytes);
                nonce = Hex.toHex(nonceBytes);

                Encryptor encryptor = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
                CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(nonceBytes,symKey);
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
                symKey = getSymKeyFromPasswordAndNonce(passwordBytes, Hex.fromHex(nonce));
                Decryptor decryptor = new Decryptor();
                CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(getNonceCipher(),symKey);
                if(cryptoDataByte.getCode()!=0){
                    System.out.println("Wrong password. Try again.");
                    continue;
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

    public static byte[] getSymKeyFromPasswordAndNonce(byte[] passwordBytes, byte[] nonce) {
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

    public static Configure loadConfig(String path, BufferedReader br){
        if(path==null)path = getConfDir();
        Configure config;
        try {
            config = JsonTools.readObjectFromJsonFile(path, CONFIG_DOT_JSON, Configure.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(config==null){
            log.debug("Failed to load config from "+ CONFIG_DOT_JSON+". It will be create.");
            config = new Configure();
        }
        config.setBr(br);
        if(config.getApiProviderMap()==null)
            config.setApiProviderMap(new HashMap<>());
        if(config.getApiAccountMap() == null)
            config.setApiAccountMap(new HashMap<>());
        if(config.getFidCipherMap()==null)
            config.setFidCipherMap(new HashMap<>());
        if(config.getMyServiceIdMap()==null)
            config.setMyServiceIdMap(new HashMap<>());
        if(config.getOwnerList()==null)
            config.setOwnerList(new ArrayList<>());
        return config;
    }

    public static Configure loadConfig(BufferedReader br){
        return loadConfig(null, br);
    }
    public static Configure loadConfig(String path){
        return loadConfig(path, null);
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

//    public JedisPool getJedisPool() {
//        return jedisPool;
//    }
//
//    public void setJedisPool(JedisPool jedisPool) {
//        this.jedisPool = jedisPool;
//    }


    public Map<String, String> getMyServiceIdMap() {
        return myServiceIdMap;
    }

    public void setMyServiceIdMap(Map<String, String> myServiceIdMap) {
        this.myServiceIdMap = myServiceIdMap;
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

    public String getEsAccountId() {
        return esAccountId;
    }

    public void setEsAccountId(String esAccountId) {
        this.esAccountId = esAccountId;
    }

    public Map<String, String> getFidCipherMap() {
        return fidCipherMap;
    }

    public void setFidCipherMap(Map<String, String> fidCipherMap) {
        this.fidCipherMap = fidCipherMap;
    }

    public String addOwner(BufferedReader br) {
        String owner = fch.Inputer.inputGoodFid(br,"Input the owner fid:");
        if(ownerList==null)ownerList = new ArrayList<>();
        ownerList.add(owner);
        saveConfig();
        return owner;
    }

    public String chooseMainFid(byte[] symKey) {
        String fid = Inputer.chooseOne(fidCipherMap.keySet().toArray(new String[0]), "Choose the FID",br);
        if(fid==null)fid = addUser(symKey);
        return fid;
    }

    public String chooseSid(byte[] symKey) {
        return Inputer.chooseOne(myServiceIdMap, "Choose your SID:",br);
    }

    public List<FreeApi> getFreeApipUrlList() {
        return freeApipUrlList;
    }

    public void setFreeApipUrlList(List<FreeApi> freeApipUrlList) {
        this.freeApipUrlList = freeApipUrlList;
    }
}
