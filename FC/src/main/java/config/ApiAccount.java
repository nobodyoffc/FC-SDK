package config;

import APIP.ApipTools;
import APIP.apipClient.*;
import APIP.apipData.ApipParams;
import APIP.apipData.SignInData;
import Exceptions.ExceptionTools;
import FCH.ParseTools;
import FCH.TxCreator;
import FCH.fchData.Cash;
import FCH.fchData.SendTo;
import FEIP.feipData.Service;
import NaSa.NaSaRpcClient;
import NaSa.RPC.GetBlockchainInfo;
import appTools.Inputer;
import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import com.google.gson.Gson;
import constants.CodeAndMsg;
import crypto.cryptoTools.Hash;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1P7.EccAes256K1P7;
import crypto.eccAes256K1P7.EccAesDataByte;
import database.esTools.NewEsClient;
import javaTools.BytesTools;
import javaTools.JsonTools;
import javaTools.NumberTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static appTools.Inputer.promptAndUpdate;
import static constants.Constants.APIP_Account_JSON;
import static constants.Constants.COIN_TO_SATOSHI;
import static crypto.cryptoTools.KeyTools.priKeyToFid;

public class ApiAccount {
    private static final Logger log = LoggerFactory.getLogger(ApiAccount.class);
    public static long minRequestTimes = 100;
    public static long orderRequestTimes = 10000;

    private transient byte[] sessionKey;
    private transient byte[] password;
    private String id;
    private String sid;
    private String userId;
    private String userName;

    private String passwordCipher;
    private String userPriKeyCipher;
    private String sessionKeyCipher;
    private String sessionName;
    private long sessionExpire;
    private Service service;
    private ApipParams apipParams;
    private String apiUrl;
    private String via;
    private long bestHeight;
    private String bestBlockId;
    private long balance;
    private transient Object client;

    public static void checkApipBalance(ApiAccount apipAccount, ApipClientData apipClientData, byte[] initSymKey) {
        if(apipClientData ==null|| apipClientData.getResponseBody()==null)return;

        long price = ParseTools.fchStrToSatoshi(apipAccount.getApipParams().getPricePerKBytes());

        long balance = apipClientData.getResponseBody().getBalance();
        apipAccount.setBalance(balance);

        if(balance < price * minRequestTimes){
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
            apipAccount.setBalance(balance + ParseTools.fchToSatoshi(topUp));
        }
    }

    public boolean isApipBalanceSufficient(){
        long price;
        if(apipParams.getPricePerKBytes()!=null){
            price= (long) (NumberTools.roundDouble8(Double.parseDouble(apipParams.getPricePerKBytes()))*COIN_TO_SATOSHI);
        }else price= (long) (NumberTools.roundDouble8(Double.parseDouble(apipParams.getPricePerRequest()))*COIN_TO_SATOSHI);

        return balance < price * minRequestTimes;
    }

    public Object connectApi(ApiProvider apiProvider, byte[] symKey, BufferedReader br) {

        if (checkApiGeneralParams(apiProvider, br)) return null;

        switch (apiProvider.getType()){
            case APIP -> {
                return connectApip(apiProvider,symKey,br);
            }
            case NaSaRPC -> {
                return connectNaSaRPC(symKey,br);
            }
            case ES -> {
                return connectEs(symKey,br);
            }
            case Redis -> {
                return connectRedis();
            }
            case Other -> {
                return connectOtherApi(apiProvider, symKey,br);
            }
        }
        return null;
    }

    private byte[] connectOtherApi(ApiProvider apiProvider, byte[] symKey, BufferedReader br) {
        System.out.println("Connect to some other APIs. Undeveloped.");
        return null;
    }

    private boolean checkApiGeneralParams(ApiProvider apiProvider, BufferedReader br) {
        if(!sid.equals(apiProvider.getSid())){
            System.out.println("The SID of apiProvider "+ apiProvider.getSid()+" is not the same as the SID "+sid+" in apiAccount.");
            if(Inputer.askIfYes(br,"Reset the SID of apiAccount to "+ apiProvider.getSid()+"? y/n")){
                sid= apiProvider.getSid();
            }else return true;
        }

        if (apiUrl==null && apiProvider.getApiUrl() == null) {
            System.out.println("The apiUrl is required.");
            if(Inputer.askIfYes(br,"Reset the apiUrl(urlHead)? y/n")){
                inputApiUrl(br);
                apiProvider.setApiUrl(apiUrl);
            }
            return true;
        }

        if(! apiProvider.getApiUrl().equals(this.apiUrl)){
            if(Inputer.askIfYes(br,"The apiUrl of apiProvider "+ apiProvider.getApiUrl() +" is not the same as the apiUrl "+ apiUrl+" of the apiAccount. Reset the apiUrl of apiAccount? y/n")) {
                this.apiUrl = apiProvider.getApiUrl();
            }else if(!Inputer.askIfYes(br,"Connect with "+apiUrl+"? y/n"))
                return true;
        }
        return false;
    }

