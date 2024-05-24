package config;

import APIP.ApipTools;
import APIP.apipData.RequestBody;
import FEIP.feipData.serviceParams.ApipParams;
import APIP.apipData.Session;
import FCH.ParseTools;
import FCH.TxCreator;
import FCH.fchData.Cash;
import FCH.fchData.SendTo;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.DiskParams;
import FEIP.feipData.serviceParams.Params;
import NaSa.NaSaRpcClient;
import NaSa.RPC.GetBlockchainInfo;
import appTools.Inputer;
import appTools.Menu;
import clients.ApiUrl;
import clients.Client;
import clients.apipClient.ApipClient;
import clients.apipClient.ApipClientTask;
import clients.apipClient.OpenAPIs;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import com.google.gson.Gson;
import constants.ApiNames;
import crypto.*;
//import crypto.old.EccAes256K1P7;
import clients.esClient.EsClientMaker;
import clients.diskClient.DiskClient;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.NumberTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static appTools.Inputer.chooseOne;
import static appTools.Inputer.promptAndUpdate;
import static constants.Constants.APIP_Account_JSON;
import static constants.Constants.COIN_TO_SATOSHI;
import static crypto.KeyTools.priKeyToFid;
import static fcData.AlgorithmId.FC_Aes256Cbc_No1_NrC7;
import static fcData.AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;

public class ApiAccount {
    private static final Logger log = LoggerFactory.getLogger(ApiAccount.class);
    public static long minRequestTimes = 100;
    public static long orderRequestTimes = 10000;
    private transient byte[] password;
    private String id;
    private String sid;
    private String userId;
    private String userName;
    private String passwordCipher;
    private String userPubKey;
    private String userPriKeyCipher;
    private Session session;
    private transient byte[] sessionKey;
    private transient Service service;
    private transient Params serviceParams;
    private String apiUrl;
    private String via;
    private long bestHeight;
    private String bestBlockId;
    private long balance;
    private transient Object client;
    private transient EsClientMaker esClientMaker;
    private transient ApipClient apipClient;

    public static void updateSession(ApiAccount apipAccount, byte[] symKey, Session session, byte[] newSessionKey) {
        String newSessionKeyCipher = new Encryptor(FC_EccK1AesCbc256_No1_NrC7).encryptToJsonBySymKey(newSessionKey, symKey);
        if(newSessionKeyCipher.contains("Error"))return;
        session.setSessionKeyCipher(newSessionKeyCipher);
        apipAccount.session.setSessionKeyCipher(newSessionKeyCipher);
        apipAccount.session.setExpireTime(session.getExpireTime());
        String newSessionName = ApipTools.getSessionName(newSessionKey);
        apipAccount.session.setSessionName(newSessionName);
        System.out.println("SessionName:" + newSessionName);
        System.out.println("SessionKeyCipher: " + session.getSessionKeyCipher());
    }

    public boolean isBalanceSufficient(){
        long price;
        if(serviceParams.getPricePerKBytes()!=null){
            price= (long) (NumberTools.roundDouble8(Double.parseDouble(serviceParams.getPricePerKBytes()))*COIN_TO_SATOSHI);
        }else price= (long) (NumberTools.roundDouble8(Double.parseDouble(serviceParams.getPricePerRequest()))*COIN_TO_SATOSHI);

        return balance < price * minRequestTimes;
    }

    public Object connectApi(ApiProvider apiProvider, byte[] symKey, BufferedReader br, @Nullable ApipClient apipClient) {

        if (!checkApiGeneralParams(apiProvider, br)) return null;

        switch (apiProvider.getType()){
            case APIP -> {
                return connectApip(apiProvider,symKey,br);
            }
            case NASARPC -> {
                return connectNaSaRPC(symKey);
            }
            case ES -> {
                return connectEs(symKey);
            }
            case REDIS -> {
                return connectRedis();
            }
            case DISK -> {
                return connectDisk(apiProvider,symKey,br,apipClient);
            }
            case OTHER -> {
                return connectOtherApi(apiProvider, symKey);
            }
        }
        return null;
    }


