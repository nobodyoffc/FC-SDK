package data;

import FEIP.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;

import java.io.BufferedReader;

public class OpenDriveParams {
    private String url;
    private String currency;
    private String account;
    private String pricePerKBytes;
    private String dataLifeDays;
    private String pricePerKBytesCarve;
    private String minPayment;
    private String sessionDays;
    private String consumeViaShare;
    private String orderViaShare;
    public static OpenDriveParams getParamsFromService(Service service) {
        OpenDriveParams params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), OpenDriveParams.class);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong.");
            return null;
        }
        return params;
    }
    public void inputParams(BufferedReader br){
        this.url = Inputer.inputString(br,"Input the url:");
        this.currency = Inputer.inputString(br,"Input the currency:");
        this.account = Inputer.inputString(br,"Input the account:");
        this.pricePerKBytes = Inputer.inputString(br,"Input the pricePerKBytes:");
        this.dataLifeDays = Inputer.inputString(br,"Input the dataLifeDays:");
        this.pricePerKBytesCarve = Inputer.inputIntegerStr(br,"Input the pricePerKBytesCarve:");
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

    public String getPricePerKBytesCarve() {
        return pricePerKBytesCarve;
    }

    public void setPricePerKBytesCarve(String pricePerKBytesCarve) {
        this.pricePerKBytesCarve = pricePerKBytesCarve;
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
