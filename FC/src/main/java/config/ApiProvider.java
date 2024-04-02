package config;


import APIP.apipClient.ApipClientData;
import APIP.apipClient.OpenAPIs;
import APIP.apipData.ApipParams;
import FEIP.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;
import crypto.cryptoTools.Hash;
import javaTools.Hex;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;

import static appTools.Inputer.promptAndUpdate;


public class ApiProvider {
    private static final Logger log = LoggerFactory.getLogger(ApiProvider.class);
    private String sid;
    private ApiType type;
    private String orgUrl;
    private String docUrl;
    private String apiUrl;
    private String owner;
    private String[] protocols;
    private String[] ticks;
    private transient Service service;
    private transient ApipParams apipParams;

    public ApiProvider() {}

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

    public ApiProvider setApipProvider(BufferedReader br) {
        apiUrl = Inputer.inputString(br,"Input the urlHead of the APIP service:");
        if(apiUrl==null) return null;
        ApipClientData apipClientData = OpenAPIs.getService(apiUrl);
        if(apipClientData.isBadResponse("get service from"+apiUrl)){
            System.out.println("Failed to get the APIP service from "+apiUrl);
            return null;
        }

        Gson gson = new Gson();
        service = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()),Service.class);
        apipParams = ApipParams.fromObject(service.getParams());
        service.setParams(apipParams);
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

    public enum ApiType {
        NaSaRPC,
        APIP,
        ES,
        Redis,
        Other
    }

    public void inputAll(BufferedReader br, ApiType apiType) {
        try  {
            if(apiType==null)inputType(br);
            else type =apiType;

            if(type==ApiType.APIP){
                setApipProvider(br);
                return;
            }
            switch (type){
                case NaSaRPC ->{
                    inputApiURL(br, "http://127.0.0.1:8332");
                    inputTicks(br);
                    sid = ticks[0]+"@"+apiUrl;
                }
                case ES -> {
                    inputApiURL(br,"http://127.0.0.1:9200");
                    sid = "ES@"+apiUrl;
                }
                case Redis -> {
                    inputApiURL(br, "http://127.0.0.1:6379");
                    sid = "Redis@"+apiUrl;
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
        System.out.println("Choose the type of the API:");
        for(int i=0;i<choices.length;i++){
            System.out.println((i+1)+" "+choices[i].name());
        }
        int choice = Inputer.inputInteger(br,"Input the number:",choices.length+1);
        type = choices[choice-1];
        return type;
    }

    private void inputApiURL(BufferedReader br, String defaultUrl) throws IOException {
        this.apiUrl = Inputer.promptAndSet(br, "url of API request", this.apiUrl);
        if(apiUrl==null){
            if(Inputer.askIfYes(br,"Set to the default: "+defaultUrl+" ?y/n"))
                apiUrl=defaultUrl;
        }
    }

    private void inputDocUrl(BufferedReader br) throws IOException {
        this.docUrl = Inputer.promptAndSet(br, "url of API document", this.docUrl);
    }

    private void inputOrgUrl(BufferedReader br) throws IOException {
        this.orgUrl = Inputer.promptAndSet(br, "url of organization", this.orgUrl);
    }

    private void inputProtocol(BufferedReader br) throws IOException {
        this.protocols = Inputer.promptAndSet(br, "protocol", this.protocols);
    }

    private void inputTicks(BufferedReader br) throws IOException {
        this.ticks = Inputer.promptAndSet(br, "ticks", this.ticks);
    }


    public void updateAll(BufferedReader br) {
        try {
            this.type = ApiType.valueOf(promptAndUpdate(br, "type ("+ Arrays.toString(ApiType.values())+")", String.valueOf(this.type)));
            if(type==ApiType.APIP){
                if(Inputer.askIfYes(br,"The apiUrl is "+apiUrl+". Update it? y/n:")){
                    apiUrl = Inputer.inputString(br,"Input the urlHead of the APIP service:");
                    if(apiUrl==null)return;
                    ApipClientData apipClientData = OpenAPIs.getService(apiUrl);
                    if(apipClientData.isBadResponse("get service from"+apiUrl)){
                        System.out.println("Failed to get the APIP service from "+apiUrl);
                        return;
                    }
                    Gson gson = new Gson();
                    service = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()),Service.class);
                    apipParams = ApipParams.fromObject(service.getParams());
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

    public ApipParams getApipParams() {
        return apipParams;
    }

    public void setApipParams(ApipParams apipParams) {
        this.apipParams = apipParams;
    }

    public String[] getProtocols() {
        return protocols;
    }
}
