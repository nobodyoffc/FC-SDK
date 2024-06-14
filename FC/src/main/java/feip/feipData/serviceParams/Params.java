package feip.feipData.serviceParams;

import clients.apipClient.ApipClient;
import feip.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;
import javaTools.JsonTools;

import java.io.BufferedReader;
import java.io.IOException;

public abstract class Params {
    protected String account;
    protected String pricePerKBytes;
    protected String minPayment;
    protected String pricePerRequest;
    protected String sessionDays;
    protected String urlHead;
    protected String consumeViaShare;
    protected String orderViaShare;
    protected String currency;

    public Params() {}

    public static <T> T getParamsFromService(Service service, Class<T> tClass) {
        T params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), tClass);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong.");
            return null;
        }
        service.setParams(params);
        return params;
    }
    protected String updateAccount(BufferedReader br, byte[] symKey, ApipClient apipClient) {
        if(Inputer.askIfYes(br,"The account is "+this.account)){
            return fch.Inputer.inputOrCreateFid("Input the account:",br,symKey,apipClient);
        }
        return this.account;
    }

    protected String updatePricePerKBytes(BufferedReader br) throws IOException {
        return Inputer.promptAndUpdate(br, "Input the pricePerKBytes:", this.pricePerKBytes);
    }

    public abstract void inputParams(BufferedReader br, byte[] symKey);
    public abstract void updateParams(BufferedReader br, byte[] symKey);
    public String toJson(){
        return JsonTools.getNiceString(this);
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPricePerKBytes() {
        return pricePerKBytes;
    }

    public void setPricePerKBytes(String pricePerKBytes) {
        this.pricePerKBytes = pricePerKBytes;
    }

    public String getMinPayment() {
        return minPayment;
    }

    public void setMinPayment(String minPayment) {
        this.minPayment = minPayment;
    }

    public String getPricePerRequest() {
        return pricePerRequest;
    }

    public void setPricePerRequest(String pricePerRequest) {
        this.pricePerRequest = pricePerRequest;
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
    }

    public String getSessionDays() {
        return sessionDays;
    }

    public void setSessionDays(String sessionDays) {
        this.sessionDays = sessionDays;
    }

    public String getConsumeViaShare() {
        return consumeViaShare;
    }

    public void setConsumeViaShare(String consumeViaShare) {
        this.consumeViaShare = consumeViaShare;
    }

    public String getOrderViaShare() {
        return orderViaShare;
    }

    public void setOrderViaShare(String orderViaShare) {
        this.orderViaShare = orderViaShare;
    }
}
