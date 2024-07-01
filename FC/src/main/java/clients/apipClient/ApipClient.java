package clients.apipClient;

import apip.apipData.*;
import appTools.Menu;
import appTools.Shower;
import clients.ApiUrl;
import constants.*;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import fcData.AlgorithmId;
import fch.Inputer;
import fch.Wallet;
import fch.fchData.*;
import feip.FeipTools;
import feip.feipClient.IdentityFEIPs;
import feip.feipData.*;
import clients.Client;
import config.ApiAccount;
import config.ApiProvider;
import config.ApiType;
import crypto.KeyTools;
import crypto.old.EccAes256K1P7;
import javaTools.Hex;
import javaTools.NumberTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import org.jetbrains.annotations.Nullable;
import server.Settings;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;

import static constants.ApiNames.*;
import static constants.FieldNames.*;
import static constants.Strings.*;
import static constants.Values.TRUE;
import static crypto.KeyTools.priKeyToFid;
import static javaTools.CollectionTools.objectToList;
import static javaTools.CollectionTools.objectToMap;

public class ApipClient extends Client {

    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symKey){
        super(apiProvider,apiAccount,symKey);
        this.signInUrlTailPath= ApiUrl.makeUrlTailPath(ApiNames.SN_0,ApiNames.Version2);
    }

    public Double feeRate(){
        fcClientEvent = requestJsonByFcdsl(HttpRequestMethod.POST,SN_18,Version2,FeeRateAPI,null,sessionKey, AuthType.FC_SIGN_BODY);
        Object data = checkResult();
        if(data==null)return null;
        return (Double)data;
    }

    public List<Service> getServiceListByOwnerAndType(String owner, @Nullable ApiType type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(owner);
        if(type!=null)fcdsl.addNewFilter().addNewMatch().addNewFields(FieldNames.TYPES).setValue(type.name());
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
    public List<Service> serviceSearch(Fcdsl fcdsl){
        fcClientEvent = requestJsonByFcdsl(HttpRequestMethod.POST,SN_6,Version2,ServiceSearchAPI,fcdsl,sessionKey, AuthType.FC_SIGN_BODY);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return objectToList(data,Service.class);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl){
        fcClientEvent = requestJsonByFcdsl(HttpRequestMethod.POST,SN_2,Version2,CashSearchAPI,fcdsl,sessionKey, AuthType.FC_SIGN_BODY);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return objectToList(data,Cash.class);
    }

    public static void setMaster(String fid,String userPriKeyCipher, long bestHeight, byte[] symKey, ApipClient apipClient, BufferedReader br) {
        if(userPriKeyCipher==null){
            System.out.println("The private key is required when set master.");
            return;
        }
        String master;
        String masterPubKey;

        byte[] priKey = decryptPriKey(userPriKeyCipher,symKey);
        if(priKey==null){
            System.out.println("Failed to get private Key.");
            return;
        }

        while (true) {
            master = Inputer.inputString(br, "Input the FID or Public Key of the master:");

            if (Hex.isHexString(master)) {
                masterPubKey = master;
                master = KeyTools.pubKeyToFchAddr(master);
            }else {
                if (KeyTools.isValidFchAddr(master)){
                    CidInfo masterInfo = Settings.getCidInfo(master,apipClient);
                    if(masterInfo==null){
                        System.out.println("Failed to get CID info.");
                        return;
                    }
                    masterPubKey = masterInfo.getPubKey();
                }else {
                    System.out.println("It's not a good FID or public Key. Try again.");
                    continue;
                }
            }
            break;
        }

        if(!Inputer.askIfYes(br,"The master will get your private key and control all your on chain assets and rights. Are you sure to set?"))
            return;

        CryptoDataByte cipher = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7).encryptByAsyOneWay(priKey, Hex.fromHex(masterPubKey));
        if(cipher==null || cipher.getCode()!=0){
            System.out.println("Failed to encrypt priKey.");
            return;
        }
        String priKeyCipher = cipher.toJson();

        String dataOnChainJson = FeipTools.getMasterData(master,masterPubKey,priKeyCipher);
        long requiredCd = 0;
        int maxCashes=20;

        if (bestHeight > Constants.CDD_CHECK_HEIGHT)
            requiredCd = Constants.CDD_REQUIRED;

        if("".equals(userPriKeyCipher)){
            String rawTx = Wallet.makeTxForCs(fid,null,dataOnChainJson,requiredCd,20,apipClient);
            System.out.println("Sign below TX with CryptoSign:");
            Shower.printUnderline(10);
            System.out.println(rawTx);
            Shower.printUnderline(10);
        }else {

            String result = Wallet.sendTxByApip(priKey, null, dataOnChainJson, requiredCd, maxCashes, apipClient);
            if (Hex.isHexString(result))
                System.out.println("CID was set. Wait for a few minutes for the confirmation on chain.");
            else System.out.println("Some thing wrong:" + result);
        }
        Menu.anyKeyToContinue(br);

    }

    public static void setCid(String fid, String userPriKeyCipher, long bestHeight, byte[] symKey, ApipClient apipClient, BufferedReader br) {
        String cid;
        cid = Inputer.inputString(br, "Input the name you want to give the address");
        if(FeipTools.isGoodCidName(cid)){
            String dataOnChainJson = FeipTools.getCidRegisterData(cid);
            long requiredCd = 0;
            int maxCashes=20;

            if (bestHeight > Constants.CDD_CHECK_HEIGHT)
                requiredCd = Constants.CDD_REQUIRED;

            if("".equals(userPriKeyCipher)){
                String rawTx = Wallet.makeTxForCs(fid,null,dataOnChainJson,requiredCd,20,apipClient);
                System.out.println("Sign below TX with CryptoSign:");
                Shower.printUnderline(10);
                System.out.println(rawTx);
                Shower.printUnderline(10);
            }else {
                byte[] priKey = decryptPriKey(userPriKeyCipher,symKey);
                if(priKey==null)return;
                String result = Wallet.sendTxByApip(priKey, null, dataOnChainJson, requiredCd, maxCashes, apipClient);
                if (Hex.isHexString(result))
                    System.out.println("CID was set. Wait for a few minutes for the confirmation on chain.");
                else System.out.println("Some thing wrong:" + result);
            }
            Menu.anyKeyToContinue(br);
        }
    }

    private static byte[] decryptPriKey(String userPriKeyCipher, byte[] symKey) {
        CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymKey(userPriKeyCipher, symKey);
        if (cryptoResult.getCode() != 0) {
            cryptoResult.printCodeMessage();
            return null;
        }
        return cryptoResult.getData();
    }
