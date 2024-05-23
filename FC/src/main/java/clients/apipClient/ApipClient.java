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
import crypto.KeyTools;
import crypto.old.EccAes256K1P7;
import javaTools.Hex;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;

import static config.ApiAccount.decryptHexWithPriKey;
import static crypto.KeyTools.priKeyToFid;

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
        clientTask = FreeGetAPIs.getCashes(apiUrl, fid, 0);
        Object data = checkApipV1Result();
        return DataGetter.getCashList(data);
    }

    @Nullable


    public Service getApipServiceFree(String urlHead){
        if(urlHead.contains(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI))
            urlHead.replaceAll(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI,"");

        clientTask = OpenAPIs.getService(urlHead);

        Object data = checkApipV1Result();
        if(data==null)return null;

        Service service = gson.fromJson(gson.toJson(data),Service.class);
        ApipParams.getParamsFromService(service,ApipParams.class);
        return service;
    }

    public CidInfo getCidInfo(String fid, ApiAccount apiAccount) {
        clientTask = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), new String[]{fid}, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCidInfoMap(data).get(fid);
    }

    public String getPubKey(String fid) {
        clientTask = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), new String[]{fid}, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getAddressMap(data).get(fid).getPubKey();
    }

    public Map<String, BlockInfo> blockByIds(String[] ids){
        clientTask = BlockchainAPIs.blockByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getBlockInfoMap(data);
    }

    public Map<String, BlockInfo> blockByHeights(String[] heights){
        clientTask = BlockchainAPIs.blockByHeightPost(apiAccount.getApiUrl(), heights, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getBlockInfoMap(data);
    }

    public List<BlockInfo> blockSearch(Fcdsl fcdsl){
        clientTask = BlockchainAPIs.blockSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getBlockInfoList(data);
    }

    public List<Cash> cashValid(Fcdsl fcdsl){
       clientTask = BlockchainAPIs.cashValidPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCashList(data);
    }

    public Map<String, Cash> cashByIds(String[] ids){
        clientTask = BlockchainAPIs.cashByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCashMap(data);
    }

    public List<Utxo> getUtxo(String id, double amount){
        clientTask = BlockchainAPIs.getUtxo(apiAccount.getApiUrl(), id,amount);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getUtxoList(data);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl){
        clientTask = BlockchainAPIs.cashSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCashList(data);
    }

    public Map<String, Address> fidByIds(String[] ids){
        clientTask = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getAddressMap(data);
    }

    public Map<String, OpReturn> opReturnByIds(String[] ids){
        clientTask =  BlockchainAPIs.opReturnByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getOpReturnMap(data);
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl){
        clientTask = BlockchainAPIs.opReturnSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getOpReturnList(data);
    }

    public Map<String, P2SH> p2shByIds(String[] ids){
        clientTask = BlockchainAPIs.p2shByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getP2SHMap(data);
    }

    public List<P2SH> p2shSearch(Fcdsl fcdsl){
        clientTask = BlockchainAPIs.p2shSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getP2SHList(data);
    }

    public List<TxInfo> txSearch(Fcdsl fcdsl){
        clientTask = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTxInfoList(data);
    }

    public Map<String, TxInfo> txByIds(String[] ids){
        clientTask = BlockchainAPIs.txByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTxInfoMap(data);
    }

    public List<TxInfo> txByFid(String fid, String[] last){
        Fcdsl fcdsl = BlockchainAPIs.txByFidQuery(fid,last);
        clientTask = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTxInfoList(data);
    }

    public Map<String, Protocol> protocolByIds(String[] ids){
        clientTask = ConstructAPIs.protocolByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getProtocolMap(data);
    }
    public List<Protocol> protocolSearch(Fcdsl fcdsl){
        clientTask = ConstructAPIs.protocolSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getProtocolList(data);
    }

    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl){
        clientTask = ConstructAPIs.protocolOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getProtocolHistoryList(data);
    }


    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl){
        clientTask = ConstructAPIs.protocolRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getProtocolHistoryList(data);
    }

    public Map<String, Code> codeByIds(String[] ids){
        clientTask = ConstructAPIs.codeByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCodeMap(data);
    }
    public List<Code> codeSearch(Fcdsl fcdsl){
        clientTask = ConstructAPIs.codeSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCodeList(data);
    }

    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl){
        clientTask = ConstructAPIs.codeOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCodeHistoryList(data);
    }


    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl){
        clientTask = ConstructAPIs.codeRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCodeHistoryList(data);
    }


    public Map<String, Service> serviceMapByIds(String[] ids){
        clientTask = ConstructAPIs.serviceByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getServiceMap(data);
    }

    public Service serviceById(String id){
        Map<String, Service> map = serviceMapByIds(new String[]{id});
        if(map==null)return null;
        return map.get(id);
    }
    public List<Service> serviceSearch(Fcdsl fcdsl){


        clientTask = ConstructAPIs.serviceSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getServiceList(data);
    }

    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl){
        clientTask = ConstructAPIs.serviceOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getServiceHistoryList(data);
    }


    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl){
        clientTask = ConstructAPIs.serviceRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getServiceHistoryList(data);
    }


    public Map<String, App> appByIds(String[] ids){
        clientTask = ConstructAPIs.appByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getAppMap(data);
    }
    public List<App> appSearch(Fcdsl fcdsl){
        clientTask = ConstructAPIs.appSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getAppList(data);
    }

    public List<AppHistory> appOpHistory(Fcdsl fcdsl){
        clientTask = ConstructAPIs.appOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getAppHistoryList(data);
    }


    public List<AppHistory> appRateHistory(Fcdsl fcdsl){
        clientTask = ConstructAPIs.appRateHistoryPost(apiAccount.getApiUrl(),fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getAppHistoryList(data);
    }

    public Map<String, String> addresses(String addrOrPubKey){
        clientTask = CryptoToolAPIs.addressesPost(apiAccount.getApiUrl(), addrOrPubKey,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringMap(data);
    }
    public String encrypt(String key, String message){
        clientTask = CryptoToolAPIs.encryptPost(apiAccount.getApiUrl(), key,message,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }
    public boolean verify(String signature){
        clientTask = CryptoToolAPIs.verifyPost(apiAccount.getApiUrl(), signature, apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return false;
        return (boolean) data;
    }
    public String sha256(String text){
        clientTask = CryptoToolAPIs.sha256Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }
    public String sha256x2(String text){
        clientTask = CryptoToolAPIs.sha256x2Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }
    public String sha256Bytes(String hex){
        clientTask = CryptoToolAPIs.sha256BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }

    public String sha256x2Bytes(String hex){
        clientTask = CryptoToolAPIs.sha256x2BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }

    public String offLineTx(String fromFid, List<SendTo> sendToList, String msg){
        clientTask = CryptoToolAPIs.offLineTxPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }

    public String offLineTxByCd(String fromFid, List<SendTo> sendToList, String msg, int cd){
        clientTask = CryptoToolAPIs.offLineTxByCdPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,cd,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String) data;
    }

    public List<App> getAppsFree(String id){
        clientTask = FreeGetAPIs.getApps(apiAccount.getApiUrl(), id);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getAppList(data);
    }

    public List<Service> getServicesFree(String id){
        clientTask = FreeGetAPIs.getServices(apiAccount.getApiUrl(), id);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getServiceList(data);
    }
//TODO unchecked
    public Object getAvatarFree(String fid){
        clientTask = FreeGetAPIs.getAvatar(apiAccount.getApiUrl(), fid);
        Object data = checkApipV1Result();
        return data;
    }


    public String broadcastFree(String txHex){
        clientTask = FreeGetAPIs.broadcast(apiAccount.getApiUrl(),txHex);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String)data;
    }

    public String broadcastRawTx(String txHex){
        clientTask = WalletAPIs.broadcastTxPost(apiAccount.getApiUrl(),txHex, apiAccount.getVia(), sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String)data;
    }

    public List<Cash> getCashesFree(String id, double amount){
        clientTask = FreeGetAPIs.getCashes(apiAccount.getApiUrl(),id,amount);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCashList(data);
    }

    public CidInfo getFidCid(String id){
        clientTask = FreeGetAPIs.getFidCid(apiAccount.getApiUrl(), id);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), CidInfo.class);
    }

    public ApipClientTask getTotals(){
        return FreeGetAPIs.getTotals(apiAccount.getApiUrl());
    }

    public Map<String, CidInfo> cidInfoByIds(String[] ids){
        clientTask = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCidInfoMap(data);
    }
    public List<CidInfo> cidInfoSearch(Fcdsl fcdsl){
        clientTask = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCidInfoList(data);
    }
    public List<CidInfo> cidInfoSearch(String searchStr){
        clientTask = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCidInfoList(data);
    }

    public List<CidHist> cidHistory(Fcdsl fcdsl){
        clientTask = IdentityAPIs.cidHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCidHistoryList(data);
    }

    public List<CidHist> homepageHistory(Fcdsl fcdsl){
        clientTask = IdentityAPIs.homepageHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCidHistoryList(data);
    }

    public List<CidHist> noticeFeeHistory(Fcdsl fcdsl){
        clientTask = IdentityAPIs.noticeFeeHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCidHistoryList(data);
    }

    public List<CidHist> reputationHistory(Fcdsl fcdsl){
        clientTask = IdentityAPIs.reputationHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCidHistoryList(data);
    }

    public Map<String, String[]> fidCidSeek(Fcdsl fcdsl){
        clientTask = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> fidCidSeek(String searchStr){
        clientTask = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> fidCidGetFree(String id){
        clientTask = IdentityAPIs.fidCidGetFree(apiAccount.getApiUrl(), id);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringArrayMap(data);
    }

    public Map<String, Nobody> nobodyByIds(String[] ids){
        clientTask = IdentityAPIs.nobodyByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getNobodyMap(data);
    }
    public List<Nobody> nobodySearch(Fcdsl fcdsl){
        clientTask = IdentityAPIs.nobodySearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getNobodyList(data);
    }

    public Map<String, String> avatars(String[] ids){
        clientTask = IdentityAPIs.avatarsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringMap(data);
    }

    public Service getServiceFree(){
        clientTask = OpenAPIs.getService(apiAccount.getApiUrl());
        Object data = checkApipV1Result();
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data),Service.class);
    }

    public Session signIn(byte[] priKey, RequestBody.SignInMode mode_RefreshOrNull){
        clientTask = OpenAPIs.signInPost(apiAccount.getApiUrl(),apiAccount.getVia(),priKey,mode_RefreshOrNull);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), Session.class);
    }


    public Session signInEcc(byte[] priKey, RequestBody.SignInMode mode){
        clientTask = OpenAPIs.signInEccPost(apiAccount.getApiUrl(), apiAccount.getVia(), priKey,mode);
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
        clientTask = OpenAPIs.totalsPost(apiAccount.getApiUrl(),apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringMap(data);
    }
    public Map<String, String> totalsFree() {
        clientTask = OpenAPIs.totalsGet(apiAccount.getApiUrl());
        Object data = checkApipV1Result();
        if (data == null) return null;
        return DataGetter.getStringMap(data);
    }

    public Object general(String index, Fcdsl fcdsl){
        clientTask = OpenAPIs.generalPost(index,apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        return checkApipV1Result();
    }

    public Map<String, Group> groupByIds(String[] ids){
        clientTask = OrganizeAPIs.groupByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getGroupMap(data);
    }
    public List<Group> groupSearch(Fcdsl fcdsl){
        clientTask = OrganizeAPIs.groupSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getGroupList(data);
    }

    public List<GroupHistory> groupOpHistory(Fcdsl fcdsl){
        clientTask = OrganizeAPIs.groupOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getGroupHistoryList(data);
    }
    public Map<String, String[]> groupMembers(String[] ids){
        clientTask = OrganizeAPIs.groupMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringArrayMap(data);
    }

    public List<MyGroupData> myGroups(String fid){
        clientTask = OrganizeAPIs.myGroupsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getMyGroupList(data);
    }


    public Map<String, Team> teamByIds(String[] ids){
        clientTask = OrganizeAPIs.teamByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTeamMap(data);
    }
    public List<Team> teamSearch(Fcdsl fcdsl){
        clientTask = OrganizeAPIs.teamSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTeamList(data);
    }

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl){
        clientTask = OrganizeAPIs.teamOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTeamHistoryList(data);
    }

    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl){
        clientTask = OrganizeAPIs.teamRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTeamHistoryList(data);
    }
    public Map<String, String[]> teamMembers(String[] ids){
        clientTask = OrganizeAPIs.teamMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringArrayMap(data);
    }

    public Map<String, String[]> teamExMembers(String[] ids){
        clientTask = OrganizeAPIs.teamExMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringArrayMap(data);
    }
    public TeamOtherPersonsData teamOtherPersons(String[] ids){
        clientTask = OrganizeAPIs.teamOtherPersonsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return gson.fromJson(gson.toJson(data), TeamOtherPersonsData.class);
    }
    public List<MyTeamData> myTeams(String fid){
        clientTask = OrganizeAPIs.myTeamsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getMyTeamList(data);
    }


    public Map<String, Box> boxByIds(String[] ids){
        clientTask = PersonalAPIs.boxByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getBoxMap(data);
    }
    public List<Box> boxSearch(Fcdsl fcdsl){
        clientTask = PersonalAPIs.boxSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getBoxList(data);
    }

    public List<BoxHistory> boxHistory(Fcdsl fcdsl){
        clientTask = PersonalAPIs.boxHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getBoxHistoryList(data);
    }

    public Map<String, Contact> contactByIds(String[] ids){
        clientTask = PersonalAPIs.contactByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getContactMap(data);
    }
    public List<Contact> contacts(Fcdsl fcdsl){
        clientTask = PersonalAPIs.contactsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getContactList(data);
    }

    public List<Contact> contactsDeleted(Fcdsl fcdsl){
        clientTask = PersonalAPIs.contactsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getContactList(data);
    }

    public Map<String, Mail> mailByIds(String[] ids){
        clientTask = PersonalAPIs.mailByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getMailMap(data);
    }
    public List<Mail> mails(Fcdsl fcdsl){
        clientTask = PersonalAPIs.mailsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getMailList(data);
    }

    public List<Mail> mailsDeleted(Fcdsl fcdsl){
        clientTask = PersonalAPIs.mailsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getMailList(data);
    }
    public Map<String, Secret> secretByIds(String[] ids){
        clientTask = PersonalAPIs.secretByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getSecretMap(data);
    }
    public List<Secret> secrets(Fcdsl fcdsl){
        clientTask = PersonalAPIs.secretsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getSecretList(data);
    }

    public List<Secret> secretsDeleted(Fcdsl fcdsl){
        clientTask = PersonalAPIs.secretsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getSecretList(data);
    }

    public Map<String, Token> tokenByIds(String[] ids){
        clientTask = PublishAPIs.tokenByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTokenMap(data);
    }
    public List<Token> tokenSearch(Fcdsl fcdsl){
        clientTask = PublishAPIs.tokenSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTokenList(data);
    }

    public List<TokenHistory> tokenHistory(Fcdsl fcdsl){
        clientTask = PublishAPIs.tokenHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTokenHistoryList(data);
    }
    public List<TokenHolder> myTokens(Fcdsl fcdsl){
        clientTask = PublishAPIs.myTokensPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTokenHolderList(data);
    }
    public Map<String, TokenHolder> tokenHolderByIds(String[] ids){
        clientTask = PublishAPIs.tokenHolderByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getTokenHolderMap(data);
    }

    public Map<String, Proof> proofByIds(String[] ids){
        clientTask = PublishAPIs.proofByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getProofMap(data);
    }
    public List<Proof> proofSearch(Fcdsl fcdsl){
        clientTask =  PublishAPIs.proofSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getProofList(data);
    }

    public List<ProofHistory> proofHistory(Fcdsl fcdsl){
        clientTask = PublishAPIs.proofHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getProofHistoryList(data);
    }

    public Map<String, Statement> statementByIds(String[] ids){
        clientTask =  PublishAPIs.statementByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStatementMap(data);
    }
    public List<Statement> statementSearch(Fcdsl fcdsl){
        clientTask = PublishAPIs.statementSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStatementList(data);
    }

    public List<Nid> nidSearch(Fcdsl fcdsl){
        clientTask = PublishAPIs.nidSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getNidList(data);
    }

    public String decodeRawTx(String rawTxHex){
        clientTask = WalletAPIs.decodeRawTxPost(apiAccount.getApiUrl(), rawTxHex,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String)data;
    }

    public List<Cash> cashValidForPay(String fid, double amount){
        clientTask = WalletAPIs.cashValidForPayPost(apiAccount.getApiUrl(), fid,amount,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCashList(data);
    }

    public List<Cash> cashValidForCd(String fid, int cd){
        clientTask = WalletAPIs.cashValidForCdPost(apiAccount.getApiUrl(), fid,cd,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getCashList(data);
    }
    public List<UnconfirmedInfo> unconfirmed(String[] ids){
        clientTask = WalletAPIs.unconfirmedPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getUnconfirmedList(data);
    }

    public String swapRegister(String sid){
        clientTask = SwapHallAPIs.swapRegisterPost(apiAccount.getApiUrl(), sid,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return (String)data;
    }

    public List<String> swapUpdate(Map<String, Object> uploadMap){
        clientTask = SwapHallAPIs.swapUpdatePost(apiAccount.getApiUrl(), uploadMap,apiAccount.getVia(),sessionKey);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getStringList(data);
    }
    public SwapStateData swapState(String sid, String[] last){
        clientTask = SwapHallAPIs.getSwapState(apiAccount.getApiUrl(), sid);
        Object data = checkApipV1Result();
        if(data==null)return null;
        try{
            return gson.fromJson(gson.toJson(data),SwapStateData.class);
        }catch (Exception e){
            clientTask.setMessage(clientTask.getMessage()+(String)data);
            return null;
        }
    }

    public SwapLpData swapLp(String sid, String[] last){
        clientTask = SwapHallAPIs.getSwapLp(apiAccount.getApiUrl(), sid);
        Object data = checkApipV1Result();
        if(data==null)return null;
        try{
            return gson.fromJson(gson.toJson(data),SwapLpData.class);
        }catch (Exception e){
            clientTask.setMessage(clientTask.getMessage()+(String)data);
            return null;
        }
    }

    public List<SwapAffair> swapFinished(String sid, String[] last){
        clientTask = SwapHallAPIs.getSwapFinished(apiAccount.getApiUrl(), sid,last);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getSwapAffairList(data);
    }

    public List<SwapAffair> swapPending(String sid){
        clientTask = SwapHallAPIs.getSwapPending(apiAccount.getApiUrl(), sid);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getSwapAffairList(data);
    }

    public List<SwapPriceData> swapPrices(String sid, String gTick, String mTick, String[] last){
        clientTask = SwapHallAPIs.getSwapPrice(apiAccount.getApiUrl(), sid,gTick,mTick,last);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return DataGetter.getSwapPriceDataList(data);
    }
    public void setClientData(ApipClientTask clientData) {
        this.clientTask = clientData;
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
