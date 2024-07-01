package server;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Session;
import feip.feipData.serviceParams.DiskParams;
import clients.redisClient.RedisTools;
import com.google.gson.Gson;
import constants.ApiNames;
import constants.FieldNames;
import constants.ReplyCodeMessage;
import constants.Strings;
import crypto.Hash;

import feip.feipData.serviceParams.Params;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.http.AuthType;
import fcData.FcReplier;
import javaTools.http.HttpTools;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static constants.Strings.*;
import static crypto.KeyTools.pubKeyToFchAddr;
import static javaTools.http.AuthType.*;
import static javaTools.http.HttpTools.getApiNameFromUrl;
import static javaTools.http.HttpTools.illegalUrl;

public class RequestChecker {

    public static RequestCheckResult checkUrlSignRequest(String sid, HttpServletRequest request, FcReplier replier, RequestCheckResult requestCheckResult, Map<String, String> paramsMap, long windowTime, Jedis jedis) {
        String url = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        if (queryString != null) {
            url += "?" + queryString;
        }
        if(illegalUrl(url)){
            replier.reply(ReplyCodeMessage.Code1016IllegalUrl, null,jedis);
            return null;
        }

        String apiNameFromUrl = HttpTools.getApiNameFromUrl(url);
        requestCheckResult.setApiName(apiNameFromUrl);

        String nonceStr = request.getParameter(NONCE);
        if(nonceStr==null){
            replier.reply(ReplyCodeMessage.Code1018NonceMissed, null, jedis);
            return null;
        }
        long nonce = Long.parseLong(nonceStr);
        String timeStr = request.getParameter(TIME);
        if(timeStr==null){
            replier.reply(ReplyCodeMessage.Code1019TimeMissed, null, jedis);
            return null;
        }
        long time = Long.parseLong(timeStr);

        String via = request.getParameter(VIA);

        String sign = request.getHeader(ReplyCodeMessage.SignInHeader);

        if(sign==null){
            promoteUrlSignRequest(replier, paramsMap.get(URL_HEAD),jedis);
            return null;
        }

        String sessionName = request.getHeader(ReplyCodeMessage.SessionNameInHeader);
        if(sessionName==null){
            replier.reply(ReplyCodeMessage.Code1002SessionNameMissed, null,jedis);
            return null;
        }

        Session session = getSession(sessionName,jedis);
        if(session ==null){
            replier.reply(ReplyCodeMessage.Code1009SessionTimeExpired, null, jedis);
            return null;
        }

        String fid = session.getFid();
        String sessionKey = session.getSessionKey();

        requestCheckResult.setSessionName(sessionName);
        requestCheckResult.setSessionKey(sessionKey);
        requestCheckResult.setFid(fid);

        if(isBadBalance(sid,fid, apiNameFromUrl, jedis)){
            String data = "Send at lest "+paramsMap.get(MIN_PAYMENT)+" F to "+paramsMap.get(ACCOUNT)+" to buy the service #"+sid+".";
            replier.reply(ReplyCodeMessage.Code1004InsufficientBalance, data, jedis);
            return null;
        }

        if(isBadNonce(nonce, windowTime, jedis)){
            replier.reply(ReplyCodeMessage.Code1007UsedNonce, null, jedis);
            return null;
        }

        replier.setNonce(nonce);

        if(isBadSymSign(sign, url.getBytes(), replier, sessionKey)){
            replier.reply(ReplyCodeMessage.Code1008BadSign, replier.getData(),jedis);
            return null;
        }

        if (isBadTime(time,windowTime)){
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("windowTime", String.valueOf(windowTime));
            replier.reply(ReplyCodeMessage.Code1006RequestTimeExpired, dataMap, jedis);
            return null;
        }
        if(via!=null) requestCheckResult.setVia(via);


        Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
        RequestBody requestBody = new RequestBody();
        requestBody.setFcdsl(fcdsl);
        requestCheckResult.setRequestBody(requestBody);

        return requestCheckResult;
    }

