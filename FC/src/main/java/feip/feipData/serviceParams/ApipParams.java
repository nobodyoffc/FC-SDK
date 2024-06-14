package feip.feipData.serviceParams;

import appTools.Inputer;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

public class ApipParams extends Params{

    public void writeParamsToRedis(String key, Jedis jedis) {
        Map<String, String> paramMap = toMap();
        jedis.hmset(key, paramMap);
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        if (this.currency != null) map.put("currency", this.currency);
        if (this.consumeViaShare != null) map.put("consumeViaShare", this.consumeViaShare);
        if (this.orderViaShare != null) map.put("orderViaShare", this.orderViaShare);
        if (this.account != null) map.put("account", this.account);
        if (this.pricePerKBytes != null) map.put("pricePerKBytes", this.pricePerKBytes);
        if (this.minPayment != null) map.put("minPayment", this.minPayment);
        if (this.pricePerRequest != null) map.put("pricePerRequest", this.pricePerRequest);
        if (this.sessionDays != null) map.put("sessionDays", this.sessionDays);
        if (this.urlHead != null) map.put("urlHead", this.urlHead);
        return map;
    }

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
        this.urlHead = Inputer.inputString(br,"Input the url:");
        this.currency = Inputer.inputString(br,"Input the currency:");
        this.account = fch.Inputer.inputOrCreateFid("Input the account:",br,symKey,null);
        this.pricePerKBytes = Inputer.inputDoubleAsString(br,"Input the pricePerKBytes:");
        this.minPayment = Inputer.inputIntegerStr(br,"Input the minPayment:");
        this.sessionDays = Inputer.inputDoubleAsString(br,"Input the sessionDays:");
        this.consumeViaShare = Inputer.inputDoubleAsString(br,"Input the consumeViaShare:");
        this.orderViaShare = Inputer.inputDoubleAsString(br,"Input the orderViaShare:");
    }

    @Override
    public void updateParams(BufferedReader br, byte[] symKey) {

    }
}