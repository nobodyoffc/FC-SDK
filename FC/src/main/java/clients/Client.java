package clients;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Session;
import fch.ParseTools;
import clients.apipClient.ApipClient;
import clients.diskClient.DiskClientEvent;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import config.ApiType;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import crypto.*;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import feip.feipData.serviceParams.Params;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.FreeApi;
import server.Settings;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static constants.ApiNames.*;
import static constants.UpStrings.BALANCE;
import static fcData.AlgorithmId.FC_Aes256Cbc_No1_NrC7;

/*
    - provider
    - account
    - data
    - methods:
 */
public class Client {
    protected static final Logger log = LoggerFactory.getLogger(Client.class);
    protected ApiProvider apiProvider;
    protected ApiAccount apiAccount;
    protected String urlHead;
    protected String via;
    protected String signInUrlTailPath;
    protected FcClientEvent fcClientEvent;
    protected byte[] symKey;
    protected byte[] sessionKey;
    protected ApipClient apipClient;
    protected boolean isAllowFreeRequest;
    protected ApiType apiType;
    protected Gson gson = new Gson();
    protected boolean sessionFreshen=false;
    protected long bestHeight;
    public Client() {}
    public Client(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey) {
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.symKey = symKey;
        this.urlHead = apiAccount.getApiUrl();
        this.via = apiAccount.getVia();
        this.apiType = apiProvider.getType();
    }
    public Client(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.apiType = apiProvider.getType();
        this.symKey = symKey;
        this.apipClient = apipClient;
        this.urlHead = apiAccount.getApiUrl();
        this.via = apiAccount.getVia();
        this.apiType = apiProvider.getType();
    }

    public static  Service getService(String urlHead, Class<ApipParams> apipParamsClass){
        ApiUrl apiUrl = new ApiUrl(urlHead,ApiNames.SN_0,ApiNames.Version2,ApiNames.GetServiceAPI, null,false,null);
        FcClientEvent apipClientData = Client.get(apiUrl.getUrl());
        if(apipClientData.checkResponse()!=0){
            System.out.println("Failed to get the APIP service from "+apiUrl.getUrl());
            return null;
        }
        Service service = new Gson().fromJson((String)apipClientData.getResponseBody().getData(), Service.class);
        Params apiParams = Params.getParamsFromService(service,apipParamsClass);
        service.setParams(apiParams);
        return service;
    }

    public static FcClientEvent get(String url){
        FcClientEvent fcClientEvent = new FcClientEvent();
        ApiUrl apiUrl = new ApiUrl();
        apiUrl.setUrl(url);
        fcClientEvent.setApiUrl(apiUrl);
        fcClientEvent.get();
        return fcClientEvent;
    }

    public FcClientEvent requestJsonByFcdslUrl(String sn, String ver, String apiName,
                                               @Nullable Map<String,String> paramMap, AuthType authType){
        String urlTailPath = ApiUrl.makeUrlTailPath(sn, ver);
        if(urlTailPath==null)urlTailPath="";
        String urlTail = urlTailPath +apiName;
        if(authType==null) {
            if (isAllowFreeRequest || sessionKey == null) authType = AuthType.FREE;
            else authType = AuthType.FC_SIGN_URL;
        }
        return request(HttpRequestMethod.GET, urlTail, FcClientEvent.RequestBodyType.FCDSL,null,null,null,
                paramMap, null,
                authType, null,
                FcClientEvent.ResponseBodyType.STRING, null, null);
    }

    public FcClientEvent requestFile(HttpRequestMethod method, String sn, String ver, String apiName,
                                     @Nullable Map<String,String> paramMap,
                                     String responseFileName,
                                     @Nullable String responseFilePath, AuthType authType, byte[] authKey){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        return request(method, urlTail, FcClientEvent.RequestBodyType.NONE,null,null,null,
                paramMap,null, authType, authKey,
                FcClientEvent.ResponseBodyType.FILE, responseFileName, responseFilePath);
    }
    public FcClientEvent requestJsonByFcdsl(HttpRequestMethod method, String sn, String ver, String apiName,
                                            @Nullable Fcdsl fcdsl,
                                            @Nullable byte[] authKey, AuthType authType){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        if(authType==null || authKey==null)
            authType = AuthType.FREE;

        return request(method, urlTail, FcClientEvent.RequestBodyType.FCDSL,fcdsl,null,null,null,null ,
                authType, authKey, FcClientEvent.ResponseBodyType.STRING, null, null);
    }

