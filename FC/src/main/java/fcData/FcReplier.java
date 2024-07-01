package fcData;

import apip.apipData.RequestBody;
import fch.ParseTools;
import clients.apipClient.DataGetter;
import clients.redisClient.RedisTools;
import com.google.gson.Gson;
import constants.ReplyCodeMessage;
import constants.Strings;
import crypto.Hash;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static clients.redisClient.RedisTools.readHashLong;
import static constants.Strings.*;
import static server.Settings.addSidBriefToName;

public class FcReplier {
    private Integer code;
    private String message;
    private Long nonce;
    private Long balance;
    private Object data;
    private List<String> last;
    private Long got;
    private Long total;
    private Long bestHeight;
    private String via;
    private transient String sid;
    private transient RequestCheckResult requestCheckResult;
    private transient HttpServletResponse response;

    public FcReplier() {
    }

    public FcReplier(String sid, HttpServletResponse response) {
        this.sid = sid;
        this.response = response;
    }

    public void Set0Success() {
        this.code = ReplyCodeMessage.Code0Success;
        this.message = ReplyCodeMessage.Msg0Success;
    }

    public void Set1020Other(String message) {
        this.code = ReplyCodeMessage.Code1020OtherError;
        if(message==null)this.message = ReplyCodeMessage.Msg1020OtherError;
        else this.message = message;
    }
    public void SetCodeMessage(Integer code) {
        this.code = code;
        this.message = ReplyCodeMessage.getMsg(code);
    }

    public void printCodeMessage(){
        System.out.println(code+":"+message);
    }
    public static String symSign(String replyJson, String sessionKey) {
        if(replyJson==null || sessionKey==null)return null;
        byte[] replyJsonBytes = replyJson.getBytes();
        byte[] keyBytes = BytesTools.hexToByteArray(sessionKey);
        byte[] bytes = BytesTools.bytesMerger(replyJsonBytes,keyBytes);
        byte[] signBytes = Hash.sha256x2(bytes);
        return BytesTools.bytesToHexStringBE(signBytes);
    }

    @Nullable
    public String getStringFromHeader(HttpServletRequest request,String name,Jedis jedis) {
        String value = request.getParameter(name);
        if (value == null) {
            reply(ReplyCodeMessage.Code3009DidMissed,null,jedis);
            return null;
        }
        return value;
    }

    @Nullable
    public String getStringFromBodyJsonData(RequestBody requestBody, String name,Jedis jedis) throws IOException {
        String value;
        try {
            Map<String, String> requestDataMap = DataGetter.getStringMap(requestBody.getData());
            value = requestDataMap.get(name);
        }catch (Exception e){
            replyOtherError("Failed to get "+name+" from request body",null,jedis);
            return null;
        }

        if (value == null) {
            replyOtherError("Failed to get "+name+" from request body",null,jedis);
            return null;
        }
        return value;
    }

    public Long updateBalance(String sid, String api, Jedis jedis) {
        long length = this.toJson().length();
        return updateBalance(sid,api,length,jedis);
    }

    public Long updateBalance(String sid, String api, long length,Jedis jedis) {
        if(requestCheckResult.getFreeRequest()!=null && requestCheckResult.getFreeRequest().equals(Boolean.TRUE))
            return null;
        String fid = requestCheckResult.getFid();
        if(fid==null)return null;
        String sessionName = requestCheckResult.getSessionName();
        String via = requestCheckResult.getVia();
        long newBalance;

        double price = RedisTools.readHashDouble(jedis, addSidBriefToName(sid, PARAMS), PRICE_PER_K_BYTES);
        long priceSatoshi = ParseTools.coinToSatoshi(price);
        long amount = length / 1000;
        long nPrice = readHashLong(jedis, addSidBriefToName(sid, N_PRICE), api);
        if (nPrice == 0) nPrice = 1;
        long cost = amount * priceSatoshi * nPrice;

        //update user balance
        long oldBalance = readHashLong(jedis, addSidBriefToName(sid, Strings.BALANCE), fid);
        newBalance = oldBalance - cost;
        if (newBalance < 0) {
            cost = oldBalance;
            jedis.hdel(addSidBriefToName(sid, Strings.BALANCE), fid);
            jedis.select(1);
            jedis.hdel(addSidBriefToName(sid, sessionName));
            jedis.select(0);
            newBalance = 0;
        } else
            jedis.hset(addSidBriefToName(sid, Strings.BALANCE), fid, String.valueOf(newBalance));

        //Update consume via balance
        if (via != null) {
            long oldViaBalance = readHashLong(jedis, addSidBriefToName(sid, CONSUME_VIA), via);
            long newViaBalance = oldViaBalance + cost;
            jedis.hset(addSidBriefToName(sid, CONSUME_VIA), via, String.valueOf(newViaBalance));
        }

        balance= Long.valueOf(newBalance);
        return newBalance;
    }