//
    public Map<String, String> checkSubscription(String endpoint) {
        WebhookRequestBody webhookRequestBody = new WebhookRequestBody();
        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setOp(CHECK);
        webhookRequestBody.setMethod(ApiNames.NewCashByFidsAPI);
        webhookRequestBody.setUserName(apiAccount.getUserId());
        String hookUserId = WebhookRequestBody.makeHookUserId(apiAccount.getProviderId(), apiAccount.getUserId(), ApiNames.NewCashByFidsAPI);
        webhookRequestBody.setHookUserId(hookUserId);

        newCashListByIds(webhookRequestBody);
        Object data = apipClient.checkResult();
        return DataGetter.getStringMap(data);
    }
//
    public boolean subscribeWebhook(String endpoint) {
        WebhookRequestBody webhookRequestBody = new WebhookRequestBody();

        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setMethod(ApiNames.NewCashByFidsAPI);
        webhookRequestBody.setUserName(apiAccount.getUserId());
        webhookRequestBody.setOp(SUBSCRIBE);
        newCashListByIds(webhookRequestBody);
        Object data1 =  checkResult();
        Map<String, String> dataMap1 = DataGetter.getStringMap(data1);
        if(dataMap1==null) return false;
        String hookUserId = dataMap1.get(HOOK_USER_ID);
        if(hookUserId==null) return false;
        return true;
    }

    public List<Cash> newCashListByIds(WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(webhookRequestBody);
        fcClientEvent = requestJsonByFcdsl(HttpRequestMethod.POST,SN_20,Version2,ApiNames.NewCashByFidsAPI,fcdsl,sessionKey, AuthType.FC_SIGN_BODY);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return objectToList(data,Cash.class);
    }

    public Object checkApipV1Result(){
        return checkResult();
    }

    public void checkMaster(String priKeyCipher,BufferedReader br) {
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(priKeyCipher, symKey);
        if (priKey == null) {
            throw new RuntimeException("Failed to decrypt priKey.");
        }

        String fid = priKeyToFid(priKey);
        CidInfo cidInfo = cidInfoById(fid);
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
                if (getCashesFree(fid, 20,null ) == null) return;
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

    public Object requestByIds(HttpRequestMethod httpRequestMethod, String sn, String ver, String apiName, AuthType authType, String... ids) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(ids);

        fcClientEvent = requestJsonByFcdsl(httpRequestMethod,sn,ver,apiName,fcdsl,sessionKey, authType);
        return checkResult();
    }

    public Object requestByOther(HttpRequestMethod httpRequestMethod, String sn, String ver, String apiName, Object other, AuthType authType) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addOther(other);
        fcClientEvent = requestJsonByFcdsl(httpRequestMethod,sn,ver,apiName,fcdsl,sessionKey,authType );
        return checkResult();
    }
    public Map<String,CidInfo> cidInfoByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(httpRequestMethod,SN_3,Version2,CidInfoByIdsAPI, authType, ids);
        return data==null ? null:DataGetter.getCidInfoMap(data);
    }
    public CidInfo cidInfoById(String id) {
        Map<String, CidInfo> map = cidInfoByIds(HttpRequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        return map.get(id);
    }

    public Map<String,Service> serviceByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(httpRequestMethod,SN_6,Version2,ServiceByIdsAPI, authType, ids);
        return data==null ? null:DataGetter.getServiceMap(data);
    }

    public Service serviceById(String id){
        Map<String, Service> map = serviceByIds(HttpRequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        return map.get(id);
    }

    public String broadcastTx(HttpRequestMethod httpRequestMethod, Object other, AuthType authType){
        Object data = requestByOther(httpRequestMethod,SN_18,Version2,BroadcastTxAPI,other,authType );
        return data==null ? null:(String)data;
    }

    public List<Cash> getCashesFree(String fid, int size, List<String> after) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
        fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
        fcdsl.addSort(CD,ASC).addSort(CASH_ID,ASC);
        if(size>0)fcdsl.addSize(size);
        if(after!=null)fcdsl.addAfter(after);
        fcClientEvent = requestJsonByFcdsl(HttpRequestMethod.POST,SN_2,Version2,CashSearchAPI,fcdsl,sessionKey,AuthType.FC_SIGN_BODY );
        Object data = checkApipV1Result();
        return DataGetter.getCashList(data);
    }

    public List<Cash> getCashes(String urlHead, String id, double amount) {
        ApipClientEvent apipClientData = new ApipClientEvent();
        String urlTail = ApiNames.makeUrlTailPath(SN_18,Version2) + ApiNames.GetCashesAPI;
        if (id != null) urlTail = urlTail + "?fid=" + id;
        if (amount != 0) {
            if(id==null){
                System.out.println(ReplyCodeMessage.Msg1021FidIsRequired);
                return null;
            }
            urlTail = urlTail + "&amount=" + amount;
        }
        apipClientData.addNewApipUrl(urlHead, urlTail);
        apipClientData.get();
        Object data = checkResult();
        if(data==null){
            System.out.println("Failed to get cashes from:"+urlHead);
            return null;
        }
        return DataGetter.getCashList(data);
    }

    public Map<String, P2SH> p2shByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_2,Version2,P2shByIdsAPI, authType, ids);
        return data==null ? null:objectToMap(data,String.class,P2SH.class);
    }

    public List<Cash> cashValidForPay(HttpRequestMethod httpRequestMethod, String fid, double amount, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(fid);
        amount = NumberTools.roundDouble8(amount);
        fcdsl.setOther(amount);

        fcClientEvent = requestJsonByFcdsl(httpRequestMethod,SN_18,Version2,CashValidForPayAPI,fcdsl,sessionKey, authType);
        Object data = checkApipV1Result();
        if(data==null)return null;
        return objectToList(data,Cash.class);
    }

    public Map<String, BlockInfo>blockByHeights(HttpRequestMethod httpRequestMethod, AuthType authType, String... heights){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Strings.HEIGHT).addNewValues(heights);

        fcClientEvent = requestJsonByFcdsl(httpRequestMethod,SN_2,Version2,BlockByHeightsAPI,fcdsl,sessionKey,authType );
        Object data = checkApipV1Result();
        if(data==null)return null;
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public String getPubKey(String fid, HttpRequestMethod httpRequestMethod, AuthType authType) {
        Object data = requestByIds(httpRequestMethod,SN_2,Version2,FidByIdsAPI, authType, fid);
        return data==null ? null:objectToMap(data,String.class,Address.class).get(fid).getPubKey();
    }
