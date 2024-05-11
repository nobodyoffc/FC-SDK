package clients.apipClient;

import APIP.apipData.*;
import APIP.apipData.TxInfo;
import FCH.Inputer;
import FCH.fchData.*;
import FEIP.feipClient.IdentityFEIPs;
import FEIP.feipData.*;
import FEIP.feipData.serviceParams.ApipParams;
import appTools.swapClass.SwapAffair;
import appTools.swapClass.SwapLpData;
import appTools.swapClass.SwapPriceData;
import appTools.swapClass.SwapStateData;
import clients.Client;
import config.ApiAccount;
import config.ApiProvider;
import config.ApiType;
import constants.ApiNames;
import constants.FieldNames;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.Hex;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;

import static config.ApiAccount.decryptHexWithPriKey;
import static crypto.cryptoTools.KeyTools.priKeyToFid;

public class ApipClient extends Client {
//    private static final Logger log = LoggerFactory.getLogger(ApipClient.class);
//    private ApiProvider apiProvider;
//    private ApiAccount apiAccount;
//    private ClientData clientData;
//    private byte[] symKey;
//    private byte[] sessionKey;
//    private Fcdsl fcdsl;
//    private Gson gson = new Gson();

    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey){
        super(apiProvider,apiAccount,symKey);
        this.signInUrlTailPath=ApiNames.APIP0V1Path;
    }

