package config;


import FEIP.feipData.serviceParams.ApipParams;
import FEIP.feipData.serviceParams.DiskParams;
import FEIP.feipData.serviceParams.Params;
import FEIP.feipData.serviceParams.SwapParams;
import clients.apipClient.ApipClient;
import clients.apipClient.ApipClientTask;
import clients.apipClient.OpenAPIs;
import FEIP.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;
import javaTools.JsonTools;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import static appTools.Inputer.promptAndUpdate;


public class ApiProvider {
    private static final Logger log = LoggerFactory.getLogger(ApiProvider.class);
    private static final String DEFAULT_API_URL = "https://cid.cash/APIP";
    private String sid;
    private ApiType type;
    private String orgUrl;
    private String docUrl;
    private String apiUrl;
    private String owner;
    private String[] protocols;
    private String[] ticks;
    private transient Service service;
    private transient Params apiParams;

    public ApiProvider() {}
    public boolean fromFcService(Service service) {
        if(service==null)return false;
        this.sid =service.getSid();
        Params params = Params.getParamsFromService(service, Params.class);
        if(params==null) return false;
        this.apiUrl=params.getUrlHead();
        this.owner=service.getOwner();
        for(String type : service.getTypes()){
            try{
                this.type=ApiType.valueOf(type);
                break;
            }catch (Exception ignore){};
        }
        if(service.getUrls().length>0)this.orgUrl=service.getUrls()[0];
        if(service.getProtocols().length>0)this.protocols=service.getProtocols();
        this.apiParams=Params.getParamsFromService(service,Params.class);
        this.service=service;
        return true;
    }

    @Nullable
    public static ApiProvider apiProviderFromFcService(Service service,ApiType type) {
        if(service==null)return null;
        ApiProvider apiProvider = new ApiProvider();
        apiProvider.setSid(service.getSid());
        Params params = null;
        switch (type){
            case APIP -> params = Params.getParamsFromService(service, ApipParams.class);
            case DISK -> params = Params.getParamsFromService(service, DiskParams.class);
            case SWAP -> params = Params.getParamsFromService(service, SwapParams.class);
        }

        if(params==null) return null;
        apiProvider.setApiUrl(params.getUrlHead());
        apiProvider.setOwner(service.getOwner());
        for(String typeStr : service.getTypes()){
            try{
                apiProvider.setType(ApiType.valueOf(typeStr));
                break;
            }catch (Exception ignore){};
        }
        if(service.getUrls()!=null&&service.getUrls().length>0)apiProvider.setOrgUrl(service.getUrls()[0]);
        if(service.getProtocols()!=null&&service.getProtocols().length>0)apiProvider.setProtocols(service.getProtocols());
        apiProvider.setApiParams((Params) service.getParams());
        apiProvider.setService(service);
        apiProvider.setType(type);

        return apiProvider;
    }

    public static ApiProvider searchFcApiProvider(ApipClient initApipClient, ApiType apiType) {
        List<Service> serviceList = initApipClient.getServiceListByType(apiType.toString().toLowerCase());
        Service service = Configure.selectService(serviceList);
        if(service==null)return null;
        return apiProviderFromFcService(service,apiType);
    }

//
//    public void initiate(){
//        sid = "";
//        type = ApiType.APIP;
//        orgUrl = "";
//        docUrl = "";
//        apiUrl = "";
//        owner = "";
//        protocol = "";
//        ticks = new String[]{""};
//    }

    private void inputOwner(BufferedReader br) throws IOException {
        this.owner = Inputer.promptAndSet(br, "API owner", this.owner);
    }
    public ApiProvider makeFcProvider(ApiType apiType,ApipClient apipClient){
        List<Service> serviceList = apipClient.getServiceListByType(apiType.toString());
        Service service = Configure.selectService(serviceList);
        if(service==null)return null;
        return apiProviderFromFcService(service,type);
    }