//
//    public Map<String, BlockInfo> blockByIds(String[] ids){
//        fcClientEvent = BlockchainAPIs.blockByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getBlockInfoMap(data);
//    }
//
//    public Map<String, BlockInfo> blockByHeights(String[] heights){
//        fcClientEvent = BlockchainAPIs.blockByHeightPost(apiAccount.getApiUrl(), heights, apiAccount.getVia(), sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getBlockInfoMap(data);
//    }
//
//    public List<BlockInfo> blockSearch(Fcdsl fcdsl){
//        fcClientEvent = BlockchainAPIs.blockSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getBlockInfoList(data);
//    }
//
//    public List<Cash> cashValid(Fcdsl fcdsl){
//       fcClientEvent = BlockchainAPIs.cashValidPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCashList(data);
//    }
//
//    public Map<String, Cash> cashByIds(String[] ids){
//        fcClientEvent = BlockchainAPIs.cashByIdsPost(apiAccount.getApiUrl(), ids, apiAccount.getVia(), sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCashMap(data);
//    }
//
//    public List<Utxo> getUtxo(String id, double amount){
//        fcClientEvent = BlockchainAPIs.getUtxo(apiAccount.getApiUrl(), id,amount);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getUtxoList(data);
//    }
//
//    public FcClientEvent cashSearch(Fcdsl fcdsl){
//        fcClientEvent = BlockchainAPIs.cashSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        return fcClientEvent;
//    }
//
//    public Map<String, Address> fidByIds(String[] ids){
//        fcClientEvent = BlockchainAPIs.fidByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getAddressMap(data);
//    }
//
//    public Map<String, OpReturn> opReturnByIds(String[] ids){
//        fcClientEvent =  BlockchainAPIs.opReturnByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getOpReturnMap(data);
//    }
//
//    public List<OpReturn> opReturnSearch(Fcdsl fcdsl){
//        fcClientEvent = BlockchainAPIs.opReturnSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getOpReturnList(data);
//    }
//
//    public Map<String, P2SH> p2shByIds(String[] ids){
//        fcClientEvent = BlockchainAPIs.p2shByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getP2SHMap(data);
//    }
//
//    public List<P2SH> p2shSearch(Fcdsl fcdsl){
//        fcClientEvent = BlockchainAPIs.p2shSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getP2SHList(data);
//    }
//
//    public List<TxInfo> txSearch(Fcdsl fcdsl){
//        fcClientEvent = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTxInfoList(data);
//    }
//
//    public Map<String, TxInfo> txByIds(String[] ids){
//        fcClientEvent = BlockchainAPIs.txByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTxInfoMap(data);
//    }
//
//    public List<TxInfo> txByFid(String fid, String[] last){
//        Fcdsl fcdsl = BlockchainAPIs.txByFidQuery(fid,last);
//        fcClientEvent = BlockchainAPIs.txSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTxInfoList(data);
//    }
//
//    public Map<String, Protocol> protocolByIds(String[] ids){
//        fcClientEvent = ConstructAPIs.protocolByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getProtocolMap(data);
//    }
//    public List<Protocol> protocolSearch(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.protocolSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getProtocolList(data);
//    }
//
//    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.protocolOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getProtocolHistoryList(data);
//    }
//
//
//    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.protocolRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getProtocolHistoryList(data);
//    }
//
//    public Map<String, Code> codeByIds(String[] ids){
//        fcClientEvent = ConstructAPIs.codeByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCodeMap(data);
//    }
//    public List<Code> codeSearch(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.codeSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCodeList(data);
//    }
//
//    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.codeOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCodeHistoryList(data);
//    }
//
//
//    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.codeRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCodeHistoryList(data);
//    }
//
//

