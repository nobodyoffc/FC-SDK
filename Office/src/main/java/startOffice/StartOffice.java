package startOffice;

import apip.apipData.CidInfo;
import apip.apipData.Fcdsl;
import apip.apipData.Sort;
import appTools.Menu;
import appTools.Shower;
import clients.apipClient.ApipClient;
import clients.apipClient.DataGetter;
import clients.diskClient.DiskClient;
import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.Configure;
import constants.Constants;
import constants.FieldNames;
import constants.ReplyCodeMessage;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.FcReplier;
import fch.Inputer;
import fch.ParseTools;
import fch.TxCreator;
import fch.Wallet;
import fch.fchData.Cash;
import fch.fchData.SendTo;
import feip.FeipTools;
import javaTools.Hex;
import nasa.NaSaRpcClient;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import static constants.FieldNames.*;
import static constants.Strings.ASC;
import static constants.Values.TRUE;
import static fch.Wallet.getCashListFromEs;

public class StartOffice {
    private static BufferedReader br;
    private static byte[] symKey;
    private static ApipClient apipClient;
    private static String fid;
    private static String userPriKeyCipher;
    private static CidInfo fidInfo;
    private static ElasticsearchClient esClient;
    private static JedisPool jedisPool;
    private static NaSaRpcClient fchClient;
    private static DiskClient diskClient;
    private static long bestHeight;
    private static OfficeSettings settings;