    public Object connectApi(ApiProvider apiProvider, byte[] symKey) {

        if (!checkApiGeneralParams(apiProvider)) return null;

        switch (apiProvider.getType()){
            case APIP -> {
                return connectApip(apiProvider,symKey);
            }
            case NASARPC -> {
                return connectNaSaRPC(symKey);
            }
            case ES -> {
                return connectEs(symKey);
            }
            case REDIS -> {
                return connectRedis();
            }
            case DISK -> {
                return connectDisk(apiProvider,symKey);
            }
            case OTHER -> {
                return connectOtherApi(apiProvider, symKey);
            }
        }
        return null;
    }

    public void closeEs(){
        if(client==null){
            System.out.println("No ES esClient running.");
            return;
        }
        closeEsClient();
    }

    public void showApipBalance(){
        System.out.println("APIP balance: "+(double) balance/ COIN_TO_SATOSHI + " F");
        System.out.println("Rest request: "+balance/(Double.parseDouble(serviceParams.getPricePerKBytes()) * COIN_TO_SATOSHI)+" times");
    }

    private byte[] connectOtherApi(ApiProvider apiProvider, byte[] symKey) {
        System.out.println("Connect to some other APIs. Undeveloped.");
        return null;
    }

    private boolean checkApiGeneralParams(ApiProvider apiProvider, BufferedReader br) {
        if(!sid.equals(apiProvider.getSid())){
            System.out.println("The SID of apiProvider "+ apiProvider.getSid()+" is not the same as the SID "+sid+" in apiAccount.");
            if(Inputer.askIfYes(br,"Reset the SID of apiAccount to "+ apiProvider.getSid()+"?")){
                sid= apiProvider.getSid();
            }else return false;
        }

        if (apiUrl==null && apiProvider.getApiUrl() == null) {
            System.out.println("The apiUrl is required.");
            if(Inputer.askIfYes(br,"Reset the apiUrl(urlHead)?")){
                inputApiUrl(br);
                apiProvider.setApiUrl(apiUrl);
            }
            return false;
        }

        if(! apiProvider.getApiUrl().equals(this.apiUrl)){
            this.apiUrl = apiProvider.getApiUrl();
        }
        return true;
    }


    private boolean checkApiGeneralParams(ApiProvider apiProvider){
        if(!sid.equals(apiProvider.getSid())){
            log.error("The SID of apiProvider "+ apiProvider.getSid()+" is not the same as the SID "+sid+" in apiAccount.");
            return false;
        }

        if (apiUrl==null && apiProvider.getApiUrl() == null) {
            log.error("The apiUrl is required.");
            return false;
        }

        if(! apiProvider.getApiUrl().equals(this.apiUrl)){
            this.apiUrl = apiProvider.getApiUrl();
        }
        return true;
    }

    private NaSaRpcClient connectNaSaRPC(byte[] symKey) {
        if(apiUrl==null) {
            System.out.println("The URL of the API is necessary.");
            return null;
        }
        if(userName==null) {
            System.out.println("The username of the API is necessary.");
            return null;
        }
        if(passwordCipher==null) {
            System.out.println("The password of the API is necessary.");
            return null;
        }
        password = new Decryptor().decryptJsonBySymKey(passwordCipher,symKey).getData();
        if(password==null)return null;

        NaSaRpcClient naSaRpcClient = new NaSaRpcClient(apiUrl,userName,password);


        GetBlockchainInfo.BlockchainInfo blockchainInfo = naSaRpcClient.getBlockchainInfo();

        if(blockchainInfo==null) return null;

        naSaRpcClient.setBestBlockId(blockchainInfo.getBestblockhash());
        naSaRpcClient.setBestHeight(blockchainInfo.getBlocks()-1);
        this.bestHeight = blockchainInfo.getBlocks()-1;
        this.bestBlockId = blockchainInfo.getBestblockhash();
        this.client = naSaRpcClient;
        log.info("The NaSa node on "+apiUrl+" is connected.");
        return naSaRpcClient;
    }

    private ElasticsearchClient connectEs(byte[] symKey) {
        if(apiUrl==null) {
            System.out.println("The URL of the API is necessary.");
            return null;
        }
        esClientMaker = new EsClientMaker();
        esClientMaker.getEsClientSilent(this,symKey);

        try {
            IndicesResponse result = esClientMaker.esClient.cat().indices();
            log.info("Got ES client. There are "+result.valueBody().size()+" indices in ES.");
        } catch (IOException e) {
            log.debug("Failed to create ES client. Check ES.");
            System.exit(0);
        }
        this.client = esClientMaker.esClient;
        return esClientMaker.esClient;
    }