//
//    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.serviceOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getServiceHistoryList(data);
//    }
//
//
//    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.serviceRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getServiceHistoryList(data);
//    }
//
//
//    public Map<String, App> appByIds(String[] ids){
//        fcClientEvent = ConstructAPIs.appByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getAppMap(data);
//    }
//    public List<App> appSearch(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.appSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getAppList(data);
//    }
//
//    public List<AppHistory> appOpHistory(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.appOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getAppHistoryList(data);
//    }
//
//
//    public List<AppHistory> appRateHistory(Fcdsl fcdsl){
//        fcClientEvent = ConstructAPIs.appRateHistoryPost(apiAccount.getApiUrl(),fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getAppHistoryList(data);
//    }
//
//    public Map<String, String> addresses(String addrOrPubKey){
//        fcClientEvent = CryptoToolAPIs.addressesPost(apiAccount.getApiUrl(), addrOrPubKey,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringMap(data);
//    }
//    public String encrypt(String key, String message){
//        fcClientEvent = CryptoToolAPIs.encryptPost(apiAccount.getApiUrl(), key,message,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String) data;
//    }
//    public boolean verify(String signature){
//        fcClientEvent = CryptoToolAPIs.verifyPost(apiAccount.getApiUrl(), signature, apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return false;
//        return (boolean) data;
//    }
//    public String sha256(String text){
//        fcClientEvent = CryptoToolAPIs.sha256Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String) data;
//    }
//    public String sha256x2(String text){
//        fcClientEvent = CryptoToolAPIs.sha256x2Post(apiAccount.getApiUrl(), text,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String) data;
//    }
//    public String sha256Bytes(String hex){
//        fcClientEvent = CryptoToolAPIs.sha256BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String) data;
//    }
//
//    public String sha256x2Bytes(String hex){
//        fcClientEvent = CryptoToolAPIs.sha256x2BytesPost(apiAccount.getApiUrl(), hex,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String) data;
//    }
//
//    public String offLineTx(String fromFid, List<SendTo> sendToList, String msg){
//        fcClientEvent = CryptoToolAPIs.offLineTxPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String) data;
//    }
//
//    public String offLineTxByCd(String fromFid, List<SendTo> sendToList, String msg, int cd){
//        fcClientEvent = CryptoToolAPIs.offLineTxByCdPost(apiAccount.getApiUrl(), fromFid,sendToList,msg,cd,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String) data;
//    }
//
//    public List<App> getAppsFree(String id){
//        fcClientEvent = FreeGetAPIs.getApps(apiAccount.getApiUrl(), id);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getAppList(data);
//    }
//
//    public List<Service> getServicesFree(String id){
//        fcClientEvent = FreeGetAPIs.getServices(apiAccount.getApiUrl(), id);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getServiceList(data);
//    }
////TODO unchecked
//    public Object getAvatarFree(String fid){
//        fcClientEvent = FreeGetAPIs.getAvatar(apiAccount.getApiUrl(), fid);
//        Object data = checkApipV1Result();
//        return data;
//    }
//
//
//    public String broadcastFree(String txHex){
//        fcClientEvent = FreeGetAPIs.broadcast(apiAccount.getApiUrl(),txHex);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String)data;
//    }
//
//    public String broadcastRawTx(String txHex){
//        fcClientEvent = WalletAPIs.broadcastTxPost(apiAccount.getApiUrl(),txHex, apiAccount.getVia(), sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String)data;
//    }
//