    public FcClientEvent requestFileByFcdsl(HttpRequestMethod method, String sn, String ver, String apiName,
                                                 @Nullable Fcdsl fcdsl,
                                                 @Nullable byte[] authKey,
                                                 String responseFileName,
                                                 @Nullable String responseFilePath){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        AuthType authType;
        if(authKey!=null)authType=AuthType.FC_SIGN_BODY;
        else authType = AuthType.FREE;

        return request(method, urlTail, FcClientEvent.RequestBodyType.FCDSL,fcdsl,null,null,null,null,
                authType, authKey, FcClientEvent.ResponseBodyType.FILE, responseFileName, responseFilePath);
    }

    public FcClientEvent requestJsonByFile(String sn, String ver, String apiName,
                                           @Nullable Map<String,String>  paramMap,
                                           @Nullable byte[] authKey,
                                           String requestFileName){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        AuthType authType;
        if(authKey!=null)authType=AuthType.FC_SIGN_BODY;
        else authType = AuthType.FREE;

        return request(HttpRequestMethod.POST, urlTail, FcClientEvent.RequestBodyType.FILE,null,null,null,
                paramMap,requestFileName, authType, authKey, FcClientEvent.ResponseBodyType.STRING, null, null);
    }

    public FcClientEvent request(HttpRequestMethod httpMethod, String urlTail,
                                 FcClientEvent.RequestBodyType requestBodyType,
                                 @Nullable Fcdsl fcdsl,
                                 @Nullable String requestBodyStr,
                                 @Nullable byte[] requestBodyBytes,
                                 @Nullable Map<String,String> paramMap,
                                 String requestFileName,
                                 AuthType authType,
                                 @Nullable byte[] authKey,
                                 FcClientEvent.ResponseBodyType responseBodyType,
                                 String responseFileName,
                                 String responseFilePath){

        if(httpMethod.equals(HttpRequestMethod.GET) && fcdsl!=null){
            String urlParamsStr = Fcdsl.fcdslToUrlParams(fcdsl);
            paramMap = Fcdsl.urlParamsStrToMap(urlParamsStr);
        }

        FcClientEvent clientEvent = new FcClientEvent(urlHead,urlTail,requestBodyType,fcdsl,requestBodyStr,requestBodyBytes,paramMap,requestFileName, responseBodyType,responseFileName,responseFilePath,authType, authKey, via);

        switch (httpMethod){
            case GET -> clientEvent.get(authKey);
            case POST -> clientEvent.post(authKey);
            default -> clientEvent.setCode(ReplyCodeMessage.Code1022NoSuchMethod);
        }
        return clientEvent;
    }

    public FcClientEvent request(HttpRequestMethod httpMethod, String sn, String ver, String apiName,
                                 FcClientEvent.RequestBodyType requestBodyType,
                                 @Nullable Fcdsl fcdsl,
                                 @Nullable String requestBodyStr,
                                 @Nullable byte[] requestBodyBytes,
                                 @Nullable Map<String,String> paramMap,
                                 String requestFileName,
                                 AuthType authType,
                                 @Nullable byte[] authKey,
                                 FcClientEvent.ResponseBodyType responseBodyType,
                                 String responseFileName,
                                 String responseFilePath){
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
       return request(httpMethod, urlTail,requestBodyType,fcdsl,requestBodyStr,requestBodyBytes,paramMap, requestFileName, authType, authKey, responseBodyType, responseFileName, responseFilePath);
    }

    public Object checkResult(){
        if(fcClientEvent ==null)return null;
        if(fcClientEvent.getCode()!= ReplyCodeMessage.Code0Success) {
            if (fcClientEvent.getResponseBody()== null) {
                log.debug("ResponseBody is null when requesting "+this.fcClientEvent.getApiUrl().getUrl());
                System.out.println(fcClientEvent.getMessage());
            } else {
                log.debug(fcClientEvent.getResponseBody().getCode() + ":" + fcClientEvent.getResponseBody().getMessage());
                if (fcClientEvent.getResponseBody().getData() != null)
                    log.debug(JsonTools.toJson(fcClientEvent.getResponseBody().getData()));
            }
            log.debug(fcClientEvent.getMessage());
            if (fcClientEvent.getCode() == ReplyCodeMessage.Code1004InsufficientBalance) {
                if(apipClient==null && this.apiType.equals(ApiType.APIP)){
                    apipClient = (ApipClient) this;
                }
                apiAccount.buyApi(symKey,apipClient);
                return null;
            }

            if (fcClientEvent.getCode() == ReplyCodeMessage.Code1002SessionNameMissed || fcClientEvent.getCode() == ReplyCodeMessage.Code1009SessionTimeExpired) {
                sessionFreshen=false;
                sessionKey = apiAccount.freshSessionKey(symKey, signInUrlTailPath, this.apiType, null);
                if (sessionKey != null) sessionFreshen=true;
            }

            return null;
        }
        checkBalance(apiAccount, fcClientEvent, symKey,apipClient);
        if(fcClientEvent.getResponseBody().getData()==null && fcClientEvent.getCode()==0)
            return true;
        return fcClientEvent.getResponseBody().getData();
    }


