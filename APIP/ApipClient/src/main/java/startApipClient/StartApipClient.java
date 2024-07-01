package startApipClient;


import apip.apipData.CidInfo;
import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Session;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import clients.apipClient.*;
import com.google.gson.Gson;
import config.ApiAccount;
import config.Configure;
import constants.ApiNames;
import crypto.Decryptor;
import crypto.Hash;
import crypto.KeyTools;
import crypto.old.EccAes256K1P7;
import fch.Wallet;
import fch.fchData.SendTo;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.ImageTools;
import javaTools.JsonTools;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static clients.apipClient.ApipClient.setCid;
import static clients.apipClient.ApipClient.setMaster;
import static config.ApiAccount.decryptSessionKey;
import static constants.Constants.APIP_Account_JSON;
import static fch.Inputer.inputGoodFid;

public class StartApipClient {
    public static final int DEFAULT_SIZE = 20;
    private static String fid;
    private static CidInfo fidInfo;
    private static BufferedReader br;
    private static ApipClient apipClient;
    private static String userPriKeyCipher;
    private static long bestHeight;
    private static ApipClientSettings settings;


    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("APIP");

        //Load config info from the file of config.json
        Configure configure = Configure.loadConfig(br);

        byte[] symKey = configure.checkPassword(configure);

        fid = configure.chooseMainFid(symKey);

