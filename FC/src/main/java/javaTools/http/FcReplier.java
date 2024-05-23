package javaTools.http;

import com.google.gson.Gson;
import constants.ReplyInfo;
import crypto.Hash;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class FcReplier {
    private Integer code;
    private String message;
    private Long nonce;
    private String balance;
    private Object data;
    private String[] last;
    private Long got;
    private Long total;

    public static String symSign(String replyJson, String sessionKey) {
        if(replyJson==null || sessionKey==null)return null;
        byte[] replyJsonBytes = replyJson.getBytes();
        byte[] keyBytes = BytesTools.hexToByteArray(sessionKey);
        byte[] bytes = BytesTools.bytesMerger(replyJsonBytes,keyBytes);
        byte[] signBytes = Hash.sha256x2(bytes);
        return BytesTools.bytesToHexStringBE(signBytes);
    }

    public void symSign(String sessionKey,HttpServletResponse response) {
        if(sessionKey==null){
            return;
        }
        String json = this.toNiceJson();
        byte[] replyJsonBytes = json.getBytes();
        byte[] keyBytes = Hex.fromHex(sessionKey);
        byte[] bytes = BytesTools.bytesMerger(replyJsonBytes,keyBytes);
        byte[] signBytes = Hash.sha256x2(bytes);
        String sign = BytesTools.bytesToHexStringBE(signBytes);
        response.setHeader(ReplyInfo.SignInHeader,sign);
    }

    public void reply0Success(HttpServletResponse response,@Nullable Object data,@Nullable String sessionKey){
        response.setContentType("application/json");
        response.setHeader(ReplyInfo.CodeInHeader, String.valueOf(ReplyInfo.Code0Success));
        this.code = ReplyInfo.Code0Success;
        this.message = ReplyInfo.Msg0Success;
        this.data=data;
        if(sessionKey!=null)symSign(sessionKey, response);
        try {
            response.getWriter().write(toNiceJson());
        } catch (IOException e) {
            this.code = ReplyInfo.Code1020OtherError;
            this.message = "IO exception when writing to response.";
        }
    }
    public void replyWithCodeAndMessage(HttpServletResponse response, int code, String message, @Nullable Object data, String sessionKey){
        response.setContentType("application/json");
        response.setHeader(ReplyInfo.CodeInHeader, String.valueOf(code));
        this.code = code;
        this.message =message;
        this.data=data;
        if(code!=0)response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        if(sessionKey!=null)symSign(sessionKey, response);
        try {
            response.getWriter().write(toNiceJson());
        } catch (IOException e) {
            this.code = ReplyInfo.Code1020OtherError;
            this.message = "IO exception when writing to response.";
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
        return JsonTools.getNiceString(this);
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

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
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

    public String[] getLast() {
        return last;
    }

    public void setLast(String[] last) {
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
}