    private DiskClient connectDisk(ApiProvider apiProvider, byte[] symKey, BufferedReader br,ApipClient apipClient) {

        DiskClient diskClient;
        if(client==null){
//            String urlTailPath = ApiUrl.makeUrlTailPath(ApiNames.DiskApiType, "0",ApiNames.VersionV1);
            diskClient = new DiskClient(apiProvider,this,symKey,apipClient);
            client = diskClient;
        }
        else diskClient = (DiskClient) client;

        if(!apiProvider.getType().equals(ApiType.DISK)){
            System.out.println("It's not Disk provider.");
            if(Inputer.askIfYes(br,"Reset the type of apiProvider to "+ ApiType.DISK +"?")){
                apiProvider.setType(ApiType.DISK);
            }else return null;
            return null;
        }

        if(!checkDiskProvider(apiProvider,apiUrl,apipClient)){
            log.debug("Failed to get service from {}", apiProvider.getApiUrl());
            System.out.println("Failed to get Disk service.");
            Menu.anyKeyToContinue(br);
            return null;
        }

        if (userPriKeyCipher == null) {
            System.out.println("Set requester priKey...");
            inputPriKeyCipher(symKey,br);
        }
        String signInPath = ApiUrl.makeUrlTailPath(ApiNames.DiskApiType, ApiNames.SN_0,ApiNames.VersionV1);
        byte[] sessionKey1 =
                checkSessionKey(symKey,signInPath,apiProvider.getType());

        if(sessionKey1==null) return null;

        sessionKey = sessionKey1;
        diskClient.setSessionKey(sessionKey);
        client = diskClient;

        log.debug("Connected to the Disk service: " + sid + " on " + apiUrl);
//        DiskClient diskClient = new DiskClient(apiProvider,this,symKey,apipClient);
        return  diskClient;
    }


    public DiskClient connectDisk(ApiProvider apiProvider, byte[] symKey){
        DiskClient diskClient;
        if(client==null) {
            diskClient = new DiskClient(apiProvider, this, symKey, apipClient);
            client=diskClient;
        } else diskClient = (DiskClient) client;

        if(!apiProvider.getType().equals(ApiType.DISK)){
            log.error("It's not DISK provider.");
            return null;
        }

        if(checkFcApiProvider(apiProvider,apipClient,ApiType.DISK,DiskParams.class)==null){
            log.debug("Failed to get service from {}", apiProvider.getApiUrl());
            return null;
        }

        if (userPriKeyCipher == null) {
            log.error("Set DISK requester priKey...");
            return null;
        }
        String signInPath = ApiUrl.makeUrlTailPath(ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1);

        byte[] sessionKey1 = checkSessionKey(symKey,signInPath, apiProvider.getType());

        if(sessionKey1==null) return null;

        sessionKey = sessionKey1;
        diskClient.setSessionKey(sessionKey);
        client = diskClient;
        log.debug("Connected to the DISK service: " + sid + " on " + apiUrl);

        return diskClient;
    }

    public void closeEsClient(){
        try {
            esClientMaker.shutdownClient();
        } catch (IOException e) {
            log.debug("Failed to close the esClient.");
        }
    }

    private JedisPool connectRedis() {
        JedisPool jedisPool;
        if(apiUrl==null)jedisPool = new JedisPool();
        else jedisPool=new JedisPool(apiUrl);
        this.client = jedisPool;
        try(Jedis jedis = jedisPool.getResource()){
            if(!"pong".equalsIgnoreCase(jedis.ping()))return null;
        }
        log.info("The JedisPool is ready.");
        return jedisPool;
    }