        settings = ApipClientSettings.loadFromFile(fid,ApipClientSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new ApipClientSettings();
        settings.initiateClient(fid, symKey,configure,br);

        if(settings.getApipAccount()!=null)
            apipClient = (ApipClient) settings.getApipAccount().getClient();

        bestHeight = new Wallet(apipClient).getBestHeight();
        fidInfo = settings.checkFidInfo(apipClient,br);
        userPriKeyCipher = configure.getFidCipherMap().get(fid);

        if(fidInfo!=null && fidInfo.getCid()==null){
            if(fch.Inputer.askIfYes(br,"No CID yet. Set CID?")){
                setCid(fid,userPriKeyCipher,bestHeight, symKey,apipClient,br);
                return;
            }
        }

        if(fidInfo!=null &&fidInfo.getMaster()==null){
            if(fch.Inputer.askIfYes(br,"No master yet. Set master for this FID?")){
                setMaster(fid,userPriKeyCipher,bestHeight, symKey,apipClient,br);
                return;
            }
        }

        Menu menu = new Menu();
        menu.setName("Apip Client");
        ArrayList<String> menuItemList = new ArrayList<>();
        menuItemList.add("Example");
        menuItemList.add("FreeGet");
        menuItemList.add("OpenAPI");
        menuItemList.add("Blockchain");
        menuItemList.add("Identity");
        menuItemList.add("Organize");
        menuItemList.add("Construct");
        menuItemList.add("Personal");
        menuItemList.add("Publish");
        menuItemList.add("Wallet");
        menuItemList.add("CryptoTools");
        menuItemList.add("SwapTools");
        menuItemList.add("Settings");

        menu.add(menuItemList);

        while (true) {
            System.out.println(" << APIP Client>>");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> showExample();
                case 2 -> basicApi();
//                case 3 -> openAPI(symKey);
//                case 4 -> blockchain();
//                case 5 -> identity();
//                case 6 -> organize();
//                case 7 -> construct();
//                case 8 -> personal();
//                case 9 -> publish();
//                case 10 -> wallet();
//                case 11 -> cryptoTools();
//                case 12 -> swapTools();
//                case 13 -> setting(symKey);
                case 0 -> {
                    BytesTools.clearByteArray(symKey);
                    BytesTools.clearByteArray(apipClient.getSessionKey());
                    return;
                }
            }
        }
    }

    public static void showExample() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields("owner", "issuer").addNewValues("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        fcdsl.getQuery().addNewRange().addNewFields("cd").addGt("1").addLt("100");
        fcdsl.addNewFilter().addNewPart().addNewFields("issuer").addNewValue("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        fcdsl.addNewExcept().addNewEquals().addNewFields("cd").addNewValues("1", "2");
        fcdsl.addSort("cd", "desc").addSize(2).addAfter("56");
        if (fcdsl.isBadFcdsl()) return;
        System.out.println("Java code:");
        Shower.printUnderline(20);
        String code = """
                public static void showExample( ) {
                \tFcdsl fcdsl = new Fcdsl();
                \tfcdsl.addNewQuery().addNewTerms().addNewFields("owner","issuer").addNewValues("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
                \tfcdsl.getQuery().addNewRange().addNewFields("cd").addGt("1").addLt("100");
                \tfcdsl.addNewFilter().addNewPart().addNewFields("issuer").addNewValue("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
                \tfcdsl.addNewExcept().addNewEquals().addNewFields("cd").addNewValues("1","2");
                \tfcdsl.addNewSort("cd","desc").addSize(2).addNewAfter("56");
                \tif(!fcdsl.checkFcdsl())return;
                \tApipClient apipClientEvent =OpenAPIs.generalPost(IndicesNames.CASH,initApipParamsForClient.getUrlHead(),fcdsl, initApipParamsForClient.getVia(), sessionKey);
                \tGson gson = new GsonBuilder().setPrettyPrinting().create();
                \tSystem.out.println("Request header:\\n"+FCH.ParseTools.gsonString(apipClientEvent.getRequestHeaderMap()));
                \tSystem.out.println("Request body:\\n"+gson.toJson(apipClientEvent.getRequestBody()));
                \tSystem.out.println("Response header:\\n"+FCH.ParseTools.gsonString(apipClientEvent.getResponseHeaderMap()));
                \tSystem.out.println("Response body:\\n"+gson.toJson(apipClientEvent.getResponseBody()));
                }""";
        System.out.println(code);
        System.out.println("Requesting ...");
//        ApipClientEvent apipClientEvent = OpenAPIs.generalPost(IndicesNames.CASH,  fcdsl);
//
//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
//        String responseBodyJson = gson.toJson(apipClientEvent.getResponseBody());
//        Shower.printUnderline(20);
//        System.out.println("Request header:\n" + JsonTools.toNiceJson(apipClientEvent.getRequestHeaderMap()));
//        Shower.printUnderline(20);
//        System.out.println("Request body:\n" + gson.toJson(apipClientEvent.getRequestBody()));
//        Shower.printUnderline(20);
//        System.out.println("Response header:\n" + JsonTools.toNiceJson(apipClientEvent.getResponseHeaderMap()));
//        Shower.printUnderline(20);
//        System.out.println("Response body:");
//        Shower.printUnderline(20);
//        System.out.println(responseBodyJson);
//        Shower.printUnderline(20);

        Menu.anyKeyToContinue(br);
    }

    public static void basicApi() {
        while (true) {
            Menu menu = new Menu();
            menu.setName("Free GET methods");
            menu.add(ApiNames.FreeGetAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
//                case 1 -> broadcast();
//                case 2 -> getApps();
//                case 3 -> getServices();
//                case 4 -> getAvatar();
//                case 5 -> getCashes();
//                case 6 -> getFidCid();
//                case 7 -> getFreeService();
//                case 8 -> getService();
//                case 9 -> getTotals();
                case 0 -> {
                    return;
                }
            }
        }
    }

//    public static void broadcast() {
//        System.out.println("Input the rawTx:");
//        String rawTx = Inputer.inputString(br);
//        System.out.println("Broadcasting...");
//        ApipClientEvent diskClientData = FreeGetAPIs.broadcast( rawTx);
//        System.out.println(diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void getCashes() {
//        String id = inputGoodFid(br, "Input the FID:");
//        System.out.println("Getting cashes...");
//        ApipClientEvent diskClientData = FreeGetAPIs.getCashes( id, 0);
//        System.out.println(diskClientData.getResponseBodyStr());
//        JsonTools.gsonPrint(DataGetter.getCashList(diskClientData.getResponseBody().getData()));
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void getApps() {
//        System.out.println("Input the aid or enter to ignore:");
//        String id = Inputer.inputString(br);
//        if ("".equals(id)) id = null;
//        System.out.println("Getting APPs...");
//        ApipClientEvent diskClientData = FreeGetAPIs.getApps( id);
//        System.out.println(diskClientData.getResponseBodyStr());
//        ;
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void getServices() {
//        System.out.println("Input the sid or enter to ignore:");
//        String id = Inputer.inputString(br);
//        if ("".equals(id)) id = null;
//        System.out.println("Getting services...");
//        ApipClientEvent diskClientData = FreeGetAPIs.getServices( id);
//        System.out.println(diskClientData.getResponseBodyStr());
//        ;
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void getTotals() {
//        System.out.println("Getting totals...");
//        ApipClientEvent diskClientData = FreeGetAPIs.getTotals(apipAccount.getApiUrl());
//        System.out.println(diskClientData.getResponseBodyStr());
//        ;
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void getFreeService() {
//        System.out.println("Getting the free service and the sessionKey...");
//        ApipClientEvent diskClientData = FreeGetAPIs.getFreeService(apipAccount.getApiUrl());
//        System.out.println(diskClientData.getResponseBodyStr());
//        ;
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void getService() {
//        System.out.println("Getting the default service information...");
//        ApipClientEvent diskClientData = OpenAPIs.getService(apipAccount.getApiUrl());
//        System.out.println(diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void openAPI(byte[] symKey) {
//        System.out.println("OpenAPI...");
//        Menu menu = new Menu();
//
//        ArrayList<String> menuItemList = new ArrayList<>();
//        menu.setName("OpenAPI");
//        menuItemList.add("getService");
//        menuItemList.add("SignInPost");
//        menuItemList.add("SignInEccPost");
//        menuItemList.add("TotalsGet");
//        menuItemList.add("TotalsPost");
//        menuItemList.add("generalPost");
//
//
//        menu.add(menuItemList);
//
//        while (true) {
//            System.out.println(" << Maker manager>>");
//            menu.show();
//            int choice = menu.choose(br);
//            switch (choice) {
//                case 1 -> getService();
//                case 2 -> signInPost(symKey, null);
//                case 3 -> signInEccPost(symKey, null);
//                case 4 -> totalsGet();
//                case 5 -> totalsPost();
//                case 6 -> generalPost();
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void generalPost( ) {
//        Fcdsl fcdsl = new Fcdsl();
//        System.out.println("Input the index name. Enter to exit:");
//        String input = Inputer.inputString(br);
//        if ("".equals(input)) return;
//        fcdsl.setIndex(input);
//
//        fcdsl.promoteInput();
//
//        if (fcdsl.isBadFcdsl()) {
//            System.out.println("Fcdsl wrong:");
//            System.out.println(JsonTools.toNiceJson(fcdsl));
//            return;
//        }
//        System.out.println(JsonTools.toNiceJson(fcdsl));
//        Menu.anyKeyToContinue(br);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OpenAPIs.generalPost(fcdsl.getIndex(),  fcdsl);
//        System.out.println(diskClientData.getResponseBodyStr());
//        ;
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void totalsGet() {
//        System.out.println("Get request for totals...");
//        ApipClientEvent diskClientData = OpenAPIs.totalsGet(apipAccount.getApiUrl());
//        System.out.println(diskClientData.getResponseBodyStr());
//        ;
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void totalsPost( ) {
//        System.out.println("Post request for totals...");
//        ApipClientEvent diskClientData = OpenAPIs.totalsPost( );
//        System.out.println(diskClientData.getResponseBodyStr());
//        ;
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static byte[] signInEccPost(byte[] symKey, RequestBody.SignInMode mode) {
//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(userPriKeyCipher, symKey);
//        if (priKey == null) return null;
//        System.out.println("Sign in for EccAES256K1P7 encrypted sessionKey...");
//        ApipClientEvent diskClientData = OpenAPIs.signInEccPost( priKey, mode);
//
//        System.out.println(diskClientData.getResponseBodyStr());
//
//        Gson gson = new Gson();
//        Session session = gson.fromJson(gson.toJson(diskClientData.getResponseBody().getData()), Session.class);
//        String sessionKeyCipherFromApip = session.getSessionKeyCipher();
//        byte[] newSessionKey = new Decryptor().decryptJsonByAsyOneWay(sessionKeyCipherFromApip,priKey).getData();
//
//        ApiAccount.updateSession(apipAccount,symKey, session, newSessionKey);
//        ApiAccount.writeApipParamsToFile(apipAccount, APIP_Account_JSON);
//
//        return newSessionKey;
//    }
//
//    public static byte[] signInPost(byte[] symKey, RequestBody.SignInMode mode) {
//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(userPriKeyCipher, symKey);
//        if (priKey == null) return null;
//
//        System.out.println("Sign in...");
//        ApipClientEvent diskClientData = OpenAPIs.signInPost(  priKey, mode);
//        System.out.println(JsonTools.toNiceJson(diskClientData.getResponseBody()));
//
//        Gson gson = new Gson();
//        Session session = gson.fromJson(gson.toJson(diskClientData.getResponseBody().getData()), Session.class);
//
//        byte[] newSessionKey = HexFormat.of().parseHex(session.getSessionKey());
//
//        ApiAccount.updateSession(apipAccount,symKey, session, newSessionKey);
//        ApiAccount.writeApipParamsToFile(apipAccount, APIP_Account_JSON);
//        return newSessionKey;
//    }
//
//    public static void blockchain( ) {
//        System.out.println("Blockchain...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Blockchain");
//            menu.add(ApiNames.BlockchainAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> blockByIds();
//                case 2 -> blockSearch(DEFAULT_SIZE, "height:desc->blockId:asc");
//                case 3 -> blockByHeights();
//                case 4 -> cashByIds();
//                case 5 -> cashSearch(DEFAULT_SIZE, "valid:desc->birthHeight:desc->cashId:asc");
//                case 6 -> cashValid(DEFAULT_SIZE, "cd:asc->value:desc->cashId:asc");
//                case 7 -> fidByIds();
//                case 8 -> fidSearch(DEFAULT_SIZE, "lastHeight:desc->fid:asc");
//                case 9 -> opReturnByIds();
//                case 10 -> opReturnSearch(DEFAULT_SIZE, "height:desc->txIndex:desc->txId:asc");
//                case 11 -> p2shByIds();
//                case 12 -> p2shSearch(DEFAULT_SIZE, "birthHeight:desc->fid:asc");
//                case 13 -> txByIds();
//                case 14 -> txSearch(DEFAULT_SIZE, "height:desc->txId:asc");
//                case 15 -> chainInfo();
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void blockByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input blockIds:", 0);
//        System.out.println("Requesting blockByIds...");
//        ApipClientEvent diskClientData = BlockchainAPIs.blockByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void blockSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting blockSearch...");
//        ApipClientEvent diskClientData = BlockchainAPIs.blockSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void blockByHeights( ) {
//        String[] heights = Inputer.inputStringArray(br, "Input block heights:", 0);
//        System.out.println("Requesting blockByHeights...");
//        ApipClientEvent diskClientData = BlockchainAPIs.blockByHeightPost( heights);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void cashByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input cashIds:", 0);
//        System.out.println("Requesting cashByIds...");
//        ApipClientEvent diskClientData = BlockchainAPIs.cashByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void cashValid(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting cashValid...");
//        ApipClientEvent diskClientData = BlockchainAPIs.cashValidPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void cashSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting cashSearch...");
//        ApipClientEvent diskClientData = BlockchainAPIs.cashSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void fidByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
//        System.out.println("Requesting fidByIds...");
//        ApipClientEvent diskClientData = BlockchainAPIs.fidByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void fidSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting fidSearch...");
//        ApipClientEvent diskClientData = BlockchainAPIs.fidSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void opReturnByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input opReturnIds:", 0);
//        System.out.println("Requesting opReturnByIds...");
//        ApipClientEvent diskClientData = BlockchainAPIs.opReturnByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void opReturnSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting opReturnSearch...");
//        ApipClientEvent diskClientData = BlockchainAPIs.opReturnSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void p2shByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input p2shIds:", 0);
//        System.out.println("Requesting p2shByIds...");
//        ApipClientEvent diskClientData = BlockchainAPIs.p2shByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void p2shSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting p2shSearch...");
//        ApipClientEvent diskClientData = BlockchainAPIs.p2shSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void txByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input txIds:", 0);
//        System.out.println("Requesting txByIds...");
//        ApipClientEvent diskClientData = BlockchainAPIs.txByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void txSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting txSearch...");
//        ApipClientEvent diskClientData = BlockchainAPIs.txSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void chainInfo() {
//        System.out.println("Requesting chainInfo...");
//        ApipClientEvent diskClientData = BlockchainAPIs.chainInfo(apipAccount.getApiUrl());
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//
//    }
//
//    @Nullable
//    public static Fcdsl inputFcdsl(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = new Fcdsl();
//
//        fcdsl.promoteSearch(defaultSize, defaultSort,br);
//
//        if (fcdsl.isBadFcdsl()) {
//            System.out.println("Fcdsl wrong:");
//            System.out.println(JsonTools.toNiceJson(fcdsl));
//            return null;
//        }
//        System.out.println("fcdsl:\n" + JsonTools.toNiceJson(fcdsl));
//
//        Menu.anyKeyToContinue(br);
//        return fcdsl;
//    }
//
//
//    public static void identity( ) {
//        System.out.println("Identity...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Identity");
//            menu.add(ApiNames.IdentityAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> cidInfoByIds();
//                case 2 -> cidInfoSearch(DEFAULT_SIZE, "nameTime:desc->fid:asc");
//                case 3 -> cidHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 4 -> fidCidSeek(DEFAULT_SIZE, "lastHeight:desc->cashId:asc");
//                case 5 -> getFidCid();
//                case 6 -> nobodyByIds();
//                case 7 -> nobodySearch(DEFAULT_SIZE, "deathHeight:desc->deathTxIndex:desc");
//                case 8 -> homepageHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 9 -> noticeFeeHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 10 -> reputationHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 11 -> avatars();
//                case 12 -> getAvatar();
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void cidInfoByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
//        System.out.println("Requesting cidInfoByIds...");
//        ApipClientEvent diskClientData = IdentityAPIs.cidInfoByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void cidInfoSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.cidInfoSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void fidCidSeek(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.fidCidSeekPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void getFidCid() {
//        System.out.println("Input FID or CID:");
//        String id = Inputer.inputString(br);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = FreeGetAPIs.getFidCid( id);
//        System.out.println(diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void nobodyByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.nobodyByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void nobodySearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.nobodySearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void cidHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.cidHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void homepageHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.homepageHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void noticeFeeHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.noticeFeeHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void reputationHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.reputationHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void avatars( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = IdentityAPIs.avatarsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void getAvatar() {
//        String fid = inputGoodFid(br, "Input FID:");
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = FreeGetAPIs.getAvatar( fid);
//        byte[] imageBytes = diskClientData.getResponseBodyBytes();
//        ImageTools.displayPng(imageBytes);
//        ImageTools.savePng(imageBytes, "test.png");
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void organize( ) {
//        System.out.println("Organize...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Organize");
//            menu.add(ApiNames.OrganizeAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> groupByIds();
//                case 2 -> groupSearch(DEFAULT_SIZE, "tCdd:desc->gid:asc");
//                case 3 -> groupMembers();
//                case 4 -> groupOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 5 -> myGroups();
//                case 6 -> teamByIds();
//                case 7 -> teamSearch(DEFAULT_SIZE, "active:desc->tRate:desc->tid:asc");
//                case 8 -> teamMembers();
//                case 9 -> teamExMembers();
//                case 10 -> teamOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 11 -> teamRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 12 -> teamOtherPersons();
//                case 13 -> myTeams();
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void groupByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input GIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.groupByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void groupSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.groupSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void groupMembers( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input GIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.groupMembersPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void groupOpHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        ApipClientEvent diskClientData = OrganizeAPIs.groupOpHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void myGroups( ) {
//        System.out.println("Input the FID. Enter to exit:");
//        String id = Inputer.inputString(br);
//        if ("".equals(id)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.myGroupsPost( id);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void teamByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
//        ApipClientEvent diskClientData = OrganizeAPIs.teamByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void teamSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.teamSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void teamMembers( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.teamMembersPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void teamExMembers( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.teamExMembersPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void teamRateHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.teamRateHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void teamOpHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.teamOpHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void teamOtherPersons( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.teamOtherPersonsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void myTeams( ) {
//        System.out.println("Input the FID. Enter to exit:");
//        String id = Inputer.inputString(br);
//        if ("".equals(id)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = OrganizeAPIs.myTeamsPost( id);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void construct( ) {
//        System.out.println("Construct...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Construct");
//            menu.add(ApiNames.ConstructAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> protocolByIds();
//                case 2 -> protocolSearch(DEFAULT_SIZE, "active:desc->tRate:desc->pid:asc");
//                case 3 -> protocolOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 4 -> protocolRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 5 -> codeByIds();
//                case 6 -> codeSearch(DEFAULT_SIZE, "active:desc->tRate:desc->codeId:asc");
//                case 7 -> codeOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 8 -> codeRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 9 -> serviceByIds();
//                case 10 -> serviceSearch(DEFAULT_SIZE, "active:desc->tRate:desc->sid:asc");
//                case 11 -> serviceOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 12 -> serviceRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 13 -> appByIds();
//                case 14 -> appSearch(DEFAULT_SIZE, "active:desc->tRate:desc->aid:asc");
//                case 15 -> appOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 16 -> appRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void protocolByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input PIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.protocolByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void protocolSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.protocolSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void protocolRateHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.protocolRateHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void protocolOpHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.protocolOpHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void codeByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input codeIds:", 0);
//        ApipClientEvent diskClientData = ConstructAPIs.codeByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void codeSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.codeSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void codeRateHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.codeRateHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void codeOpHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.codeOpHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void serviceByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input SIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.serviceByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void serviceSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.serviceSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void serviceRateHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.serviceRateHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void serviceOpHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.serviceOpHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void appByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input AIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.appByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void appSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.appSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void appRateHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.appRateHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void appOpHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.appOpHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void personal( ) {
//        System.out.println("Personal...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Personal");
//            menu.add(ApiNames.PersonalAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> boxByIds();
//                case 2 -> boxSearch(DEFAULT_SIZE, "lastHeight:desc->bid:asc");
//                case 3 -> boxHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 4 -> contactByIds();
//                case 5 -> contacts(DEFAULT_SIZE, "birthHeight:desc->contactId:asc");
//                case 6 -> contactsDeleted(DEFAULT_SIZE, "lastHeight:desc->contactId:asc");
//                case 7 -> secretByIds();
//                case 8 -> secrets(DEFAULT_SIZE, "birthHeight:desc->secretId:asc");
//                case 9 -> secretsDeleted(DEFAULT_SIZE, "lastHeight:desc->secretId:asc");
//                case 10 -> mailByIds();
//                case 11 -> mails(DEFAULT_SIZE, "birthHeight:desc->mailId:asc");
//                case 12 -> mailsDeleted(DEFAULT_SIZE, "lastHeight:desc->mailId:asc");
//                case 13 -> mailThread(DEFAULT_SIZE, "birthHeight:desc->mailId:asc");
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void boxByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input BIDs:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.boxByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void boxSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.boxSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void boxHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.boxHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void contactByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input contactIds:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.contactByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void contacts(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.contactsPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void contactsDeleted(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.contactsDeletedPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void secretByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input secretIds:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.secretByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void secrets(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.secretsPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void secretsDeleted(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.secretsDeletedPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void mailByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input mailIds:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.mailByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void mails(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.mailsPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void mailsDeleted(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.mailsDeletedPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void mailThread(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PersonalAPIs.mailThreadPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void publish( ) {
//        System.out.println("Publish...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Publish");
//            menu.add(ApiNames.PublishAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> proofByIds();
//                case 2 -> proofSearch(DEFAULT_SIZE, "lastHeight:desc->bid:asc");
//                case 3 -> proofHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 4 -> statementByIds();
//                case 5 -> statementSearch(DEFAULT_SIZE, "birthHeight:desc->contactId:asc");
//                case 6 -> nidSearch(DEFAULT_SIZE, "birthHeight:desc->nameId:asc");
//                case 7 -> tokenByIds();
//                case 8 -> tokenSearch(DEFAULT_SIZE, "lastHeight:desc->tokenId:asc");
//                case 9 -> tokenHistory(DEFAULT_SIZE, "height:desc->index:desc");
//                case 10 -> tokenHolderByIds();
//                case 11 -> myTokens(DEFAULT_SIZE, "lastHeight:desc->id:asc");
//                case 12 -> tokenHolders(DEFAULT_SIZE, "lastHeight:desc->id:asc");
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void tokenByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input tokenIds:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.tokenByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void tokenSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.tokenSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void tokenHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.tokenHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void tokenHolderByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input tokenHolderIds:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.tokenHolderByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void myTokens(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.myTokensPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void tokenHolders(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.tokenHoldersPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void proofByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input proofIds:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.proofByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void proofSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.proofSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void proofHistory(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.proofHistoryPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void statementByIds( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input statementIds:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.statementByIdsPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void statementSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.statementSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void nidSearch(int defaultSize, String defaultSort) {
//        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
//        if (fcdsl == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = PublishAPIs.nidSearchPost( fcdsl);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void wallet( ) {
//        System.out.println("Wallet...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Wallet");
//            menu.add(ApiNames.WalletAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> broadcastTx();
//                case 2 -> decodeRawTx();
//                case 3 -> cashValidForPay();
//                case 4 -> cashValidForCd();
//                case 5 -> unconfirmed();
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void broadcastTx( ) {
//        String txHex;
//        while (true) {
//            System.out.println("Input the hex of the TX:");
//            txHex = Inputer.inputString(br);
//            if (Hex.isHexString(txHex)) {
//                System.out.println("It's not a hex. Try again.");
//                break;
//            }
//        }
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = WalletAPIs.broadcastTxPost( txHex);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void decodeRawTx( ) {
//        String txHex;
//        while (true) {
//            System.out.println("Input the hex of the raw TX:");
//            txHex = Inputer.inputString(br);
//            if (Hex.isHexString(txHex)) break;
//            System.out.println("It's not a hex. Try again.");
//        }
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = WalletAPIs.decodeRawTxPost( txHex);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void cashValidForPay( ) {
//        String fid;
//
//        while (true) {
//            System.out.println("Input the sender's FID:");
//            fid = Inputer.inputString(br);
//            if (KeyTools.isValidFchAddr(fid)) break;
//            System.out.println("It's not a FID. Try again.");
//        }
//        Double amount = Inputer.inputDouble(br, "Input the amount:");
//        if (amount == null) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = WalletAPIs.cashValidForPayPost( fid, amount);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void cashValidForCd( ) {
//        String fid;
//
//        while (true) {
//            System.out.println("Input the sender's FID:");
//            fid = Inputer.inputString(br);
//            if (KeyTools.isValidFchAddr(fid)) break;
//            System.out.println("It's not a FID. Try again.");
//        }
//        int cd = Inputer.inputInteger(br, "Input the required CD:", 0);
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = WalletAPIs.cashValidForCdPost( fid, cd);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void unconfirmed( ) {
//        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
//        ApipClientEvent diskClientData = WalletAPIs.unconfirmedPost( ids);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void cryptoTools( ) {
//        System.out.println("CryptoTools");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("CryptoTools");
//            menu.add(ApiNames.CryptoToolsAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> addresses();
//                case 2 -> encrypt();
//                case 3 -> verify();
//                case 4 -> sha256();
//                case 5 -> sha256x2();
//                case 6 -> sha256Bytes();
//                case 7 -> sha256x2Bytes();
//                case 8 -> offLineTx();
//                case 9 -> offLineTxByCd();
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static void addresses( ) {
//        System.out.println("Input the address or public key. Enter to exit:");
//        String addrOrKey = Inputer.inputString(br);
//        if ("".equals(addrOrKey)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = CryptoToolAPIs.addressesPost( addrOrKey);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void encrypt( ) {
//        System.out.println("Input the text. Enter to exit:");
//        String msg = Inputer.inputString(br);
//        if ("".equals(msg)) return;
//        System.out.println("Requesting ...");
//        System.out.println("Input the pubKey or symKey. Enter to exit:");
//        String key = Inputer.inputString(br);
//        if ("".equals(key)) return;
//
//        ApipClientEvent diskClientData = CryptoToolAPIs.encryptPost( key, msg);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void verify( ) {
//        System.out.println("Input the signature. Enter to exit:");
//        String signature = Inputer.inputString(br);
//        if ("".equals(signature)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = CryptoToolAPIs.verifyPost( signature);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void sha256( ) {
//        System.out.println("Input the text. Enter to exit:");
//        String text = Inputer.inputString(br);
//        if ("".equals(text)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = CryptoToolAPIs.sha256Post( text);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void sha256x2( ) {
//        System.out.println("Input the text. Enter to exit:");
//        String text = Inputer.inputString(br);
//        if ("".equals(text)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = CryptoToolAPIs.sha256x2Post( text);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void sha256Bytes( ) {
//        System.out.println("Input the text. Enter to exit:");
//        String text = Inputer.inputString(br);
//        if ("".equals(text)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = CryptoToolAPIs.sha256BytesPost( text);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void sha256x2Bytes( ) {
//        System.out.println("Input the text. Enter to exit:");
//        String text = Inputer.inputString(br);
//        if ("".equals(text)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = CryptoToolAPIs.sha256x2BytesPost( text);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void offLineTx( ) {
//
//        String fid = inputGoodFid(br, "Input the sender's FID:");
//
//        List<SendTo> sendToList = SendTo.inputSendToList();
//
//        System.out.println("Input the text of OpReturn. Enter to skip:");
//        String msg = Inputer.inputString(br);
//        if ("".equals(msg)) msg = null;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = CryptoToolAPIs.offLineTxPost( fid, sendToList, msg);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void offLineTxByCd( ) {
//
//        String fid = inputGoodFid(br, "Input the FID:");
//
//        int cd = Inputer.inputInteger(br, "Input the required CD:", 0);
//
//        List<SendTo> sendToList = SendTo.inputSendToList();
//
//        System.out.println("Input the text of OpReturn. Enter to skip:");
//        String msg = Inputer.inputString(br);
//        if ("".equals(msg)) msg = null;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = CryptoToolAPIs.offLineTxByCdPost( fid, sendToList, msg, cd);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//
//    public static void swapTools( ) {
//        System.out.println("SwapTools...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("SwapTools");
//            menu.add(ApiNames.SwapHallAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> swapRegister();
//                case 2 -> swapUpdate();
//                case 3 -> swapInfo();
//                case 4 -> swapState();
//                case 5 -> swapLp();
//                case 6 -> swapPending();
//                case 7 -> swapFinished();
//                case 8 -> swapPrice();
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    private static void swapRegister( ) {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.swapRegisterPost( sid);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void swapUpdate( ) {
//        System.out.println("In developing...");
//    }
//
//    private static void swapInfo() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapInfo( new String[]{sid}, null);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//
//        List<String> last = diskClientData.getResponseBody().getLast();
//        if (last != null&& !last.isEmpty()) {
//            diskClientData = SwapHallAPIs.getSwapInfo( new String[]{sid}, last);
//            System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        }
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void swapState() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapState( sid);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//    }
//
//    private static void swapLp() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapLp( sid);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//    }
//
//    private static void swapPending() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapPending( sid);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//    }
//
//    private static void swapFinished() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapFinished( sid, null);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//
//        List<String> last = diskClientData.getResponseBody().getLast();
//        if (last != null && !last.isEmpty()) {
//            diskClientData = SwapHallAPIs.getSwapInfo( new String[]{sid}, last);
//            System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        }
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void swapPrice() {
//        System.out.println("Input the sid. Enter to ignore:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) sid = null;
//
//        System.out.println("Input the sid. Enter to exit:");
//        String gTick = Inputer.inputString(br);
//        if ("".equals(gTick)) gTick = null;
//
//        System.out.println("Input the sid. Enter to exit:");
//        String mTick = Inputer.inputString(br);
//        if ("".equals(mTick)) mTick = null;
//
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapPrice( sid, gTick, mTick, null);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//
//        List<String> last = diskClientData.getResponseBody().getLast();
//        if (last != null && !last.isEmpty()) {
//            diskClientData = SwapHallAPIs.getSwapPrice( sid, gTick, mTick, last);
//            System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        }
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void setting( byte[] symKey)) {
//        System.out.println("Setting...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Settings");
//            menu.add("Check APIP", "Reset APIP", "Refresh SessionKey", "Change password");
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> checkApip(apipAccount);
//                case 2 -> resetApip(apipAccount, br);
//                case 3 -> sessionKey = refreshSessionKey(symKey);
//                case 4 -> {
//                    byte[] symKeyNew = resetPassword();
//                    if (symKeyNew == null) break;
//                    symKey = symKeyNew;
//                }
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static byte[] resetPassword() {
//
//        byte[] passwordBytesOld;
//        while (true) {
//            System.out.print("Check password. ");
//
//            passwordBytesOld = Inputer.getPasswordBytes();
//            byte[] sessionKey = decryptSessionKey(apipAccount.getSession().getSessionKeyCipher(), Hash.sha256x2(passwordBytesOld));
//            if (sessionKey != null) break;
//            System.out.println("Wrong password. Try again.");
//        }
//
//        byte[] passwordBytesNew;
//        passwordBytesNew = Inputer.inputAndCheckNewPassword();
//
//        byte[] symKeyOld = Hash.sha256x2(passwordBytesOld);
//
//        byte[] sessionKey = decryptSessionKey(apipAccount.getSession().getSessionKeyCipher(), symKeyOld);
//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(userPriKeyCipher, symKeyOld);
//
//        byte[] symKeyNew = Hash.sha256x2(passwordBytesNew);
//        String buyerPriKeyCipherNew = EccAes256K1P7.encryptWithSymKey(priKey, symKeyNew);
//        if(buyerPriKeyCipherNew.contains("Error"))return null;
//
//        String sessionKeyCipherNew = EccAes256K1P7.encryptWithSymKey(sessionKey, symKeyNew);
//        if (sessionKeyCipherNew.contains("Error")) {
//            System.out.println("Get sessionKey wrong:" + sessionKeyCipherNew);
//        }
//        apipAccount.getSession().setSessionKeyCipher(sessionKeyCipherNew);
//        apipAccount.setUserPriKeyCipher(buyerPriKeyCipherNew);
//
//        ApiAccount.writeApipParamsToFile(apipAccount, APIP_Account_JSON);
//        return symKeyNew;
//    }
//
//    public static byte[] refreshSessionKey(byte[] symKey) {
//        System.out.println("Refreshing ...");
//        return signInEccPost(symKey, RequestBody.SignInMode.REFRESH);
//    }
//
//    public static void checkApip(ApiAccount initApiAccount)) {
//        Shower.printUnderline(20);
//        System.out.println("Apip Service:");
//        String urlHead = initApiAccount.getApiUrl();
//        String[] ids = new String[]{initApiAccount.getProviderId()};
//        String via = initApiAccount.getVia();
//
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = ConstructAPIs.serviceByIdsPost(urlHead, ids, via, sessionKey);
//        System.out.println(diskClientData.getResponseBodyStr());
//
//        Shower.printUnderline(20);
//        System.out.println("User Params:");
//        System.out.println(JsonTools.toNiceJson(initApiAccount));
//        Shower.printUnderline(20);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void resetApip(ApiAccount initApiAccount)) {
//        byte[] passwordBytes = Inputer.getPasswordBytes();
//        initApiAccount.updateApipAccount(br, passwordBytes);
//    }
}