    public static Long checkBalance(ApiAccount apiAccount, final FcClientEvent fcClientEvent, byte[] symKey, ApipClient apipClient) {
        if(fcClientEvent ==null)return null;
        if(fcClientEvent.getResponseBody()==null)return null;
        Long balance = null;
        if( fcClientEvent.getResponseBody().getBalance()!=null)
            balance = fcClientEvent.getResponseBody().getBalance();
        else if(fcClientEvent.getResponseHeaderMap()!=null&& fcClientEvent.getResponseHeaderMap().get(BALANCE)!=null)
                balance = Long.valueOf(fcClientEvent.getResponseHeaderMap().get(BALANCE));
        if(balance==null)return null;
        apiAccount.setBalance(balance);

        String priceStr;
        if(apiAccount.getServiceParams().getPricePerKBytes()==null)
            priceStr=apiAccount.getApipParams().getPricePerRequest();
        else priceStr =apiAccount.getApipParams().getPricePerKBytes();
        long price = ParseTools.fchStrToSatoshi(priceStr);

        if(balance!=0 && balance < price * ApiAccount.minRequestTimes){
            double topUp = apiAccount.buyApi(symKey,apipClient);
            if(topUp==0){
                log.debug("Failed to buy APIP service.");
                return null;
            }
            apiAccount.setBalance(balance + ParseTools.coinToSatoshi(topUp));
        }else {

            return balance/price;
        }
        return null;
    }