    public byte[] inputPriKeyCipher(BufferedReader br, byte[] symKey) {
        byte[] priKey32;
        while (true) {
            String input = FCH.Inputer.inputString(br, "Generate a new private key? y/n");
            if ("y".equals(input)) {
                priKey32 = KeyTools.genNewFid(br).getPrivKeyBytes();
            } else {
                priKey32 = KeyTools.inputCipherGetPriKey(br);
            }
            if (priKey32 == null) return null;
            this.userPubKey = Hex.toHex(KeyTools.priKeyToPubKey(priKey32));
            userId = priKeyToFid(priKey32);
            System.out.println("The FID is: \n" + userId);

            Encryptor encryptor = new Encryptor(FC_Aes256Cbc_No1_NrC7);
            CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(priKey32,symKey);//EccAes256K1P7.encryptWithSymKey(priKey32, symKey);
            if(cryptoDataByte.getCode() !=0){
                System.out.println(cryptoDataByte.getMessage());
                return null;
            }
            String buyerPriKeyCipher = cryptoDataByte.toJson();
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

        if (apiAccount.session.getSessionKeyCipher() == null) {
            sessionKey = apiAccount.freshSessionKey(symKey,ApiNames.APIP0V1Path, ApiType.APIP, null);
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
        sessionKey = apiAccount.freshSessionKey(symKey, ApiNames.APIP0V1Path,ApiType.APIP, null);
        if (sessionKey == null) return null;

        writeApipParamsToFile(apiAccount, APIP_Account_JSON);
        return apiAccount;
    }

    static Service getService(String urlHead) {
        ApipClientTask apipClientData = new OpenAPIs().getService(urlHead);

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

        CryptoDataByte cryptoDataByte = new Encryptor(FC_EccK1AesCbc256_No1_NrC7).encryptBySymKey(sessionKey, symKey);
        if(cryptoDataByte.getCode()==0)
            return cryptoDataByte.toJson();
        else return null;
    }

    public static byte[] decryptHexWithPriKey(String cipher, byte[] priKey) {

        CryptoDataByte cryptoDataBytes = new Decryptor().decryptJsonByAsyOneWay(cipher, priKey);
        if (cryptoDataBytes.getCode() != 0) {
            System.out.println("Failed to decrypt: " + cryptoDataBytes.getMessage());
            BytesTools.clearByteArray(priKey);
            return null;
        }
        String sessionKeyHex = new String(cryptoDataBytes.getData(), StandardCharsets.UTF_8);
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
        return HexFormat.of().formatHex(Hash.sha256x2(bundleBytes));
    }

    public void inputAll(byte[] symKey, ApiProvider apiProvider,BufferedReader br) {
        try  {
            this.sid=apiProvider.getSid();
            this.apiUrl = apiProvider.getApiUrl();
            ApiType type = apiProvider.getType();
            if(type==null)type = chooseOne(ApiType.values(),"Choose the type:",br);

            switch (type) {
                case NASARPC,ES -> {
                    inputUsername(br);
                    inputPasswordCipher(symKey, br);
                }
                case REDIS -> {}
                case APIP, DISK -> {
                    if(sid==null)inputSid(br);
                    inputPriKeyCipher(symKey, br);
                    while(userName==null) {
                        if(userPriKeyCipher==null)return;
                        byte[] userPriKey = new Decryptor().decryptJsonBySymKey(userPriKeyCipher, symKey).getData();
                        userName = KeyTools.priKeyToFid(userPriKey);
                        userId = userName;
                        BytesTools.clearByteArray(userPriKey);
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
            this.sid = promptAndUpdate(br, "sid", this.sid);
            this.userName = promptAndUpdate(br, "userName", this.userName);
            this.passwordCipher = updateKeyCipher(br, "user's passwordCipher", this.passwordCipher,symKey);
            this.userPriKeyCipher = updateKeyCipher(br, "userPriKeyCipher", this.userPriKeyCipher,symKey);
            this.userPubKey = makePubKey(this.userPriKeyCipher,symKey);
            this.session.setSessionName(promptAndUpdate(br, "sessionName", this.session.getSessionName()));
            this.session.setSessionKeyCipher(updateKeyCipher(br, "sessionKeyCipher", this.session.getSessionKeyCipher(),symKey));
            this.session.setExpireTime(promptAndUpdate(br, "sessionExpire", this.session.getExpireTime()));

            this.via = promptAndUpdate(br, "via", this.via);
            this.id = makeApiAccountId(sid,this.userName);
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }

    public static String makePubKey(String userPriKeyCipher, byte[] symKey) {
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(userPriKeyCipher,symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] pubKey = KeyTools.priKeyToPubKey(cryptoDataByte.getData());
        return Hex.toHex(pubKey);
    }

//    @org.jetbrains.annotations.Nullable
//    public void makePubKey(byte[] symKey) {
//        DecryptorSym decryptorSym = new DecryptorSym();
//        CryptoDataByte result = decryptorSym.decrypt(this.userPriKeyCipher, symKey);
//        if(result.getCode()!=0){
//            return;
//        }
//        byte[] priKey = result.getData();
//        byte[] pubKey = KeyTools.priKeyToPubKey(priKey);
//        if(pubKey!=null)this.userPubKey = Hex.toHex(pubKey);
//        return;
//    }

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
            if (Inputer.askIfYes(br, "Input the password?")) {
                char[] password = Inputer.inputPassword(br, "Input the password:");
                this.passwordCipher = new Encryptor(FC_Aes256Cbc_No1_NrC7).encryptToJsonBySymKey(BytesTools.utf8CharArrayToByteArray(password), symKey);
                return;
            }
            if (Inputer.askIfYes(br, "Input the password cipher?")) {
                String input = inputKeyCipher(br, "user's passwordCipher", symKey);
                if (input == null) continue;
                this.passwordCipher = input;
            }
            return;
        }
    }

    public void inputPriKeyCipher(byte[] symKey, BufferedReader br) {
        System.out.println();
        if(Inputer.askIfYes(br,"Set the API buyer priKey?"))
            while(true) {
                try {
                    String cipherJson = FCH.Inputer.inputPriKeyCipher(br, symKey);
                    if(cipherJson==null){
                        System.out.println("Wrong input. Try again.");
                        continue;
                    }
                    this.userPriKeyCipher = cipherJson;
                    this.userPubKey=makePubKey(this.userPriKeyCipher,symKey);
                    break;
                }catch (Exception e){
                    System.out.println("Wrong input. Try again.");
                }
            }
    }

    private void inputSessionName(BufferedReader br) throws IOException {
        this.session.setSessionName(Inputer.promptAndSet(br, "sessionName", this.session.getSessionName()));
    }

    private void inputSessionKeyCipher(byte[] symKey, BufferedReader br) throws IOException {
        this.session.setSessionKeyCipher(inputKeyCipher(br, "sessionKeyCipher", symKey));
    }

    private void inputSessionExpire(BufferedReader br) throws IOException {
        this.session.setExpireTime(Inputer.promptForLong(br, "expireTime", this.session.getExpireTime()));
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
                CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonByPassword(str, BytesTools.byteArrayToUtf8CharArray(Inputer.getPasswordBytes(br)));
                if(cryptoDataByte.getCode()!=0){
                    System.out.println("Something wrong. Try again.");
                    continue;
                }
                password = cryptoDataByte.getData();
                break;
            }catch (Exception e){
                System.out.println("Something wrong. Try again.");
            }
        }
        return new Encryptor(FC_EccK1AesCbc256_No1_NrC7).encryptToJsonBySymKey(password,symKey);
    }

    public double buyService(Params params,String sid, ApipClient apipClient, byte[] symKey) {

        boolean done = updateService(sid, apipClient);
        if (!done) return 0;

        long minPay = ParseTools.fchStrToSatoshi(params.getMinPayment());

        long price;
        try {
            price = (long) (Double.parseDouble(params.getPricePerKBytes()) * COIN_TO_SATOSHI);
        } catch (Exception ignore) {
            try {
                price = (long) (Double.parseDouble(params.getPricePerKBytes()) * COIN_TO_SATOSHI);
            } catch (Exception e) {
                System.out.println("The price of APIP service is 0.");
                price = 0;
            }
        }
        double payValue;

        payValue = (double) Math.max(price * ApiAccount.orderRequestTimes, minPay) / COIN_TO_SATOSHI;

        List<Cash> cashList = apipClient.getCashesFree(apipClient.getApiAccount().getUserId(), payValue);
        if(cashList==null)return 0;
        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();

        sendTo.setFid(params.getAccount());
        sendTo.setAmount(payValue);
        sendToList.add(sendTo);

        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymKey(userPriKeyCipher, symKey);
        if(cryptoDataByte.getCode()!=0){
            System.out.println("Failed to decrypt the priKey.");
            return 0;
        }
        byte[] priKey = cryptoDataByte.getData();
        String txHex = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, null);

        String result = apipClient.broadcastRawTx(txHex);

        if(result!=null) {
            log.debug("Paid for service: " + payValue + " f to " + params.getAccount() + ". \nWait for confirmation for a few minutes.");
            log.debug("RawTx: " + txHex);
            log.debug("TxId: " + result);
        }
        return payValue;
    }
    public boolean updateService(String sid, ApipClient apipClient) {
        Service service = apipClient.serviceMapByIds(new String[]{sid}).get(sid);
        if(service == null)return false;
        Params params = Params.getParamsFromService(service,Params.class);
        if(params==null)return false;
        service.setParams(params);
        this.service= service;
        this.serviceParams=params;
        return true;
    }

    public double buyApi(byte[] symKey) {
        byte[] priKey = decryptUserPriKey(userPriKeyCipher, symKey);

        long minPay = ParseTools.fchStrToSatoshi(serviceParams.getMinPayment());

        long price;
        try {
            price = (long) (Double.parseDouble(serviceParams.getPricePerKBytes()) * COIN_TO_SATOSHI);
        } catch (Exception ignore) {
            try {
                price = (long) (Double.parseDouble(serviceParams.getPricePerRequest()) * COIN_TO_SATOSHI);
            } catch (Exception e) {
                System.out.println("The price of APIP service is 0.");
                price = 0;
            }
        }
        double payValue;

        payValue = (double) Math.max(price * orderRequestTimes, minPay) / COIN_TO_SATOSHI;

        String id = userId==null?userName:userId;
//        ApipClient apipClient = (ApipClient)client;

        List<Cash> cashList = apipClient.getCashesFree(id,payValue);//ApipDataGetter.getCashList(apipClientData.getResponseBody().getData());

        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();

        sendTo.setFid(serviceParams.getAccount());
        sendTo.setAmount(payValue);
        sendToList.add(sendTo);

        String txHex = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, null);

        String result = apipClient.broadcastFree(txHex);
        if(result==null){
            log.error("Failed to buy APIP service. Failed to broadcast TX.");
            return 0;
        }

        log.debug("Paid for APIP service: " + payValue + " f to " + serviceParams.getAccount() + ". \nWait for confirmation for a few minutes.");
        log.debug("RawTx: " + txHex);
        log.debug("TxId: " + result);
        BytesTools.clearByteArray(priKey);
        return payValue;
    }

