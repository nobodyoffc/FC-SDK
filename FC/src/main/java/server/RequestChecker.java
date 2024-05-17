package server;

import APIP.apipData.RequestBody;
import APIP.apipData.Session;
import FEIP.feipData.serviceParams.DiskParams;
import clients.redisClient.RedisTools;
import com.google.gson.Gson;
import constants.ApiNames;
import constants.FieldNames;
import constants.ReplyInfo;
import constants.Strings;
import crypto.cryptoTools.Hash;

import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.http.AuthType;
import javaTools.http.FcReplier;
import javaTools.http.HttpTools;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.Settings;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static constants.Strings.*;
import static crypto.cryptoTools.KeyTools.pubKeyToFchAddr;
import static server.Counter.updateBalance;
import static javaTools.http.HttpTools.illegalUrl;

public class RequestChecker {

    public static RequestCheckResult checkUrlSignRequest(String sid, HttpServletRequest request, HttpServletResponse response, FcReplier replier, Map<String, String> paramsMap, long windowTime, Jedis jedis) {

        RequestCheckResult requestCheckResult = new RequestCheckResult();

        String url = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        if (queryString != null) {
            url += "?" + queryString;
        }
        if(illegalUrl(url)){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1016IllegalUrl,ReplyInfo.Msg1016IllegalUrl,null,null );
            return null;
        }

