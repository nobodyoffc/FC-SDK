package server.reward;

import java.util.HashMap;
import java.util.Map;

public class RewardReturn {
    private int code;
    private String msg;
    private Object data;

    public static Map<Integer,String> codeMsgMap;

    public static String codeToMsg(int code){
        return switch (code){
            case 0 -> "Success.";
            case 1 -> "Get account failed. Check the service parameters in redis.";
            case 2 -> "Get last order id failed.";
            case 3 -> "No income.";
            case 4 -> "Get reward parameters failed. Check redis.";
            case 5 -> "Making rewardInfo wrong.";
            case 6 -> "The balance is less than the reward sum.";
//            case 2 ->
//            case 2 ->
//            case 2 ->
//            case 2 ->
//            case 2 ->
//            case 2 ->

            default -> throw new IllegalStateException("Unexpected value: " + code);
        };
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