    public void symSign(String sessionKey) {
        if(sessionKey==null){
            return;
        }
        String json = this.toNiceJson();
        byte[] replyJsonBytes = json.getBytes();
        byte[] keyBytes = Hex.fromHex(sessionKey);
        byte[] bytes = BytesTools.bytesMerger(replyJsonBytes,keyBytes);
        byte[] signBytes = Hash.sha256x2(bytes);
        String sign = BytesTools.bytesToHexStringBE(signBytes);
        response.setHeader(ReplyCodeMessage.SignInHeader,sign);
    }
    public void reply(int code,Object data, Jedis jedis){
        reply(code,null,data,jedis);
    }
    public void reply0Success(Object data, Jedis jedis) {
        reply(0,null,data,jedis);
    }

    public void reply0Success(Jedis jedis) {
        reply(0,null,data,jedis);
    }
    public void set0Success() {
        set0Success(null);
    }

    public void set0Success(Object data) {
        code = ReplyCodeMessage.Code0Success;
        message = ReplyCodeMessage.getMsg(code);
        this.data = data;
    }
    public void reply0Success(Object data, HttpServletResponse response) {
        code = ReplyCodeMessage.Code0Success;
        message = ReplyCodeMessage.getMsg(code);
        this.data = data;
        try {
            response.getWriter().write(this.toNiceJson());
        } catch (IOException ignore) {
            System.out.println("Failed to reply success.");
        }
    }

    public void replyOtherError(String otherError,Object data, Jedis jedis) {
        reply(9,otherError,data,jedis);
    }
    public void setOtherError(String otherError) {
        code = ReplyCodeMessage.Code1020OtherError;
        if(otherError!=null)message = otherError;
        else message = ReplyCodeMessage.getMsg(code);
    }

    public void replyOtherError(String otherError, HttpServletResponse response) {
        code = ReplyCodeMessage.Code1020OtherError;
        if(otherError!=null)message = otherError;
        else message = ReplyCodeMessage.getMsg(code);
        String replyStr = this.toNiceJson();
        try {
            response.getWriter().write(replyStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void reply(int code,String otherError, Object data, Jedis jedis) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(ReplyCodeMessage.CodeInHeader, String.valueOf(code));
        this.code = code;
        if(code==ReplyCodeMessage.Code1020OtherError)this.message = otherError;
        else this.message=ReplyCodeMessage.getMsg(code);
        if(data!=null)this.data=data;
        updateBalance(sid, requestCheckResult.getApiName(), jedis);
//        if(code!=0)response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        String sessionKey = requestCheckResult.getSessionKey();
        String replyStr = this.toNiceJson();
        if(sessionKey !=null){
            String sign = symSign(replyStr,sessionKey);
            if(sign!=null) response.setHeader(ReplyCodeMessage.SignInHeader,sign);
        }
        try {
            response.getWriter().write(replyStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public void clean() {
        code=null;
        message=null;
        nonce=null;
        balance=null;
        data=null;
        last=null;
    }
    public String toJson(){
        return new Gson().toJson(this);
    }
    public String toNiceJson(){
        return JsonTools.toNiceJson(this);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public void setNonce(Long nonce) {
        this.nonce = nonce;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<String> getLast() {
        return last;
    }

    public void setLast(List<String> last) {
        this.last = last;
    }

    public Long getNonce() {
        return nonce;
    }

    public Long getGot() {
        return got;
    }

    public void setGot(Long got) {
        this.got = got;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public RequestCheckResult getRequestCheckResult() {
        return requestCheckResult;
    }

    public void setRequestCheckResult(RequestCheckResult requestCheckResult) {
        this.requestCheckResult = requestCheckResult;
    }

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }
}
