package crypto;

import java.util.HashMap;
import java.util.Map;

public class CryptoCodeMessage {
    public static String getMessage(int code){
        Map<Integer,String> codeMsgMap = new HashMap<>();
        codeMsgMap.put(0,"OK");
        codeMsgMap.put(1,"No such algorithm.");
        codeMsgMap.put(2,"No such provider.");
        codeMsgMap.put(3,"No such padding.");
        codeMsgMap.put(4,"Invalid algorithm parameter.");
        codeMsgMap.put(5,"Invalid key.");
        codeMsgMap.put(6,"IO exception.");
        codeMsgMap.put(7,"Failed to parse hex.");
        codeMsgMap.put(8,"Failed to parse crypto data.");
        codeMsgMap.put(9,"Other error.");
        codeMsgMap.put(10,"Stream error.");
        codeMsgMap.put(11,"File not found.");
        return codeMsgMap.get(code);
    }

    public static String getErrorStringCodeMsg(int code) {
        return "Error:"+code+"_"+CryptoCodeMessage.getMessage(code);
    }
}
