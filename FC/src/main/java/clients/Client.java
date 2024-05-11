package clients;

import APIP.apipData.RequestBody;
import APIP.apipData.Session;
import FCH.ParseTools;
import clients.apipClient.ApipClient;
import clients.diskClient.DiskClientData;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import config.ApiType;
import constants.ApiNames;
import constants.ReplyInfo;
import crypto.cryptoTools.Hash;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

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
    protected ClientData clientData;
    protected byte[] symKey;
    protected byte[] sessionKey;
    protected ApipClient apipClient;
    protected Gson gson = new Gson();
    protected boolean sessionFreshen=false;
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
        this.symKey = symKey;
        this.apipClient = apipClient;
    }

    public Object checkResult(ApiType type){
        if(clientData ==null)return null;

        if(clientData.getCode()!= ReplyInfo.Code0Success) {
//            System.out.println("Failed to " + taskName);
            if (clientData.getResponseBody()== null) {
                System.out.println("ResponseBody is null when requesting "+this.clientData.getApiUrl().getUrl());
                System.out.println(clientData.getMessage());
            } else {
                System.out.println(clientData.getResponseBody().getCode() + ":" + clientData.getResponseBody().getMessage());
                if (clientData.getResponseBody().getData() != null)
                    System.out.println(JsonTools.getString(clientData.getResponseBody().getData()));
            }
            log.debug(clientData.getMessage());
            if (clientData.getCode() == ReplyInfo.Code1004InsufficientBalance) {
                apiAccount.buyApi(symKey);
                return null;
            }

            if (clientData.getCode() == ReplyInfo.Code1002SessionNameMissed || clientData.getCode() == ReplyInfo.Code1009SessionTimeExpired) {
                sessionFreshen=false;
                sessionKey = apiAccount.freshSessionKey(symKey, signInUrlTailPath, type, null);
                if (sessionKey != null) sessionFreshen=true;
            }
            return null;
        }
        checkBalance(apiAccount, clientData, symKey);
        if(clientData.getResponseBody().getData()==null && clientData.getCode()==0)
            return true;
        return clientData.getResponseBody().getData();
    }


    public static void checkBalance(ApiAccount apiAccount, final ClientData clientData, byte[] symKey) {
        if(clientData ==null|| clientData.getResponseBody()==null)return;
        if(clientData.getResponseBody().getCode()!=0)return;

        String priceStr;
        if(apiAccount.getServiceParams().getPricePerKBytes()==null)
            priceStr=apiAccount.getApipParams().getPricePerRequest();
        else priceStr =apiAccount.getApipParams().getPricePerKBytes();
        long price = ParseTools.fchStrToSatoshi(priceStr);

        if( clientData.getResponseBody().getBalance()==null)return;
        Long balance = clientData.getResponseBody().getBalance();
        if(balance==null)return;
        apiAccount.setBalance(balance);

        if(balance!=0 && balance < price * ApiAccount.minRequestTimes){
            double topUp = apiAccount.buyApi(symKey);
            if(topUp==0){
                log.debug("Failed to buy APIP service.");
                return;
            }
            apiAccount.setBalance(balance + ParseTools.coinToSatoshi(topUp));
        }
    }

    public static String getSessionKeySign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return HexFormat.of().formatHex(Hash.Sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes)));
    }

    public static boolean checkSign(String msg, String sign, String symKey) {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        return checkSign(msgBytes, sign, HexFormat.of().parseHex(symKey));
    }

    public static boolean checkSign(byte[] msgBytes, String sign, byte[] symKey) {
        if (sign == null || msgBytes == null) return false;
        byte[] signBytes = BytesTools.bytesMerger(msgBytes, symKey);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.Sha256x2(signBytes));
        return (sign.equals(doubleSha256Hash));
    }

    public static String getSessionName(byte[] sessionKey) {
        if (sessionKey == null) return null;
        return HexFormat.of().formatHex(Arrays.copyOf(sessionKey, 6));
    }

    public boolean ping(ApiType type) {
        clientData = new ClientData(apiAccount.getApiUrl(),null,null,null,ApiNames.PingAPI);
        clientData.postWithJsonBody(apiAccount.getVia(),sessionKey);
        Object data = checkResult(type);
        if(data==null)return false;
        return (boolean) data;
    }

    public boolean pingWithAuth(ApiType type, ClientData.AuthType authType) {
        clientData = new ClientData(sessionKey,apiAccount.getApiUrl(),null,null,null,ApiNames.PingAPI, apiAccount.getVia(), authType,null);
        clientData.post(sessionKey);
        Object data = checkResult(type);
        if(data==null)return false;
        return (boolean) data;
    }

    public Session signIn(byte[] priKey,ApiType type, @Nullable RequestBody.SignInMode mode) {
        clientData = new ClientData(apiAccount.getApiUrl(),signInUrlTailPath,ApiNames.SignInAPI);
        clientData.signInPost(apiAccount.getVia(), priKey, mode);
        Object data = checkResult(type);
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }

    public Session signInEcc(byte[] priKey, ApiType type, @Nullable RequestBody.SignInMode mode) {
        clientData = new ClientData(apiAccount.getApiUrl(),signInUrlTailPath,ApiNames.SignInEccAPI);
        clientData.signInPost(apiAccount.getVia(), priKey, mode);
        Object data = checkResult(type);
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

    public ClientData getClientData() {
        return clientData;
    }

    public void setClientData(DiskClientData clientData) {
        this.clientData = clientData;
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
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getUserPriKeyCipher(),symKey);
        Session session = signIn(priKey, type, mode);
        if(session==null||session.getSessionKey()==null)return null;
        byte[] sessionKey = Hex.fromHex(session.getSessionKey());

        apiAccount.setSessionKey(sessionKey);

        String sessionName = Session.makeSessionName(session.getSessionKey());
        String sessionKeyCipher=EccAes256K1P7.encryptWithSymKey(sessionKey,symKey);
        String fid = KeyTools.priKeyToFid(priKey);

        session.setSessionKeyCipher(sessionKeyCipher);
        session.setFid(fid);
        session.setSessionName(sessionName);

        apiAccount.setSession(session);
        apiAccount.setSessionKey(sessionKey);
        return session;
    }

    public Session signInEcc(ApiAccount apiAccount, ApiType type, RequestBody.SignInMode mode, byte[] symKey) {
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getUserPriKeyCipher(),symKey);
        String fid = KeyTools.priKeyToFid(priKey);
        Session session = signInEcc(priKey, type,mode);
        String sessionKeyCipher1 = session.getSessionKeyCipher();
        byte[] sessionKeyHexBytes = EccAes256K1P7.decryptWithPriKey(sessionKeyCipher1,priKey);
        if(sessionKeyHexBytes==null)return null;

        String sessionKeyHex =new String(sessionKeyHexBytes);
        sessionKey = Hex.fromHex(sessionKeyHex);

        String newCipher = EccAes256K1P7.encryptWithSymKey(sessionKey,symKey);
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


    public void setClientData(ClientData clientData) {
        this.clientData = clientData;
    }

    public String getSignInUrlTailPath() {
        return signInUrlTailPath;
    }

    public void setSignInUrlTailPath(String signInUrlTailPath) {
        this.signInUrlTailPath = signInUrlTailPath;
    }
}