    public ApiProvider makeApipProvider(BufferedReader br) {
        apiUrl = Inputer.inputString(br,"Input the urlHead of the APIP service. Enter to set default as "+ DEFAULT_API_URL);
        if(apiUrl==null) return null;
        if("".equals(apiUrl))apiUrl = DEFAULT_API_URL;
        ApipClientTask apipClientData = OpenAPIs.getService(apiUrl);
        if(apipClientData.checkResponse()!=0){
            System.out.println("Failed to get the APIP service from "+apiUrl);
            return null;
        }

        Gson gson = new Gson();
        service = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()),Service.class);
        apiParams = Params.getParamsFromService(service,Params.class);
        service.setParams(apiParams);
        System.out.println("Got the service:");
        JsonTools.gsonPrint(service);
        sid = service.getSid();
        owner = service.getOwner();
        protocols = service.getProtocols();
        ticks = new String[]{"fch"};
        try {
            inputOrgUrl(br);
            inputDocUrl(br);
        } catch (IOException e) {
            log.debug("BufferReader wrong.");
        }
        return this;
    }


    public void makeApiProvider(BufferedReader br, ApiType apiType,@Nullable ApipClient apipClient) {
        try  {
            if(apiType==null)inputType(br);
            else type =apiType;

            if(type==ApiType.APIP){
                makeApipProvider(br);
                return;
            }
            switch (type){
                case NASARPC ->{
                    inputApiURL(br, "http://127.0.0.1:8332");
                    inputTicks(br);
                    sid = ticks[0]+"@"+apiUrl;
                }
                case ES -> {
                    inputApiURL(br,"http://127.0.0.1:9200");
                    sid = "ES@"+apiUrl;
                }
                case REDIS -> {
                    inputApiURL(br, "http://127.0.0.1:6379");
                    sid = "Redis@"+apiUrl;
                }
                case DISK -> {
                    if(apipClient==null)throw new RuntimeException("The initial APIP client is null.");
                    List<Service> serviceList = apipClient.getServiceListByType(apiType.toString());
                    Service service = Configure.selectService(serviceList);
                    boolean done = fromFcService(service);
                    if(!done) System.out.println("Failed to make provider from on-chain service information.");
                }
                default -> {
                    inputSid(br);
                    inputApiURL(br, null);
                    inputOrgUrl(br);
                    inputDocUrl(br);
                    inputOwner(br);
                    inputProtocol(br);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }

    private void inputSid(BufferedReader br) throws IOException {
        while(true) {
            String input = Inputer.promptAndSet(br, "sid", this.sid);
            if(input!=null){
                this.sid = input;
                break;
            }
            System.out.println("Sid is necessary. Input again.");
        }
    }

    private ApiType inputType(BufferedReader br) throws IOException {
        ApiType[] choices = ApiType.values();
        type = Inputer.chooseOne(choices,"Choose the type of API provider:",br);
//
//        for(int i=0;i<choices.length;i++){
//            System.out.println((i+1)+" "+choices[i].name());
//        }
//        int choice = Inputer.inputInteger(br,"Input the number:",choices.length+1);
//        type = choices[choice-1];
        return type;
    }

    private void inputApiURL(BufferedReader br, String defaultUrl) throws IOException {
        this.apiUrl = Inputer.promptAndSet(br, "the url of API request. The default is "+defaultUrl, this.apiUrl);
        if(apiUrl==null)
                apiUrl=defaultUrl;
    }

    private void inputDocUrl(BufferedReader br) throws IOException {
        this.docUrl = Inputer.promptAndSet(br, "the url of API document", this.docUrl);
    }

    private void inputOrgUrl(BufferedReader br) throws IOException {
        this.orgUrl = Inputer.promptAndSet(br, "the url of organization", this.orgUrl);
    }

    private void inputProtocol(BufferedReader br) throws IOException {
        this.protocols = Inputer.promptAndSet(br, "protocol", this.protocols);
    }

    private void inputTicks(BufferedReader br) throws IOException {
        this.ticks = Inputer.promptAndSet(br, "ticks", this.ticks);
    }


    public void updateAll(BufferedReader br) {
        try {
            this.type = Inputer.chooseOne(ApiType.values(),"Choose the type:",br);//ApiType.valueOf(promptAndUpdate(br, "type ("+ Arrays.toString(ApiType.values())+")", String.valueOf(this.type)));
            if(type==ApiType.APIP){
                if(Inputer.askIfYes(br,"The apiUrl is "+apiUrl+". Update it?")){
                    apiUrl = Inputer.inputString(br,"Input the urlHead of the APIP service:");
                    if(apiUrl==null)return;
                    ApipClientTask apipClientData = OpenAPIs.getService(apiUrl);
                    if(apipClientData.checkResponse()!=0){
                        System.out.println("Failed to get the APIP service from "+apiUrl);
                        return;
                    }
                    Gson gson = new Gson();
                    service = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()),Service.class);
                    apiParams = Params.getParamsFromService(service,Params.class);
                    orgUrl = promptAndUpdate(br, "url of the organization", this.orgUrl);
                    docUrl = promptAndUpdate(br, "url of the API documents", this.docUrl);
                    inputDocUrl(br);
                    owner = service.getOwner();
                    protocols = service.getProtocols();
                    return;
                }
            }

            this.sid = promptAndUpdate(br, "sid of API request", this.sid);
            this.apiUrl = promptAndUpdate(br, "url of the API requests", this.apiUrl);
            this.docUrl = promptAndUpdate(br, "url of the API documents", this.docUrl);
            this.orgUrl = promptAndUpdate(br, "url of the organization", this.orgUrl);
            this.owner = promptAndUpdate(br, "API owner", this.owner);
            this.protocols = promptAndUpdate(br, "protocol", this.protocols);
            this.ticks = promptAndUpdate(br, "ticks", this.ticks);
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }



    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public ApiType getType() {
        return type;
    }

    public void setType(ApiType type) {
        this.type = type;
    }

    public String getOrgUrl() {
        return orgUrl;
    }

    public void setOrgUrl(String orgUrl) {
        this.orgUrl = orgUrl;
    }

    public String getDocUrl() {
        return docUrl;
    }

    public void setDocUrl(String docUrl) {
        this.docUrl = docUrl;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String[] getTicks() {
        return ticks;
    }

    public void setTicks(String[] ticks) {
        this.ticks = ticks;
    }

    public void setProtocols(String[] protocols) {
        this.protocols = protocols;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Params getApiParams() {
        return apiParams;
    }

    public void setApiParams(Params apiParams) {
        this.apiParams = apiParams;
    }

    public String[] getProtocols() {
        return protocols;
    }
}
