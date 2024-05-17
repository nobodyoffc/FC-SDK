package javaTools.http;

import APIP.apipData.RequestBody;
import clients.ApiUrl;
import clients.apipClient.DataGetter;
import constants.ReplyInfo;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import server.RequestCheckResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static constants.ApiNames.apiList;
import static constants.ApiNames.freeApiList;

public class HttpTools {
    public static String getApiNameFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        int firstQuestionIndex = url.indexOf('?');
        if (lastSlashIndex != -1 && lastSlashIndex != url.length() - 1) {
            String name = url.substring(lastSlashIndex + 1);
            if(firstQuestionIndex!=-1){
                name = name.substring(0,firstQuestionIndex);
            }
            if (apiList.contains(name) || freeApiList.contains(name)) {
                return name;
            }
            return "";
        } else {
            return "";  // Return empty string if '/' is the last character or not found
        }
    }

    @Nullable
    public static Map<String, String> parseParamsMapFromUrl(String rawStr) {
        Map<String,String >paramMap = new HashMap<>();
        try {
            int questionMarkIndex = rawStr.indexOf('?');
            if (questionMarkIndex == -1) {
                return null;
            }
            String paramString = rawStr.substring(questionMarkIndex + 1);
            String[] pairs = paramString.split("&");

            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                paramMap.put(key, value);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Handle the exception appropriately in real code
        }
        return paramMap;
    }

    public static String parseApiName(String url) {
        int index = url.lastIndexOf('/');
        if(index!=url.length()-1){
            return url.substring(index);
        }
        return null;
    }

    @Nullable
    public static String getStringFromHeader(HttpServletRequest request, HttpServletResponse response, FcReplier replier, RequestCheckResult requestCheckResult, String name) {
        String value = request.getParameter(name);
        if (value == null) {
            replier.replyWithCodeAndMessage(response, ReplyInfo.Code3009DidMissed,ReplyInfo.Msg3009DidMissed,null, requestCheckResult.getSessionKey() );
            return null;
        }
        return value;
    }

    @Nullable
    public static String getStringFromBodyJsonData(HttpServletResponse response, FcReplier replier, RequestCheckResult requestCheckResult, RequestBody requestBody, String name) {
        String value;
        try {
            Map<String, String> requestDataMap = DataGetter.getStringMap(requestBody.getData());
            value = requestDataMap.get(name);
        }catch (Exception e){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1020OtherError,"Can not get the"+ name +"from request body.",null, requestCheckResult.getSessionKey());
            return null;
        }

        if (value == null) {
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1020OtherError,"Can not get the "+ name +" from request body.", null, requestCheckResult.getSessionKey());
            return null;
        }
        return value;
    }

    @Test
    public void test() {
        Map<String,String>map = new HashMap<>();
        map.put("p1","10");
        map.put("p2","200");

        String url = ApiUrl.makeUrl("http://120.1.1.1/","/tail/1/","/put",map);
        System.out.println(url);
    }
    public static boolean illegalUrl(String url){
        try {
            URI uri = new URI(url);
            uri.toURL();
            return false;
        }catch (Exception e){
            e.printStackTrace();
            return true;
        }
    }

    public static String makeUrlParamsString(Map<String, String> paramMap) {
        StringBuilder stringBuilder = new StringBuilder();
        if(paramMap !=null&& paramMap.size()>0){
            stringBuilder.append("?");
            for(String key: paramMap.keySet()){
                stringBuilder.append(key).append("=").append(paramMap.get(key)).append("&");
            }
            stringBuilder.deleteCharAt(stringBuilder.lastIndexOf("&"));
        }
        return stringBuilder.toString();
    }

}