    private NaSaRpcClient connectNaSaRPC(byte[] symKey, BufferedReader br) {
        if(apiUrl==null)apiUrl=Inputer.inputString(br,"Input the apiUrl:");
        if(userName==null)userName = Inputer.inputString(br,"Input the userName:");

        if(passwordCipher==null) {
            try {
                inputPasswordCipher(symKey,br);
                if(passwordCipher==null)return null;
            } catch (IOException e) {
                return null;
            }
        }
        password = EccAes256K1P7.decryptJsonBytes(passwordCipher,symKey);
        if(password==null)return null;

        GetBlockchainInfo.BlockchainInfo blockchainInfo = new GetBlockchainInfo().getBlockchainInfo(apiUrl, userName, new String(password,StandardCharsets.UTF_8));

        if(blockchainInfo==null) return null;

        this.bestHeight = blockchainInfo.getBlocks()-1;
        this.bestBlockId = blockchainInfo.getBestblockhash();

        return new NaSaRpcClient(apiUrl,userName,password);
    }

    private ElasticsearchClient connectEs(byte[] symKey, BufferedReader br) {
        if(apiUrl==null)apiUrl=Inputer.inputString(br,"Input the apiUrl:");

        NewEsClient newEsClient = new NewEsClient();
        newEsClient.getEsClientSilent(this,symKey);

        try {
            IndicesResponse result = newEsClient.esClient.cat().indices();
            System.out.println("Got ES client. There are "+result.valueBody().size()+" indices in ES.");
        } catch (IOException e) {
            log.debug("Failed to create ES client. Check ES.");
            System.exit(0);
        }
        this.client = newEsClient.esClient;
        return newEsClient.esClient;
    }

    private JedisPool connectRedis() {
        JedisPool jedisPool;
        if(apiUrl==null)jedisPool = new JedisPool();
        else jedisPool=new JedisPool(apiUrl);
        this.client = jedisPool;
        return jedisPool;
    }

    public byte[] inputPriKeyCipher(BufferedReader br, byte[] symKey) {
        byte[] priKey32;

        while (true) {
            String input = FCH.Inputer.inputString(br, "Generate a new private key? y/n");
            if ("y".equals(input)) {
                priKey32 = KeyTools.genNewFid(br).getPrivKeyBytes();
            } else priKey32 = KeyTools.inputCipherGetPriKey(br);

            if (priKey32 == null) return null;

            userId = priKeyToFid(priKey32);
            System.out.println("The FID is: \n" + userId);

            String buyerPriKeyCipher = EccAes256K1P7.encryptWithSymKey(priKey32, symKey);
            if (buyerPriKeyCipher.contains("Error")) continue;
            userPriKeyCipher = buyerPriKeyCipher;
            BytesTools.clearByteArray(priKey32);
            return priKey32;
        }
    }

    public static ApiAccount checkApipAccount(BufferedReader br, byte[] symKey) {

        ApiAccount apiAccount = readApipAccountFromFile();

        byte[] sessionKey;

        if (apiAccount == null) {
            apiAccount = createApipAccount(br, symKey);
            if (apiAccount == null) return null;
        }

        boolean revised = false;

        if (apiAccount.getApiUrl() == null) {
            apiAccount.inputApiUrl(br);
            System.out.println("Request the service information...");
            Service service = getService(apiAccount.apiUrl);
            apiAccount.setSid(service.getSid());

            revised = true;
        }
        if (apiAccount.getUserPriKeyCipher() == null) {
            apiAccount.inputPriKeyCipher(br, symKey);
            if (apiAccount.getUserPriKeyCipher() == null) return null;
            revised = true;
        }

        if (apiAccount.getSessionKeyCipher() == null) {
            sessionKey = apiAccount.freshApipSessionKey(symKey, null);
            if (sessionKey == null) return null;
            revised = true;
        }

        if (revised) writeApipParamsToFile(apiAccount, APIP_Account_JSON);

        return apiAccount;
    }