    public static void main(String[] args) throws IOException {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("Office");

        //Load config info from the file of config.json
        Configure configure = Configure.loadConfig(br);
        symKey = configure.checkPassword(configure);

        fid = configure.chooseMainFid(symKey);
        settings = OfficeSettings.loadFromFile(fid,OfficeSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new OfficeSettings();
        settings.initiateClient(fid, symKey, configure, br);

        if(settings.getApipAccount()!=null)
            apipClient = (ApipClient) settings.getApipAccount().getClient();
        if(settings.getEsAccount()!=null)
            esClient = (ElasticsearchClient) settings.getEsAccount().getClient();
        if(settings.getRedisAccount()!=null)
            jedisPool = (JedisPool) settings.getRedisAccount().getClient();
        if(settings.getNasaAccount()!=null)
            fchClient = (NaSaRpcClient) settings.getNasaAccount().getClient();
//        if(settings.getDiskAccount()!=null)
//            diskClient = (DiskClient) settings.getDiskAccount().getClient();

        fidInfo = settings.checkFidInfo(apipClient,br);
        userPriKeyCipher = configure.getFidCipherMap().get(fid);

        if(fidInfo!=null && fidInfo.getCid()==null){
            if(Inputer.askIfYes(br,"No CID yet. Set CID?")){
                setCid(userPriKeyCipher,br);
                return;
            }
        }

        if(fidInfo!=null &&fidInfo.getMaster()==null){
            if(Inputer.askIfYes(br,"No master yet. Set master for this FID?")){
                setMaster(userPriKeyCipher,symKey,br);
                return;
            }
        }

        Menu menu = new Menu();
        menu.setName("Office");
        menu.add("Cash");
        menu.add("Relative");
        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> cashManager(br, userPriKeyCipher,symKey);
//                case 2 -> relative(br);
                case 3 -> sendFch(br);
                case 0 -> {
                    settings.close();
                    return;
                }
            }
        }
    }

    private static void setMaster(String userPriKeyCipher, byte[] symKey, BufferedReader br) {
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
        Menu.anyKeyToContinue(StartOffice.br);

    }
    private static void setCid(String userPriKeyCipher, BufferedReader br) {
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

    private static byte[] decryptPriKey(String userPriKeyCipher,byte[] symKey) {
        CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymKey(userPriKeyCipher, symKey);
        if (cryptoResult.getCode() != 0) {
            cryptoResult.printCodeMessage();
            return null;
        }
        return cryptoResult.getData();
    }

    private static void sendFch(BufferedReader br) {
        System.out.println("Send fch...");
        Menu.anyKeyToContinue(br);
    }

    private static void cashManager(BufferedReader br, String priKeyCipher, byte[] symKey) {
        Menu menu = new Menu();
        menu.setName("Cash Manager");
        menu.add("List by Apip",
                "List by ES",
                "List by Node",
                "Merge/split Cashes");
        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> listCashByApip(br);
                case 2 -> listCashByEs(br);
                case 3 -> listCashByNasaNode(br);
                case 4 -> mergeCashes(br,priKeyCipher,symKey);
                case 0-> {
                    return;
                }
            }
        }
    }

    private static void mergeCashes(BufferedReader br, String priKeyCipher, byte[] symKey) {
        if (fidInfo == null) return;
        Object data;
//        System.out.println(JsonTools.getNiceString(fidInfo));
        if(Inputer.askIfYes(br,"There are "+fidInfo.getCash()+" cashes with "+ fidInfo.getCd()+" cd in total. \nMerge/Split them?")){
            int maxCd;
            while(true) {
                maxCd = Inputer.inputInteger(br, "Input the maximum destroyable CD for every cash.", 0);
                Fcdsl fcdsl = new Fcdsl();

                fcdsl.addNewQuery().addNewRange().addNewFields(CD).addLte(String.valueOf(maxCd));
                fcdsl.appendQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
                fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);

                fcdsl.addSize(EsTools.READ_MAX);
                fcdsl.addNewSort(CD, ASC);
                fcdsl.appendSort(CASH_ID, ASC);


                apipClient.cashSearch(fcdsl);
                data = apipClient.checkResult();
                if (data == null) {
                    apipClient.getClientData().getResponseBody().printCodeMessage();
                    if(apipClient.getClientData().getResponseBody().getCode()== ReplyCodeMessage.Code1011DataNotFound) {
                        System.out.println("Try again.");
                    }else return;
                }
                List<Cash>cashList = DataGetter.getCashList(data);
                showCashList(cashList);
                if(appTools.Inputer.askIfYes(br,"Merge/split them?")){
                    int issueNum = appTools.Inputer.inputInteger(br,"Input the number of the new cashes you want:",100);

                    long fee = TxCreator.calcTxSize(cashList.size(), issueNum, 0);

                    long sumValue = sumCashValue(cashList)-fee;
                    if(sumValue<0) {
                        System.out.println("Cash value is too small:"+sumValue+fee+". Try again.");
                        continue;
                    }
                    long valueForOne = sumValue/issueNum;
                    if(valueForOne < constants.Constants.SatoshiDust){
                        System.out.println("The sum of all cash values is too small to split. Try again.");
                        continue;
                    }
                    List<SendTo> sendToList = new ArrayList<>();
                    SendTo sendTo = new SendTo();
                    sendTo.setFid(fid);
                    sendTo.setAmount(ParseTools.satoshiToCoin(valueForOne));
                    for(int i=0;i<issueNum-1;i++)sendToList.add(sendTo);
                    SendTo sendTo1 = new SendTo();
                    sendTo1.setFid(fid);
                    long lastCashValue = sumValue - (valueForOne * (issueNum - 1));
                    sendTo1.setAmount(ParseTools.satoshiToCoin(lastCashValue));
                    sendToList.add(sendTo1);

                    CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymKey(priKeyCipher, symKey);
                    if(cryptoResult.getCode()!=0){
                        cryptoResult.printCodeMessage();
                        return;
                    }
                    byte[]priKey = cryptoResult.getData();

                    String txSigned = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, null);

                    apipClient.broadcastRawTx(txSigned);
                    data = apipClient.checkResult();
                    if(Hex.isHexString((String)data)) System.out.println("Done:");
                    System.out.println((String) data);
                    if(Inputer.askIfYes(br,"Continue?"))continue;
                }else if(appTools.Inputer.askIfYes(br,"Try again?"))continue;
                return;
            }


        }


    }

    private static long sumCashValue(List<Cash> cashList) {
        if(cashList==null || cashList.isEmpty())return 0;
        long sum=0;
        for(Cash cash: cashList){
            sum+=cash.getValue();
        }
        return sum;
    }

    private static void listCashByApip(BufferedReader br) {
        List<Cash> cashList;
        Wallet wallet = new Wallet(apipClient);
        System.out.println("Input the sort:");
        ArrayList<Sort> sorts = null;
        List<String> lastList = null;
        if (Inputer.askIfYes(br, "Input sorts?")) {
            sorts = Sort.inputSortList(br);
            if (Inputer.askIfYes(br, "Input the last?")) {
                String[] last = Inputer.inputStringArray(br, "Input after strings:", sorts.size());
                lastList = Arrays.asList(last);
            }
        }
        boolean onlyValid = false;
        if(!Inputer.askIfYes(br,"Including spent?"))
            onlyValid = true;
        FcReplier fcReplier = wallet.getCashListFromApip(fid, onlyValid,40, sorts, lastList,apipClient);
        if (fcReplier.getCode() != 0) {
            fcReplier.printCodeMessage();
        }else {
            cashList = DataGetter.getCashList(fcReplier.getData());
            showCashList(cashList);
        }
        Menu.anyKeyToContinue(br);
    }
    private static void listCashByEs(BufferedReader br) {
        List<Cash> cashList;
        Wallet wallet = new Wallet(esClient);
        ArrayList<Sort> sorts = null;
        List<String> lastList = null;
        if (Inputer.askIfYes(br, "Input sorts?")) {
            sorts = Sort.inputSortList(br);
            if (Inputer.askIfYes(br, "Input the last?")) {
                String[] last = Inputer.inputStringArray(br, "Input after strings:", sorts.size());
                lastList = Arrays.asList(last);
            }
        }
        boolean onlyValid = false;
        if(!Inputer.askIfYes(br,"Including spent?"))
            onlyValid = true;
        FcReplier fcReplier = getCashListFromEs(fid, onlyValid,40, sorts, lastList,esClient);
        if (fcReplier.getCode() != 0) {
            fcReplier.printCodeMessage();
        }else {
            cashList = DataGetter.getCashList(fcReplier.getData());
            showCashList(cashList);
        }
        Menu.anyKeyToContinue(br);
    }
    private static void listCashByNasaNode(BufferedReader br) {
        List<Cash> cashList;
        Wallet wallet = new Wallet(fchClient);
        FcReplier fcReplier = wallet.getCashListFromNasaNode(fid, String.valueOf(1), true);
        if (fcReplier.getCode() != 0) {
            fcReplier.printCodeMessage();
        }else {
            cashList = DataGetter.getCashList(fcReplier.getData());
            showCashList(cashList);
        }
        Menu.anyKeyToContinue(br);
    }

    private static void showCashList(List<Cash> cashList) {
        String title = "Cash List";
        String[] fields = new String[]{FieldNames.CASH_ID, VALID, VALUE, CD,CDD};
        int[] widths = new int[]{12, 6, 16, 16,16};
        List<List<Object>> valueListList = new ArrayList<>();
        for (Cash cash : cashList) {
            List<Object> list = new ArrayList<>();
            list.add(cash.getCashId());
            list.add(cash.isValid());
            list.add(ParseTools.satoshiToCoin(cash.getValue()));
            list.add(cash.getCd());
            list.add(cash.getCdd());
            valueListList.add(list);
        }
        Shower.showDataTable(title, fields, widths, valueListList);
    }

}