    public static RequestCheckResult checkBodySignRequest(String sid, HttpServletRequest request, FcReplier replier, RequestCheckResult requestCheckResult, Jedis jedis) {

        Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(sid, PARAMS));
        long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, SETTINGS), WINDOW_TIME);


        String url = request.getRequestURL().toString();

        if(illegalUrl(url)){
            replier.reply(ReplyCodeMessage.Code1016IllegalUrl, null,jedis);
            return null;
        }


        String apiName = HttpTools.getApiNameFromUrl(url);
        requestCheckResult.setApiName(apiName);

        String sign = request.getHeader(ReplyCodeMessage.SignInHeader);

        if(sign==null){
            promoteJsonRequest( replier, paramsMap.get(URL_HEAD),jedis);
            return null;
        }

        String sessionName = request.getHeader(ReplyCodeMessage.SessionNameInHeader);
        if(sessionName==null){
            replier.reply(ReplyCodeMessage.Code1002SessionNameMissed, null,jedis);
            return null;
        }

        Session session = getSession(sessionName,jedis);
        if(session ==null){
            replier.reply(ReplyCodeMessage.Code1009SessionTimeExpired, null, jedis);
            return null;
        }

        String fid = session.getFid();
        String sessionKey = session.getSessionKey();

        requestCheckResult.setSessionName(sessionName);
        requestCheckResult.setSessionKey(sessionKey);
        requestCheckResult.setFid(fid);

        if(isBadBalance(sid,fid, apiName, jedis)){
            String data = "Send at lest "+paramsMap.get(MIN_PAYMENT)+" F to "+paramsMap.get(ACCOUNT)+" to buy the service #"+sid+".";
            replier.reply(ReplyCodeMessage.Code1004InsufficientBalance, data, jedis);
            return null;
        }

        byte[] requestBodyBytes;
        try {
            requestBodyBytes = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            replier.reply(ReplyCodeMessage.Code1020OtherError, "Getting request body bytes wrong.", jedis);
            return null;
        }
        if(requestBodyBytes==null){
            replier.reply(ReplyCodeMessage.Code1003BodyMissed, null, jedis);
            return null;
        }

        RequestBody requestBody = getRequestBody(requestBodyBytes,replier,jedis);
        if(requestBody==null)return null;

        if(isBadNonce(requestBody.getNonce(), windowTime,jedis )){
            replier.reply(ReplyCodeMessage.Code1007UsedNonce, null, jedis);
            return null;
        }

        replier.setNonce(requestBody.getNonce());

        if(isBadSymSign(sign, requestBodyBytes, replier, sessionKey)){
            replier.reply(ReplyCodeMessage.Code1008BadSign, replier.getData(), jedis);
            return null;
        }

        if(isBadUrl(requestBody.getUrl(),url)){
            Map<String,String> dataMap = new HashMap<>();
            dataMap.put("requestedURL",request.getRequestURL().toString());
            dataMap.put("signedURL",requestBody.getUrl());
            replier.setData(dataMap);
            replier.reply(ReplyCodeMessage.Code1005UrlUnequal, replier.getData(), jedis);
            return null;
        }

        if (isBadTime(requestBody.getTime(),windowTime)){
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("windowTime", String.valueOf(windowTime));
            replier.reply(ReplyCodeMessage.Code1006RequestTimeExpired, dataMap,jedis);
            return null;
        }
        if(requestBody.getVia()!=null) requestCheckResult.setVia(requestBody.getVia());

        requestCheckResult.setRequestBody(requestBody);
        return requestCheckResult;
    }

    public static RequestCheckResult checkUrlSignRequest(String sid, HttpServletRequest request, FcReplier replier, RequestCheckResult requestCheckResult, Jedis jedis) {

        Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(sid, PARAMS));
        long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, SETTINGS), WINDOW_TIME);
        return checkUrlSignRequest(sid, request, replier,requestCheckResult, paramsMap, windowTime, jedis);
    }

    private static boolean isBadSymSign(String sign, byte[] requestBodyBytes, FcReplier replier, String sessionKey) {
        if(sign==null)return true;
        byte[] signBytes = BytesTools.bytesMerger(requestBodyBytes, Hex.fromHex(sessionKey));
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));

        if(!sign.equals(doubleSha256Hash)){
            replier.setData("The sign of the request body should be: "+doubleSha256Hash);
            return true;
        }
        return false;
    }

    public static Session getSession(String sessionName,Jedis jedis) {
        Session session;

        jedis.select(1);

        String fid = jedis.hget(sessionName, "fid");
        String sessionKey = jedis.hget(sessionName, "sessionKey");

        jedis.select(0);
        if (fid == null || sessionKey == null) {
            return null;
        }

        session = new Session();
        session.setFid(fid);
        session.setSessionKey(sessionKey);
        session.setSessionName(sessionName);

        return session;
    }

    @Nullable
    public static RequestCheckResult checkRequest(String sid, HttpServletRequest request, FcReplier replier, AuthType authType, Jedis jedis) {

        RequestCheckResult requestCheckResult = new RequestCheckResult();
        replier.setRequestCheckResult(requestCheckResult);
        boolean isForbidFreeApi;

        if(authType.equals(FREE)){
            requestCheckResult.setFreeRequest(Boolean.TRUE);
            RequestBody requestBody;
            try {
                byte[]  requestBodyBytes = request.getInputStream().readAllBytes();
                requestBody = getRequestBody(requestBodyBytes,replier,jedis);
            } catch (IOException ignore) {
                requestBody=null;
            }
            if(requestBody!=null){
                requestCheckResult.setRequestBody(requestBody);
            }else {
                String url = request.getRequestURL().toString();
                Fcdsl fcdsl = Fcdsl.urlParamsToFcdsl(url);
                requestBody = new RequestBody();
                requestBody.setFcdsl(fcdsl);
                requestCheckResult.setRequestBody(requestBody);
                requestCheckResult.setApiName(HttpTools.getApiNameFromUrl(url));
            }
        }
        else {
            String sign = request.getHeader(SIGN);
            String sessionName = request.getHeader(SESSION_NAME);

            if(sign == null || sessionName == null) {
                String isForbidFreeApiStr = jedis.hget(Settings.addSidBriefToName(sid, SETTINGS), FieldNames.FORBID_FREE_API);
                isForbidFreeApi = "true".equalsIgnoreCase(isForbidFreeApiStr);
                if (isForbidFreeApi) {
                    replier.reply(ReplyCodeMessage.Code2001FreeGetIsForbidden, null, jedis);
                    return null;
                }
                requestCheckResult.setFreeRequest(Boolean.TRUE);
            }else{
                if(authType.equals(FC_SIGN_URL))
                    requestCheckResult = checkUrlSignRequest(sid, request, replier, requestCheckResult,jedis);
                else {
                    if(authType.equals(FC_SIGN_BODY)) {
                        requestCheckResult =  checkBodySignRequest(sid, request, replier, requestCheckResult,jedis);
                    }
                    else {
                        replier.reply(ReplyCodeMessage.Code1020OtherError, "Wrong AuthType.", jedis);
                        return null;
                    }
                }
            }
        }
        return requestCheckResult;
    }

    public RequestCheckResult checkSignInRequest(String sid, HttpServletRequest request, FcReplier replier, Map<String, String> paramsMap, long windowTime, Jedis jedis){
        RequestCheckResult requestCheckResult = new RequestCheckResult();
        replier.setRequestCheckResult(requestCheckResult);
        String url = request.getRequestURL().toString();
        requestCheckResult.setApiName(getApiNameFromUrl(url));
        if(illegalUrl(url)){
            replier.reply(ReplyCodeMessage.Code1016IllegalUrl, null, jedis);
            return null;
        }
        String apiName = HttpTools.getApiNameFromUrl(url);
        requestCheckResult.setApiName(apiName);


        String fid = request.getHeader(ReplyCodeMessage.FidInHeader);
        if(fid==null){
            String data = "A FID is required in request header.";
            replier.reply(ReplyCodeMessage.Code1015FidMissed, data,jedis);
            return null;
        }
        requestCheckResult.setFid(fid);

        String sign = request.getHeader(ReplyCodeMessage.SignInHeader);

        if (paramsMap==null||paramsMap.get(URL_HEAD)==null) {
            replier.reply(ReplyCodeMessage.Code1020OtherError, "Failed to get parameters from redis.", jedis);
            return null;
        }

        if(sign==null||"".equals(sign)){
            promoteSignInRequest(replier, paramsMap.get(URL_HEAD),jedis);
            return null;
        }

        byte[] requestBodyBytes;
        try {
            requestBodyBytes = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            replier.reply(ReplyCodeMessage.Code1020OtherError, "Getting request body bytes wrong.", jedis);
            return null;
        }
        if(requestBodyBytes==null){
            replier.reply(ReplyCodeMessage.Code1003BodyMissed, null, jedis);
            return null;
        }

        RequestBody signInRequestBody = getRequestBody(requestBodyBytes,replier, jedis);
        if(signInRequestBody==null)return null;
        if(isBadNonce(signInRequestBody.getNonce(), windowTime,jedis )){
            replier.reply(ReplyCodeMessage.Code1007UsedNonce, null,jedis);
            return null;
        }
        replier.setNonce(signInRequestBody.getNonce());

        String pubKey;
        try {
            pubKey = checkAsySignAndGetPubKey(fid,sign,requestBodyBytes);
        } catch (SignatureException e) {
            replier.reply(ReplyCodeMessage.Code1008BadSign, null, jedis);
            return null;
        }
        if(null==pubKey){
            replier.reply(ReplyCodeMessage.Code1008BadSign, null,jedis);
            return null;
        }
        requestCheckResult.setPubKey(pubKey);

        if(isBadBalance(sid,fid,apiName, jedis)){
            String data = "Send at lest "+paramsMap.get(MIN_PAYMENT)+" F to "+paramsMap.get(ACCOUNT)+" to buy the service #"+sid+".";
            replier.reply(ReplyCodeMessage.Code1004InsufficientBalance, data, jedis);
            return null;
        }

        if(isBadUrl(signInRequestBody.getUrl(),url)){
            Map<String,String> dataMap = new HashMap<>();
            dataMap.put("requestedURL",request.getRequestURL().toString());
            dataMap.put("signedURL",signInRequestBody.getUrl());
            replier.setData(dataMap);
            replier.reply(ReplyCodeMessage.Code1005UrlUnequal, replier.getData(), jedis);
            return null;
        }

        if (isBadTime(signInRequestBody.getTime(),windowTime)){
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("windowTime", String.valueOf(windowTime));
            replier.reply(ReplyCodeMessage.Code1006RequestTimeExpired, dataMap, jedis);
            return null;
        }
        if(signInRequestBody.getVia()!=null) requestCheckResult.setVia(signInRequestBody.getVia());

        requestCheckResult.setRequestBody(signInRequestBody);
        return requestCheckResult;
    }

    private static void promoteSignInRequest(FcReplier replier, String urlHead, Jedis jedis) {
        String data = "A Sign is required in request header.";
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        int nonce = ByteBuffer.wrap(bytes).getInt();
        if(nonce<0)nonce=(-nonce);
        long timestamp = System.currentTimeMillis();
        if(urlHead!=null) {
            data = """
                    A signature are requested:
                    \tRequest header:
                    \t\tFid = <Freecash address of the requester>
                    \t\tSign = <The signature of request body signed by the private key of the FID.>
                    \tRequest body:{"url":"%s","nonce":"%d","time":"%d"}"""
                    .formatted(urlHead+ApiNames.Version1 + ApiNames.SignInAPI,nonce,timestamp);
        }
        replier.reply(ReplyCodeMessage.Code1000SignMissed, data,jedis);
    }

    private static void promoteJsonRequest(FcReplier replier, String urlHead, Jedis jedis) {
        String data = "A Sign is required in request header.";
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        int nonce = ByteBuffer.wrap(bytes).getInt();
        if(nonce<0)nonce=(-nonce);
        long timestamp = System.currentTimeMillis();
        if(urlHead!=null) {
            data = """
                    A signature are requested:
                    \tRequest header:
                    \t\tSessionName = <The session name which is the hex of the first 6 bytes of the sessionKey>
                    \t\tSign = <The value of double sha256 of the request body bytes adding the sessionKey bytes.>
                    \tRequest body:
                    \t\t{
                    \t\t\t"url":%s,
                    \t\t\t"nonce":%d
                    \t\t\t"time":%d
                    \t\t\t<your request parameters...>
                    \t\t}
                    """
                    .formatted(urlHead+ApiNames.Version1 + ApiNames.SignInAPI,nonce,timestamp);
        }
        replier.reply(ReplyCodeMessage.Code1000SignMissed, data,jedis);
    }

    private static void promoteUrlSignRequest(FcReplier replier, String urlHead, Jedis jedis) {
        String data = "A Sign is required in request header.";
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        int nonce = ByteBuffer.wrap(bytes).getInt();
        if(nonce<0)nonce=(-nonce);
        long timestamp = System.currentTimeMillis();
        if(urlHead!=null) {
            data = """
                    A signature are requested:
                    \tRequest header:
                    \t\tSessionName = <The session name which is the hex of the first 6 bytes of the sessionKey>
                    \t\tSign = <The value of double sha256 of the whole requested URL bytes adding the sessionKey bytes.>
                    \tRequest URL should be:
                    \t\t\t%s/"nonce=%s&time=%s<your more parameters>
                    """
                    .formatted(urlHead+ApiNames.Version1 + ApiNames.SignInAPI,nonce,timestamp);
        }
        replier.reply(ReplyCodeMessage.Code1000SignMissed, data, jedis);
    }

    private static RequestBody getRequestBody(byte[] requestBodyBytes, FcReplier replier, Jedis jedis) {
        String requestDataJson = new String(requestBodyBytes);
        RequestBody connectRequestBody;
        try {
            connectRequestBody = new Gson().fromJson(requestDataJson, RequestBody.class);
        }catch(Exception e){
            replier.reply(ReplyCodeMessage.Code1013BadRequest, "Parsing request body wrong.", jedis);
            return null;
        }
        return connectRequestBody;
    }

    public static boolean isBadNonce(long nonce, long windowTime, Jedis jedis){

        if(windowTime==0)return false;

        jedis.select(2);
        if (nonce == 0) return true;
        String nonceStr = String.valueOf(nonce);
        if (jedis.get(nonceStr) != null)
            return true;
        jedis.set(nonceStr, "");
        jedis.expire(nonceStr, windowTime);
        jedis.select(0);
        return false;
    }
    public static boolean isBadBalance(String sid, String fid, String apiName, Jedis jedis){

        long nPrice = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, Strings.N_PRICE), apiName);
        double price;
        String priceStr;
        String paramsKeyInRedis = Settings.addSidBriefToName(sid, PARAMS);
        jedis.select(0);
        priceStr = jedis.hget(paramsKeyInRedis,PRICE_PER_K_BYTES);
        if(priceStr==null)priceStr = jedis.hget(paramsKeyInRedis,PRICE_PER_REQUEST);
        if(priceStr==null)return false;
        price = Double.parseDouble(priceStr);
        long balance = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, BALANCE),fid);
        return balance < nPrice*price*100000000;
    }

    private String checkAsySignAndGetPubKey(String fid, String sign, byte[] requestBodyBytes) throws SignatureException {
        String message = new String(requestBodyBytes);

        sign = sign.replace("\\u003d", "=");

        String signPubKey = ECKey.signedMessageToKey(message, sign).getPublicKeyAsHex();

        String signFid = pubKeyToFchAddr(signPubKey);

        if(signFid.equals(fid))return signPubKey;
        return null;
    }

    public static boolean isBadTime(long userTime, long windowTime){
        if(windowTime==0)return false;
        long currentTime = System.currentTimeMillis();
        return Math.abs(currentTime - userTime) > windowTime;
    }
    public static boolean isBadUrl(String signedUrl, String requestUrl){
        return !requestUrl.equals(signedUrl);
    }
}