    @Nullable
    public static ApiAccount createApipAccount(BufferedReader br, byte[] symKey) {
        byte[] sessionKey;
        ApiAccount apiAccount = new ApiAccount();

        System.out.println("Input the urlHead of the APIP service. Enter to set as 'https://cid.cash/APIP':");

        String urlHead = FCH.Inputer.inputString(br);

        if ("".equals(urlHead)) {
            urlHead = "https://cid.cash/APIP";
        }
        apiAccount.setApiUrl(urlHead);

        System.out.println("Request the service information...");
        Service service = getService(urlHead);
        if (service == null) {
            System.out.println("Get APIP service wrong.");
            return null;
        }
        apiAccount.setSid(service.getSid());
        apiAccount.setService(service);
        apiAccount.setApipParams(ApipParams.fromObject(service.getParams()));
        apiAccount.inputVia(br);

        System.out.println();
        System.out.println("Set the APIP service buyer(requester)...");
        apiAccount.inputPriKeyCipher(br, symKey);
        sessionKey = apiAccount.freshApipSessionKey(symKey, null);
        if (sessionKey == null) return null;

        writeApipParamsToFile(apiAccount, APIP_Account_JSON);
        return apiAccount;
    }

    static Service getService(String urlHead) {
        ApipClientData apipClientData = new OpenAPIs().getService(urlHead);

        Service service;
        try {
            Object serviceObj = apipClientData.getResponseBody().getData();
            Gson gson = new Gson();
            service = gson.fromJson(gson.toJson(serviceObj), Service.class);
        } catch (Exception e) {
            System.out.println("Get service of " + urlHead + " wrong.");
            e.printStackTrace();
            return null;
        }
        System.out.println("Got the service:");
        System.out.println(JsonTools.getNiceString(service));
        return service;
    }

    @Nullable
    public static String priKeyCipherToSymKeyCipher(String cipher, byte[] priKey, byte[] symKey) {
        String symKeyCipher;
        byte[] sessionKey = decryptHexWithPriKey(cipher, priKey);
        if (sessionKey == null) {
            System.out.println("Failed to decrypt.");
            return null;
        }

        symKeyCipher = EccAes256K1P7.encryptWithSymKey(sessionKey, symKey);
        if (symKeyCipher.contains("Error")) {
            System.out.println("Get sessionKey wrong:" + symKeyCipher);
        }
        return symKeyCipher;
    }

    public static byte[] decryptHexWithPriKey(String cipher, byte[] priKey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        EccAesDataByte eccAesDataBytes = ecc.decrypt(cipher, priKey);
        if (eccAesDataBytes.getError() != null) {
            System.out.println("Failed to decrypt: " + eccAesDataBytes.getError());
            BytesTools.clearByteArray(priKey);
            return null;
        }
        String sessionKeyHex = new String(eccAesDataBytes.getMsg(), StandardCharsets.UTF_8);
        return HexFormat.of().parseHex(sessionKeyHex);
    }

    public static void writeApipParamsToFile(ApiAccount apipParamsForClient, String fileName) {
        JsonTools.writeObjectToJsonFile(apipParamsForClient, fileName, false);
    }