    public boolean updateApipService(String urlHead) {
        ApipClientTask apipClientData = OpenAPIs.getService(urlHead);

        if (apipClientData.checkResponse()!=0) {
            log.error("Failed to buy APIP service. Code:{},Message:{}", apipClientData.getCode(), apipClientData.getMessage());
            return false;
        }
        balance = apipClientData.getResponseBody().getBalance();
        Gson gson1 = new Gson();

        try {
            service = gson1.fromJson(gson1.toJson(apipClientData.getResponseBody().getData()), Service.class);
            serviceParams = gson1.fromJson(gson1.toJson(service.getParams()), ApipParams.class);
            sid = service.getSid();
            ApipParams apipParams = (ApipParams)serviceParams;
            this.apiUrl = apipParams.getUrlHead();
        } catch (Exception e) {
            System.out.println(JsonTools.getNiceString(service));
            log.error("Failed to get APIP service information.", e);
            return false;
        }
        return true;
    }

    public ApipClient connectApip(ApiProvider apiProvider, byte[] symKey, BufferedReader br){

        if(client==null)client = new ApipClient(apiProvider,this,symKey);
        if(!apiProvider.getType().equals(ApiType.APIP)){
            System.out.println("It's not APIP provider.");
            if(Inputer.askIfYes(br,"Reset the type of apiProvider to "+ ApiType.APIP+"?")){
                apiProvider.setType(ApiType.APIP);
            }else return null;
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
                checkSessionKey(symKey, ApiNames.APIP0V1Path,apiProvider.getType());

        if(sessionKey1==null) return null;

        sessionKey = sessionKey1;
        log.debug("Connected to the APIP service: " + sid + " on " + apiUrl);
        apipClient = (ApipClient) client;
        return apipClient;
    }

    public ApipClient connectApip(ApiProvider apiProvider, byte[] symKey){

        if(!apiProvider.getType().equals(ApiType.APIP)){
            log.error("It's not APIP provider.");
            return null;
        }

        if(!checkApipProvider(apiProvider,apiUrl)){
            log.debug("Failed to get service from {}", apiProvider.getApiUrl());
            return null;
        }

        if (userPriKeyCipher == null) {
            log.error("Set APIP requester priKey...");
            return null;
        }
        ApipClient apipClient = null;
        if(client==null){
            apipClient = new ApipClient(apiProvider,this,symKey);
            client = apipClient;
        }
        byte[] sessionKey1 = checkSessionKey(symKey, ApiNames.APIP0V1Path, apiProvider.getType());

        if(sessionKey1==null) return null;

        sessionKey = sessionKey1;
        log.debug("Connected to the initial APIP service: " + sid + " on " + apiUrl);
        return apipClient;
    }




    public boolean checkApipProvider(ApiProvider apiProvider,String apiUrl) {
        Service apipService = ApipTools.getApipService(apiUrl);
        if(apipService==null) {
            System.out.println("Failed to get APIP service from "+apiUrl);
            return false;
        }
        service = apipService;
        serviceParams = (ApipParams) apipService.getParams();

        sid = apipService.getSid();
        if(apipService.getUrls()!=null && apipService.getUrls().length>0)
            apiProvider.setOrgUrl(apipService.getUrls()[0]);
        ApipParams apipParams = (ApipParams) apipService.getParams();
        if(apipParams!=null && apipParams.getUrlHead()!=null)
            this.apiUrl = apipParams.getUrlHead();
        apiProvider.setOwner(apipService.getOwner());
        apiProvider.setProtocols(apipService.getProtocols());
        apiProvider.setTicks(new String[]{"fch"});

        return true;
    }

    public <T> ApiProvider checkFcApiProvider(ApiProvider apiProvider,ApipClient apipClient,ApiType type,Class<T> paramsClass) {
//        String apiUrl =apiProvider.getApiUrl();
        Service service = apipClient.serviceById(apiProvider.getSid());
        if(service==null)return null;
        this.service = service;
        this.serviceParams = (Params) Params.getParamsFromService(service,paramsClass);
        this.sid = service.getSid();
        return ApiProvider.apiProviderFromFcService(service,type);
    }

    public boolean checkDiskProvider(ApiProvider apiProvider,String apiUrl,ApipClient apipClient) {
        String sid = apiProvider.getSid();
        Service diskService = apipClient.serviceById(sid);//serviceMapByIds(new String[]{sid}).get(sid);
        if(diskService==null) {
            System.out.println("Failed to get disk service "+sid);
            return false;
        }
        service = diskService;
        serviceParams = DiskParams.getParamsFromService(diskService);
        sid = diskService.getSid();
        if(diskService.getUrls()!=null && diskService.getUrls().length>0)
            apiProvider.setOrgUrl(diskService.getUrls()[0]);
        DiskParams diskParams = (DiskParams) serviceParams;
        if(diskParams!=null && diskParams.getUrlHead()!=null)
            apiUrl = diskParams.getUrlHead();
        apiProvider.setOwner(diskService.getOwner());
        apiProvider.setProtocols(diskService.getProtocols());
        apiProvider.setTicks(new String[]{"fch"});

        return true;
    }


    private byte[] checkSessionKey(byte[] symKey, String signInPath,ApiType type) {
        if(this.session==null)this.session = new Session();
        if (this.session.getSessionKeyCipher()== null) {
            this.sessionKey=freshSessionKey(symKey,signInPath,type, null);
        } else {
            this.sessionKey =decryptSessionKey(session.getSessionKeyCipher(),symKey);
        }
        if (this.sessionKey==null) {
            return null;
        }
//test the client
        Client client1 = (Client) client;

        client1.setSessionKey(sessionKey);


        boolean done = client1.pingFree(type);
//        Service service1 = serviceMap.get(sid);
//        freshApipService(service1);
        if(isBalanceSufficient()) buyApi(symKey);
        if(done)return sessionKey;
        else return null;
    }

    private byte[] checkApipSessionKey(byte[] symKey, ApiType type) {
        if(this.session==null)this.session = new Session();
        if (this.session.getSessionKeyCipher()== null) {
            this.sessionKey=freshSessionKey(symKey,ApiNames.APIP0V1Path,type, null);
        } else {
            this.sessionKey =decryptSessionKey(session.getSessionKeyCipher(),symKey);
        }
        if (this.sessionKey==null) {
            return null;
        }
//test the client
        ApipClient client1 = (ApipClient)client;

        client1.setSessionKey(sessionKey);


        Map<String, Service> serviceMap = client1.serviceMapByIds(new String[]{sid});
        Service service1 = serviceMap.get(sid);
        freshApipService(service1);
        if(isBalanceSufficient()) buyApi(symKey);
        return sessionKey;
    }

    private void freshApipService(Service service) {
//        Map<String, Service> stringServiceMap = ApipTools.parseApipServiceMap(apipClientData);
//        if(stringServiceMap==null){
//            System.out.println("Failed to get service with the sessionKey.");
//            return;
//        }
//        Service
//        service = stringServiceMap.get(sid);

        if(service==null){
            System.out.println("Failed to get service with the sessionKey.");
            return;
        }
        this.service = service;
        ApipParams apipParams = ApipParams.fromObject(this.service.getParams());
        this.service.setParams(apipParams);
        if(!apiUrl.equals(apipParams.getUrlHead())){
           apiUrl = apipParams.getUrlHead();
        }
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
    public byte[] freshSessionKey(byte[] symKey, String signInPath,ApiType type, RequestBody.SignInMode mode) {
        System.out.println("Fresh the sessionKey of the "+type+" service...");
//        byte[] sessionKey = new byte[0];
//        String sessionKeyCipher;
        byte[] priKey = decryptUserPriKey(userPriKeyCipher,symKey);
        if(priKey==null)return null;
        Session session;
        switch (type){
            case APIP -> {
                ApipClient apipClient = (ApipClient) client;
                session = apipClient.signInEcc(this,ApiType.APIP,RequestBody.SignInMode.NORMAL,symKey);
            }
            case DISK -> {
                DiskClient diskClient = (DiskClient) client;
//                diskClient.setProtocol(ApiNames.DiskApiProtocol);
//                diskClient.setVersion(ApiNames.VersionV1);
//                diskClient.makeUrlTailPath();
//                diskClient.setSignInUrlTailPath(diskClient.getUrlTailPath());
                session = diskClient.signInEcc(this,ApiType.DISK,RequestBody.SignInMode.NORMAL,symKey);
            }
            default -> {
                Client client1 = (Client)client;
                session = client1.signInEcc(priKey,type,mode);
            }
        }

        if (session == null) return null;

//        sessionKeyCipher = priKeyCipherToSymKeyCipher(session.getSessionKeyCipher(), priKey, symKey);
//        if (sessionKeyCipher == null) return null;
//        this.sessionKeyCipher=sessionKeyCipher;

//        sessionKey = EccAes256K1P7.decryptJsonBytes(sessionKeyCipher,symKey);
//        if(sessionKey==null) throw new RuntimeException("Decrypting sessionKey wrong.");
        this.sessionKey = Hex.fromHex(session.getSessionKey());
        session.setSessionKey(null);

        BytesTools.clearByteArray(priKey);
//
//
//        session.setSessionName(Session.makeSessionName(session.getSessionKey()));
//        session.setExpireTime(session.getExpireTime());

        return sessionKey;
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
                sessionKey = freshSessionKey(symKey,ApiNames.APIP0V1Path, ApiType.APIP, null);
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
        serviceParams = ApipParams.fromObject(service1.getParams());

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
        String ask = "Input the FID by whom you knew this service. Default: 'FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7'";
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
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymKey(cipher, symKey);

        if (cryptoDataByte.getMessage() != null) {
            System.out.println("Error: " + cryptoDataByte.getMessage());
            return null;
        }
        return cryptoDataByte.getData();
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
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymKey(sessionKeyCipher,symKey);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.getData();
//        return EccAes256K1P7.decryptJsonBytes(sessionKeyCipher,symKey);
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

    public Params getApipParams() {
        return serviceParams;
    }

    public void setApipParams(ApipParams serviceParams) {
        this.serviceParams = serviceParams;
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

    public Params getServiceParams() {
        return serviceParams;
    }

    public void setServiceParams(Params serviceParams) {
        this.serviceParams = serviceParams;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getUserPubKey() {
        return userPubKey;
    }

    public void setUserPubKey(String userPubKey) {
        this.userPubKey = userPubKey;
    }
}
