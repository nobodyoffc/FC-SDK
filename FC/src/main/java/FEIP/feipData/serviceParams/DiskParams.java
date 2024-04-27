package FEIP.feipData.serviceParams;

import clients.apipClient.ApipClient;
import FEIP.feipData.Service;
import appTools.Inputer;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class DiskParams extends Params {
    private String currency;
    private String dataLifeDays;
    private String pricePerKBytesPermanent;

//    private String sessionDays;
    private String consumeViaShare;
    private String orderViaShare;
    private final transient ApipClient apipClient;

    public DiskParams(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public static DiskParams fromObject(Object data) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), DiskParams.class);
    }
    public void setParamsToRedis(String key, Jedis jedis) {
        Map<String, String> paramMap = new HashMap<>();
        for (Field field : DiskParams.class.getDeclaredFields()) {
            field.setAccessible(true); // to access private fields
            try {
                Object value = field.get(this);
                if(value!=null)
                    paramMap.put(field.getName(), String.valueOf(value));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        jedis.hmset(key, paramMap);
    }
    @Override
    public void updateParams(BufferedReader br, byte[] symKey) {
        try {
            this.urlHead = Inputer.promptAndUpdate(br,"url",this.urlHead);
            this.currency = Inputer.promptAndUpdate(br,"Input the currency:",this.currency);
            this.account = updateAccount(br,symKey,apipClient);
            this.pricePerKBytes = updatePricePerKBytes(br);
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



    public static DiskParams getParamsFromService(Service service) {
        DiskParams params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), DiskParams.class);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong："+e.getMessage());
            return null;
        }
        service.setParams(params);
        return params;
    }
    public void inputParams(BufferedReader br, byte[]symKey){
        this.urlHead = Inputer.inputString(br,"Input the url:");
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

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
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
