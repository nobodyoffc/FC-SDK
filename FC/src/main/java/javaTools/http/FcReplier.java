package javaTools.http;

import com.google.gson.Gson;
import constants.ReplyInfo;
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

    public void replyWithCodeAndMessage(HttpServletResponse response,int code, String message,@Nullable Object data){
        response.setHeader(ReplyInfo.CodeInHeader, String.valueOf(code));
        this.code = code;
        this.message =message;
        this.data=data;
        if(code!=0)response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        try {
            response.getWriter().write(toNiceJson());
        } catch (IOException e) {
            this.code = ReplyInfo.Code1020OtherError;
            this.message = ReplyInfo.Msg1020OtherError;
            this.data = "IO exception when writing to response.";
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
}