//    public static void checkBalance(ApiAccount apipAccount, final ClientData apipClientData, byte[] initSymKey) {
//        if(apipClientData ==null|| apipClientData.getResponseBody()==null)return;
//        if(apipClientData.getResponseBody().getCode()!=0)return;
//
//        String priceStr;
//        if(apipAccount.getApipParams().getPricePerKBytes()==null)priceStr=apipAccount.getApipParams().getPricePerRequest();
//        else priceStr =apipAccount.getApipParams().getPricePerKBytes();
//        long price = ParseTools.fchStrToSatoshi(priceStr);
//
//        if(apipClientData.getResponseBody().getBalance()==null)return;
//
//        Long balance = apipClientData.getResponseBody().getBalance();
//        if(balance==null)return;
//        apipAccount.setBalance(balance);
//
//        if(balance!=0 && balance < price * ApiAccount.minRequestTimes){
//            double topUp = apipAccount.buyApi(initSymKey);
//            if(topUp==0){
//                log.debug("Failed to buy APIP service.");
//                return;
//            }
//            apipAccount.setBalance(balance + ParseTools.coinToSatoshi(topUp));
//        }
//    }

    public Object checkApipV1Result(){
        return checkResult(ApiType.APIP);
//        if(clientData ==null)return null;
//
//        if(clientData.getCode()!= ReplyInfo.Code0Success) {
//            System.out.println("Failed to " + taskName);
//            if (clientData.getResponseBody()== null) {
//                System.out.println("ResponseBody is null when requesting "+this.clientData.getUrl());
//                System.out.println(clientData.getMessage());
//            } else {
//                System.out.println(clientData.getResponseBody().getCode() + ":" + clientData.getResponseBody().getMessage());
//                if (clientData.getResponseBody().getData() != null)
//                    System.out.println(JsonTools.getString(clientData.getResponseBody().getData()));
//            }
//            log.debug(clientData.getMessage());
//            if (clientData.getCode() == ReplyInfo.Code1004InsufficientBalance) {
//                apiAccount.buyApi(symKey);
//                return null;
//            }
//
//            if (clientData.getCode() == ReplyInfo.Code1002SessionNameMissed || clientData.getCode() == ReplyInfo.Code1009SessionTimeExpired) {
//                apiAccount.freshSessionKey(symKey, ApiNames.APIP0V1Path,ApiType.APIP, null);
//                if (sessionKey == null) {
//                    return null;
//                }
//            }
//            return null;
//        }
//        checkBalance(apiAccount, clientData, symKey);
//        return clientData.getResponseBody().getData();
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
            if (Inputer.askIfYes(br, "Stop to send?")) System.exit(0);
        }
        if (cidInfo != null) {
            if (cidInfo.getMaster() != null) {
                System.out.println("The master of "+fid+" is " + cidInfo.getMaster());
                return;
            }
            if (Inputer.askIfYes(br, "Assign the master for " + fid + "?")) {
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
        clientData = FreeGetAPIs.getCashes(apiUrl, fid, 0);
        Object data = checkApipV1Result();
        return ApipDataGetter.getCashList(data);
    }

    @Nullable


    public Service getApipServiceFree(String urlHead){
        if(urlHead.contains(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI))
            urlHead.replaceAll(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI,"");

        clientData = OpenAPIs.getService(urlHead);

        Object data = checkApipV1Result();
        if(data==null)return null;

        Service service = gson.fromJson(gson.toJson(data),Service.class);
        ApipParams.getParamsFromService(service,ApipParams.class);
        return service;
    }

    public CidInfo getCidInfo(String fid, ApiAccount apiAccount) {
        clientData = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), new String[]{fid}, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCidInfoMap(data).get(fid);
    }

    public String getPubKey(String fid) {
        clientData = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), new String[]{fid}, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getAddressMap(data).get(fid).getPubKey();
    }

    public Map<String, BlockInfo> blockByIds(String[] ids){
        clientData = BlockchainAPIs.blockByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getBlockInfoMap(data);
    }

    public Map<String, BlockInfo> blockByHeights(String[] heights){
        clientData = BlockchainAPIs.blockByHeightPost(apiAccount.getApiUrl(), heights, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getBlockInfoMap(data);
    }

    public List<BlockInfo> blockSearch(Fcdsl fcdsl){
        clientData = BlockchainAPIs.blockSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getBlockInfoList(data);
    }

    public List<Cash> cashValid(Fcdsl fcdsl){
       clientData = BlockchainAPIs.cashValidPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }

    public Map<String, Cash> cashByIds(String[] ids){
        clientData = BlockchainAPIs.cashByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCashMap(data);
    }

    public List<Utxo> getUtxo(String id, double amount){
        clientData = BlockchainAPIs.getUtxo(apiAccount.getApiUrl(), id,amount);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getUtxoList(data);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl){
        clientData = BlockchainAPIs.cashSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }

    public Map<String, Address> fidByIds(String[] ids){
        clientData = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getAddressMap(data);
    }

    public Map<String, OpReturn> opReturnByIds(String[] ids){
        clientData =  BlockchainAPIs.opReturnByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getOpReturnMap(data);
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl){
        clientData = BlockchainAPIs.opReturnSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getOpReturnList(data);
    }

    public Map<String, P2SH> p2shByIds(String[] ids){
        clientData = BlockchainAPIs.p2shByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getP2SHMap(data);
    }

    public List<P2SH> p2shSearch(Fcdsl fcdsl){
        clientData = BlockchainAPIs.p2shSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getP2SHList(data);
    }

    public List<TxInfo> txSearch(Fcdsl fcdsl){
        clientData = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTxInfoList(data);
    }

    public Map<String, TxInfo> txByIds(String[] ids){
        clientData = BlockchainAPIs.txByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTxInfoMap(data);
    }

    public List<TxInfo> txByFid(String fid, String[] last){
        Fcdsl fcdsl = BlockchainAPIs.txByFidQuery(fid,last);
        clientData = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTxInfoList(data);
    }

    public Map<String, Protocol> protocolByIds(String[] ids){
        clientData = ConstructAPIs.protocolByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getProtocolMap(data);
    }
    public List<Protocol> protocolSearch(Fcdsl fcdsl){
        clientData = ConstructAPIs.protocolSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getProtocolList(data);
    }

    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl){
        clientData = ConstructAPIs.protocolOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getProtocolHistoryList(data);
    }


    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl){
        clientData = ConstructAPIs.protocolRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getProtocolHistoryList(data);
    }

    public Map<String, Code> codeByIds(String[] ids){
        clientData = ConstructAPIs.codeByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCodeMap(data);
    }
    public List<Code> codeSearch(Fcdsl fcdsl){
        clientData = ConstructAPIs.codeSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCodeList(data);
    }

    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl){
        clientData = ConstructAPIs.codeOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCodeHistoryList(data);
    }


    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl){
        clientData = ConstructAPIs.codeRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCodeHistoryList(data);
    }


    public Map<String, Service> serviceMapByIds(String[] ids){
        clientData = ConstructAPIs.serviceByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getServiceMap(data);
    }

    public Service serviceById(String id){
        Map<String, Service> map = serviceMapByIds(new String[]{id});
        if(map==null)return null;
        return map.get(id);
    }
    public List<Service> serviceSearch(Fcdsl fcdsl){


        clientData = ConstructAPIs.serviceSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getServiceList(data);
    }

    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl){
        clientData = ConstructAPIs.serviceOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getServiceHistoryList(data);
    }


    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl){
        clientData = ConstructAPIs.serviceRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getServiceHistoryList(data);
    }


    public Map<String, App> appByIds(String[] ids){
        clientData = ConstructAPIs.appByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getAppMap(data);
    }
    public List<App> appSearch(Fcdsl fcdsl){
        clientData = ConstructAPIs.appSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getAppList(data);
    }

    public List<AppHistory> appOpHistory(Fcdsl fcdsl){
        clientData = ConstructAPIs.appOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getAppHistoryList(data);
    }


    public List<AppHistory> appRateHistory(Fcdsl fcdsl){
        clientData = ConstructAPIs.appRateHistoryPost(apiAccount.getApiUrl(),fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getAppHistoryList(data);
    }

    public Map<String, String> addresses(String addrOrPubKey){
        clientData = CryptoToolAPIs.addressesPost(apiAccount.getApiUrl(), addrOrPubKey,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringMap(data);
    }
    public String encrypt(String key, String message){
        clientData = CryptoToolAPIs.encryptPost(apiAccount.getApiUrl(), key,message,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }
    public boolean verify(String signature){
        clientData = CryptoToolAPIs.verifyPost(apiAccount.getApiUrl(), signature, apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return false;
        return (boolean) data;
    }
    public String sha256(String text){
        clientData = CryptoToolAPIs.sha256Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }
    public String sha256x2(String text){
        clientData = CryptoToolAPIs.sha256x2Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }
    public String sha256Bytes(String hex){
        clientData = CryptoToolAPIs.sha256BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }

    public String sha256x2Bytes(String hex){
        clientData = CryptoToolAPIs.sha256x2BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }

    public String offLineTx(String fromFid, List<SendTo> sendToList, String msg){
        clientData = CryptoToolAPIs.offLineTxPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }

    public String offLineTxByCd(String fromFid, List<SendTo> sendToList, String msg, int cd){
        clientData = CryptoToolAPIs.offLineTxByCdPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,cd,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }

    public List<App> getAppsFree(String id){
        clientData = FreeGetAPIs.getApps(apiAccount.getApiUrl(), id);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getAppList(data);
    }

    public List<Service> getServicesFree(String id){
        clientData = FreeGetAPIs.getServices(apiAccount.getApiUrl(), id);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getServiceList(data);
    }
//TODO unchecked
    public Object getAvatarFree(String fid){
        clientData = FreeGetAPIs.getAvatar(apiAccount.getApiUrl(), fid);
        Object data = checkApipV1Result();
        return data;
    }


    public String broadcastFree(String txHex){
        clientData = FreeGetAPIs.broadcast(apiAccount.getApiUrl(),txHex);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String)data;
    }

    public String broadcastRawTx(String txHex){
        clientData = WalletAPIs.broadcastTxPost(apiAccount.getApiUrl(),txHex, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String)data;
    }

    public List<Cash> getCashesFree(String id, double amount){
        clientData = FreeGetAPIs.getCashes(apiAccount.getApiUrl(),id,amount);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }

    public CidInfo getFidCid(String id){
        clientData = FreeGetAPIs.getFidCid(apiAccount.getApiUrl(), id);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), CidInfo.class);
    }

    public ApipClientData getTotals(){
        return FreeGetAPIs.getTotals(apiAccount.getApiUrl());
    }

    public Map<String, CidInfo> cidInfoByIds(String[] ids){
        clientData = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCidInfoMap(data);
    }
    public List<CidInfo> cidInfoSearch(Fcdsl fcdsl){
        clientData = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCidInfoList(data);
    }
    public List<CidInfo> cidInfoSearch(String searchStr){
        clientData = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCidInfoList(data);
    }

    public List<CidHist> cidHistory(Fcdsl fcdsl){
        clientData = IdentityAPIs.cidHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCidHistoryList(data);
    }

    public List<CidHist> homepageHistory(Fcdsl fcdsl){
        clientData = IdentityAPIs.homepageHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCidHistoryList(data);
    }

    public List<CidHist> noticeFeeHistory(Fcdsl fcdsl){
        clientData = IdentityAPIs.noticeFeeHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCidHistoryList(data);
    }

    public List<CidHist> reputationHistory(Fcdsl fcdsl){
        clientData = IdentityAPIs.reputationHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCidHistoryList(data);
    }

    public Map<String, String[]> fidCidSeek(Fcdsl fcdsl){
        clientData = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> fidCidSeek(String searchStr){
        clientData = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> fidCidGetFree(String id){
        clientData = IdentityAPIs.fidCidGetFree(apiAccount.getApiUrl(), id);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public Map<String, Nobody> nobodyByIds(String[] ids){
        clientData = IdentityAPIs.nobodyByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getNobodyMap(data);
    }
    public List<Nobody> nobodySearch(Fcdsl fcdsl){
        clientData = IdentityAPIs.nobodySearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getNobodyList(data);
    }

    public Map<String, String> avatars(String[] ids){
        clientData = IdentityAPIs.avatarsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringMap(data);
    }

    public Service getServiceFree(){
        clientData = OpenAPIs.getService(apiAccount.getApiUrl());
        Object data = checkApipV1Result();
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data),Service.class);
    }

    public Session signIn(byte[] priKey, RequestBody.SignInMode mode_RefreshOrNull){
        clientData = OpenAPIs.signInPost(apiAccount.getApiUrl(),apiAccount.getVia(),priKey,mode_RefreshOrNull);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }


    public Session signInEcc(byte[] priKey, RequestBody.SignInMode mode){
        clientData = OpenAPIs.signInEccPost(apiAccount.getApiUrl(), apiAccount.getVia(), priKey,mode);
        Object data = checkApipV1Result();
        if(data==null)return null;
        Session session = gson.fromJson(gson.toJson(data), Session.class);
        String oldCipher = session.getSessionKeyCipher();
        byte[] sessionKey = decryptHexWithPriKey(oldCipher, priKey);
        if(sessionKey==null)return null;
        String newCipher = EccAes256K1P7.encryptWithSymKey(sessionKey, symKey);
        session.setSessionKeyCipher(newCipher);
        String sessionName = Hex.toHex(sessionKey);
        session.setSessionKey(Hex.toHex(sessionKey));
        session.setSessionName(sessionName);
        return session;
    }

    public Map<String, String> totals(){
        clientData = OpenAPIs.totalsPost(apiAccount.getApiUrl(),apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringMap(data);
    }
    public Map<String, String> totalsFree() {
        clientData = OpenAPIs.totalsGet(apiAccount.getApiUrl());
        Object data = checkApipV1Result();
        if (data == null) return null;
        return ApipDataGetter.getStringMap(data);
    }

    public Object general(String index, Fcdsl fcdsl){
        clientData = OpenAPIs.generalPost(index,apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        return checkApipV1Result();
    }

    public Map<String, Group> groupByIds(String[] ids){
        clientData = OrganizeAPIs.groupByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getGroupMap(data);
    }
    public List<Group> groupSearch(Fcdsl fcdsl){
        clientData = OrganizeAPIs.groupSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getGroupList(data);
    }

    public List<GroupHistory> groupOpHistory(Fcdsl fcdsl){
        clientData = OrganizeAPIs.groupOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getGroupHistoryList(data);
    }
    public Map<String, String[]> groupMembers(String[] ids){
        clientData = OrganizeAPIs.groupMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public List<MyGroupData> myGroups(String fid){
        clientData = OrganizeAPIs.myGroupsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getMyGroupList(data);
    }


    public Map<String, Team> teamByIds(String[] ids){
        clientData = OrganizeAPIs.teamByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTeamMap(data);
    }
    public List<Team> teamSearch(Fcdsl fcdsl){
        clientData = OrganizeAPIs.teamSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTeamList(data);
    }

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl){
        clientData = OrganizeAPIs.teamOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTeamHistoryList(data);
    }

    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl){
        clientData = OrganizeAPIs.teamRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTeamHistoryList(data);
    }
    public Map<String, String[]> teamMembers(String[] ids){
        clientData = OrganizeAPIs.teamMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> teamExMembers(String[] ids){
        clientData = OrganizeAPIs.teamExMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringArrayMap(data);
    }
    public TeamOtherPersonsData teamOtherPersons(String[] ids){
        clientData = OrganizeAPIs.teamOtherPersonsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), TeamOtherPersonsData.class);
    }
    public List<MyTeamData> myTeams(String fid){
        clientData = OrganizeAPIs.myTeamsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getMyTeamList(data);
    }


    public Map<String, Box> boxByIds(String[] ids){
        clientData = PersonalAPIs.boxByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getBoxMap(data);
    }
    public List<Box> boxSearch(Fcdsl fcdsl){
        clientData = PersonalAPIs.boxSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getBoxList(data);
    }

    public List<BoxHistory> boxHistory(Fcdsl fcdsl){
        clientData = PersonalAPIs.boxHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getBoxHistoryList(data);
    }

    public Map<String, Contact> contactByIds(String[] ids){
        clientData = PersonalAPIs.contactByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getContactMap(data);
    }
    public List<Contact> contacts(Fcdsl fcdsl){
        clientData = PersonalAPIs.contactsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getContactList(data);
    }

    public List<Contact> contactsDeleted(Fcdsl fcdsl){
        clientData = PersonalAPIs.contactsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getContactList(data);
    }

    public Map<String, Mail> mailByIds(String[] ids){
        clientData = PersonalAPIs.mailByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getMailMap(data);
    }
    public List<Mail> mails(Fcdsl fcdsl){
        clientData = PersonalAPIs.mailsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getMailList(data);
    }

    public List<Mail> mailsDeleted(Fcdsl fcdsl){
        clientData = PersonalAPIs.mailsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getMailList(data);
    }
    public Map<String, Secret> secretByIds(String[] ids){
        clientData = PersonalAPIs.secretByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getSecretMap(data);
    }
    public List<Secret> secrets(Fcdsl fcdsl){
        clientData = PersonalAPIs.secretsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getSecretList(data);
    }

    public List<Secret> secretsDeleted(Fcdsl fcdsl){
        clientData = PersonalAPIs.secretsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getSecretList(data);
    }

    public Map<String, Token> tokenByIds(String[] ids){
        clientData = PublishAPIs.tokenByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTokenMap(data);
    }
    public List<Token> tokenSearch(Fcdsl fcdsl){
        clientData = PublishAPIs.tokenSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTokenList(data);
    }

    public List<TokenHistory> tokenHistory(Fcdsl fcdsl){
        clientData = PublishAPIs.tokenHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTokenHistoryList(data);
    }
    public List<TokenHolder> myTokens(Fcdsl fcdsl){
        clientData = PublishAPIs.myTokensPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTokenHolderList(data);
    }
    public Map<String, TokenHolder> tokenHolderByIds(String[] ids){
        clientData = PublishAPIs.tokenHolderByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getTokenHolderMap(data);
    }

    public Map<String, Proof> proofByIds(String[] ids){
        clientData = PublishAPIs.proofByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getProofMap(data);
    }
    public List<Proof> proofSearch(Fcdsl fcdsl){
        clientData =  PublishAPIs.proofSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getProofList(data);
    }

    public List<ProofHistory> proofHistory(Fcdsl fcdsl){
        clientData = PublishAPIs.proofHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getProofHistoryList(data);
    }

    public Map<String, Statement> statementByIds(String[] ids){
        clientData =  PublishAPIs.statementByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStatementMap(data);
    }
    public List<Statement> statementSearch(Fcdsl fcdsl){
        clientData = PublishAPIs.statementSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStatementList(data);
    }

    public List<Nid> nidSearch(Fcdsl fcdsl){
        clientData = PublishAPIs.nidSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getNidList(data);
    }

    public String decodeRawTx(String rawTxHex){
        clientData = WalletAPIs.decodeRawTxPost(apiAccount.getApiUrl(), rawTxHex,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String)data;
    }

    public List<Cash> cashValidForPay(String fid, double amount){
        clientData = WalletAPIs.cashValidForPayPost(apiAccount.getApiUrl(), fid,amount,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }

    public List<Cash> cashValidForCd(String fid, int cd){
        clientData = WalletAPIs.cashValidForCdPost(apiAccount.getApiUrl(), fid,cd,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getCashList(data);
    }
    public List<UnconfirmedInfo> unconfirmed(String[] ids){
        clientData = WalletAPIs.unconfirmedPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getUnconfirmedList(data);
    }

    public String swapRegister(String sid){
        clientData = SwapHallAPIs.swapRegisterPost(apiAccount.getApiUrl(), sid,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String)data;
    }

    public List<String> swapUpdate(Map<String, Object> uploadMap){
        clientData = SwapHallAPIs.swapUpdatePost(apiAccount.getApiUrl(), uploadMap,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getStringList(data);
    }
    public SwapStateData swapState(String sid, String[] last){
        clientData = SwapHallAPIs.getSwapState(apiAccount.getApiUrl(), sid);
        Object data = checkApipV1Result();
        if(data==null)return null;
        try{
            return gson.fromJson(gson.toJson(data),SwapStateData.class);
        }catch (Exception e){
            clientData.setMessage(clientData.getMessage()+(String)data);
            return null;
        }
    }

    public SwapLpData swapLp(String sid, String[] last){
        clientData = SwapHallAPIs.getSwapLp(apiAccount.getApiUrl(), sid);
        Object data = checkApipV1Result();
        if(data==null)return null;
        try{
            return gson.fromJson(gson.toJson(data),SwapLpData.class);
        }catch (Exception e){
            clientData.setMessage(clientData.getMessage()+(String)data);
            return null;
        }
    }

    public List<SwapAffair> swapFinished(String sid, String[] last){
        clientData = SwapHallAPIs.getSwapFinished(apiAccount.getApiUrl(), sid,last);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getSwapAffairList(data);
    }

    public List<SwapAffair> swapPending(String sid){
        clientData = SwapHallAPIs.getSwapPending(apiAccount.getApiUrl(), sid);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getSwapAffairList(data);
    }

    public List<SwapPriceData> swapPrices(String sid, String gTick, String mTick, String[] last){
        clientData = SwapHallAPIs.getSwapPrice(apiAccount.getApiUrl(), sid,gTick,mTick,last);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return ApipDataGetter.getSwapPriceDataList(data);
    }
    public void setClientData(ApipClientData clientData) {
        this.clientData = clientData;
    }

//    public Fcdsl getFcdsl() {
//        return fcdsl;
//    }
//
//    public void setFcdsl(Fcdsl fcdsl) {
//        this.fcdsl = fcdsl;
//    }

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
