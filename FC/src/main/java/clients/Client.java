package clients;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import fch.ParseTools;
import clients.apipClient.ApipClient;
import clients.diskClient.DiskClientTask;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import config.ApiType;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import crypto.*;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

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

    protected String signInUrlTailPath;
    protected ClientTask clientTask;
    protected byte[] symKey;
    protected byte[] sessionKey;
    protected ApipClient apipClient;
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
    }
    public Client(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.apiType = apiProvider.getType();
        this.symKey = symKey;
        this.apipClient = apipClient;
    }
    public Object checkResult(){
        return checkResult(this.apiType);
    }
    public Object checkResult(ApiType type){
        if(clientTask ==null)return null;
        if(clientTask.getCode()!= ReplyCodeMessage.Code0Success) {
            if (clientTask.getResponseBody()== null) {
                log.debug("ResponseBody is null when requesting "+this.clientTask.getApiUrl().getUrl());
                System.out.println(clientTask.getMessage());
            } else {
                log.debug(clientTask.getResponseBody().getCode() + ":" + clientTask.getResponseBody().getMessage());
                if (clientTask.getResponseBody().getData() != null)
                    log.debug(JsonTools.getString(clientTask.getResponseBody().getData()));
            }
            log.debug(clientTask.getMessage());
            if (clientTask.getCode() == ReplyCodeMessage.Code1004InsufficientBalance) {
                apiAccount.buyApi(symKey,apipClient);
                return null;
            }

            if (clientTask.getCode() == ReplyCodeMessage.Code1002SessionNameMissed || clientTask.getCode() == ReplyCodeMessage.Code1009SessionTimeExpired) {
                sessionFreshen=false;
                sessionKey = apiAccount.freshSessionKey(symKey, signInUrlTailPath, type, null);
                if (sessionKey != null) sessionFreshen=true;
            }
            return null;
        }
        checkBalance(apiAccount, clientTask, symKey,apipClient);
        if(clientTask.getResponseBody().getData()==null && clientTask.getCode()==0)
            return true;
        return clientTask.getResponseBody().getData();
    }


    public static Long checkBalance(ApiAccount apiAccount, final ClientTask clientTask, byte[] symKey,ApipClient apipClient) {
        if(clientTask ==null)return null;
        if(clientTask.getResponseBody()==null)return null;
        Long balance = null;
        if( clientTask.getResponseBody().getBalance()!=null)
            balance = clientTask.getResponseBody().getBalance();
        else if(clientTask.getResponseHeaderMap()!=null&&clientTask.getResponseHeaderMap().get(BALANCE)!=null)
                balance = Long.valueOf(clientTask.getResponseHeaderMap().get(BALANCE));
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

    public boolean pingFree(ApiType type) {
        clientTask = new ClientTask(apiAccount.getApiUrl(),null,null,null,ApiNames.PingAPI);
        clientTask.get();
        Object data = checkResult(type);
        if(data==null)return false;
        return (boolean) data;
    }

    public Long ping(ApiType type) {
        clientTask = new ClientTask(sessionKey,apiAccount.getApiUrl(),null,null,null,ApiNames.PingAPI, apiAccount.getVia(), AuthType.FC_SIGN_BODY,null);
        clientTask.post(sessionKey);
        Object data = checkResult(type);
        Long rest = checkBalance(apiAccount,clientTask,symKey,apipClient);
        if(data==null)return null;
        return rest;
    }

    public Session signIn(byte[] priKey,ApiType type, @Nullable RequestBody.SignInMode mode) {
        clientTask = new ClientTask(apiAccount.getApiUrl(),signInUrlTailPath,ApiNames.SignInAPI);
        clientTask.signInPost(apiAccount.getVia(), priKey, mode);
        Object data = checkResult(type);
        checkBalance(apiAccount,clientTask,symKey,apipClient);
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }

    public Session signInEcc(byte[] priKey, ApiType type, @Nullable RequestBody.SignInMode mode) {
        clientTask = new ClientTask(apiAccount.getApiUrl(),signInUrlTailPath,ApiNames.SignInEccAPI);
        clientTask.signInPost(apiAccount.getVia(), priKey, mode);
        Object data = checkResult(type);
        checkBalance(apiAccount,clientTask,symKey,apipClient);
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

    public ClientTask getClientData() {
        return clientTask;
    }

    public void setClientData(DiskClientTask clientData) {
        this.clientTask = clientData;
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

    public Session signIn(ApiAccount apiAccount, ApiType type, RequestBody.SignInMode mode, byte[] symKey) {

        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getUserPriKeyCipher(),symKey);
        Session session = signIn(priKey, type, mode);
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

    public Session signInEcc(ApiAccount apiAccount, ApiType type, RequestBody.SignInMode mode, byte[] symKey) {

        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(apiAccount.getUserPriKeyCipher(),symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] priKey = cryptoDataByte.getData();

        String fid = KeyTools.priKeyToFid(priKey);
        Session session = signInEcc(priKey, type,mode);
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


    public void setClientData(ClientTask clientTask) {
        this.clientTask = clientTask;
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
}