        String nonceStr = request.getParameter(NONCE);
        if(nonceStr==null){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1018NonceMissed,ReplyInfo.Msg1018NonceMissed,null, null);
            return null;
        }
        long nonce = Long.parseLong(nonceStr);
        String timeStr = request.getParameter(TIME);
        if(timeStr==null){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1019TimeMissed,ReplyInfo.Msg1019TimeMissed,null, null);
            return null;
        }
        long time = Long.parseLong(timeStr);

        String via = request.getParameter(VIA);

        String sign = request.getHeader(ReplyInfo.SignInHeader);

        if(sign==null){
            promoteUrlSignRequest(response, replier, paramsMap.get(URL_HEAD));
            return null;
        }

        String sessionName = request.getHeader(ReplyInfo.SessionNameInHeader);
        if(sessionName==null){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1002SessionNameMissed,ReplyInfo.Msg1002SessionNameMissed,null, null);
            return null;
        }

        Session session = getSession(sessionName,jedis);
        if(session ==null){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1009SessionTimeExpired,ReplyInfo.Msg1009SessionTimeExpired,null, null);
            return null;
        }

        String fid = session.getFid();
        String sessionKey = session.getSessionKey();

        requestCheckResult.setSessionName(sessionName);
        requestCheckResult.setSessionKey(sessionKey);
        requestCheckResult.setFid(fid);

        if(isBadBalance(sid,fid, ApiNames.SignInAPI, jedis)){
            String data = "Send at lest "+paramsMap.get(MIN_PAYMENT)+" F to "+paramsMap.get(ACCOUNT)+" to buy the service #"+sid+".";
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1004InsufficientBalance,ReplyInfo.Msg1004InsufficientBalance,data, sessionKey);
            return null;
        }

        if(isBadNonce(nonce, windowTime, jedis)){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1007UsedNonce,ReplyInfo.Msg1007UsedNonce,null, sessionKey);
            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );
            return null;
        }

        replier.setNonce(nonce);

        if(isBadSymSign(sign, url.getBytes(), replier, sessionKey)){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1008BadSign,ReplyInfo.Msg1008BadSign,replier.getData(), sessionKey);
            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );
            return null;
        }

        if (isBadTime(time,windowTime)){
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("windowTime", String.valueOf(windowTime));
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1006RequestTimeExpired,ReplyInfo.Msg1006RequestTimeExpired,dataMap, sessionKey);
            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );
            return null;
        }
        if(via!=null) requestCheckResult.setVia(via);

        return requestCheckResult;
    }

    public static RequestCheckResult checkBodySignRequest(String sid, HttpServletRequest request, HttpServletResponse response, FcReplier replier, Jedis jedis) {

        Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(sid, PARAMS));
        long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, SETTINGS), WINDOW_TIME);

        RequestCheckResult requestCheckResult = new RequestCheckResult();
        String url = request.getRequestURL().toString();

        if(illegalUrl(url)){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1016IllegalUrl,ReplyInfo.Msg1016IllegalUrl,null,null );
            return null;
        }

        String sign = request.getHeader(ReplyInfo.SignInHeader);

        if(sign==null){
            promoteSignInRequest(response, replier, paramsMap.get(URL_HEAD));
            return null;
        }

        String sessionName = request.getHeader(ReplyInfo.SessionNameInHeader);
        if(sessionName==null){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1002SessionNameMissed,ReplyInfo.Msg1002SessionNameMissed,null,null );
            return null;
        }

        Session session = getSession(sessionName,jedis);
        if(session ==null){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1009SessionTimeExpired,ReplyInfo.Msg1009SessionTimeExpired,null, null);
            return null;
        }

        String fid = session.getFid();
        String sessionKey = session.getSessionKey();

        requestCheckResult.setSessionName(sessionName);
        requestCheckResult.setSessionKey(sessionKey);
        requestCheckResult.setFid(fid);

        if(isBadBalance(sid,fid, ApiNames.SignInAPI, jedis)){
            String data = "Send at lest "+paramsMap.get(MIN_PAYMENT)+" F to "+paramsMap.get(ACCOUNT)+" to buy the service #"+sid+".";
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1004InsufficientBalance,ReplyInfo.Msg1004InsufficientBalance,data, sessionKey);
            return null;
        }

        byte[] requestBodyBytes = new byte[0];
        try {
            requestBodyBytes = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1020OtherError,ReplyInfo.Msg1020OtherError,"Getting request body bytes wrong.", sessionKey);

            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );

            return null;
        }
        if(requestBodyBytes==null){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1003BodyMissed,ReplyInfo.Msg1003BodyMissed,null, sessionKey);
            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );
            return null;
        }

        RequestBody requestBody = getRequestBody(requestBodyBytes,response,replier,sessionKey);
        if(requestBody==null)return null;

        if(isBadNonce(requestBody.getNonce(), windowTime,jedis )){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1007UsedNonce,ReplyInfo.Msg1007UsedNonce,null, sessionKey);
            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );
            return null;
        }

        replier.setNonce(requestBody.getNonce());

        if(isBadSymSign(sign, requestBodyBytes, replier, sessionKey)){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1008BadSign,ReplyInfo.Msg1008BadSign,replier.getData(), sessionKey);
            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );
            return null;
        }

        if(isBadUrl(requestBody.getUrl(),url)){
            Map<String,String> dataMap = new HashMap<>();
            dataMap.put("requestedURL",request.getRequestURL().toString());
            dataMap.put("signedURL",requestBody.getUrl());
            replier.setData(dataMap);
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1005UrlUnequal,ReplyInfo.Msg1005UrlUnequal,replier.getData(), sessionKey);
            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );
            return null;
        }

        if (isBadTime(requestBody.getTime(),windowTime)){
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("windowTime", String.valueOf(windowTime));
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1006RequestTimeExpired,ReplyInfo.Msg1006RequestTimeExpired,dataMap,sessionKey );
            updateBalance(sid, HttpTools.getApiNameFromUrl(url), replier.toJson().length(), replier, requestCheckResult, jedis );
            return null;
        }
        if(requestBody.getVia()!=null) requestCheckResult.setVia(requestBody.getVia());

        requestCheckResult.setRequestBody(requestBody);
        return requestCheckResult;
    }

    public static RequestCheckResult checkUrlSignRequest(String sid, HttpServletRequest request, HttpServletResponse response, FcReplier replier, Jedis jedis) {

        Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(sid, PARAMS));
        long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, SETTINGS), WINDOW_TIME);
        return checkUrlSignRequest(sid, request, response, replier, paramsMap, windowTime, jedis);
    }

    private static boolean isBadSymSign(String sign, byte[] requestBodyBytes, FcReplier replier, String sessionKey) {
        if(sign==null)return true;
        byte[] signBytes = BytesTools.bytesMerger(requestBodyBytes, Hex.fromHex(sessionKey));
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.Sha256x2(signBytes));

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
    public static RequestCheckResult checkRequest(String sid, HttpServletRequest request, HttpServletResponse response, FcReplier replier, AuthType authType, Jedis jedis) {
        String sign = request.getHeader(SIGN);
        String sessionName = request.getHeader(SESSION_NAME);
        boolean isSigned = false;
        if(sign!=null && sessionName!=null)
            isSigned=true;

        String isForbidFreeApiStr = jedis.hget(Settings.addSidBriefToName(sid,SETTINGS), FieldNames.FORBID_FREE_API);
        boolean isForbidFreeApi = isForbidFreeApiStr.equalsIgnoreCase("true");
        RequestCheckResult requestCheckResult = null;
        if(isSigned){
            switch (authType){
                case FC_SIGN_URL -> requestCheckResult = checkUrlSignRequest(sid, request, response, replier, jedis);
                case FC_SIGN_BODY -> requestCheckResult = checkBodySignRequest(sid, request, response, replier, jedis);
                default -> {
                }
            }
        }
        if(requestCheckResult==null) {
            if (isForbidFreeApi) {
                replier.replyWithCodeAndMessage(response, ReplyInfo.Code2001FreeGetIsForbidden, ReplyInfo.Msg2001FreeGetIsForbidden, null, null);
            } else {
                requestCheckResult = new RequestCheckResult();
                requestCheckResult.setFreeRequest(Boolean.TRUE);
            }
        }
        return requestCheckResult;
    }

    public RequestCheckResult checkSignInRequest(String sid, HttpServletRequest request, HttpServletResponse response, FcReplier replier, Map<String, String> paramsMap, long windowTime, Jedis jedis){
        RequestCheckResult requestCheckResult = new RequestCheckResult();
        String url = request.getRequestURL().toString();

        if(illegalUrl(url)){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1016IllegalUrl,ReplyInfo.Msg1016IllegalUrl,null, null);
            return null;
        }

        String fid = request.getHeader(ReplyInfo.FidInHeader);
        if(fid==null){
            String data = "A FID is required in request header.";
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1015FidMissed,ReplyInfo.Msg1015FidMissed,data,null );
            return null;
        }
        requestCheckResult.setFid(fid);

        String sign = request.getHeader(ReplyInfo.SignInHeader);

        if (paramsMap==null||paramsMap.get(URL_HEAD)==null) {
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1020OtherError,ReplyInfo.Msg1020OtherError,"Failed to get parameters from redis.", null);
            return null;
        }

        if(sign==null||"".equals(sign)){
            promoteSignInRequest(response, replier, paramsMap.get(URL_HEAD));
            return null;
        }

        byte[] requestBodyBytes = new byte[0];
        try {
            requestBodyBytes = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1020OtherError,ReplyInfo.Msg1020OtherError,"Getting request body bytes wrong.", null);
            return null;
        }
        if(requestBodyBytes==null){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1003BodyMissed,ReplyInfo.Msg1003BodyMissed,null, null);
            return null;
        }

        RequestBody signInRequestBody = getRequestBody(requestBodyBytes,response,replier, null);
        if(signInRequestBody==null)return null;
        if(isBadNonce(signInRequestBody.getNonce(), windowTime,jedis )){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1007UsedNonce,ReplyInfo.Msg1007UsedNonce,null,null );
            return null;
        }
        replier.setNonce(signInRequestBody.getNonce());

        String pubKey;
        try {
            pubKey = checkAsySignAndGetPubKey(fid,sign,requestBodyBytes);
        } catch (SignatureException e) {
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1008BadSign,ReplyInfo.Msg1008BadSign,null, null);
            return null;
        }
        if(null==pubKey){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1008BadSign,ReplyInfo.Msg1008BadSign,null,null );
            return null;
        }
        requestCheckResult.setPubKey(pubKey);

        if(isBadBalance(sid,fid,ApiNames.SignInAPI, jedis)){
            String data = "Send at lest "+paramsMap.get(MIN_PAYMENT)+" F to "+paramsMap.get(ACCOUNT)+" to buy the service #"+sid+".";
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1004InsufficientBalance,ReplyInfo.Msg1004InsufficientBalance,data, null);
            return null;
        }

        if(isBadUrl(signInRequestBody.getUrl(),url)){
            Map<String,String> dataMap = new HashMap<>();
            dataMap.put("requestedURL",request.getRequestURL().toString());
            dataMap.put("signedURL",signInRequestBody.getUrl());
            replier.setData(dataMap);
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1005UrlUnequal,ReplyInfo.Msg1005UrlUnequal,replier.getData(), null);
            return null;
        }

        if (isBadTime(signInRequestBody.getTime(),windowTime)){
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("windowTime", String.valueOf(windowTime));
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1006RequestTimeExpired,ReplyInfo.Msg1006RequestTimeExpired,dataMap, null);
            return null;
        }
        if(signInRequestBody.getVia()!=null) requestCheckResult.setVia(signInRequestBody.getVia());

        requestCheckResult.setRequestBody(signInRequestBody);
        return requestCheckResult;
    }

    private static void promoteSignInRequest(HttpServletResponse response, FcReplier replier, String urlHead) {
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
                    .formatted(urlHead+ApiNames.VersionV1 + ApiNames.SignInAPI,nonce,timestamp);
        }
        replier.replyWithCodeAndMessage(response,ReplyInfo.Code1000SignMissed,ReplyInfo.Msg1000SignMissed,data,null );
    }

    private static void promoteJsonRequest(HttpServletResponse response, FcReplier replier, DiskParams params) {
        String urlHead = params.getUrlHead();
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
                    """
                    .formatted(urlHead+ApiNames.VersionV1 + ApiNames.SignInAPI,nonce,timestamp);
        }
        replier.replyWithCodeAndMessage(response,ReplyInfo.Code1000SignMissed,ReplyInfo.Msg1000SignMissed,data,null );
    }

    private static void promoteUrlSignRequest(HttpServletResponse response, FcReplier replier, String urlHead) {
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
                    """
                    .formatted(urlHead+ApiNames.VersionV1 + ApiNames.SignInAPI,nonce,timestamp);
        }
        replier.replyWithCodeAndMessage(response,ReplyInfo.Code1000SignMissed,ReplyInfo.Msg1000SignMissed,data, null);
    }

    private static RequestBody getRequestBody(byte[] requestBodyBytes, HttpServletResponse response, FcReplier replier, String sessionKey) {
        String requestDataJson = new String(requestBodyBytes);
        RequestBody connectRequestBody;
        try {
            connectRequestBody = new Gson().fromJson(requestDataJson, RequestBody.class);
        }catch(Exception e){
            replier.replyWithCodeAndMessage(response,ReplyInfo.Code1013BadRequest,ReplyInfo.Msg1013BadRequest,"Parsing request body wrong.", sessionKey);
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

        return false;
    }
    public static boolean isBadBalance(String sid, String fid, String signInAPI, Jedis jedis){

        long nPrice = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(sid, Strings.N_PRICE), signInAPI);
        double price = RedisTools.readHashDouble(jedis, Settings.addSidBriefToName(sid,Strings.PARAMS),PRICE_PER_K_BYTES);
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
