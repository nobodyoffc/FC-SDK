package FEIP.feipData.serviceParams;

import com.google.gson.Gson;

import java.io.BufferedReader;

public class ApipParams extends Params{
    private String currency;
    private String consumeViaShare;
    private String orderViaShare;

    public static ApipParams fromObject(Object data) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), ApipParams.class);
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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

    @Override
    public void inputParams(BufferedReader br, byte[] symKey) {

    }

    @Override
    public void updateParams(BufferedReader br, byte[] symKey) {

    }
}