    public static ApiAccount readApipAccountFromFile() {
        File file = new File(APIP_Account_JSON);

        ApiAccount apipParamsForClient;
        try {
            if (!file.exists()) {
                boolean done = file.createNewFile();
                if (!done) {
                    System.out.println("Create " + APIP_Account_JSON + " wrong.");
                }
            }
            FileInputStream fis = new FileInputStream(file);
            apipParamsForClient = JsonTools.readObjectFromJsonFile(fis, ApiAccount.class);
            if (apipParamsForClient != null) return apipParamsForClient;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

//    public void initiate(){
//        id = "";
//        sid = "";
//        userName = "";
//        passwordCipher = "";
//        userPriKeyCipher = "";
//        sessionName = "";
//        sessionKeyCipher = "";
//        sessionExpire = 0;
//        lastHeight = 0;
//        balance = 0;
//    }

    public String makeApiAccountId(String sid, String userName) {
        byte[] bundleBytes;
        if(userName!=null) {
            bundleBytes = BytesTools.bytesMerger(sid.getBytes(), userName.getBytes());
        }else bundleBytes = sid.getBytes();
        return HexFormat.of().formatHex(Hash.Sha256x2(bundleBytes));
    }

    public void inputAll(byte[] symKey, ApiProvider apiProvider,BufferedReader br) {
        try  {
            this.sid=apiProvider.getSid();
            this.apiUrl = apiProvider.getApiUrl();
            ApiProvider.ApiType type = apiProvider.getType();

            switch (type) {
                case NaSaRPC,ES -> {
                    inputUsername(br);
                    inputPasswordCipher(symKey, br);
                }
                case Redis -> {}
                case APIP -> {
                    if(sid==null)inputSid(br);
                    inputPriKeyCipher(symKey, br);
                    while(userName==null) {
                        inputUsername(br);
                        if(userName!=null) {
                            userId = userName;
                            break;
                        }
                        System.out.println("UserName is necessary");
                    }
                    inputVia(br);
                }
                default -> {
                    inputUsername(br);
                    inputUserId(br);
                    inputPasswordCipher(symKey, br);
                    inputPasswordCipher(symKey, br);
                    inputSessionName(br);
                    inputSessionKeyCipher(symKey, br);
                    inputSessionExpire(br);
                }
            }
            this.id = makeApiAccountId(sid,this.userName);
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }

    private void inputSid(BufferedReader br) throws IOException {
        this.sid = Inputer.promptAndSet(br, "sid", this.sid);
    }
    private void inputUserId(BufferedReader br) throws IOException {
        this.userName = Inputer.promptAndSet(br, "userId", this.userId);
    }
    private void inputUsername(BufferedReader br) throws IOException {
        this.userName = Inputer.promptAndSet(br, "userName", this.userName);
    }

    public void updateAll(byte[]symKey, ApiProvider apiProvider,BufferedReader br) {
        try {
            inputSid(br);
            this.userName = promptAndUpdate(br, "userName", this.userName);
            this.passwordCipher = updateKeyCipher(br, "user's passwordCipher", this.passwordCipher,symKey);
            this.userPriKeyCipher = updateKeyCipher(br, "userPriKeyCipher", this.userPriKeyCipher,symKey);
            this.sessionName = promptAndUpdate(br, "sessionName", this.sessionName);
            this.via = promptAndUpdate(br, "via", this.via);
            this.sessionKeyCipher = updateKeyCipher(br, "sessionKeyCipher", this.sessionKeyCipher,symKey);
            this.sessionExpire = Long.parseLong(promptAndUpdate(br, "sessionExpire", String.valueOf(this.sessionExpire)));
            this.id = makeApiAccountId(sid,this.userName);
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }
    private String updateKeyCipher(BufferedReader reader, String fieldName, String currentValue, byte[] symKey) throws IOException {
        System.out.println(fieldName + " current value: " + currentValue);
        System.out.print("Do you want to update it? (y/n): ");

        if ("y".equalsIgnoreCase(reader.readLine())) {
            return inputKeyCipher(reader,fieldName,symKey);
        }
        return currentValue;
    }

    private void inputPasswordCipher(byte[] symKey, BufferedReader br) throws IOException {
        while (true) {
            if (Inputer.askIfYes(br, "Input the password? y/n")) {
                char[] password = Inputer.inputPassword(br, "Input the password:");
                this.passwordCipher = EccAes256K1P7.encryptWithSymKey(BytesTools.utf8CharArrayToByteArray(password), symKey);
                return;
            }
            if (Inputer.askIfYes(br, "Input the password cipher? y/n")) {
                String input = inputKeyCipher(br, "user's passwordCipher", symKey);
                if (input == null) continue;
                this.passwordCipher = input;
            }
            return;
        }
    }

    public void inputPriKeyCipher(byte[] symKey, BufferedReader br) {
        System.out.println();
        if(Inputer.askIfYes(br,"Set the API buyer priKey? y/n"))
            while(true) {
                try {
                    this.userPriKeyCipher = EccAes256K1P7.inputPriKeyCipher(br, symKey);
                    break;
                }catch (Exception e){
                    System.out.println("Wrong input. Try again.");
                }
            }
    }

    private void inputSessionName(BufferedReader br) throws IOException {
        this.sessionName = Inputer.promptAndSet(br, "sessionName", this.sessionName);
    }

    private void inputSessionKeyCipher(byte[] symKey, BufferedReader br) throws IOException {
        this.sessionKeyCipher = inputKeyCipher(br, "sessionKeyCipher", symKey);
    }

    private void inputSessionExpire(BufferedReader br) throws IOException {
        this.sessionExpire = Inputer.promptForLong(br, "sessionExpire", this.sessionExpire);
    }

    public static String inputKeyCipher(BufferedReader br, String keyName, byte[]symKey) {
        byte[] password;
        while(true) {
            System.out.println("Input the " + keyName + ", enter to exit:");
            String str = Inputer.inputString(br);
            if ("".equals(str)) {
                return null;
            }
            try {
                password = EccAes256K1P7.decryptJsonBytes(str, Inputer.getPasswordBytes(br));
                if(password!=null)break;
            }catch (Exception e){
                System.out.println("Something wrong. Try again.");
            }
        }
        return EccAes256K1P7.encryptWithSymKey(password,symKey);
    }

    public double buyApip(String urlHead, byte[] priKey) {

        boolean done = updateApipService(urlHead);
        if (!done) return 0;
        ApipClientData apipClientData;

        long minPay = ParseTools.fchStrToSatoshi(apipParams.getMinPayment());

        long price;
        try {
            price = (long) (Double.parseDouble(apipParams.getPricePerKBytes()) * COIN_TO_SATOSHI);
        } catch (Exception ignore) {
            try {
                price = (long) (Double.parseDouble(apipParams.getPricePerRequest()) * COIN_TO_SATOSHI);
            } catch (Exception e) {
                System.out.println("The price of APIP service is 0.");
                price = 0;
            }
        }
        double payValue;

        payValue = (double) Math.max(price * orderRequestTimes, minPay) / COIN_TO_SATOSHI;

        apipClientData = FreeGetAPIs.getCashes(urlHead, this.getUserId(), payValue);

        if (apipClientData.checkResponse() != 0) {
            log.error("Failed to buy APIP service. Code:{},Message:{}", apipClientData.getCode(), apipClientData.getMessage());
            return payValue;
        }
        List<Cash> cashList = ApipDataGetter.getCashList(apipClientData.getResponseBody().getData());

        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();

        sendTo.setFid(apipParams.getAccount());
        sendTo.setAmount(payValue);
        sendToList.add(sendTo);

        String txHex = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, null);

        apipClientData = FreeGetAPIs.broadcast(urlHead, txHex);

        if (apipClientData.isBadResponse("buy APIP service")) return payValue;

        log.debug("Paid for APIP service: " + payValue + " f to " + apipParams.getAccount() + ". \nWait for confirmation for a few minutes.");
        log.debug("RawTx: " + txHex);
        log.debug("TxId: " + apipClientData.getResponseBody().getData());

        return payValue;
    }

    public boolean updateApipService(String urlHead) {
        ApipClientData apipClientData = OpenAPIs.getService(urlHead);

        if (apipClientData.isBadResponse("get service")) {
            log.error("Failed to buy APIP service. Code:{},Message:{}", apipClientData.getCode(), apipClientData.getMessage());
            return false;
        }
        balance = apipClientData.getResponseBody().getBalance();
        Gson gson1 = new Gson();

        try {
            service = gson1.fromJson(gson1.toJson(apipClientData.getResponseBody().getData()), Service.class);
            apipParams = gson1.fromJson(gson1.toJson(service.getParams()), ApipParams.class);
            sid = service.getSid();
            this.apiUrl = apipParams.getUrlHead();
        } catch (Exception e) {
            System.out.println(JsonTools.getNiceString(service));
            log.error("Failed to get APIP service information.", e);
            return false;
        }
        return true;
    }

    public byte[] connectApip(ApiProvider apiProvider, byte[] symKey, BufferedReader br){

        if(!sid.equals(apiProvider.getSid())){
            System.out.println("The SID of apiProvider "+apiProvider.getSid()+" is not the same as the SID "+sid+" in apiAccount.");
            if(Inputer.askIfYes(br,"Reset the SID of apiAccount to "+apiProvider.getSid()+"? y/n")){
                sid=apiProvider.getSid();
            }else return null;
        }

        if(!apiProvider.getType().equals(ApiProvider.ApiType.APIP)){
            System.out.println("It's not APIP provider.");
            if(Inputer.askIfYes(br,"Reset the type of apiProvider to "+ApiProvider.ApiType.APIP+"? y/n")){
                apiProvider.setType(ApiProvider.ApiType.APIP);
            }else return null;
            return null;
        }

        if (apiUrl==null && apiProvider.getApiUrl() == null) {
            System.out.println("The apiUrl is required.");
            if(Inputer.askIfYes(br,"Reset the apiUrl(urlHead)? y/n")){
                inputApiUrl(br);
                apiProvider.setApiUrl(apiUrl);
            }return null;
        }

        if(! apiProvider.getApiUrl().equals(this.apiUrl)){
            if(Inputer.askIfYes(br,"The apiUrl of apiProvider "+apiProvider.getApiUrl() +" is not the same as the apiUrl "+ apiUrl+" of the apiAccount. Reset the apiUrl of apiAccount? y/n")) {
                this.apiUrl = apiProvider.getApiUrl();
            }else if(!Inputer.askIfYes(br,"Connect with "+apiUrl+"? y/n"))
                return null;
        }

        if(!checkApipProvider(apiProvider,apiUrl)){
            log.debug("Failed to get service from {}", apiProvider.getApiUrl());
            System.out.println("Failed to get APIP service.");
            Menu.anyKeyToContinue(br);
            return null;
        }

        if (userPriKeyCipher == null) {
            System.out.println("Set APIP requester priKey...");
            inputPriKeyCipher(symKey,br);
        }

        byte[] sessionKey1 =
                checkSessionKey(apiProvider, symKey, br);

        if(sessionKey1==null) return null;

        sessionKey = sessionKey1;
        System.out.println("Connected to the initial APIP service: " + sid + " on " + apiUrl);
        return sessionKey;
    }


    public boolean checkApipProvider(ApiProvider apiProvider,String apiUrl) {
        Service apipService = ApipTools.getApipService(apiUrl);
        if(apipService==null) {
            System.out.println("Failed to get APIP service from "+apiUrl);
            return false;
        }
        service = apipService;
        apipParams = (ApipParams) apipService.getParams();

        sid = apipService.getSid();
        if(apipService.getUrls()!=null && apipService.getUrls().length>0)
            apiProvider.setOrgUrl(apipService.getUrls()[0]);
        ApipParams apipParams = (ApipParams) apipService.getParams();
        if(apipParams!=null && apipParams.getUrlHead()!=null)
            apiUrl = apipParams.getUrlHead();
        apiProvider.setOwner(apipService.getOwner());
        apiProvider.setProtocols(apipService.getProtocols());
        apiProvider.setTicks(new String[]{"fch"});

        return true;
    }

    private byte[] checkSessionKey(ApiProvider apiProvider, byte[] symKey, BufferedReader br) {
        if (sessionKeyCipher== null) {
            this.sessionKey = freshApipSessionKey(symKey, null);
            if (this.sessionKey==null) {
                return null;
            }
        } else {
            this.sessionKey = decryptSessionKey(sessionKeyCipher,symKey);
            if(sessionKey==null)return null;
        }

        ApipClientData apipClientData = ConstructAPIs.serviceByIdsPost(this.apiUrl, new String[]{sid}, via, this.sessionKey);
        if(apipClientData == null)return null;
        if(apipClientData.isBadResponse(" get APIP service")){
            if(apipClientData.getCode() == CodeAndMsg.Code1004InsufficientBalance){
                buyApip(symKey);
                return null;
            }
            if(apipClientData.getCode()== CodeAndMsg.Code1002SessionNameMissed || apipClientData.getCode()== CodeAndMsg.Code1009SessionTimeExpired){
                this.sessionKey = freshApipSessionKey(symKey, null);
                if (this.sessionKey==null) {
                    return null;
                }
            }
        }
        freshApipService(apipClientData,br);

        balance = apipClientData.getResponseBody().getBalance();
        if(isApipBalanceSufficient()) buyApip(symKey);
        return sessionKey;
    }

    private void freshApipService(ApipClientData apipClientData,BufferedReader br) {
        Map<String, Service> stringServiceMap = ApipTools.parseApipServiceMap(apipClientData);
        if(stringServiceMap==null){
            System.out.println("Failed to get service with sessionKey.");
            return;
        }
        Service newService = stringServiceMap.get(sid);
        if(newService==null){
            System.out.println("Failed to get service with sessionKey.");
            return;
        }

        service = newService;
        apipParams = ApipParams.fromObject(service.getParams());
        service.setParams(apipParams);
        if(!apiUrl.equals(apipParams.getUrlHead())){
            if(Inputer.askIfYes(br,"The current API URL is "+ apiUrl + ". Replace it with the new updated URL "+apipParams.getUrlHead()+"? y/n")){
                apiUrl = apipParams.getUrlHead();
            }
        }

    }

    public void buyApip(byte[] symKey) {
        byte[] priKey = decryptUserPriKey(userPriKeyCipher, symKey);
        buyApip(apiUrl, priKey);
        BytesTools.clearByteArray(priKey);
    }

    //TODO Repeated
//    public byte[] initApipSessionKey(BufferedReader br, byte[] symKey, String mode) {
//        byte[] priKey = this.inputUserPriKey(symKey, br);
//
//        SignInData signInData = requestApipSessionKey(priKey, mode);
//
//        if (signInData == null) {
//            log.error("Sign in APIP failed.");
//            return null;
//        }
//
//        String sessionKeyCipher = priKeyCipherToSymKeyCipher(signInData.getSessionKeyCipher(), priKey, symKey);
//
//        if (sessionKeyCipher == null) {
//            log.error("Handle sessionKey failed.");
//            return null;
//        }
//
//        setSessionKeyCipher(sessionKeyCipher);
//
//        byte[] sessionKey = decryptSessionKey(sessionKeyCipher,symKey);
//
//        if (sessionKey == null) {
//            log.error("Decrypt sessionKey failed.");
//            return null;
//        }
//
//        setSessionName(ApipTools.getSessionName(sessionKey));
//        setSessionExpire(signInData.getExpireTime());
//
//        BytesTools.clearByteArray(priKey);
//        return sessionKey;
//    }
    public byte[] freshApipSessionKey(byte[] symKey, String mode) {
        byte[] apipSessionKey;
        String sessionKeyCipher;
        byte[] priKey = decryptUserPriKey(userPriKeyCipher,symKey);
        if(priKey==null)return null;

        String fid = KeyTools.priKeyToFid(priKey);

        ApipClientData apipClientData = OpenAPIs.signInEccPost(apiUrl, via, priKey, null);
        if (apipClientData.getResponseBody().getCode() != 0) {
            System.out.println("Get sessionKey wrong. \nCode: " + apipClientData.getResponseBody().getCode() + " Message: " + apipClientData.getResponseBody().getMessage());
            if(apipClientData.getCode()== CodeAndMsg.Code1004InsufficientBalance) {
                buyApip(apiUrl,priKey);
            }
            if(apipClientData.getResponseBody().getData()!=null) {
                System.out.println(JsonTools.getNiceString(apipClientData.getResponseBody().getData()));
                return null;
            }
            return null;
        }

        Gson gson = new Gson();
        SignInData signInData = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()), SignInData.class);

        if (signInData == null) return null;

        sessionKeyCipher = priKeyCipherToSymKeyCipher(signInData.getSessionKeyCipher(), priKey, symKey);
        if (sessionKeyCipher == null) return null;
        this.sessionKeyCipher=sessionKeyCipher;

        apipSessionKey = EccAes256K1P7.decryptJsonBytes(sessionKeyCipher,symKey);
        if(apipSessionKey==null) ExceptionTools.throwRunTimeException("Decrypting sessionKey wrong.");
        sessionKey = apipSessionKey;

        BytesTools.clearByteArray(priKey);

        byte[] sessionNameBytes = Arrays.copyOf(apipSessionKey, 6);
        sessionName = HexFormat.of().formatHex(sessionNameBytes);
        sessionExpire=signInData.getExpireTime();

        return apipSessionKey;
    }

//    public String buyApip(byte[] priKey, String fid, String urlHead) {
//        List<Cash> cashList = ApipTools.getFreeCashes(apiUrl,userId);
//        if (cashList != null) {
//            double toAmount = Double.parseDouble(apipParams.getMinPayment());
//            String account = apipParams.getAccount();
//            String result = WalletTools.sendFch(cashList, priKey, account, toAmount, null, urlHead);
//            if (result.contains("{")) {
//                System.out.println(result);
//                return result;
//            }
//            System.out.println("Paid for the APIP service. Wait for a few minutes for confirmation: " + result);
//            return result;
//        } else {
//            System.out.println("No cashes of " + fid + ". Send some fch to it.");
//            return null;
//        }
//    }


    public void updateApipAccount(BufferedReader br, byte[] symKey) {
        byte[] sessionKey;
        String input;

        System.out.println("The urlHead:\n" + apiUrl + "\nInput the new one. Enter to skip:");
        input = FCH.Inputer.inputString(br);
        if (!"".equals(input)) {
            apiUrl = input;
            Service service = getService(apiUrl);
            if (service == null) return;
            sid = service.getSid();
        }

        String ask = "The via FID is :" + via + "\nInput the new one. Enter to skip:";
        input = FCH.Inputer.inputGoodFid(br, ask);
        if (input != null && !"".equals(input)) via = input;


        while (true) {
            System.out.println("The buyerPriKeyCipher:\n" + userPriKeyCipher + ".\nChange it? y/n:");
            input = FCH.Inputer.inputString(br);
            if ("n".equals(input)) break;
            if ("y".equals(input)) {
                inputPriKeyCipher(br, symKey);
                sessionKey = freshApipSessionKey(symKey, null);
                if (sessionKey == null) return;
                break;
            }
            System.out.println("Wrong input. Try again.");
        }

        System.out.println("Request the service information...");
        Service service1 = getService(apiUrl);
        if (service1 == null) {
            System.out.println("Get APIP service wrong.");
            return;
        }
        sid = service1.getSid();
        service = service1;
        apipParams = ApipParams.fromObject(service1.getParams());

        if(new File(APIP_Account_JSON).exists())writeApipParamsToFile(this, APIP_Account_JSON);

        System.out.println("APIP service updated to " + apiUrl + " at (sid)" + sid + ".");
        System.out.println("The buyer is " + userId);
        Menu.anyKeyToContinue(br);
    }

    public void inputApiUrl(BufferedReader br) {
        System.out.println("Input the urlHead of the APIP service. Enter to set as 'https://cid.cash/APIP':");
        String input = FCH.Inputer.inputString(br);
        if (input.endsWith("/")) input = input.substring(0, input.length() - 1);
        if ("".equals(input)) {
            this.apiUrl = "https://cid.cash/APIP";
        } else this.apiUrl = input;
    }

    public void inputVia(BufferedReader br) {
        String input;
        String ask = "Input the via FID when requesting the APIP service. Enter to set as 'FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7':";
        input = FCH.Inputer.inputString(br,ask);
        while(true) {
            if ("".equals(input)) {
                this.via = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";
                break;
            } else {
                if (KeyTools.isValidFchAddr(input)) {
                    this.via = input;
                    break;
                }else System.out.println("It's not a valid FID. Try again.");
            }
        }
    }


    public byte[] decryptUserPriKey(String cipher, byte[] symKey) {
        System.out.println("Decrypt APIP buyer private key...");
        EccAes256K1P7 ecc = new EccAes256K1P7();
        EccAesDataByte eccAesDataByte = ecc.decrypt(cipher, symKey);

        if (eccAesDataByte.getError() != null) {
            System.out.println("Error: " + eccAesDataByte.getError());
            return null;
        }
        return eccAesDataByte.getMsg();
    }

//    public void inputSessionKeyCipher(BufferedReader br, final byte[] initSymKey) {
//        String ask = "Input sessionKey:";
//        char[] sessionKey = FCH.Inputer.input32BytesKey(br, ask);
//        assert sessionKey != null;
//        EccAes256K1P7 ecc = new EccAes256K1P7();
//        EccAesDataByte eccAesDataByte = new EccAesDataByte();
//        eccAesDataByte.setType(EccAesType.SymKey);
//        eccAesDataByte.setMsg(BytesTools.hexCharArrayToByteArray(sessionKey));
//        eccAesDataByte.setSymKey(initSymKey);
//        ecc.encrypt(eccAesDataByte);//encryptKey(sessionKeyBytes,BytesTools.hexCharArrayToByteArray(passwordBytes));
//        sessionKeyCipher = EccAesData.fromEccAesDataByte(eccAesDataByte).toJson();
//        System.out.println("SessionKeyCipher is: " + sessionKeyCipher);
//    }

    public static byte[] decryptSessionKey(String sessionKeyCipher, byte[] symKey) {
        return EccAes256K1P7.decryptJsonBytes(sessionKeyCipher,symKey);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPasswordCipher() {
        return passwordCipher;
    }

    public void setPasswordCipher(String passwordCipher) {
        this.passwordCipher = passwordCipher;
    }

    public String getUserPriKeyCipher() {
        return userPriKeyCipher;
    }

    public void setUserPriKeyCipher(String userPriKeyCipher) {
        this.userPriKeyCipher = userPriKeyCipher;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getSessionKeyCipher() {
        return sessionKeyCipher;
    }

    public void setSessionKeyCipher(String sessionKeyCipher) {
        this.sessionKeyCipher = sessionKeyCipher;
    }

    public long getSessionExpire() {
        return sessionExpire;
    }

    public void setSessionExpire(long sessionExpire) {
        this.sessionExpire = sessionExpire;
    }

    public long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public static long getMinRequestTimes() {
        return minRequestTimes;
    }

    public static void setMinRequestTimes(long minRequestTimes) {
        ApiAccount.minRequestTimes = minRequestTimes;
    }

    public static long getOrderRequestTimes() {
        return orderRequestTimes;
    }

    public static void setOrderRequestTimes(long orderRequestTimes) {
        ApiAccount.orderRequestTimes = orderRequestTimes;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public ApipParams getApipParams() {
        return apipParams;
    }

    public void setApipParams(ApipParams apipParams) {
        this.apipParams = apipParams;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
    }

    public byte[] getPassword() {
        return password;
    }

    public void setPassword(byte[] password) {
        this.password = password;
    }

    public String getBestBlockId() {
        return bestBlockId;
    }

    public void setBestBlockId(String bestBlockId) {
        this.bestBlockId = bestBlockId;
    }

    public Object getClient() {
        return client;
    }

    public void setClient(Object client) {
        this.client = client;
    }
}
