package start;

import APIP.apipClient.ApipClient;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.Params;
import appTools.Inputer;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;

public class OpenDriveParams extends Params {
    private String url;
    private String currency;
    private String account;
    private String pricePerKBytes;
    private String dataLifeDays;
    private String pricePerKBytesPermanent;
    private String minPayment;
    private String sessionDays;
    private String consumeViaShare;
    private String orderViaShare;
    private final transient ApipClient apipClient;

    public OpenDriveParams(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    @Override
    public void updateParams(BufferedReader br, byte[] symKey) {
        try {
            this.url = Inputer.promptAndUpdate(br,"url",this.url);
            this.currency = Inputer.promptAndUpdate(br,"Input the currency:",this.currency);
            this.account = updateAccount(br,symKey,apipClient);
            this.pricePerKBytes = Inputer.promptAndUpdate(br,"Input the pricePerKBytes:",this.pricePerKBytes);
            this.dataLifeDays = Inputer.promptAndUpdate(br,"Input the dataLifeDays:",this.dataLifeDays);
            this.pricePerKBytesPermanent = Inputer.promptAndUpdate(br,"Input the pricePerKBytesPermanent:",this.pricePerKBytesPermanent);
            this.minPayment = Inputer.promptAndUpdate(br,"Input the minPayment:",this.minPayment);
            this.sessionDays = Inputer.promptAndUpdate(br,"Input the sessionDays:",this.sessionDays);
            this.consumeViaShare = Inputer.promptAndUpdate(br,"Input the consumeViaShare:",this.consumeViaShare);
            this.orderViaShare = Inputer.promptAndUpdate(br,"Input the orderViaShare:",this.orderViaShare);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String updateAccount(BufferedReader br, byte[] symKey, ApipClient apipClient) {
        if(Inputer.askIfYes(br,"The account is "+this.account)){
            return FCH.Inputer.inputOrCreateFid("Input the account:",br,symKey,apipClient);
        }
        return this.account;
    }

    public static OpenDriveParams getParamsFromService(Service service) {
        OpenDriveParams params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), OpenDriveParams.class);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong.");
            return null;
        }
        service.setParams(params);
        return params;
    }
    public void inputParams(BufferedReader br, byte[]symKey){
        this.url = Inputer.inputString(br,"Input the url:");
        this.currency = Inputer.inputString(br,"Input the currency:");
        this.account = FCH.Inputer.inputOrCreateFid("Input the account:",br,symKey,apipClient);
        this.pricePerKBytes = Inputer.inputDoubleAsString(br,"Input the pricePerKBytes:");
        this.dataLifeDays = Inputer.inputString(br,"Input the dataLifeDays:");
        this.pricePerKBytesPermanent = Inputer.inputDoubleAsString(br,"Input the pricePerKBytesPermanent:");
        this.minPayment = Inputer.inputIntegerStr(br,"Input the minPayment:");
        this.sessionDays = Inputer.inputDoubleAsString(br,"Input the sessionDays:");
        this.consumeViaShare = Inputer.inputDoubleAsString(br,"Input the consumeViaShare:");
        this.orderViaShare = Inputer.inputDoubleAsString(br,"Input the orderViaShare:");
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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

    public String getDataLifeDays() {
        return dataLifeDays;
    }

    public void setDataLifeDays(String dataLifeDays) {
        this.dataLifeDays = dataLifeDays;
    }

    public String getPricePerKBytesPermanent() {
        return pricePerKBytesPermanent;
    }

    public void setPricePerKBytesPermanent(String pricePerKBytesPermanent) {
        this.pricePerKBytesPermanent = pricePerKBytesPermanent;
    }

    public String getMinPayment() {
        return minPayment;
    }

    public void setMinPayment(String minPayment) {
        this.minPayment = minPayment;
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
