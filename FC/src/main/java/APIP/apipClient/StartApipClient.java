package APIP.apipClient;

import APIP.ApipTools;
import APIP.apipClient.*;
import APIP.apipData.Fcdsl;
import APIP.apipData.SignInData;
import FCH.fchData.SendTo;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ApiAccount;
import constants.ApiNames;
import constants.IndicesNames;
import constants.Strings;
import crypto.cryptoTools.Hash;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1P7.EccAes256K1P7;
import crypto.eccAes256K1P7.EccAesDataByte;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.ImageTools;
import javaTools.JsonTools;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static FCH.Inputer.inputGoodFid;
import static config.ApiAccount.decryptSessionKey;
import static constants.Constants.APIP_Account_JSON;

public class StartApipClient {
    public static final int DEFAULT_SIZE = 20;

    public static ApiAccount initApiAccount;

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        byte[] sessionKey;
        byte[] symKey;
        while (true) {
            Shower.printUnderline(20);
            System.out.println("\nWelcome to the Freeverse with APIP Client.");
            Shower.printUnderline(20);
            System.out.println("Confirm or set your password...");
            byte[] passwordBytes = Inputer.getPasswordBytes(br);
            BytesTools.clearByteArray(passwordBytes);

            symKey = Hash.Sha256x2(passwordBytes);
            try {
                initApiAccount = ApiAccount.checkApipAccount(br, symKey);
                if (initApiAccount == null) return;
                sessionKey = decryptSessionKey(initApiAccount.getSessionKeyCipher(), symKey);

                if (sessionKey == null) continue;

                break;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Wrong password, try again.");
            }
        }