    public static String getSessionKeySign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return HexFormat.of().formatHex(Hash.sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes)));
    }

    public static boolean checkSign(String msg, String sign, String symKey) {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        return checkSign(msgBytes, sign, HexFormat.of().parseHex(symKey));
    }

    public static boolean checkSign(byte[] msgBytes, String sign, byte[] symKey) {
        if (sign == null || msgBytes == null) return false;
        byte[] signBytes = BytesTools.bytesMerger(msgBytes, symKey);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));
        return (sign.equals(doubleSha256Hash));
    }

    public static String getSessionName(byte[] sessionKey) {
        if (sessionKey == null) return null;
        return HexFormat.of().formatHex(Arrays.copyOf(sessionKey, 6));
    }

    public boolean pingFree() {
        fcClientEvent = request(HttpRequestMethod.GET, PingApi, FcClientEvent.RequestBodyType.NONE,null,null,null,null, null, AuthType.FREE, null, FcClientEvent.ResponseBodyType.STRING, null,null);
        Object data = checkResult();
        Map<String, FreeApi> freeApiMap = Settings.freeApiMap;
        if(data==null){
            if(freeApiMap !=null && freeApiMap.get(this.urlHead)!=null ){
                freeApiMap.get(this.urlHead).setActive(false);
            }
            return false;
        }
        if(freeApiMap==null)freeApiMap = new HashMap<>();
        if(freeApiMap.get(this.urlHead)==null){
            FreeApi freeApi = new FreeApi(this.urlHead,true);
            freeApiMap.put(this.urlHead,freeApi);
        }freeApiMap.get(this.urlHead).setActive(true);
        return (boolean) data;
    }

    public Long ping() {
        fcClientEvent = request(HttpRequestMethod.POST, PingApi, FcClientEvent.RequestBodyType.FCDSL,null,null,null,null, null, AuthType.FC_SIGN_BODY, sessionKey, FcClientEvent.ResponseBodyType.STRING, null,null);
        Object data = checkResult();
        Long rest = checkBalance(apiAccount, fcClientEvent,symKey,apipClient);
        if(data==null)return null;
        return rest;
    }

    public Session signIn(byte[] priKey, @Nullable RequestBody.SignInMode mode) {
        fcClientEvent = new FcClientEvent(apiAccount.getApiUrl(),signInUrlTailPath,ApiNames.SignInAPI);
        fcClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
        Object data = checkResult();
        checkBalance(apiAccount, fcClientEvent,symKey,apipClient);
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }

    public Session signInEcc(byte[] priKey, @Nullable RequestBody.SignInMode mode) {
        fcClientEvent = new FcClientEvent(apiAccount.getApiUrl(),signInUrlTailPath,ApiNames.SignInEccAPI);
        fcClientEvent.signInPost(apiAccount.getVia(), priKey, mode);
        Object data = checkResult();
        checkBalance(apiAccount, fcClientEvent,symKey,apipClient);
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }

    public ApiProvider getApiProvider() {
        return apiProvider;
    }

    public void setApiProvider(ApiProvider apiProvider) {
        this.apiProvider = apiProvider;
    }

    public ApiAccount getApiAccount() {
        return apiAccount;
    }

    public void setApiAccount(ApiAccount apiAccount) {
        this.apiAccount = apiAccount;
    }

    public FcClientEvent getClientData() {
        return fcClientEvent;
    }

    public void setClientData(DiskClientEvent clientData) {
        this.fcClientEvent = clientData;
    }

    public byte[] getSymKey() {
        return symKey;
    }

    public void setSymKey(byte[] symKey) {
        this.symKey = symKey;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public Session signIn(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey) {

        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getUserPriKeyCipher(),symKey);
        Session session = signIn(priKey, mode);
        if(session==null||session.getSessionKey()==null)return null;
        byte[] sessionKey = Hex.fromHex(session.getSessionKey());

        apiAccount.setSessionKey(sessionKey);

        String sessionName = Session.makeSessionName(session.getSessionKey());

        Encryptor encryptor = new Encryptor();
        CryptoDataByte cryptoDataByte2 = encryptor.encryptBySymKey(sessionKey,symKey);
        if(cryptoDataByte2.getCode()!=0)return null;
        String sessionKeyCipher = cryptoDataByte2.toJson();
//        String sessionKeyCipher=EccAes256K1P7.encryptWithSymKey(sessionKey,symKey);

        String fid = KeyTools.priKeyToFid(priKey);

        session.setSessionKeyCipher(sessionKeyCipher);
        session.setFid(fid);
        session.setSessionName(sessionName);

        apiAccount.setSession(session);
        apiAccount.setSessionKey(sessionKey);
        return session;
    }

    public Session signInEcc(ApiAccount apiAccount, RequestBody.SignInMode mode, byte[] symKey) {

        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

        String fid = KeyTools.priKeyToFid(priKey);
        Session session = signInEcc(priKey, mode);
        String sessionKeyCipher1 = session.getSessionKeyCipher();

        CryptoDataByte cryptoDataByte1 =
                decryptor.decryptJsonByAsyOneWay(sessionKeyCipher1,priKey);
        if(cryptoDataByte1.getCode()!=0)return null;
        byte[] sessionKeyHexBytes = cryptoDataByte1.getData();
        if(sessionKeyHexBytes==null)return null;

        String sessionKeyHex =new String(sessionKeyHexBytes);
        sessionKey = Hex.fromHex(sessionKeyHex);

        Encryptor encryptor = new Encryptor(FC_Aes256Cbc_No1_NrC7);
        CryptoDataByte cryptoDataByte2 = encryptor.encryptBySymKey(sessionKey,symKey);
        if(cryptoDataByte2.getCode()!=0)return null;
        String newCipher = cryptoDataByte2.toJson();
        String sessionName = Session.makeSessionName(sessionKeyHex);
        Long expireTime = session.getExpireTime();

        session.setSessionKey(Hex.toHex(sessionKey));
        session.setSessionKeyCipher(newCipher);
        session.setSessionName(sessionName);
        session.setExpireTime(expireTime);
        session.setFid(fid);

        apiAccount.setSession(session);
        apiAccount.setSessionKey(sessionKey);

        return session;
    }
    public boolean isSessionFreshen() {
        return sessionFreshen;
    }

    public void setSessionFreshen(boolean sessionFreshen) {
        this.sessionFreshen = sessionFreshen;
    }


    public void setClientData(FcClientEvent fcClientEvent) {
        this.fcClientEvent = fcClientEvent;
    }

    public String getSignInUrlTailPath() {
        return signInUrlTailPath;
    }

    public void setSignInUrlTailPath(String signInUrlTailPath) {
        this.signInUrlTailPath = signInUrlTailPath;
    }

    public ApiType getApiType() {
        return apiType;
    }

    public void setApiType(ApiType apiType) {
        this.apiType = apiType;
    }

    public long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public boolean isAllowFreeRequest() {
        return isAllowFreeRequest;
    }

    public void setAllowFreeRequest(boolean allowFreeRequest) {
        isAllowFreeRequest = allowFreeRequest;
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }
}