//
//    public List<Cash> getCashesFree(String id, double amount){
//        fcClientEvent = FreeGetAPIs.getCashes(apiAccount.getApiUrl(),id,amount);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCashList(data);
//    }
//
//    public CidInfo getFidCid(String id){
//        fcClientEvent = FreeGetAPIs.getFidCid(apiAccount.getApiUrl(), id);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return gson.fromJson(gson.toJson(data), CidInfo.class);
//    }
//
//    public ApipClientEvent getTotals(){
//        return FreeGetAPIs.getTotals(apiAccount.getApiUrl());
//    }
//
//    public FcClientEvent cidInfoByIds(String[] ids){
//        fcClientEvent = IdentityAPIs.cidInfoByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        return fcClientEvent;
//    }
//    public List<CidInfo> cidInfoSearch(Fcdsl fcdsl){
//        fcClientEvent = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCidInfoList(data);
//    }
//    public List<CidInfo> cidInfoSearch(String searchStr){
//        fcClientEvent = IdentityAPIs.cidInfoSearchPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCidInfoList(data);
//    }
//
//    public List<CidHist> cidHistory(Fcdsl fcdsl){
//        fcClientEvent = IdentityAPIs.cidHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCidHistoryList(data);
//    }
//
//    public List<CidHist> homepageHistory(Fcdsl fcdsl){
//        fcClientEvent = IdentityAPIs.homepageHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCidHistoryList(data);
//    }
//
//    public List<CidHist> noticeFeeHistory(Fcdsl fcdsl){
//        fcClientEvent = IdentityAPIs.noticeFeeHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCidHistoryList(data);
//    }
//
//    public List<CidHist> reputationHistory(Fcdsl fcdsl){
//        fcClientEvent = IdentityAPIs.reputationHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCidHistoryList(data);
//    }
//
//    public Map<String, String[]> fidCidSeek(Fcdsl fcdsl){
//        fcClientEvent = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringArrayMap(data);
//    }
//
//    public Map<String, String[]> fidCidSeek(String searchStr){
//        fcClientEvent = IdentityAPIs.fidCidSeekPost(apiAccount.getApiUrl(), searchStr,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringArrayMap(data);
//    }
//
//    public Map<String, String[]> fidCidGetFree(String id){
//        fcClientEvent = IdentityAPIs.fidCidGetFree(apiAccount.getApiUrl(), id);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringArrayMap(data);
//    }
//
//    public Map<String, Nobody> nobodyByIds(String[] ids){
//        fcClientEvent = IdentityAPIs.nobodyByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getNobodyMap(data);
//    }
//    public List<Nobody> nobodySearch(Fcdsl fcdsl){
//        fcClientEvent = IdentityAPIs.nobodySearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getNobodyList(data);
//    }
//
//    public Map<String, String> avatars(String[] ids){
//        fcClientEvent = IdentityAPIs.avatarsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringMap(data);
//    }
//
//    public Service getServiceFree(){
//        fcClientEvent = OpenAPIs.getService(apiAccount.getApiUrl());
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return gson.fromJson(gson.toJson(data),Service.class);
//    }
//
//    public Session signIn(byte[] priKey, RequestBody.SignInMode mode_RefreshOrNull){
//        fcClientEvent = OpenAPIs.signInPost(apiAccount.getApiUrl(),apiAccount.getVia(),priKey,mode_RefreshOrNull);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return gson.fromJson(gson.toJson(data), Session.class);
//    }
//
//    public Map<String, String> totals(){
//        fcClientEvent = OpenAPIs.totalsPost(apiAccount.getApiUrl(),apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringMap(data);
//    }
//    public Map<String, String> totalsFree() {
//        fcClientEvent = OpenAPIs.totalsGet(apiAccount.getApiUrl());
//        Object data = checkApipV1Result();
//        if (data == null) return null;
//        return DataGetter.getStringMap(data);
//    }
//
//    public Object general(String index, Fcdsl fcdsl){
//        fcClientEvent = OpenAPIs.generalPost(index,apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        return checkApipV1Result();
//    }
//
//    public Map<String, Group> groupByIds(String[] ids){
//        fcClientEvent = OrganizeAPIs.groupByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getGroupMap(data);
//    }
//    public List<Group> groupSearch(Fcdsl fcdsl){
//        fcClientEvent = OrganizeAPIs.groupSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getGroupList(data);
//    }
//
//    public List<GroupHistory> groupOpHistory(Fcdsl fcdsl){
//        fcClientEvent = OrganizeAPIs.groupOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getGroupHistoryList(data);
//    }
//    public Map<String, String[]> groupMembers(String[] ids){
//        fcClientEvent = OrganizeAPIs.groupMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringArrayMap(data);
//    }
//
//    public List<MyGroupData> myGroups(String fid){
//        fcClientEvent = OrganizeAPIs.myGroupsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getMyGroupList(data);
//    }
//
//
//    public Map<String, Team> teamByIds(String[] ids){
//        fcClientEvent = OrganizeAPIs.teamByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTeamMap(data);
//    }
//    public List<Team> teamSearch(Fcdsl fcdsl){
//        fcClientEvent = OrganizeAPIs.teamSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTeamList(data);
//    }
//
//    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl){
//        fcClientEvent = OrganizeAPIs.teamOpHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTeamHistoryList(data);
//    }
//
//    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl){
//        fcClientEvent = OrganizeAPIs.teamRateHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTeamHistoryList(data);
//    }
//    public Map<String, String[]> teamMembers(String[] ids){
//        fcClientEvent = OrganizeAPIs.teamMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringArrayMap(data);
//    }
//
//    public Map<String, String[]> teamExMembers(String[] ids){
//        fcClientEvent = OrganizeAPIs.teamExMembersPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringArrayMap(data);
//    }
//    public TeamOtherPersonsData teamOtherPersons(String[] ids){
//        fcClientEvent = OrganizeAPIs.teamOtherPersonsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return gson.fromJson(gson.toJson(data), TeamOtherPersonsData.class);
//    }
//    public List<MyTeamData> myTeams(String fid){
//        fcClientEvent = OrganizeAPIs.myTeamsPost(apiAccount.getApiUrl(), fid,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getMyTeamList(data);
//    }
//
//
//    public Map<String, Box> boxByIds(String[] ids){
//        fcClientEvent = PersonalAPIs.boxByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getBoxMap(data);
//    }
//    public List<Box> boxSearch(Fcdsl fcdsl){
//        fcClientEvent = PersonalAPIs.boxSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getBoxList(data);
//    }
//
//    public List<BoxHistory> boxHistory(Fcdsl fcdsl){
//        fcClientEvent = PersonalAPIs.boxHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getBoxHistoryList(data);
//    }
//
//    public Map<String, Contact> contactByIds(String[] ids){
//        fcClientEvent = PersonalAPIs.contactByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getContactMap(data);
//    }
//    public List<Contact> contacts(Fcdsl fcdsl){
//        fcClientEvent = PersonalAPIs.contactsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getContactList(data);
//    }
//
//    public List<Contact> contactsDeleted(Fcdsl fcdsl){
//        fcClientEvent = PersonalAPIs.contactsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getContactList(data);
//    }
//
//    public Map<String, Mail> mailByIds(String[] ids){
//        fcClientEvent = PersonalAPIs.mailByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getMailMap(data);
//    }
//    public List<Mail> mails(Fcdsl fcdsl){
//        fcClientEvent = PersonalAPIs.mailsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getMailList(data);
//    }
//
//    public List<Mail> mailsDeleted(Fcdsl fcdsl){
//        fcClientEvent = PersonalAPIs.mailsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getMailList(data);
//    }
//    public Map<String, Secret> secretByIds(String[] ids){
//        fcClientEvent = PersonalAPIs.secretByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSecretMap(data);
//    }
//    public List<Secret> secrets(Fcdsl fcdsl){
//        fcClientEvent = PersonalAPIs.secretsPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSecretList(data);
//    }
//
//    public List<Secret> secretsDeleted(Fcdsl fcdsl){
//        fcClientEvent = PersonalAPIs.secretsDeletedPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSecretList(data);
//    }
//
//    public Map<String, Token> tokenByIds(String[] ids){
//        fcClientEvent = PublishAPIs.tokenByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTokenMap(data);
//    }
//    public List<Token> tokenSearch(Fcdsl fcdsl){
//        fcClientEvent = PublishAPIs.tokenSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTokenList(data);
//    }
//
//    public List<TokenHistory> tokenHistory(Fcdsl fcdsl){
//        fcClientEvent = PublishAPIs.tokenHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTokenHistoryList(data);
//    }
//    public List<TokenHolder> myTokens(Fcdsl fcdsl){
//        fcClientEvent = PublishAPIs.myTokensPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTokenHolderList(data);
//    }
//    public Map<String, TokenHolder> tokenHolderByIds(String[] ids){
//        fcClientEvent = PublishAPIs.tokenHolderByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getTokenHolderMap(data);
//    }
//
//    public Map<String, Proof> proofByIds(String[] ids){
//        fcClientEvent = PublishAPIs.proofByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getProofMap(data);
//    }
//    public List<Proof> proofSearch(Fcdsl fcdsl){
//        fcClientEvent =  PublishAPIs.proofSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getProofList(data);
//    }
//
//    public List<ProofHistory> proofHistory(Fcdsl fcdsl){
//        fcClientEvent = PublishAPIs.proofHistoryPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getProofHistoryList(data);
//    }
//
//    public Map<String, Statement> statementByIds(String[] ids){
//        fcClientEvent =  PublishAPIs.statementByIdsPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStatementMap(data);
//    }
//    public List<Statement> statementSearch(Fcdsl fcdsl){
//        fcClientEvent = PublishAPIs.statementSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStatementList(data);
//    }
//
//    public List<Nid> nidSearch(Fcdsl fcdsl){
//        fcClientEvent = PublishAPIs.nidSearchPost(apiAccount.getApiUrl(), fcdsl,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getNidList(data);
//    }
//
//    public String decodeRawTx(String rawTxHex){
//        fcClientEvent = WalletAPIs.decodeRawTxPost(apiAccount.getApiUrl(), rawTxHex,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String)data;
//    }
//
//    public List<Cash> cashValidForPay(String fid, double amount){
//        fcClientEvent = WalletAPIs.cashValidForPayPost(apiAccount.getApiUrl(), fid,amount,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCashList(data);
//    }
//
//    public List<Cash> cashValidForCd(String fid, int cd){
//        fcClientEvent = WalletAPIs.cashValidForCdPost(apiAccount.getApiUrl(), fid,cd,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getCashList(data);
//    }
//    public List<UnconfirmedInfo> unconfirmed(String[] ids){
//        fcClientEvent = WalletAPIs.unconfirmedPost(apiAccount.getApiUrl(), ids,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getUnconfirmedList(data);
//    }
//
//    public String swapRegister(String sid){
//        fcClientEvent = SwapHallAPIs.swapRegisterPost(apiAccount.getApiUrl(), sid,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return (String)data;
//    }
//
//    public List<String> swapUpdate(Map<String, Object> uploadMap){
//        fcClientEvent = SwapHallAPIs.swapUpdatePost(apiAccount.getApiUrl(), uploadMap,apiAccount.getVia(),sessionKey);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getStringList(data);
//    }
//    public SwapStateData swapState(String sid, String[] last){
//        fcClientEvent = SwapHallAPIs.getSwapState(apiAccount.getApiUrl(), sid);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        try{
//            return gson.fromJson(gson.toJson(data),SwapStateData.class);
//        }catch (Exception e){
//            fcClientEvent.setMessage(fcClientEvent.getMessage()+(String)data);
//            return null;
//        }
//    }
//
//    public SwapLpData swapLp(String sid, String[] last){
//        fcClientEvent = SwapHallAPIs.getSwapLp(apiAccount.getApiUrl(), sid);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        try{
//            return gson.fromJson(gson.toJson(data),SwapLpData.class);
//        }catch (Exception e){
//            fcClientEvent.setMessage(fcClientEvent.getMessage()+(String)data);
//            return null;
//        }
//    }
//
//    public List<SwapAffair> swapFinished(String sid, String[] last){
//        fcClientEvent = SwapHallAPIs.getSwapFinished(apiAccount.getApiUrl(), sid,last);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSwapAffairList(data);
//    }
//
//    public List<SwapAffair> swapPending(String sid){
//        fcClientEvent = SwapHallAPIs.getSwapPending(apiAccount.getApiUrl(), sid);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSwapAffairList(data);
//    }
//
//    public List<SwapPriceData> swapPrices(String sid, String gTick, String mTick, List<String> last){
//        fcClientEvent = SwapHallAPIs.getSwapPrice(apiAccount.getApiUrl(), sid,gTick,mTick,last);
//        Object data = checkApipV1Result();
//        if(data==null)return null;
//        return DataGetter.getSwapPriceDataList(data);
//    }
    public void setClientData(ApipClientEvent clientData) {
        this.fcClientEvent = clientData;
    }

//    public Fcdsl getFcdsl() {
//        return fcdsl;
//    }
//
//    public void setFcdsl(Fcdsl fcdsl) {
//        this.fcdsl = fcdsl;
//    }

}
