package clients;

import APIP.apipData.RequestBody;
import APIP.apipData.Session;
import FCH.ParseTools;
import clients.apipClient.ApipClient;
import clients.diskClient.DiskClientData;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import constants.ApiNames;
import constants.ReplyInfo;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

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
    protected ClientData clientData;
    protected byte[] symKey;
    protected byte[] sessionKey;
    protected ApipClient apipClient;
    protected Gson gson = new Gson();
    public Client() {}
    public Client(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.symKey = symKey;
        this.apipClient = apipClient;
    }

    public Object checkResult(String taskName){
        if(clientData ==null)return null;

        if(clientData.getCode()!= ReplyInfo.Code0Success) {
            System.out.println("Failed to " + taskName);
            if (clientData.getResponseBody()== null) {
                System.out.println("ResponseBody is null.");
                System.out.println(clientData.getMessage());
            } else {
                System.out.println(clientData.getResponseBody().getCode() + ":" + clientData.getResponseBody().getMessage());
                if (clientData.getResponseBody().getData() != null)
                    System.out.println(JsonTools.getString(clientData.getResponseBody().getData()));
            }
            log.debug(clientData.getMessage());
            if (clientData.getCode() == ReplyInfo.Code1004InsufficientBalance) {
                apiAccount.buyApip(symKey);
                return null;
            }

            if (clientData.getCode() == ReplyInfo.Code1002SessionNameMissed || clientData.getCode() == ReplyInfo.Code1009SessionTimeExpired) {
                apiAccount.freshSessionKey(symKey, null);
                if (sessionKey == null) {
                    return null;
                }
            }
            return null;
        }
        checkBalance(apiAccount, clientData, symKey);
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

        long balance = clientData.getResponseBody().getBalance();
        apiAccount.setBalance(balance);

        if(balance < price * ApiAccount.minRequestTimes){
            byte[] priKey = EccAes256K1P7.decryptJsonBytes
                    (
                            apiAccount.getUserPriKeyCipher(),
                            symKey
                    );
            double topUp = apiAccount.buyApip(apiAccount.getApiUrl(), priKey);
            if(topUp==0){
                log.debug("Failed to buy APIP service.");
                return;
            }
            apiAccount.setBalance(balance + ParseTools.coinToSatoshi(topUp));
        }
    }

    public Session signIn(String urlTailPath, byte[] priKey, @Nullable RequestBody.SignInMode mode) {
        clientData = new ClientData();
        clientData.setUrl(apiAccount.getApiUrl());
        String url = makeApiUrl(clientData.getUrl(), urlTailPath,ApiNames.SignInAPI);
        clientData.setUrl(url);
        clientData.signInPost(apiAccount.getVia(), priKey, mode);
        Object data = checkResult("sign in");
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }

    public Session signInEcc(String versionPath, byte[] priKey, @Nullable RequestBody.SignInMode mode) {
        clientData = new ClientData();
        clientData.setUrl(apiAccount.getApiUrl());
        String url = makeApiUrl(clientData.getUrl(), versionPath,ApiNames.SignInEccAPI);
        clientData.setUrl(url);
        clientData.signInPost(apiAccount.getVia(), priKey, mode);
        Object data = checkResult("sign in");
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }


    public static String makeApiUrl(String urlHead, String urlTailPath,String apiName) {
        if(urlHead.endsWith("/")&& urlTailPath.startsWith("/")) urlTailPath.substring(1);
        if(!urlTailPath.endsWith("/"))urlTailPath=urlTailPath+"/";
        String urlTail=(urlHead + urlTailPath);
        return urlTail+apiName;
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
}
