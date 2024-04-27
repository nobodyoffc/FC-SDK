package clients.apipClient;

import APIP.apipData.*;
import APIP.apipData.TxInfo;
import FCH.Inputer;
import FCH.ParseTools;
import FCH.fchData.*;
import FEIP.feipClient.IdentityFEIPs;
import FEIP.feipData.*;
import FEIP.feipData.serviceParams.ApipParams;
import appTools.swapClass.SwapAffair;
import appTools.swapClass.SwapLpData;
import appTools.swapClass.SwapPriceData;
import appTools.swapClass.SwapStateData;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import constants.ApiNames;
import constants.FieldNames;
import constants.ReplyInfo;
import crypto.cryptoTools.Hash;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
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
    private Gson gson = new Gson();

    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey) {
        this.apiAccount = apiAccount;
        this.sessionKey = apiAccount.getSessionKey();
        this.apiProvider = apiProvider;
        this.symKey = symKey;
    }

    public static void checkApipBalance(ApiAccount apipAccount, final ApipClientData apipClientData, byte[] initSymKey) {
        if(apipClientData ==null|| apipClientData.getResponseBody()==null)return;
        if(apipClientData.getResponseBody().getCode()!=0)return;

        String priceStr;
        if(apipAccount.getApipParams().getPricePerKBytes()==null)priceStr=apipAccount.getApipParams().getPricePerRequest();
        else priceStr =apipAccount.getApipParams().getPricePerKBytes();
        long price = ParseTools.fchStrToSatoshi(priceStr);

        long balance = apipClientData.getResponseBody().getBalance();
        apipAccount.setBalance(balance);

        if(balance < price * ApiAccount.minRequestTimes){
            byte[] priKey = EccAes256K1P7.decryptJsonBytes
                    (
                            apipAccount.getUserPriKeyCipher(),
                            initSymKey
                    );
            double topUp = apipAccount.buyApip(apipAccount.getApiUrl(), priKey);
            if(topUp==0){
                log.debug("Failed to buy APIP service.");
                return;
            }
            apipAccount.setBalance(balance + ParseTools.coinToSatoshi(topUp));
        }
    }

    public Object checkApipResult(String taskName){
        if(apipClientData ==null)return null;

        if(apipClientData.getCode()!= ReplyInfo.Code0Success) {
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
            if (apipClientData.getCode() == ReplyInfo.Code1004InsufficientBalance) {
                apiAccount.buyApip(symKey);
                return null;
            }

            if (apipClientData.getCode() == ReplyInfo.Code1002SessionNameMissed || apipClientData.getCode() == ReplyInfo.Code1009SessionTimeExpired) {
                apiAccount.freshSessionKey(symKey, null);
                if (sessionKey == null) {
                    return null;
                }
            }
            return null;
        }
        checkApipBalance(apiAccount, apipClientData, symKey);
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
                if (getCashesFree(apiAccount.getApiUrl(), fid) == null) return;
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
    public List<Cash> getCashesFree(String apiUrl, String fid) {
        apipClientData = FreeGetAPIs.getCashes(apiUrl, fid, 0);
        Object data = checkApipResult("get cashes");
        return ApipDataGetter.getCashList(data);
    }

    @Nullable


    public Service getApipServiceFree(String urlHead){
        if(urlHead.contains(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI))
            urlHead.replaceAll(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI,"");

        apipClientData = OpenAPIs.getService(urlHead);

        Object data = checkApipResult("get block info by heights");
        if(data==null)return null;

        Service service = gson.fromJson(gson.toJson(data),Service.class);
        ApipParams.getParamsFromService(service,ApipParams.class);
        return service;
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
        if(data==null)return null;
        return ApipDataGetter.getAddressMap(data).get(fid).getPubKey();
    }

    public Map<String, BlockInfo> blockByIds(String[] ids){
        apipClientData = BlockchainAPIs.blockByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
        Object data = checkApipResult("get block info by heights");
        if(data==null)return null;
        return ApipDataGetter.getBlockInfoMap(data);
    }

    public Map<String, BlockInfo> blockByHeights(String[] heights){
        apipClientData = BlockchainAPIs.blockByHeightPost(apiAccount.getApiUrl(), heights, apiAccount.getVia(), sessionKey);
        Object data = checkApipResult("get block info by heights");
        if(data==null)return null;
        return ApipDataGetter.getBlockInfoMap(data);
    }

    public List<BlockInfo> blockSearch(Fcdsl fcdsl){
        apipClientData = BlockchainAPIs.blockSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search block");
        if(data==null)return null;
        return ApipDataGetter.getBlockInfoList(data);
    }

    public List<Cash> cashValid(Fcdsl fcdsl){
       apipClientData = BlockchainAPIs.cashValidPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search valid cash");
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }

    public Map<String, Cash> cashByIds(String[] ids){
        apipClientData = BlockchainAPIs.cashByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
        Object data = checkApipResult("get cash by IDs");
        if(data==null)return null;
        return ApipDataGetter.getCashMap(data);
    }

    public List<Utxo> getUtxo(String id, double amount){
        apipClientData = BlockchainAPIs.getUtxo(apiAccount.getApiUrl(), id,amount);
        Object data = checkApipResult("get UTXO list");
        if(data==null)return null;
        return ApipDataGetter.getUtxoList(data);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl){
        apipClientData = BlockchainAPIs.cashSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search cash");
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }

    public Map<String, Address> fidByIds(String[] ids){
        apipClientData = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("get FIDs");
        if(data==null)return null;
        return ApipDataGetter.getAddressMap(data);
    }

    public Map<String, OpReturn> opReturnByIds(String[] ids){
        apipClientData =  BlockchainAPIs.opReturnByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("get OP_RETURN by IDs");
        if(data==null)return null;
        return ApipDataGetter.getOpReturnMap(data);
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl){
        apipClientData = BlockchainAPIs.opReturnSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search OP_RETURN");
        if(data==null)return null;
        return ApipDataGetter.getOpReturnList(data);
    }

    public Map<String, P2SH> p2shByIds(String[] ids){
        apipClientData = BlockchainAPIs.p2shByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("get P2SH info by IDs");
        if(data==null)return null;
        return ApipDataGetter.getP2SHMap(data);
    }

    public List<P2SH> p2shSearch(Fcdsl fcdsl){
        apipClientData = BlockchainAPIs.p2shSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search P2SH");
        if(data==null)return null;
        return ApipDataGetter.getP2SHList(data);
    }

    public List<TxInfo> txSearch(Fcdsl fcdsl){
        apipClientData = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search TX");
        if(data==null)return null;
        return ApipDataGetter.getTxInfoList(data);
    }

    public Map<String, TxInfo> txByIds(String[] ids){
        apipClientData = BlockchainAPIs.txByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("get TX by IDs");
        if(data==null)return null;
        return ApipDataGetter.getTxInfoMap(data);
    }

    public List<TxInfo> txByFid(String fid, String[] last){
        Fcdsl fcdsl = BlockchainAPIs.txByFidQuery(fid,last);
        apipClientData = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("get TX by FID");
        if(data==null)return null;
        return ApipDataGetter.getTxInfoList(data);
    }

    public Map<String, Protocol> protocolByIds(String[] ids){
        apipClientData = ConstructAPIs.protocolByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("get protocol by IDs");
        if(data==null)return null;
        return ApipDataGetter.getProtocolMap(data);
    }
    public List<Protocol> protocolSearch(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.protocolSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search protocol");
        if(data==null)return null;
        return ApipDataGetter.getProtocolList(data);
    }

    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.protocolOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search protocol operation history");
        if(data==null)return null;
        return ApipDataGetter.getProtocolHistoryList(data);
    }


    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.protocolRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search protocol operation history");
        if(data==null)return null;
        return ApipDataGetter.getProtocolHistoryList(data);
    }

    public Map<String, Code> codeByIds(String[] ids){
        apipClientData = ConstructAPIs.codeByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("get code by IDs");
        if(data==null)return null;
        return ApipDataGetter.getCodeMap(data);
    }
    public List<Code> codeSearch(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.codeSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search code");
        if(data==null)return null;
        return ApipDataGetter.getCodeList(data);
    }

    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.codeOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search code operation history");
        if(data==null)return null;
        return ApipDataGetter.getCodeHistoryList(data);
    }


    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.codeRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("search code rate history");
        if(data==null)return null;
        return ApipDataGetter.getCodeHistoryList(data);
    }


    public Map<String, Service> serviceMapByIds(String[] ids){
        apipClientData = ConstructAPIs.serviceByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("serviceByIds");
        if(data==null)return null;
        return ApipDataGetter.getServiceMap(data);
    }

    public Service serviceById(String id){
        Map<String, Service> map = serviceMapByIds(new String[]{id});
        if(map==null)return null;
        return map.get(id);
    }
    public List<Service> serviceSearch(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.serviceSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("serviceSearch");
        if(data==null)return null;
        return ApipDataGetter.getServiceList(data);
    }

    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.serviceOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("serviceOpHistory");
        if(data==null)return null;
        return ApipDataGetter.getServiceHistoryList(data);
    }


    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.serviceRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("serviceRateHistory");
        if(data==null)return null;
        return ApipDataGetter.getServiceHistoryList(data);
    }


    public Map<String, App> appByIds(String[] ids){
        apipClientData = ConstructAPIs.appByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("appByIds");
        if(data==null)return null;
        return ApipDataGetter.getAppMap(data);
    }
    public List<App> appSearch(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.appSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("appSearch");
        if(data==null)return null;
        return ApipDataGetter.getAppList(data);
    }

    public List<AppHistory> appOpHistory(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.appOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("appOpHistory");
        if(data==null)return null;
        return ApipDataGetter.getAppHistoryList(data);
    }


    public List<AppHistory> appRateHistory(Fcdsl fcdsl){
        apipClientData = ConstructAPIs.appRateHistoryPost(apiAccount.getApiUrl(),fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("appRateHistory");
        if(data==null)return null;
        return ApipDataGetter.getAppHistoryList(data);
    }

    public Map<String, String> addresses(String addrOrPubKey){
        apipClientData = CryptoToolAPIs.addressesPost(apiAccount.getApiUrl(), addrOrPubKey,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("addresses");
        if(data==null)return null;
        return ApipDataGetter.getStringMap(data);
    }
    public String encrypt(String key, String message){
        apipClientData = CryptoToolAPIs.encryptPost(apiAccount.getApiUrl(), key,message,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("encrypt");
        if(data==null)return null;
        return (String) data;
    }
    public boolean verify(String signature){
        apipClientData = CryptoToolAPIs.verifyPost(apiAccount.getApiUrl(), signature, apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("verify");
        if(data==null)return false;
        return (boolean) data;
    }
    public String sha256(String text){
        apipClientData = CryptoToolAPIs.sha256Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("sha256");
        if(data==null)return null;
        return (String) data;
    }
    public String sha256x2(String text){
        apipClientData = CryptoToolAPIs.sha256x2Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("sha256x2");
        if(data==null)return null;
        return (String) data;
    }
    public String sha256Bytes(String hex){
        apipClientData = CryptoToolAPIs.sha256BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("sha256Bytes");
        if(data==null)return null;
        return (String) data;
    }

    public String sha256x2Bytes(String hex){
        apipClientData = CryptoToolAPIs.sha256x2BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("sha256x2Bytes");
        if(data==null)return null;
        return (String) data;
    }

    public String offLineTx(String fromFid, List<SendTo> sendToList, String msg){
        apipClientData = CryptoToolAPIs.offLineTxPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("offLineTx");
        if(data==null)return null;
        return (String) data;
    }

    public String offLineTxByCd(String fromFid, List<SendTo> sendToList, String msg, int cd){
        apipClientData = CryptoToolAPIs.offLineTxByCdPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,cd,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("offLineTxByCd");
        if(data==null)return null;
        return (String) data;
    }

    public List<App> getAppsFree(String id){
        apipClientData = FreeGetAPIs.getApps(apiAccount.getApiUrl(), id);
        Object data = checkApipResult("getAppsFree");
        if(data==null)return null;
        return ApipDataGetter.getAppList(data);
    }

    public List<Service> getServicesFree(String id){
        apipClientData = FreeGetAPIs.getServices(apiAccount.getApiUrl(), id);
        Object data = checkApipResult("getServicesFree");
        if(data==null)return null;
        return ApipDataGetter.getServiceList(data);
    }
//TODO unchecked
    public Object getAvatarFree(String fid){
        apipClientData = FreeGetAPIs.getAvatar(apiAccount.getApiUrl(), fid);
        Object data = checkApipResult("getServicesFree");
        return data;
    }


    public String broadcastFree(String txHex){
        apipClientData = FreeGetAPIs.broadcast(apiAccount.getApiUrl(),txHex);
        Object data = checkApipResult("broadcast raw tx");
        if(data==null)return null;
        return (String)data;
    }

    public String broadcastRawTx(String txHex){
        apipClientData = WalletAPIs.broadcastTxPost(apiAccount.getApiUrl(),txHex, apiAccount.getVia(), sessionKey);
        Object data = checkApipResult("broadcast raw tx");
        if(data==null)return null;
        return (String)data;
    }

    public List<Cash> getCashesFree(String id, double amount){
        apipClientData = FreeGetAPIs.getCashes(apiAccount.getApiUrl(),id,amount);
        Object data = checkApipResult("get cashes by IDs");
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }

    public CidInfo getFidCid(String id){
        apipClientData = FreeGetAPIs.getFidCid(apiAccount.getApiUrl(), id);
        Object data = checkApipResult("get cashes by IDs");
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), CidInfo.class);
    }

    public ApipClientData getTotals(){
        return FreeGetAPIs.getTotals(apiAccount.getApiUrl());
    }

    public Map<String, CidInfo> cidInfoByIds(String[] ids){
        apipClientData = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("cidInfoByIds");
        if(data==null)return null;
        return ApipDataGetter.getCidInfoMap(data);
    }
    public List<CidInfo> cidInfoSearch(Fcdsl fcdsl){
        apipClientData = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("cidInfoSearch");
        if(data==null)return null;
        return ApipDataGetter.getCidInfoList(data);
    }
    public List<CidInfo> cidInfoSearch(String searchStr){
        apipClientData = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("cidInfoSearch");
        if(data==null)return null;
        return ApipDataGetter.getCidInfoList(data);
    }

    public List<CidHist> cidHistory(Fcdsl fcdsl){
        apipClientData = IdentityAPIs.cidHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("cidHistory");
        if(data==null)return null;
        return ApipDataGetter.getCidHistoryList(data);
    }

    public List<CidHist> homepageHistory(Fcdsl fcdsl){
        apipClientData = IdentityAPIs.homepageHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("homepageHistory");
        if(data==null)return null;
        return ApipDataGetter.getCidHistoryList(data);
    }

    public List<CidHist> noticeFeeHistory(Fcdsl fcdsl){
        apipClientData = IdentityAPIs.noticeFeeHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("noticeFeeHistory");
        if(data==null)return null;
        return ApipDataGetter.getCidHistoryList(data);
    }

    public List<CidHist> reputationHistory(Fcdsl fcdsl){
        apipClientData = IdentityAPIs.reputationHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("reputationHistory");
        if(data==null)return null;
        return ApipDataGetter.getCidHistoryList(data);
    }

    public Map<String, String[]> fidCidSeek(Fcdsl fcdsl){
        apipClientData = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("fidCidSeek");
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> fidCidSeek(String searchStr){
        apipClientData = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("fidCidSeek");
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> fidCidGetFree(String id){
        apipClientData = IdentityAPIs.fidCidGetFree(apiAccount.getApiUrl(), id);
        Object data = checkApipResult("fidCidGetFree");
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public Map<String, Nobody> nobodyByIds(String[] ids){
        apipClientData = IdentityAPIs.nobodyByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("nobodyByIds");
        if(data==null)return null;
        return ApipDataGetter.getNobodyMap(data);
    }
    public List<Nobody> nobodySearch(Fcdsl fcdsl){
        apipClientData = IdentityAPIs.nobodySearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("nobodySearch");
        if(data==null)return null;
        return ApipDataGetter.getNobodyList(data);
    }

    public Map<String, String> avatars(String[] ids){
        apipClientData = IdentityAPIs.avatarsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("avatars");
        if(data==null)return null;
        return ApipDataGetter.getStringMap(data);
    }

    public Service getServiceFree(){
        apipClientData = OpenAPIs.getService(apiAccount.getApiUrl());
        Object data = checkApipResult("getServiceFree");
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data),Service.class);
    }

    public Session signIn(byte[] priKey, RequestBody.SignInMode mode_RefreshOrNull){
        apipClientData = OpenAPIs.signInPost(apiAccount.getApiUrl(),apiAccount.getVia(),priKey,mode_RefreshOrNull);
        Object data = checkApipResult("signIn");
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }

    public Session signInEcc(byte[] priKey, RequestBody.SignInMode mode){
        apipClientData = OpenAPIs.signInEccPost(apiAccount.getApiUrl(), apiAccount.getVia(), priKey,mode);
        Object data = checkApipResult("signInEcc");
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }

    public Map<String, String> totals(){
        apipClientData = OpenAPIs.totalsPost(apiAccount.getApiUrl(),apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("totals");
        if(data==null)return null;
        return ApipDataGetter.getStringMap(data);
    }
    public Map<String, String> totalsFree() {
        apipClientData = OpenAPIs.totalsGet(apiAccount.getApiUrl());
        Object data = checkApipResult("totalsFree");
        if (data == null) return null;
        return ApipDataGetter.getStringMap(data);
    }

    public Object general(String index, Fcdsl fcdsl){
        apipClientData = OpenAPIs.generalPost(index,apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        return checkApipResult("general");
    }

    public Map<String, Group> groupByIds(String[] ids){
        apipClientData = OrganizeAPIs.groupByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("groupByIds");
        if(data==null)return null;
        return ApipDataGetter.getGroupMap(data);
    }
    public List<Group> groupSearch(Fcdsl fcdsl){
        apipClientData = OrganizeAPIs.groupSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("groupSearch");
        if(data==null)return null;
        return ApipDataGetter.getGroupList(data);
    }

    public List<GroupHistory> groupOpHistory(Fcdsl fcdsl){
        apipClientData = OrganizeAPIs.groupOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("groupOpHistory");
        if(data==null)return null;
        return ApipDataGetter.getGroupHistoryList(data);
    }
    public Map<String, String[]> groupMembers(String[] ids){
        apipClientData = OrganizeAPIs.groupMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("groupMembers");
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public List<MyGroupData> myGroups(String fid){
        apipClientData = OrganizeAPIs.myGroupsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("myGroups");
        if(data==null)return null;
        return ApipDataGetter.getMyGroupList(data);
    }


    public Map<String, Team> teamByIds(String[] ids){
        apipClientData = OrganizeAPIs.teamByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("teamByIds");
        if(data==null)return null;
        return ApipDataGetter.getTeamMap(data);
    }
    public List<Team> teamSearch(Fcdsl fcdsl){
        apipClientData = OrganizeAPIs.teamSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("teamSearch");
        if(data==null)return null;
        return ApipDataGetter.getTeamList(data);
    }

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl){
        apipClientData = OrganizeAPIs.teamOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("teamOpHistory");
        if(data==null)return null;
        return ApipDataGetter.getTeamHistoryList(data);
    }

    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl){
        apipClientData = OrganizeAPIs.teamRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("teamRateHistory");
        if(data==null)return null;
        return ApipDataGetter.getTeamHistoryList(data);
    }
    public Map<String, String[]> teamMembers(String[] ids){
        apipClientData = OrganizeAPIs.teamMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("teamMembers");
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> teamExMembers(String[] ids){
        apipClientData = OrganizeAPIs.teamExMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("teamMembers");
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }
    public TeamOtherPersonsData teamOtherPersons(String[] ids){
        apipClientData = OrganizeAPIs.teamOtherPersonsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("teamOtherPersons");
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), TeamOtherPersonsData.class);
    }
    public List<MyTeamData> myTeams(String fid){
        apipClientData = OrganizeAPIs.myTeamsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("myTeams");
        if(data==null)return null;
        return ApipDataGetter.getMyTeamList(data);
    }


    public Map<String, Box> boxByIds(String[] ids){
        apipClientData = PersonalAPIs.boxByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("boxByIds");
        if(data==null)return null;
        return ApipDataGetter.getBoxMap(data);
    }
    public List<Box> boxSearch(Fcdsl fcdsl){
        apipClientData = PersonalAPIs.boxSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("boxSearch");
        if(data==null)return null;
        return ApipDataGetter.getBoxList(data);
    }

    public List<BoxHistory> boxHistory(Fcdsl fcdsl){
        apipClientData = PersonalAPIs.boxHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("boxSearch");
        if(data==null)return null;
        return ApipDataGetter.getBoxHistoryList(data);
    }

    public Map<String, Contact> contactByIds(String[] ids){
        apipClientData = PersonalAPIs.contactByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("contactByIds");
        if(data==null)return null;
        return ApipDataGetter.getContactMap(data);
    }
    public List<Contact> contacts(Fcdsl fcdsl){
        apipClientData = PersonalAPIs.contactsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("contacts");
        if(data==null)return null;
        return ApipDataGetter.getContactList(data);
    }

    public List<Contact> contactsDeleted(Fcdsl fcdsl){
        apipClientData = PersonalAPIs.contactsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("contactsDeleted");
        if(data==null)return null;
        return ApipDataGetter.getContactList(data);
    }

    public Map<String, Mail> mailByIds(String[] ids){
        apipClientData = PersonalAPIs.mailByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("mailByIds");
        if(data==null)return null;
        return ApipDataGetter.getMailMap(data);
    }
    public List<Mail> mails(Fcdsl fcdsl){
        apipClientData = PersonalAPIs.mailsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("mails");
        if(data==null)return null;
        return ApipDataGetter.getMailList(data);
    }

    public List<Mail> mailsDeleted(Fcdsl fcdsl){
        apipClientData = PersonalAPIs.mailsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("mailsDeleted");
        if(data==null)return null;
        return ApipDataGetter.getMailList(data);
    }
    public Map<String, Secret> secretByIds(String[] ids){
        apipClientData = PersonalAPIs.secretByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("secretByIds");
        if(data==null)return null;
        return ApipDataGetter.getSecretMap(data);
    }
    public List<Secret> secrets(Fcdsl fcdsl){
        apipClientData = PersonalAPIs.secretsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("secrets");
        if(data==null)return null;
        return ApipDataGetter.getSecretList(data);
    }

    public List<Secret> secretsDeleted(Fcdsl fcdsl){
        apipClientData = PersonalAPIs.secretsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("secretsDeleted");
        if(data==null)return null;
        return ApipDataGetter.getSecretList(data);
    }

    public Map<String, Token> tokenByIds(String[] ids){
        apipClientData = PublishAPIs.tokenByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("tokenByIds");
        if(data==null)return null;
        return ApipDataGetter.getTokenMap(data);
    }
    public List<Token> tokenSearch(Fcdsl fcdsl){
        apipClientData = PublishAPIs.tokenSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("tokenSearch");
        if(data==null)return null;
        return ApipDataGetter.getTokenList(data);
    }

    public List<TokenHistory> tokenHistory(Fcdsl fcdsl){
        apipClientData = PublishAPIs.tokenHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("tokenHistory");
        if(data==null)return null;
        return ApipDataGetter.getTokenHistoryList(data);
    }
    public List<TokenHolder> myTokens(Fcdsl fcdsl){
        apipClientData = PublishAPIs.myTokensPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("myTokens");
        if(data==null)return null;
        return ApipDataGetter.getTokenHolderList(data);
    }
    public Map<String, TokenHolder> tokenHolderByIds(String[] ids){
        apipClientData = PublishAPIs.tokenHolderByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("tokenHolderByIds");
        if(data==null)return null;
        return ApipDataGetter.getTokenHolderMap(data);
    }

    public Map<String, Proof> proofByIds(String[] ids){
        apipClientData = PublishAPIs.proofByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("proofByIds");
        if(data==null)return null;
        return ApipDataGetter.getProofMap(data);
    }
    public List<Proof> proofSearch(Fcdsl fcdsl){
        apipClientData =  PublishAPIs.proofSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("proofSearch");
        if(data==null)return null;
        return ApipDataGetter.getProofList(data);
    }

    public List<ProofHistory> proofHistory(Fcdsl fcdsl){
        apipClientData = PublishAPIs.proofHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("proofHistory");
        if(data==null)return null;
        return ApipDataGetter.getProofHistoryList(data);
    }

    public Map<String, Statement> statementByIds(String[] ids){
        apipClientData =  PublishAPIs.statementByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("statementByIds");
        if(data==null)return null;
        return ApipDataGetter.getStatementMap(data);
    }
    public List<Statement> statementSearch(Fcdsl fcdsl){
        apipClientData = PublishAPIs.statementSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("statementSearch");
        if(data==null)return null;
        return ApipDataGetter.getStatementList(data);
    }

    public List<Nid> nidSearch(Fcdsl fcdsl){
        apipClientData = PublishAPIs.nidSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("nidSearch");
        if(data==null)return null;
        return ApipDataGetter.getNidList(data);
    }

    public String decodeRawTx(String rawTxHex){
        apipClientData = WalletAPIs.decodeRawTxPost(apiAccount.getApiUrl(), rawTxHex,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("decodeRawTx");
        if(data==null)return null;
        return (String)data;
    }

    public List<Cash> cashValidForPay(String fid, double amount){
        apipClientData = WalletAPIs.cashValidForPayPost(apiAccount.getApiUrl(), fid,amount,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("cashValidForPay");
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }

    public List<Cash> cashValidForCd(String fid, int cd){
        apipClientData = WalletAPIs.cashValidForCdPost(apiAccount.getApiUrl(), fid,cd,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("cashValidForCd");
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }
    public List<UnconfirmedInfo> unconfirmed(String[] ids){
        apipClientData = WalletAPIs.unconfirmedPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("unconfirmed");
        if(data==null)return null;
        return ApipDataGetter.getUnconfirmedList(data);
    }

    public String swapRegister(String sid){
        apipClientData = SwapHallAPIs.swapRegisterPost(apiAccount.getApiUrl(), sid,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("swapRegister");
        if(data==null)return null;
        return (String)data;
    }

    public List<String> swapUpdate(Map<String, Object> uploadMap){
        apipClientData = SwapHallAPIs.swapUpdatePost(apiAccount.getApiUrl(), uploadMap,apiAccount.getVia(),sessionKey);
        Object data = checkApipResult("swapUpdate");
        if(data==null)return null;
        return ApipDataGetter.getStringList(data);
    }
    public SwapStateData swapState(String sid, String[] last){
        apipClientData = SwapHallAPIs.getSwapState(apiAccount.getApiUrl(), sid);
        Object data = checkApipResult("swapState");
        if(data==null)return null;
        try{
            return gson.fromJson(gson.toJson(data),SwapStateData.class);
        }catch (Exception e){
            apipClientData.setMessage(apipClientData.getMessage()+(String)data);
            return null;
        }
    }

    public SwapLpData swapLp(String sid, String[] last){
        apipClientData = SwapHallAPIs.getSwapLp(apiAccount.getApiUrl(), sid);
        Object data = checkApipResult("swapLp");
        if(data==null)return null;
        try{
            return gson.fromJson(gson.toJson(data),SwapLpData.class);
        }catch (Exception e){
            apipClientData.setMessage(apipClientData.getMessage()+(String)data);
            return null;
        }
    }

    public List<SwapAffair> swapFinished(String sid, String[] last){
        apipClientData = SwapHallAPIs.getSwapFinished(apiAccount.getApiUrl(), sid,last);
        Object data = checkApipResult("swapFinished");
        if(data==null)return null;
        return ApipDataGetter.getSwapAffairList(data);
    }

    public List<SwapAffair> swapPending(String sid){
        apipClientData = SwapHallAPIs.getSwapPending(apiAccount.getApiUrl(), sid);
        Object data = checkApipResult("swapPending");
        if(data==null)return null;
        return ApipDataGetter.getSwapAffairList(data);
    }

    public List<SwapPriceData> swapPrices(String sid, String gTick, String mTick, String[] last){
        apipClientData = SwapHallAPIs.getSwapPrice(apiAccount.getApiUrl(), sid,gTick,mTick,last);
        Object data = checkApipResult("swapPrices");
        if(data==null)return null;
        return ApipDataGetter.getSwapPriceDataList(data);
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

    public List<Service> getServiceListByOwner(String owner) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(owner);
        serviceList = serviceSearch(fcdsl);
        return serviceList;
    }

    public List<Service> getServiceListByType(String type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPES).addNewValues(type);
        serviceList = serviceSearch(fcdsl);
        return serviceList;
    }
}
