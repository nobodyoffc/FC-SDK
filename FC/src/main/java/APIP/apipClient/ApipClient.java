package APIP.apipClient;

import APIP.apipData.CidInfo;
import APIP.apipData.Fcdsl;
import FCH.fchData.Address;
import FCH.fchData.SendTo;
import config.ApiAccount;
import config.ApiProvider;
import constants.CodeAndMsg;
import crypto.cryptoTools.KeyTools;
import javaTools.JsonTools;
import javaTools.http.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

public class ApipClient {
    private static final Logger log = LoggerFactory.getLogger(ApipClient.class);
    private ApiProvider apiProvider;
    private ApiAccount apiAccount;
    private ApipClientData apipClientData;
    private Fcdsl fcdsl;

    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount) {
        this.apiAccount = apiAccount;
        this.apiProvider = apiProvider;
    }

    public Object checkApipResult(byte[]symKey, String taskName){
        if(apipClientData ==null)return null;

        if(apipClientData.getCode()!= CodeAndMsg.Code0Success) {
            System.out.println("Failed to " + taskName);
            if (apipClientData.getResponseBody()== null) {
                System.out.println("ResponseBody is null.");
                System.out.println(apipClientData.getMessage());
            } else {
                System.out.println(apipClientData.getResponseBody().getCode() + ":" + apipClientData.getResponseBody().getMessage());
                if (apipClientData.getResponseBody().getData() != null) System.out.println(JsonTools.getString(apipClientData.getResponseBody().getData()));
            }
            log.debug(apipClientData.getMessage());
            if (apipClientData.getCode() == CodeAndMsg.Code1004InsufficientBalance) {
                apiAccount.buyApip(symKey);
                return null;
            }

            if (apipClientData.getCode() == CodeAndMsg.Code1002SessionNameMissed || apipClientData.getCode() == CodeAndMsg.Code1009SessionTimeExpired) {
                apiAccount.freshApipSessionKey(symKey, null);
                if (apiAccount.getSessionKey() == null) {
                    return null;
                }
            }
        }
        ApiAccount.checkApipBalance(apiAccount, apipClientData, symKey);
        return apipClientData.getResponseBody().getData();
    }


    public static CidInfo getCidInfo(String fid, ApiAccount apiAccount, byte[] symKey) {
        ApipClientData apipClientData = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), new String[]{fid}, apiAccount.getVia(), symKey);
        assert apipClientData != null;
        if (apipClientData.checkResponse() != 0) {
            if (apipClientData.getMessage() != null) System.out.println(apipClientData.getMessage());
            if (apipClientData.getResponseBody() != null && apipClientData.getResponseBody().getData() != null)
                System.out.println(JsonTools.getString(apipClientData.getResponseBody().getData()));
            return null;
        }
        Map<String, CidInfo> addrMap = ApipDataGetter.getCidInfoMap(apipClientData.getResponseBody().getData());
        CidInfo cid = addrMap.get(fid);
        if (cid == null) {
            System.out.println("The pubKey is not shown on-chain.");
            return null;
        }
        return cid;
    }

    public static String getPubKey(String fid, ApiAccount apipParams, byte[] symKey) {
        ApipClientData apipClientData = BlockchainAPIs.fidByIdsPost(apipParams.getApiUrl(), new String[]{fid}, apipParams.getVia(), symKey);
        if (apipClientData == null || apipClientData.checkResponse() != 0) {
            if (apipClientData.getMessage() != null) System.out.println(apipClientData.getMessage());
            return null;
        }
        Map<String, Address> addrMap = ApipDataGetter.getAddressMap(apipClientData.getResponseBody().getData());
        Address address = addrMap.get(fid);
        if (address == null) {
            System.out.println("The pubKey is not shown on-chain.");
            return null;
        }
        String pubKey = address.getPubKey();
        if (pubKey == null) {
            System.out.println("This address " + fid + " has no pubKey on-chain.");
            return null;
        }
        if (!KeyTools.isValidPubKey(pubKey)) {
            System.out.println("Invalid pubKey:" + pubKey);
            return null;
        }
        return pubKey;
    }

    public ApipClientData blockByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.blockByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData blockSearch(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.blockSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData cashValid(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
           case POST -> apipClientData = BlockchainAPIs.cashValidPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData cashByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.cashByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData getUtxo(String id, double amount, HttpMethods httpMethods){
        switch (httpMethods) {
            case GET -> apipClientData = BlockchainAPIs.getUtxo(apiAccount.getApiUrl(), id,amount);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData cashSearch(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.cashSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData fidByIds(String[] ids, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData opReturnByIds(String[] ids, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.opReturnByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData opReturnSearch(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
           case POST -> apipClientData = BlockchainAPIs.opReturnSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData p2shByIds(String[] ids, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.p2shByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData p2shSearch(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.p2shSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData txSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData txByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.txByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData txByFid(String fid, String[] last, HttpMethods httpMethods){
        Fcdsl fcdsl = BlockchainAPIs.txByFidQuery(fid,last);
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData protocolByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.protocolByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData protocolSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.protocolSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData protocolOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.protocolOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData protocolRateHistory(Fcdsl fcdsl,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.protocolRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData codeByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.codeByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData codeSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.codeSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData codeOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.codeOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData codeRateHistory(Fcdsl fcdsl,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.codeRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData serviceByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.serviceByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData serviceSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.serviceSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData serviceOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.serviceOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData serviceRateHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.serviceRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData appByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.appByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData appSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.appSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData appOpHistory(Fcdsl fcdsl,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.appOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData appRateHistory(Fcdsl fcdsl,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.appRateHistoryPost(apiAccount.getApiUrl(),fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData addresses(String addrOrPubKey, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.addressesPost(apiAccount.getApiUrl(), addrOrPubKey,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData encrypt(String key, String message, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.encryptPost(apiAccount.getApiUrl(), key,message,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData verify(String signature,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.verifyPost(apiAccount.getApiUrl(), signature, apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData sha256(String text,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.sha256Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData sha256x2(String text, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.sha256x2Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData sha256Bytes(String hex,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.sha256BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData sha256x2Bytes(String hex,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.sha256x2BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData offLineTx(String fromFid, List<SendTo> sendToList, String msg, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.offLineTxPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData offLineTxByCd(String fromFid, List<SendTo> sendToList, String msg, int cd, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.offLineTxByCdPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,cd,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData getApps(String id, HttpMethods httpMethods){
        switch (httpMethods) {
            case GET -> apipClientData = FreeGetAPIs.getApps(apiAccount.getApiUrl(), id);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData getServices(String id, HttpMethods httpMethods){
        switch (httpMethods) {
            case GET -> apipClientData = FreeGetAPIs.getServices(apiAccount.getApiUrl(), id);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData getAvatar(String fid, HttpMethods httpMethods){
        switch (httpMethods) {
            case GET -> apipClientData = FreeGetAPIs.getAvatar(apiAccount.getApiUrl(), fid);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData broadcast(String txHex, HttpMethods httpMethods){
        switch (httpMethods) {
            case GET -> apipClientData = FreeGetAPIs.broadcast(apiAccount.getApiUrl(),txHex);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData getCashes(String id,double amount, HttpMethods httpMethods){
        switch (httpMethods) {
            case GET -> apipClientData = FreeGetAPIs.getCashes(apiAccount.getApiUrl(),id,amount);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData getFidCid(String id){
        apipClientData = FreeGetAPIs.getFidCid(apiAccount.getApiUrl(), id);
        return apipClientData;
    }

    public ApipClientData getTotals(){
        return FreeGetAPIs.getTotals(apiAccount.getApiUrl());
    }

    public ApipClientData cidInfoByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData cidInfoSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData cidInfoSearch(String searchStr, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData cidHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.cidHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData homepageHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.homepageHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData noticeFeeHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.noticeFeeHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData reputationHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.reputationHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData fidCidSeek(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData fidCidSeek(String searchStr, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData fidCidGet(String id, HttpMethods httpMethods){
        switch (httpMethods) {
            case GET -> apipClientData = IdentityAPIs.fidCidGet(apiAccount.getApiUrl(), id);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData nobodyByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.nobodyByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData nobodySearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.nobodySearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData avatars(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.avatarsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData getService(){
        apipClientData = OpenAPIs.getService(apiAccount.getApiUrl());
        return apipClientData;
    }

    public ApipClientData signIn(byte[] priKey, String mode_RefreshOrNull){
        apipClientData = OpenAPIs.signInPost(apiAccount.getApiUrl(),apiAccount.getVia(),priKey,mode_RefreshOrNull);
        return apipClientData;
    }

    public ApipClientData signInEcc(byte[] priKey, String mode_RefreshOrNull){
        apipClientData = OpenAPIs.signInPost(apiAccount.getApiUrl(), apiAccount.getVia(), priKey,mode_RefreshOrNull);
        return apipClientData;
    }

    public ApipClientData totals(HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OpenAPIs.totalsPost(apiAccount.getApiUrl(),apiAccount.getVia(),apiAccount.getSessionKey());
            case GET -> apipClientData = OpenAPIs.totalsGet(apiAccount.getApiUrl());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData general(String index, Fcdsl fcdsl){
        apipClientData = OpenAPIs.generalPost(index,apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
        return apipClientData;
    }

    public ApipClientData groupByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.groupByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData groupSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.groupSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData groupOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.groupOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData groupMembers(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.groupMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData myGroups(String fid, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.myGroupsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData teamByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData teamSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData teamOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData teamRateHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData teamMembers(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData teamExMembers(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamExMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData teamOtherPersons(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamOtherPersonsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData myTeams(String fid, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.myTeamsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData boxByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.boxByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData boxSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.boxSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData boxHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.boxHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData contactByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.contactByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData contacts(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.contactsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData contactsDeleted(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.contactsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData mailByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.mailByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData mails(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.mailsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData mailsDeleted(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.mailsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData secretByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.secretByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData secrets(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.secretsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData secretsDeleted(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.secretsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData tokenByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.tokenByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData tokenSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.tokenSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData tokenHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.tokenHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData myTokens(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.myTokensPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData tokenHolderByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.tokenHolderByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData proofByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.proofByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData proofSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.proofSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData proofHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.proofHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData statementByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.statementByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData statementSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.statementSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData nidSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.nidSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData broadcastTx(String txHex, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.broadcastTxPost(apiAccount.getApiUrl(), txHex,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData decodeRawTx(String rawTxHex, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.decodeRawTxPost(apiAccount.getApiUrl(), rawTxHex,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData cashValidForPay(String fid, double amount,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.cashValidForPayPost(apiAccount.getApiUrl(), fid,amount,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData cashValidForCd(String fid, int cd,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.cashValidForCdPost(apiAccount.getApiUrl(), fid,cd,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData unconfirmedPost(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.unconfirmedPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData swapRegister(String sid, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = SwapHallAPIs.swapRegisterPost(apiAccount.getApiUrl(), sid,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData swapUpdate(Map<String, Object> uploadMap, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = SwapHallAPIs.swapUpdatePost(apiAccount.getApiUrl(), uploadMap,apiAccount.getVia(),apiAccount.getSessionKey());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData getSwapState(String sid, String[] last){
        apipClientData = SwapHallAPIs.getSwapState(apiAccount.getApiUrl(), sid);
        return apipClientData;
    }

    public ApipClientData getSwapLp(String sid, String[] last){
        apipClientData = SwapHallAPIs.getSwapLp(apiAccount.getApiUrl(), sid);
        return apipClientData;
    }

    public ApipClientData getSwapFinished(String sid, String[] last){
        apipClientData = SwapHallAPIs.getSwapFinished(apiAccount.getApiUrl(), sid,last);
        return apipClientData;
    }

    public ApipClientData getSwapPending(String sid){
        apipClientData = SwapHallAPIs.getSwapPending(apiAccount.getApiUrl(), sid);
        return apipClientData;
    }

    public ApipClientData getSwapPrice(String sid, String gTick, String mTick, String[] last){
        apipClientData = SwapHallAPIs.getSwapPrice(apiAccount.getApiUrl(), sid,gTick,mTick,last);
        return apipClientData;
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

    public ApipClientData getApipClientData() {
        return apipClientData;
    }

    public void setApipClientData(ApipClientData apipClientData) {
        this.apipClientData = apipClientData;
    }

    public Fcdsl getFcdsl() {
        return fcdsl;
    }

    public void setFcdsl(Fcdsl fcdsl) {
        this.fcdsl = fcdsl;
    }
}