        Menu menu = new Menu();

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
                case 1 -> showExample(sessionKey, br);
                case 2 -> freeGet(br);
                case 3 -> openAPI(sessionKey, symKey, br);
                case 4 -> blockchain(sessionKey, br);
                case 5 -> identity(sessionKey, br);
                case 6 -> organize(sessionKey, br);
                case 7 -> construct(sessionKey, br);
                case 8 -> personal(sessionKey, br);
                case 9 -> publish(sessionKey, br);
                case 10 -> wallet(sessionKey, br);
                case 11 -> cryptoTools(sessionKey, br);
                case 12 -> swapTools(sessionKey, br);
                case 13 -> setting(sessionKey, symKey, br);
                case 0 -> {
                    BytesTools.clearByteArray(sessionKey);
                    return;
                }
            }
        }
    }

    public static void showExample(byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields("owner", "issuer").addNewValues("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        fcdsl.getQuery().addNewRange().addNewFields("cd").addGt("1").addLt("100");
        fcdsl.addNewFilter().addNewPart().addNewFields("issuer").addNewValue("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        fcdsl.addNewExcept().addNewEquals().addNewFields("cd").addNewValues("1", "2");
        fcdsl.addNewSort("cd", "desc").addSize(2).addNewAfter("56");
        if (!fcdsl.checkFcdsl()) return;
        System.out.println("Java code:");
        Shower.printUnderline(20);
        String code = """
                public static void showExample(byte[] sessionKey, BufferedReader br) {
                \tFcdsl fcdsl = new Fcdsl();
                \tfcdsl.addNewQuery().addNewTerms().addNewFields("owner","issuer").addNewValues("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
                \tfcdsl.getQuery().addNewRange().addNewFields("cd").addGt("1").addLt("100");
                \tfcdsl.addNewFilter().addNewPart().addNewFields("issuer").addNewValue("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
                \tfcdsl.addNewExcept().addNewEquals().addNewFields("cd").addNewValues("1","2");
                \tfcdsl.addNewSort("cd","desc").addSize(2).addNewAfter("56");
                \tif(!fcdsl.checkFcdsl())return;
                \tApipClient apipClientData =OpenAPIs.generalPost(IndicesNames.CASH,initApipParamsForClient.getUrlHead(),fcdsl, initApipParamsForClient.getVia(), sessionKey);
                \tGson gson = new GsonBuilder().setPrettyPrinting().create();
                \tSystem.out.println("Request header:\\n"+FCH.ParseTools.gsonString(apipClientData.getRequestHeaderMap()));
                \tSystem.out.println("Request body:\\n"+gson.toJson(apipClientData.getRequestBody()));
                \tSystem.out.println("Response header:\\n"+FCH.ParseTools.gsonString(apipClientData.getResponseHeaderMap()));
                \tSystem.out.println("Response body:\\n"+gson.toJson(apipClientData.getResponseBody()));
                }""";
        System.out.println(code);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OpenAPIs.generalPost(IndicesNames.CASH, initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String responseBodyJson = gson.toJson(apipClientData.getResponseBody());
        Shower.printUnderline(20);
        System.out.println("Request header:\n" + JsonTools.getNiceString(apipClientData.getRequestHeaderMap()));
        Shower.printUnderline(20);
        System.out.println("Request body:\n" + gson.toJson(apipClientData.getRequestBody()));
        Shower.printUnderline(20);
        System.out.println("Response header:\n" + JsonTools.getNiceString(apipClientData.getResponseHeaderMap()));
        Shower.printUnderline(20);
        System.out.println("Response body:");
        Shower.printUnderline(20);
        System.out.println(responseBodyJson);
        Shower.printUnderline(20);

        Menu.anyKeyToContinue(br);
    }

    public static void freeGet(BufferedReader br) {
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.FreeGetAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> broadcast(br);
                case 2 -> getApps(br);
                case 3 -> getServices(br);
                case 4 -> getAvatar(br);
                case 5 -> getCashes(br);
                case 6 -> getFidCid(br);
                case 7 -> getFreeService(br);
                case 8 -> getService(br);
                case 9 -> getTotals(br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void broadcast(BufferedReader br) {
        System.out.println("Input the rawTx:");
        String rawTx = Inputer.inputString(br);
        System.out.println("Broadcasting...");
        ApipClientData apipClientData = FreeGetAPIs.broadcast(initApiAccount.getApiUrl(), rawTx);
        System.out.println(apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void getCashes(BufferedReader br) {
        String id = inputGoodFid(br, "Input the FID:");
        System.out.println("Getting cashes...");
        ApipClientData apipClientData = FreeGetAPIs.getCashes(initApiAccount.getApiUrl(), id, 0);
        System.out.println(apipClientData.getResponseBodyStr());
        JsonTools.gsonPrint(ApipDataGetter.getCashList(apipClientData.getResponseBody().getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void getApps(BufferedReader br) {
        System.out.println("Input the aid or enter to ignore:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) id = null;
        System.out.println("Getting APPs...");
        ApipClientData apipClientData = FreeGetAPIs.getApps(initApiAccount.getApiUrl(), id);
        System.out.println(apipClientData.getResponseBodyStr());
        ;
        Menu.anyKeyToContinue(br);
    }

    public static void getServices(BufferedReader br) {
        System.out.println("Input the sid or enter to ignore:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) id = null;
        System.out.println("Getting services...");
        ApipClientData apipClientData = FreeGetAPIs.getServices(initApiAccount.getApiUrl(), id);
        System.out.println(apipClientData.getResponseBodyStr());
        ;
        Menu.anyKeyToContinue(br);
    }

    public static void getTotals(BufferedReader br) {
        System.out.println("Getting totals...");
        ApipClientData apipClientData = FreeGetAPIs.getTotals(initApiAccount.getApiUrl());
        System.out.println(apipClientData.getResponseBodyStr());
        ;
        Menu.anyKeyToContinue(br);
    }

    public static void getFreeService(BufferedReader br) {
        System.out.println("Getting the free service and the sessionKey...");
        ApipClientData apipClientData = FreeGetAPIs.getFreeService(initApiAccount.getApiUrl());
        System.out.println(apipClientData.getResponseBodyStr());
        ;
        Menu.anyKeyToContinue(br);
    }

    public static void getService(BufferedReader br) {
        System.out.println("Getting the default service information...");
        ApipClientData apipClientData = OpenAPIs.getService(initApiAccount.getApiUrl());
        System.out.println(apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void openAPI(byte[] sessionKey, byte[] symKey, BufferedReader br) {
        System.out.println("OpenAPI...");
        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("getService");
        menuItemList.add("SignInPost");
        menuItemList.add("SignInEccPost");
        menuItemList.add("TotalsGet");
        menuItemList.add("TotalsPost");
        menuItemList.add("generalPost");


        menu.add(menuItemList);

        while (true) {
            System.out.println(" << Maker manager>>");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> getService(br);
                case 2 -> sessionKey = signInPost(symKey, null);
                case 3 -> sessionKey = signInEccPost(symKey, null);
                case 4 -> totalsGet(br);
                case 5 -> totalsPost(sessionKey, br);
                case 6 -> generalPost(sessionKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void generalPost(byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = new Fcdsl();
        System.out.println("Input the index name. Enter to exit:");
        String input = Inputer.inputString(br);
        if ("".equals(input)) return;
        fcdsl.setIndex(input);

        fcdsl.promoteInput(br);

        if (!fcdsl.checkFcdsl()) {
            System.out.println("Fcdsl wrong:");
            System.out.println(JsonTools.getNiceString(fcdsl));
            return;
        }
        System.out.println(JsonTools.getNiceString(fcdsl));
        Menu.anyKeyToContinue(br);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OpenAPIs.generalPost(fcdsl.getIndex(), initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println(apipClientData.getResponseBodyStr());
        ;
        Menu.anyKeyToContinue(br);
    }

    public static void totalsGet(BufferedReader br) {
        System.out.println("Get request for totals...");
        ApipClientData apipClientData = OpenAPIs.totalsGet(initApiAccount.getApiUrl());
        System.out.println(apipClientData.getResponseBodyStr());
        ;
        Menu.anyKeyToContinue(br);
    }

    public static void totalsPost(byte[] sessionKey, BufferedReader br) {
        System.out.println("Post request for totals...");
        ApipClientData apipClientData = OpenAPIs.totalsPost(initApiAccount.getApiUrl(), initApiAccount.getVia(), sessionKey);
        System.out.println(apipClientData.getResponseBodyStr());
        ;
        Menu.anyKeyToContinue(br);
    }

    public static byte[] signInEccPost(byte[] symKey, String mode) {
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(initApiAccount.getUserPriKeyCipher(), symKey);
        if (priKey == null) return null;
        System.out.println("Sign in for EccAES256K1P7 encrypted sessionKey...");
        ApipClientData apipClientData = OpenAPIs.signInEccPost(initApiAccount.getApiUrl(), initApiAccount.getVia(), priKey, mode);

        System.out.println(apipClientData.getResponseBodyStr());

        Gson gson = new Gson();
        SignInData signInData = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()), SignInData.class);
        String sessionKeyCipherFromApip = signInData.getSessionKeyCipher();
        byte[] newSessionKey = decryptSessionKeyWithPriKey(sessionKeyCipherFromApip, priKey);

        updateSession(symKey, signInData, newSessionKey);

        return newSessionKey;
    }

    public static byte[] decryptSessionKeyWithPriKey(String cipher, byte[] priKey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        EccAesDataByte eccAesDataBytes = ecc.decrypt(cipher, priKey);
        if (eccAesDataBytes.getError() != null) {
            System.out.println("Decrypt sessionKey wrong: " + eccAesDataBytes.getError());
            BytesTools.clearByteArray(priKey);
            return null;
        }
        String sessionKeyHex = new String(eccAesDataBytes.getMsg(), StandardCharsets.UTF_8);
        return HexFormat.of().parseHex(sessionKeyHex);
    }

    public static byte[] signInPost(byte[] symKey, String mode) {
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(initApiAccount.getUserPriKeyCipher(), symKey);
        if (priKey == null) return null;

        System.out.println("Sign in...");
        ApipClientData apipClientData = OpenAPIs.signInPost(initApiAccount.getApiUrl(), initApiAccount.getVia(), priKey, mode);
        System.out.println(JsonTools.getNiceString(apipClientData.getResponseBody()));

        Gson gson = new Gson();
        SignInData signInData = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()), SignInData.class);

        byte[] newSessionKey = HexFormat.of().parseHex(signInData.getSessionKey());

        updateSession(symKey, signInData, newSessionKey);
        return newSessionKey;
    }

    public static void updateSession(byte[] symKey, SignInData signInData, byte[] newSessionKey) {
        String newSessionKeyCipher = EccAes256K1P7.encryptWithSymKey(newSessionKey, symKey);
        if(newSessionKeyCipher.contains("Error"))return;
        signInData.setSessionKeyCipher(newSessionKeyCipher);
        initApiAccount.setSessionKeyCipher(newSessionKeyCipher);
        initApiAccount.setSessionExpire(signInData.getExpireTime());
        String newSessionName = ApipTools.getSessionName(newSessionKey);
        initApiAccount.setSessionName(newSessionName);
        System.out.println("SessionName:" + newSessionName);
        System.out.println("SessionKeyCipher: " + signInData.getSessionKeyCipher());
        ApiAccount.writeApipParamsToFile(initApiAccount, APIP_Account_JSON);
    }

    public static void blockchain(byte[] sessionKey, BufferedReader br) {
        System.out.println("Blockchain...");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.BlockchainAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> blockByIds(sessionKey, br);
                case 2 -> blockSearch(DEFAULT_SIZE, "height:desc->blockId:asc", sessionKey, br);
                case 3 -> cashByIds(sessionKey, br);
                case 4 -> cashSearch(DEFAULT_SIZE, "valid:desc->birthHeight:desc->cashId:asc", sessionKey, br);
                case 5 -> cashValid(DEFAULT_SIZE, "cd:asc->value:desc->cashId:asc", sessionKey, br);
                case 6 -> fidByIds(sessionKey, br);
                case 7 -> fidSearch(DEFAULT_SIZE, "lastHeight:desc->fid:asc", sessionKey, br);
                case 8 -> opReturnByIds(sessionKey, br);
                case 9 -> opReturnSearch(DEFAULT_SIZE, "height:desc->txIndex:desc->txId:asc", sessionKey, br);
                case 10 -> p2shByIds(sessionKey, br);
                case 11 -> p2shSearch(DEFAULT_SIZE, "birthHeight:desc->fid:asc", sessionKey, br);
                case 12 -> txByIds(sessionKey, br);
                case 13 -> txSearch(DEFAULT_SIZE, "height:desc->txId:asc", sessionKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void blockByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input blockIds:", 0);
        System.out.println("Requesting blockByIds...");
        ApipClientData apipClientData = BlockchainAPIs.blockByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void blockSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting blockSearch...");
        ApipClientData apipClientData = BlockchainAPIs.blockSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void cashByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input cashIds:", 0);
        System.out.println("Requesting cashByIds...");
        ApipClientData apipClientData = BlockchainAPIs.cashByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void cashValid(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting cashValid...");
        ApipClientData apipClientData = BlockchainAPIs.cashValidPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void cashSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting cashSearch...");
        ApipClientData apipClientData = BlockchainAPIs.cashSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void fidByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting fidByIds...");
        ApipClientData apipClientData = BlockchainAPIs.fidByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void fidSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting fidSearch...");
        ApipClientData apipClientData = BlockchainAPIs.fidSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void opReturnByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input opReturnIds:", 0);
        System.out.println("Requesting opReturnByIds...");
        ApipClientData apipClientData = BlockchainAPIs.opReturnByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void opReturnSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting opReturnSearch...");
        ApipClientData apipClientData = BlockchainAPIs.opReturnSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void p2shByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input p2shIds:", 0);
        System.out.println("Requesting p2shByIds...");
        ApipClientData apipClientData = BlockchainAPIs.p2shByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void p2shSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting p2shSearch...");
        ApipClientData apipClientData = BlockchainAPIs.p2shSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void txByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input txIds:", 0);
        System.out.println("Requesting txByIds...");
        ApipClientData apipClientData = BlockchainAPIs.txByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    public static void txSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting txSearch...");
        ApipClientData apipClientData = BlockchainAPIs.txSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    @Nullable
    public static Fcdsl inputFcdsl(int defaultSize, String defaultSort, BufferedReader br) {
        Fcdsl fcdsl = new Fcdsl();

        fcdsl.promoteSearch(defaultSize, defaultSort, br);

        if (!fcdsl.checkFcdsl()) {
            System.out.println("Fcdsl wrong:");
            System.out.println(JsonTools.getNiceString(fcdsl));
            return null;
        }
        System.out.println("fcdsl:\n" + JsonTools.getNiceString(fcdsl));

        Menu.anyKeyToContinue(br);
        return fcdsl;
    }


    public static void identity(byte[] sessionKey, BufferedReader br) {
        System.out.println("Identity...");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.IdentityAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> cidInfoByIds(sessionKey, br);
                case 2 -> cidInfoSearch(DEFAULT_SIZE, "nameTime:desc->fid:asc", sessionKey, br);
                case 3 -> cidHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 4 -> fidCidSeek(DEFAULT_SIZE, "lastHeight:desc->cashId:asc", sessionKey, br);
                case 5 -> getFidCid(br);
                case 6 -> nobodyByIds(sessionKey, br);
                case 7 -> nobodySearch(DEFAULT_SIZE, "deathHeight:desc->deathTxIndex:desc", sessionKey, br);
                case 8 -> homepageHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 9 -> noticeFeeHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 10 -> reputationHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 11 -> avatars(sessionKey, br);
                case 12 -> getAvatar(br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void cidInfoByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting cidInfoByIds...");
        ApipClientData apipClientData = IdentityAPIs.cidInfoByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void cidInfoSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.cidInfoSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void fidCidSeek(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.fidCidSeekPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void getFidCid(BufferedReader br) {
        System.out.println("Input FID or CID:");
        String id = Inputer.inputString(br);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = FreeGetAPIs.getFidCid(initApiAccount.getApiUrl(), id);
        System.out.println(apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void nobodyByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.nobodyByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void nobodySearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.nobodySearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void cidHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.cidHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void homepageHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.homepageHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void noticeFeeHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.noticeFeeHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void reputationHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.reputationHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void avatars(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = IdentityAPIs.avatarsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void getAvatar(BufferedReader br) {
        String fid = inputGoodFid(br, "Input FID:");
        System.out.println("Requesting ...");
        ApipClientData apipClientData = FreeGetAPIs.getAvatar(initApiAccount.getApiUrl(), fid);
        byte[] imageBytes = apipClientData.getResponseBodyBytes();
        ImageTools.displayPng(imageBytes);
        ImageTools.savePng(imageBytes, "test.png");
        Menu.anyKeyToContinue(br);
    }

    public static void organize(byte[] sessionKey, BufferedReader br) {
        System.out.println("Organize...");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.OrganizeAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> groupByIds(sessionKey, br);
                case 2 -> groupSearch(DEFAULT_SIZE, "tCdd:desc->gid:asc", sessionKey, br);
                case 3 -> groupMembers(sessionKey, br);
                case 4 -> groupOpHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 5 -> myGroups(sessionKey, br);
                case 6 -> teamByIds(sessionKey, br);
                case 7 -> teamSearch(DEFAULT_SIZE, "active:desc->tRate:desc->tid:asc", sessionKey, br);
                case 8 -> teamMembers(sessionKey, br);
                case 9 -> teamExMembers(sessionKey, br);
                case 10 -> teamOpHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 11 -> teamRateHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 12 -> teamOtherPersons(sessionKey, br);
                case 13 -> myTeams(sessionKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void groupByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input GIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.groupByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void groupSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.groupSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void groupMembers(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input GIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.groupMembersPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void groupOpHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        ApipClientData apipClientData = OrganizeAPIs.groupOpHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void myGroups(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.myGroupsPost(initApiAccount.getApiUrl(), id, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void teamByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        ApipClientData apipClientData = OrganizeAPIs.teamByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void teamSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.teamSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void teamMembers(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.teamMembersPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void teamExMembers(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.teamExMembersPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void teamRateHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.teamRateHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void teamOpHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.teamOpHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void teamOtherPersons(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.teamOtherPersonsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void myTeams(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = OrganizeAPIs.myTeamsPost(initApiAccount.getApiUrl(), id, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void construct(byte[] sessionKey, BufferedReader br) {
        System.out.println("Construct...");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.ConstructAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> protocolByIds(sessionKey, br);
                case 2 -> protocolSearch(DEFAULT_SIZE, "active:desc->tRate:desc->pid:asc", sessionKey, br);
                case 3 -> protocolOpHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 4 -> protocolRateHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 5 -> codeByIds(sessionKey, br);
                case 6 -> codeSearch(DEFAULT_SIZE, "active:desc->tRate:desc->codeId:asc", sessionKey, br);
                case 7 -> codeOpHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 8 -> codeRateHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 9 -> serviceByIds(sessionKey, br);
                case 10 -> serviceSearch(DEFAULT_SIZE, "active:desc->tRate:desc->sid:asc", sessionKey, br);
                case 11 -> serviceOpHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 12 -> serviceRateHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 13 -> appByIds(sessionKey, br);
                case 14 -> appSearch(DEFAULT_SIZE, "active:desc->tRate:desc->aid:asc", sessionKey, br);
                case 15 -> appOpHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 16 -> appRateHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void protocolByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input PIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.protocolByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void protocolSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.protocolSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void protocolRateHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.protocolRateHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void protocolOpHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.protocolOpHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void codeByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input codeIds:", 0);
        ApipClientData apipClientData = ConstructAPIs.codeByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void codeSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.codeSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void codeRateHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.codeRateHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void codeOpHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.codeOpHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input SIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.serviceByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.serviceSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceRateHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.serviceRateHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceOpHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.serviceOpHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void appByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input AIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.appByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void appSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.appSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void appRateHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.appRateHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void appOpHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.appOpHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void personal(byte[] sessionKey, BufferedReader br) {
        System.out.println("Personal...");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.PersonalAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> boxByIds(sessionKey, br);
                case 2 -> boxSearch(DEFAULT_SIZE, "lastHeight:desc->bid:asc", sessionKey, br);
                case 3 -> boxHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 4 -> contactByIds(sessionKey, br);
                case 5 -> contacts(DEFAULT_SIZE, "birthHeight:desc->contactId:asc", sessionKey, br);
                case 6 -> contactsDeleted(DEFAULT_SIZE, "lastHeight:desc->contactId:asc", sessionKey, br);
                case 7 -> secretByIds(sessionKey, br);
                case 8 -> secrets(DEFAULT_SIZE, "birthHeight:desc->secretId:asc", sessionKey, br);
                case 9 -> secretsDeleted(DEFAULT_SIZE, "lastHeight:desc->secretId:asc", sessionKey, br);
                case 10 -> mailByIds(sessionKey, br);
                case 11 -> mails(DEFAULT_SIZE, "birthHeight:desc->mailId:asc", sessionKey, br);
                case 12 -> mailsDeleted(DEFAULT_SIZE, "lastHeight:desc->mailId:asc", sessionKey, br);
                case 13 -> mailThread(DEFAULT_SIZE, "birthHeight:desc->mailId:asc", sessionKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void boxByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input BIDs:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.boxByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void boxSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.boxSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void boxHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.boxHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void contactByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input contactIds:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.contactByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void contacts(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.contactsPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void contactsDeleted(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.contactsDeletedPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void secretByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input secretIds:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.secretByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void secrets(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.secretsPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void secretsDeleted(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.secretsDeletedPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void mailByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input mailIds:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.mailByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void mails(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.mailsPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void mailsDeleted(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.mailsDeletedPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void mailThread(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PersonalAPIs.mailThreadPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void publish(byte[] sessionKey, BufferedReader br) {
        System.out.println("Publish...");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.PublishAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> proofByIds(sessionKey, br);
                case 2 -> proofSearch(DEFAULT_SIZE, "lastHeight:desc->bid:asc", sessionKey, br);
                case 3 -> proofHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 4 -> statementByIds(sessionKey, br);
                case 5 -> statementSearch(DEFAULT_SIZE, "birthHeight:desc->contactId:asc", sessionKey, br);
                case 6 -> nidSearch(DEFAULT_SIZE, "birthHeight:desc->nameId:asc", sessionKey, br);
                case 7 -> tokenByIds(sessionKey, br);
                case 8 -> tokenSearch(DEFAULT_SIZE, "lastHeight:desc->tokenId:asc", sessionKey, br);
                case 9 -> tokenHistory(DEFAULT_SIZE, "height:desc->index:desc", sessionKey, br);
                case 10 -> tokenHolderByIds(sessionKey, br);
                case 11 -> myTokens(DEFAULT_SIZE, "lastHeight:desc->id:asc", sessionKey, br);
                case 12 -> tokenHolders(DEFAULT_SIZE, "lastHeight:desc->id:asc", sessionKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void tokenByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input tokenIds:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.tokenByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.tokenSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.tokenHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenHolderByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input tokenHolderIds:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.tokenHolderByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void myTokens(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.myTokensPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenHolders(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.tokenHoldersPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void proofByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input proofIds:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.proofByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void proofSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.proofSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void proofHistory(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.proofHistoryPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void statementByIds(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input statementIds:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.statementByIdsPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void statementSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.statementSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void nidSearch(int defaultSize, String defaultSort, byte[] sessionKey, BufferedReader br) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort, br);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = PublishAPIs.nidSearchPost(initApiAccount.getApiUrl(), fcdsl, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void wallet(byte[] sessionKey, BufferedReader br) {
        System.out.println("Wallet...");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.WalletAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> broadcastTx(sessionKey, br);
                case 2 -> decodeRawTx(sessionKey, br);
                case 3 -> cashValidForPay(sessionKey, br);
                case 4 -> cashValidForCd(sessionKey, br);
                case 5 -> unconfirmed(sessionKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void broadcastTx(byte[] sessionKey, BufferedReader br) {
        String txHex;
        while (true) {
            System.out.println("Input the hex of the TX:");
            txHex = Inputer.inputString(br);
            if (Hex.isHexString(txHex)) {
                System.out.println("It's not a hex. Try again.");
                break;
            }
        }
        System.out.println("Requesting ...");
        ApipClientData apipClientData = WalletAPIs.broadcastTxPost(initApiAccount.getApiUrl(), txHex, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void decodeRawTx(byte[] sessionKey, BufferedReader br) {
        String txHex;
        while (true) {
            System.out.println("Input the hex of the raw TX:");
            txHex = Inputer.inputString(br);
            if (Hex.isHexString(txHex)) break;
            System.out.println("It's not a hex. Try again.");
        }
        System.out.println("Requesting ...");
        ApipClientData apipClientData = WalletAPIs.decodeRawTxPost(initApiAccount.getApiUrl(), txHex, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void cashValidForPay(byte[] sessionKey, BufferedReader br) {
        String fid;

        while (true) {
            System.out.println("Input the sender's FID:");
            fid = Inputer.inputString(br);
            if (KeyTools.isValidFchAddr(fid)) break;
            System.out.println("It's not a FID. Try again.");
        }
        Double amount = Inputer.inputDouble(br, "Input the amount:");
        if (amount == null) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = WalletAPIs.cashValidForPayPost(initApiAccount.getApiUrl(), fid, amount, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void cashValidForCd(byte[] sessionKey, BufferedReader br) {
        String fid;

        while (true) {
            System.out.println("Input the sender's FID:");
            fid = Inputer.inputString(br);
            if (KeyTools.isValidFchAddr(fid)) break;
            System.out.println("It's not a FID. Try again.");
        }
        int cd = Inputer.inputInteger(br, "Input the required CD:", 0);
        System.out.println("Requesting ...");
        ApipClientData apipClientData = WalletAPIs.cashValidForCdPost(initApiAccount.getApiUrl(), fid, cd, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void unconfirmed(byte[] sessionKey, BufferedReader br) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        ApipClientData apipClientData = WalletAPIs.unconfirmedPost(initApiAccount.getApiUrl(), ids, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void cryptoTools(byte[] sessionKey, BufferedReader br) {
        System.out.println("CryptoTools");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.CryptoToolsAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> addresses(sessionKey, br);
                case 2 -> encrypt(sessionKey, br);
                case 3 -> verify(sessionKey, br);
                case 4 -> sha256(sessionKey, br);
                case 5 -> sha256x2(sessionKey, br);
                case 6 -> sha256Bytes(sessionKey, br);
                case 7 -> sha256x2Bytes(sessionKey, br);
                case 8 -> offLineTx(sessionKey, br);
                case 9 -> offLineTxByCd(sessionKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void addresses(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the address or public key. Enter to exit:");
        String addrOrKey = Inputer.inputString(br);
        if ("".equals(addrOrKey)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = CryptoToolAPIs.addressesPost(initApiAccount.getApiUrl(), addrOrKey, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void encrypt(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the text. Enter to exit:");
        String msg = Inputer.inputString(br);
        if ("".equals(msg)) return;
        System.out.println("Requesting ...");
        System.out.println("Input the pubKey or symKey. Enter to exit:");
        String key = Inputer.inputString(br);
        if ("".equals(key)) return;

        ApipClientData apipClientData = CryptoToolAPIs.encryptPost(initApiAccount.getApiUrl(), key, msg, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void verify(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the signature. Enter to exit:");
        String signature = Inputer.inputString(br);
        if ("".equals(signature)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = CryptoToolAPIs.verifyPost(initApiAccount.getApiUrl(), signature, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = CryptoToolAPIs.sha256Post(initApiAccount.getApiUrl(), text, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = CryptoToolAPIs.sha256x2Post(initApiAccount.getApiUrl(), text, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256Bytes(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = CryptoToolAPIs.sha256BytesPost(initApiAccount.getApiUrl(), text, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2Bytes(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = CryptoToolAPIs.sha256x2BytesPost(initApiAccount.getApiUrl(), text, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void offLineTx(byte[] sessionKey, BufferedReader br) {

        String fid = inputGoodFid(br, "Input the sender's FID:");

        List<SendTo> sendToList = SendTo.inputSendToList(br);

        System.out.println("Input the text of OpReturn. Enter to skip:");
        String msg = Inputer.inputString(br);
        if ("".equals(msg)) msg = null;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = CryptoToolAPIs.offLineTxPost(initApiAccount.getApiUrl(), fid, sendToList, msg, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    public static void offLineTxByCd(byte[] sessionKey, BufferedReader br) {

        String fid = inputGoodFid(br, "Input the FID:");

        int cd = Inputer.inputInteger(br, "Input the required CD:", 0);

        List<SendTo> sendToList = SendTo.inputSendToList(br);

        System.out.println("Input the text of OpReturn. Enter to skip:");
        String msg = Inputer.inputString(br);
        if ("".equals(msg)) msg = null;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = CryptoToolAPIs.offLineTxByCdPost(initApiAccount.getApiUrl(), fid, sendToList, msg, cd, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }


    public static void swapTools(byte[] sessionKey, BufferedReader br) {
        System.out.println("CryptoTools");
        while (true) {
            Menu menu = new Menu();
            menu.add(ApiNames.SwapHallAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> swapRegister(sessionKey, br);
                case 2 -> swapUpdate(sessionKey, br);
                case 3 -> swapInfo(br);
                case 4 -> swapState(br);
                case 5 -> swapLp(br);
                case 6 -> swapPending(br);
                case 7 -> swapFinished(br);
                case 8 -> swapPrice(br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void swapRegister(byte[] sessionKey, BufferedReader br) {
        System.out.println("Input the sid. Enter to exit:");
        String sid = Inputer.inputString(br);
        if ("".equals(sid)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = SwapHallAPIs.swapRegisterPost(initApiAccount.getApiUrl(), sid, initApiAccount.getVia(), sessionKey);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        Menu.anyKeyToContinue(br);
    }

    private static void swapUpdate(byte[] sessionKey, BufferedReader br) {
        System.out.println("In developing...");
    }

    private static void swapInfo(BufferedReader br) {
        System.out.println("Input the sid. Enter to exit:");
        String sid = Inputer.inputString(br);
        if ("".equals(sid)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = SwapHallAPIs.getSwapInfo(initApiAccount.getApiUrl(), new String[]{sid}, null);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());

        String[] last = apipClientData.getResponseBody().getLast();
        if (last != null && !Arrays.stream(last).isParallel()) {
            apipClientData = SwapHallAPIs.getSwapInfo(initApiAccount.getApiUrl(), new String[]{sid}, last);
            System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        }
        Menu.anyKeyToContinue(br);
    }

    private static void swapState(BufferedReader br) {
        System.out.println("Input the sid. Enter to exit:");
        String sid = Inputer.inputString(br);
        if ("".equals(sid)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = SwapHallAPIs.getSwapState(initApiAccount.getApiUrl(), sid);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    private static void swapLp(BufferedReader br) {
        System.out.println("Input the sid. Enter to exit:");
        String sid = Inputer.inputString(br);
        if ("".equals(sid)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = SwapHallAPIs.getSwapLp(initApiAccount.getApiUrl(), sid);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    private static void swapPending(BufferedReader br) {
        System.out.println("Input the sid. Enter to exit:");
        String sid = Inputer.inputString(br);
        if ("".equals(sid)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = SwapHallAPIs.getSwapPending(initApiAccount.getApiUrl(), sid);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
    }

    private static void swapFinished(BufferedReader br) {
        System.out.println("Input the sid. Enter to exit:");
        String sid = Inputer.inputString(br);
        if ("".equals(sid)) return;
        System.out.println("Requesting ...");
        ApipClientData apipClientData = SwapHallAPIs.getSwapFinished(initApiAccount.getApiUrl(), sid, null);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());

        String[] last = apipClientData.getResponseBody().getLast();
        if (last != null && !Arrays.stream(last).isParallel()) {
            apipClientData = SwapHallAPIs.getSwapInfo(initApiAccount.getApiUrl(), new String[]{sid}, last);
            System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        }
        Menu.anyKeyToContinue(br);
    }

    private static void swapPrice(BufferedReader br) {
        System.out.println("Input the sid. Enter to ignore:");
        String sid = Inputer.inputString(br);
        if ("".equals(sid)) sid = null;

        System.out.println("Input the sid. Enter to exit:");
        String gTick = Inputer.inputString(br);
        if ("".equals(gTick)) gTick = null;

        System.out.println("Input the sid. Enter to exit:");
        String mTick = Inputer.inputString(br);
        if ("".equals(mTick)) mTick = null;

        System.out.println("Requesting ...");
        ApipClientData apipClientData = SwapHallAPIs.getSwapPrice(initApiAccount.getApiUrl(), sid, gTick, mTick, null);
        System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());

        String[] last = apipClientData.getResponseBody().getLast();
        if (last != null && !Arrays.stream(last).isParallel()) {
            apipClientData = SwapHallAPIs.getSwapPrice(initApiAccount.getApiUrl(), sid, gTick, mTick, last);
            System.out.println("apipClientData:\n" + apipClientData.getResponseBodyStr());
        }
        Menu.anyKeyToContinue(br);
    }

    public static void setting(byte[] sessionKey, byte[] symKey, BufferedReader br) {
        System.out.println("setting...");
        while (true) {
            Menu menu = new Menu();
            menu.add("Check APIP", "Reset APIP", "Refresh SessionKey", "Change password");
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> checkApip(initApiAccount, sessionKey, br);
                case 2 -> resetApip(initApiAccount, br);
                case 3 -> sessionKey = refreshSessionKey(symKey);
                case 4 -> {
                    byte[] symKeyNew = resetPassword(br);
                    if (symKeyNew == null) break;
                    symKey = symKeyNew;
                }
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static byte[] resetPassword(BufferedReader br) {

        byte[] passwordBytesOld;
        while (true) {
            System.out.print("Check password. ");

            passwordBytesOld = Inputer.getPasswordBytes(br);
            byte[] sessionKey = decryptSessionKey(initApiAccount.getSessionKeyCipher(), Hash.Sha256x2(passwordBytesOld));
            if (sessionKey != null) break;
            System.out.println("Wrong password. Try again.");
        }

        byte[] passwordBytesNew;
        passwordBytesNew = Inputer.inputAndCheckNewPassword(br);

        byte[] symKeyOld = Hash.Sha256x2(passwordBytesOld);

        byte[] sessionKey = decryptSessionKey(initApiAccount.getSessionKeyCipher(), symKeyOld);
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(initApiAccount.getUserPriKeyCipher(), symKeyOld);

        byte[] symKeyNew = Hash.Sha256x2(passwordBytesNew);
        String buyerPriKeyCipherNew = EccAes256K1P7.encryptWithSymKey(priKey, symKeyNew);
        if(buyerPriKeyCipherNew.contains("Error"))return null;

        String sessionKeyCipherNew = EccAes256K1P7.encryptWithSymKey(sessionKey, symKeyNew);
        if (sessionKeyCipherNew.contains("Error")) {
            System.out.println("Get sessionKey wrong:" + sessionKeyCipherNew);
        }
        initApiAccount.setSessionKeyCipher(sessionKeyCipherNew);
        initApiAccount.setUserPriKeyCipher(buyerPriKeyCipherNew);

        ApiAccount.writeApipParamsToFile(initApiAccount, APIP_Account_JSON);
        return symKeyNew;
    }

    public static byte[] refreshSessionKey(byte[] symKey) {
        System.out.println("Refreshing ...");
        return signInEccPost(symKey, Strings.REFRESH);
    }

    public static void checkApip(ApiAccount initApiAccount, byte[] sessionKey, BufferedReader br) {
        Shower.printUnderline(20);
        System.out.println("Apip Service:");
        String urlHead = initApiAccount.getApiUrl();
        String[] ids = new String[]{initApiAccount.getSid()};
        String via = initApiAccount.getVia();

        System.out.println("Requesting ...");
        ApipClientData apipClientData = ConstructAPIs.serviceByIdsPost(urlHead, ids, via, sessionKey);
        System.out.println(apipClientData.getResponseBodyStr());

        Shower.printUnderline(20);
        System.out.println("User Params:");
        System.out.println(JsonTools.getNiceString(initApiAccount));
        Shower.printUnderline(20);
        Menu.anyKeyToContinue(br);
    }

    private static void resetApip(ApiAccount initApiAccount, BufferedReader br) {
        byte[] passwordBytes = Inputer.getPasswordBytes(br);
        initApiAccount.updateApipAccount(br, passwordBytes);
    }
}