package APIP.apipClient;

import APIP.apipData.BlockInfo;
import APIP.apipData.CidInfo;
import APIP.apipData.Fcdsl;
import APIP.apipData.TxInfo;
import FCH.Inputer;
import FCH.fchData.Cash;
import FCH.fchData.OpReturn;
import FCH.fchData.SendTo;
import FCH.fchData.Tx;
import FEIP.feipClient.IdentityFEIPs;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.ApipParams;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import constants.ApiNames;
import constants.CodeAndMsg;
import crypto.cryptoTools.Hash;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.HttpMethods;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static constants.ApiNames.apiList;
import static constants.ApiNames.freeApiList;
import static crypto.cryptoTools.KeyTools.priKeyToFid;

public class ApipClient {
    private static final Logger log = LoggerFactory.getLogger(ApipClient.class);
    private ApiProvider apiProvider;
    private ApiAccount apiAccount;
    private ApipClientData apipClientData;
    private byte[] symKey;
    private byte[] sessionKey;
    private Fcdsl fcdsl;

    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey) {
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.symKey = symKey;
    }

    public Object checkApipResult(String taskName){
        if(apipClientData ==null)return null;

        if(apipClientData.getCode()!= CodeAndMsg.Code0Success) {
            System.out.println("Failed to " + taskName);
            if (apipClientData.getResponseBody()== null) {
                System.out.println("ResponseBody is null.");
                System.out.println(apipClientData.getMessage());
            } else {
                System.out.println(apipClientData.getResponseBody().getCode() + ":" + apipClientData.getResponseBody().getMessage());
                if (apipClientData.getResponseBody().getData() != null)
                    System.out.println(JsonTools.getString(apipClientData.getResponseBody().getData()));
            }
            log.debug(apipClientData.getMessage());
            if (apipClientData.getCode() == CodeAndMsg.Code1004InsufficientBalance) {
                apiAccount.buyApip(symKey);
                return null;
            }

            if (apipClientData.getCode() == CodeAndMsg.Code1002SessionNameMissed || apipClientData.getCode() == CodeAndMsg.Code1009SessionTimeExpired) {
                apiAccount.freshApipSessionKey(symKey, null);
                if (sessionKey == null) {
                    return null;
                }
            }
            return null;
        }
        ApiAccount.checkApipBalance(apiAccount, apipClientData, symKey);

        return apipClientData.getResponseBody().getData();
    }


    public static String getApiNameFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex != url.length() - 1) {
            String name = url.substring(lastSlashIndex + 1);
            if (apiList.contains(name) || freeApiList.contains(name)) {
                return name;
            }
            return "";
        } else {
            return "";  // Return empty string if '/' is the last character or not found
        }
    }

    public static String getSessionKeySign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return HexFormat.of().formatHex(Hash.Sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes)));
    }

    public static boolean isGoodSign(String msg, String sign, String symKey) {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        return isGoodSign(msgBytes, sign, HexFormat.of().parseHex(symKey));
    }

    public static boolean isGoodSign(byte[] msgBytes, String sign, byte[] symKey) {
        if (sign == null || msgBytes == null) return false;
        byte[] signBytes = BytesTools.bytesMerger(msgBytes, symKey);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.Sha256x2(signBytes));
        return (sign.equals(doubleSha256Hash));
    }

    public static String getSessionName(byte[] sessionKey) {
        if (sessionKey == null) return null;
        return HexFormat.of().formatHex(Arrays.copyOf(sessionKey, 6));
    }

    public void checkMaster(String priKeyCipher,BufferedReader br) {

        byte[] priKey = EccAes256K1P7.decryptJsonBytes(priKeyCipher, symKey);
        if (priKey == null) {
            throw new RuntimeException("Failed to decrypt priKey.");
        }

        String fid = priKeyToFid(priKey);
        CidInfo cidInfo = getCidInfo(fid, apiAccount);
        if (cidInfo == null) {
            System.out.println("This fid was never seen on chain. Send some fch to it.");
            if (Inputer.askIfYes(br, "Stop to send? y/n")) System.exit(0);
        }
        if (cidInfo != null) {
            if (cidInfo.getMaster() != null) {
                System.out.println("The master of "+fid+" is " + cidInfo.getMaster());
                return;
            }
            if (Inputer.askIfYes(br, "Assign the master for " + fid + "? y/n:")) {
                if (getCashes(apiAccount.getApiUrl(), fid) == null) return;
                String master;
                while (true) {
                    master = Inputer.inputString(br, "Input the master FID or pubKey:");
                    if (KeyTools.isValidPubKey(master) || KeyTools.isValidFchAddr(master)) {
                        String result = IdentityFEIPs.setMaster(priKeyCipher, master, this);
                        if (result == null) System.out.println("Failed to set master.");
                        if (master.length() > 34) master = KeyTools.pubKeyToFchAddr(master);
                        if (Hex.isHexString(result))
                            System.out.println("Master " + master + " was set at txId: " + result);
                        else System.out.println(result);
                        break;
                    }
                }
            }
        } else {
            System.out.println("Failed to get CID information of " + fid + ".");

        }
    }
    public static List<Cash> getCashes(String apiUrl, String fid) {
        ApipClientData apipClientData = FreeGetAPIs.getCashes(apiUrl, fid, 0);
        if (apipClientData.isBadResponse("get cashes")) {
            return null;
        }
        List<Cash> cashList = ApipDataGetter.getCashList(apipClientData.getResponseBody().getData());
        if (cashList == null || cashList.isEmpty()) {
            System.out.println("No FCH of " + fid + ". Send at lest 0.001 fch to it.");
            return null;
        }
        return cashList;
    }

    @Nullable


    public static Service getApipService(String urlHead){
        if(urlHead.contains(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI))
            urlHead.replaceAll(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI,"");

        ApipClientData apipClientData = OpenAPIs.getService(urlHead);
        if(apipClientData.isBadResponse("get service from "+urlHead))return null;
        Gson gson = new Gson();
        Service service = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()),Service.class);
        ApipParams apipParams = ApipParams.fromObject(service);
        service.setParams(apipParams);

        return service;
    }

    @Nullable
    public Map<String, Service> parseApipServiceMap() {
        if (apipClientData.isBadResponse("get service")) {
            System.out.println("Failed to buy APIP service. Code:"+ apipClientData.getCode()+", Message:"+ apipClientData.getMessage());
            return null;
        }

        try {
            Map<String, Service> serviceMap = ApipDataGetter.getServiceMap(apipClientData.getResponseBody().getData());
            if(serviceMap==null) return null;
            for(String sid :serviceMap.keySet()) {
                Service service = serviceMap.get(sid);
                ApipParams apipParams = ApipParams.fromObject(service.getParams());
                service.setParams(apipParams);
            }
            return serviceMap;
        } catch (Exception e) {
            System.out.println("Failed to get APIP service.");
            e.printStackTrace();
            return null;
        }
    }


    public CidInfo getCidInfo(String fid, ApiAccount apiAccount) {
        apipClientData = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), new String[]{fid}, apiAccount.getVia(), sessionKey);
        Object data = checkApipResult("get block info by heights");
        if(data==null)return null;
        return ApipDataGetter.getCidInfoMap(data).get(fid);
    }

    public String getPubKey(String fid) {
        apipClientData = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), new String[]{fid}, apiAccount.getVia(), sessionKey);
        Object data = checkApipResult("get fid info");
        if(data==null)data = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), new String[]{fid}, apiAccount.getVia(), sessionKey);;
        if(data==null)return null;
        return ApipDataGetter.getAddressMap(data).get(fid).getPubKey();
    }

    public ApipClientData blockByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.blockByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public Map<String, BlockInfo> blockByHeights(String[] heights, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.blockByHeightPost(apiAccount.getApiUrl(), heights, apiAccount.getVia(), sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        Object data = checkApipResult("get block info by heights");
        return ApipDataGetter.getBlockInfoMap(data);
    }

    public ApipClientData blockSearch(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.blockSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData cashValid(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
           case POST -> apipClientData = BlockchainAPIs.cashValidPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData cashByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.cashByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
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

    public List<Cash> cashSearch(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.cashSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
           default -> apipClientData.set1017NoSuchMethod();
       }
        if (apipClientData.isBadResponse("get cashes")) {
            return null;
        }
        List<Cash> cashList = ApipDataGetter.getCashList(apipClientData.getResponseBody().getData());
        if (cashList == null || cashList.isEmpty()) {
            log.debug("No FCH of this FID. Send at lest 0.001 fch to it.");
            return null;
        }
        return cashList;
    }

    public ApipClientData fidByIds(String[] ids, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData opReturnByIds(String[] ids, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.opReturnByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
           case POST -> apipClientData = BlockchainAPIs.opReturnSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
           default -> apipClientData.set1017NoSuchMethod();
       }
        if (apipClientData.isBadResponse("get Op_Return list")) {
            return null;
        }
        List<OpReturn> opReturnList = ApipDataGetter.getOpReturnList(apipClientData.getResponseBody().getData());
        if (opReturnList == null || opReturnList.isEmpty()) {
            System.out.println("No FCH of this FID. Send at lest 0.001 fch to it.");
            return null;
        }
        return opReturnList;
    }

    public ApipClientData p2shByIds(String[] ids, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.p2shByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData p2shSearch(Fcdsl fcdsl, HttpMethods httpMethods){
       switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.p2shSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
           default -> apipClientData.set1017NoSuchMethod();
       }
        return apipClientData;
    }

    public ApipClientData txSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public Map<String, TxInfo> txByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.txByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        if (apipClientData.isBadResponse("get Tx info")) {
            return null;
        }
        Map<String, TxInfo> txMap = ApipDataGetter.getTxMap(apipClientData.getResponseBody().getData());
        if (txMap == null || txMap.isEmpty()) {
            System.out.println("No FCH of this FID. Send at lest 0.001 fch to it.");
            return null;
        }
        return txMap;
    }

    public ApipClientData txByFid(String fid, String[] last, HttpMethods httpMethods){
        Fcdsl fcdsl = BlockchainAPIs.txByFidQuery(fid,last);
        switch (httpMethods) {
            case POST -> apipClientData = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData protocolByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.protocolByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData protocolSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.protocolSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData protocolOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.protocolOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData protocolRateHistory(Fcdsl fcdsl,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.protocolRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData codeByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.codeByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData codeSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.codeSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData codeOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.codeOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData codeRateHistory(Fcdsl fcdsl,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.codeRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData serviceByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.serviceByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData serviceSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.serviceSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData serviceOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.serviceOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData serviceRateHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.serviceRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData appByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.appByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData appSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.appSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData appOpHistory(Fcdsl fcdsl,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.appOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData appRateHistory(Fcdsl fcdsl,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = ConstructAPIs.appRateHistoryPost(apiAccount.getApiUrl(),fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData addresses(String addrOrPubKey, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.addressesPost(apiAccount.getApiUrl(), addrOrPubKey,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData encrypt(String key, String message, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.encryptPost(apiAccount.getApiUrl(), key,message,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData verify(String signature,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.verifyPost(apiAccount.getApiUrl(), signature, apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData sha256(String text,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.sha256Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData sha256x2(String text, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.sha256x2Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData sha256Bytes(String hex,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.sha256BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData sha256x2Bytes(String hex,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.sha256x2BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData offLineTx(String fromFid, List<SendTo> sendToList, String msg, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.offLineTxPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData offLineTxByCd(String fromFid, List<SendTo> sendToList, String msg, int cd, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = CryptoToolAPIs.offLineTxByCdPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,cd,apiAccount.getVia(),sessionKey);
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
            case POST -> apipClientData = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData cidInfoSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData cidInfoSearch(String searchStr, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData cidHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.cidHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData homepageHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.homepageHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData noticeFeeHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.noticeFeeHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData reputationHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.reputationHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData fidCidSeek(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData fidCidSeek(String searchStr, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
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
            case POST -> apipClientData = IdentityAPIs.nobodyByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData nobodySearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.nobodySearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData avatars(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = IdentityAPIs.avatarsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
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
            case POST -> apipClientData = OpenAPIs.totalsPost(apiAccount.getApiUrl(),apiAccount.getVia(),sessionKey);
            case GET -> apipClientData = OpenAPIs.totalsGet(apiAccount.getApiUrl());
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData general(String index, Fcdsl fcdsl){
        apipClientData = OpenAPIs.generalPost(index,apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        return apipClientData;
    }

    public ApipClientData groupByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.groupByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData groupSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.groupSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData groupOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.groupOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData groupMembers(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.groupMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData myGroups(String fid, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.myGroupsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData teamByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData teamSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData teamOpHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData teamRateHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData teamMembers(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData teamExMembers(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamExMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData teamOtherPersons(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.teamOtherPersonsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData myTeams(String fid, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = OrganizeAPIs.myTeamsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }


    public ApipClientData boxByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.boxByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData boxSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.boxSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData boxHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.boxHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData contactByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.contactByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData contacts(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.contactsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData contactsDeleted(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.contactsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData mailByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.mailByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData mails(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.mailsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData mailsDeleted(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.mailsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData secretByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.secretByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData secrets(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.secretsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData secretsDeleted(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PersonalAPIs.secretsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData tokenByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.tokenByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData tokenSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.tokenSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData tokenHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.tokenHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData myTokens(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.myTokensPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData tokenHolderByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.tokenHolderByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData proofByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.proofByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData proofSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.proofSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData proofHistory(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.proofHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData statementByIds(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.statementByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData statementSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.statementSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData nidSearch(Fcdsl fcdsl, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = PublishAPIs.nidSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData broadcastTx(String txHex, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.broadcastTxPost(apiAccount.getApiUrl(), txHex,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData decodeRawTx(String rawTxHex, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.decodeRawTxPost(apiAccount.getApiUrl(), rawTxHex,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData cashValidForPay(String fid, double amount,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.cashValidForPayPost(apiAccount.getApiUrl(), fid,amount,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData cashValidForCd(String fid, int cd,  HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.cashValidForCdPost(apiAccount.getApiUrl(), fid,cd,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }
    public ApipClientData unconfirmedPost(String[] ids, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = WalletAPIs.unconfirmedPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData swapRegister(String sid, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = SwapHallAPIs.swapRegisterPost(apiAccount.getApiUrl(), sid,apiAccount.getVia(),sessionKey);
            default -> apipClientData.set1017NoSuchMethod();
        }
        return apipClientData;
    }

    public ApipClientData swapUpdate(Map<String, Object> uploadMap, HttpMethods httpMethods){
        switch (httpMethods) {
            case POST -> apipClientData = SwapHallAPIs.swapUpdatePost(apiAccount.getApiUrl(), uploadMap,apiAccount.getVia(),sessionKey);
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
}
