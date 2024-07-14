package config;

import apip.ApipTools;
import apip.apipData.RequestBody;
import apip.apipData.Session;
import appTools.Inputer;
import appTools.Menu;
import clients.ApiUrl;
import clients.Client;
import clients.apipClient.ApipClient;
import clients.diskClient.DiskClient;
import clients.esClient.EsClientMaker;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import constants.ApiNames;
import crypto.*;
import fcData.FcReplier;
import fch.ParseTools;
import fch.TxCreator;
import fch.fchData.Cash;
import fch.fchData.SendTo;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import feip.feipData.serviceParams.DiskParams;
import feip.feipData.serviceParams.Params;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.NumberTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;

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
    private String providerId;
    private String userId;
    private String userName;
    private String passwordCipher;
    private String userPubKey;
    private String userPriKeyCipher;
    private Session session;
    private Double minPayment;
    private Map<String,Double> payments;
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
            case NASA_RPC -> {
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

        return switch (apiProvider.getType()){
            case APIP -> connectApip(apiProvider,symKey);
            case NASA_RPC ->  connectNaSaRPC(symKey);
            case ES -> connectEs(symKey);
            case REDIS -> connectRedis();
            case DISK -> connectDisk(apiProvider,symKey);
            case SWAP -> null;
            case OTHER -> connectOtherApi(apiProvider, symKey);
        };
    }


    public void showApipBalance(){
        System.out.println("APIP balance: "+(double) balance/ COIN_TO_SATOSHI + " F");
        System.out.println("Rest request: "+balance/(Double.parseDouble(serviceParams.getPricePerKBytes()) * COIN_TO_SATOSHI)+" times");
    }

    private byte[] connectOtherApi(ApiProvider apiProvider, byte[] symKey) {
        System.out.println("Connect to some other APIs. Undeveloped.");
        System.out.println("Client on "+apiUrl +" is created.\n");
        return null;
    }

    private boolean checkApiGeneralParams(ApiProvider apiProvider, BufferedReader br) {
        if(!providerId.equals(apiProvider.getId())){
            System.out.println("The SID of apiProvider "+ apiProvider.getId()+" is not the same as the SID "+ providerId +" in apiAccount.");
            if(Inputer.askIfYes(br,"Reset the SID of apiAccount to "+ apiProvider.getId()+"?")){
                providerId = apiProvider.getId();
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
        if(!providerId.equals(apiProvider.getId())){
            log.error("The SID of apiProvider "+ apiProvider.getId()+" is not the same as the SID "+ providerId +" in apiAccount.");
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


        NaSaRpcClient.BlockchainInfo blockchainInfo = naSaRpcClient.getBlockchainInfo();

        if(blockchainInfo==null) return null;

        naSaRpcClient.setBestBlockId(blockchainInfo.getBestblockhash());
        naSaRpcClient.setBestHeight(blockchainInfo.getBlocks()-1);
;
        this.bestHeight = blockchainInfo.getBlocks()-1;
        this.bestBlockId = blockchainInfo.getBestblockhash();
        this.client = naSaRpcClient;


        log.info("The NaSa node on "+apiUrl+" is connected.");
        System.out.println("NaSa RPC client on "+apiUrl +" is created.\n");
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
            System.out.println("ES client on "+apiUrl +" is created.\n");
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
        String signInPath = ApiUrl.makeUrlTailPath(ApiNames.SN_0,ApiNames.Version1);
        byte[] sessionKey1 =
                checkSessionKey(symKey,signInPath,apiProvider.getType());

        if(sessionKey1==null) return null;

        sessionKey = sessionKey1;
        diskClient.setSessionKey(sessionKey);
        client = diskClient;

        log.debug("Connected to the Disk service: " + providerId + " on " + apiUrl);
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
        String signInPath = ApiUrl.makeUrlTailPath(ApiNames.SN_0, ApiNames.Version1);
        byte[] sessionKey1 = checkSessionKey(symKey,signInPath, apiProvider.getType());

        if(sessionKey1==null) return null;

        sessionKey = sessionKey1;
        diskClient.setSessionKey(sessionKey);
        diskClient.setUrlHead(apiUrl);
        diskClient.setVia(via);
        client = diskClient;
        log.debug("Connected to the DISK service: " + providerId + " on " + apiUrl);
        System.out.println("Disk client on "+apiUrl +" is created.\n");
        return diskClient;
    }

    public void closeEs(){
        if(client==null){
            System.out.println("No ES esClient running.");
            return;
        }
        try {
            esClientMaker.shutdownClient();
        } catch (IOException e) {
            log.debug("Failed to close the esClient.");
        }
    }

    public JedisPool connectRedis() {
        JedisPool jedisPool;
        if(apiUrl==null)jedisPool = new JedisPool();
        else jedisPool=new JedisPool(apiUrl);
        this.client = jedisPool;
        try(Jedis jedis = jedisPool.getResource()){
            if(!"pong".equalsIgnoreCase(jedis.ping()))return null;
        }
        log.info("The JedisPool is ready.");
        System.out.println("Redis client on "+apiUrl +" is created.\n");
        return jedisPool;
    }

    public byte[] inputPriKeyCipher(BufferedReader br, byte[] symKey) {
        byte[] priKey32;
        while (true) {
            String input = fch.Inputer.inputString(br, "Generate a new private key? y/n");
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
            apiAccount.setProviderId(service.getSid());

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

        String urlHead = fch.Inputer.inputString(br);

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
        apiAccount.setProviderId(service.getSid());
        apiAccount.setService(service);
        ApipParams serviceParams1 = ApipParams.fromObject(service.getParams());
        apiAccount.setApipParams(serviceParams1);
        try {
            apiAccount.setMinPayment(Double.valueOf(serviceParams1.getMinPayment()));
        }catch (Exception ignore){}
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
        FcReplier replier = ApipClient.getService(urlHead, ApipParams.class, ApiNames.Version2);
        if(replier==null)return null;
        Service service = (Service) replier.getData();
        if(service!=null) {
            System.out.println("Got the service:");
            System.out.println(JsonTools.toNiceJson(service));
        }
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
            this.providerId =apiProvider.getId();
            this.apiUrl = apiProvider.getApiUrl();
            ApiType type = apiProvider.getType();
            if(type==null)type = chooseOne(ApiType.values(),"Choose the type:",br);

            switch (type) {
                case NASA_RPC,ES -> {
                    inputUsername(br);
                    inputPasswordCipher(symKey, br);
                }
                case REDIS -> {}
                case APIP, DISK -> {
                    if(providerId ==null)inputSid(br);
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
            this.id = makeApiAccountId(providerId,this.userName);
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }

    private void inputSid(BufferedReader br) throws IOException {
        this.providerId = Inputer.promptAndSet(br, "sid", this.providerId);
    }
    private void inputUserId(BufferedReader br) throws IOException {
        this.userName = Inputer.promptAndSet(br, "userId", this.userId);
    }
    private void inputUsername(BufferedReader br) throws IOException {
        this.userName = Inputer.promptAndSet(br, "userName", this.userName);
    }

    public void updateAll(byte[]symKey, ApiProvider apiProvider,BufferedReader br) {
        try {
            this.providerId = promptAndUpdate(br, "sid", this.providerId);
            this.userName = promptAndUpdate(br, "userName", this.userName);
            this.passwordCipher = updateKeyCipher(br, "user's passwordCipher", this.passwordCipher,symKey);
            this.userPriKeyCipher = updateKeyCipher(br, "userPriKeyCipher", this.userPriKeyCipher,symKey);
            this.userPubKey = makePubKey(this.userPriKeyCipher,symKey);
            this.session.setSessionName(promptAndUpdate(br, "sessionName", this.session.getSessionName()));
            this.session.setSessionKeyCipher(updateKeyCipher(br, "sessionKeyCipher", this.session.getSessionKeyCipher(),symKey));
            this.session.setExpireTime(promptAndUpdate(br, "sessionExpire", this.session.getExpireTime()));

            this.via = promptAndUpdate(br, "via", this.via);
            this.id = makeApiAccountId(providerId,this.userName);
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
            char[] password = Inputer.inputPassword(br, "Input the password. Enter to ignore:");
            if(password!=null)
                this.passwordCipher = new Encryptor(FC_Aes256Cbc_No1_NrC7).encryptToJsonBySymKey(BytesTools.utf8CharArrayToByteArray(password), symKey);
            if(this.passwordCipher==null) {
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
                    String cipherJson = fch.Inputer.inputPriKeyCipher(br, symKey);
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

        List<Cash> cashList  = apipClient.cashValidForPay(HttpRequestMethod.GET,this.userId,payValue,AuthType.FREE);//apipClient.getCashes(apiUrl,this.userId,payValue);
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

        String result = apipClient.broadcastTx(txHex, HttpRequestMethod.GET, AuthType.FREE);

        if(result!=null) {
            log.debug("Paid for service: " + payValue + " f to " + params.getAccount() + ". \nWait for confirmation for a few minutes.");
            log.debug("RawTx: " + txHex);
            log.debug("TxId: " + result);
        }
        return payValue;
    }
    public boolean updateService(String sid, ApipClient apipClient) {
        Service service = apipClient.serviceById(sid);
        if(service == null)return false;
        Params params = Params.getParamsFromService(service,Params.class);
        if(params==null)return false;
        service.setParams(params);
        this.service= service;
        this.serviceParams=params;
        return true;
    }

    public double buyApi(byte[] symKey,ApipClient apipClient) {
        if(apipClient==null) apipClient = Settings.getFreeApipClient();

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

        List<Cash> cashList = apipClient.cashValidForPay(HttpRequestMethod.GET,this.userId,payValue,AuthType.FREE);//apipClient.getCashes(apiUrl,this.userId,payValue);
        if(cashList==null|| cashList.isEmpty())return 0;

        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();

        sendTo.setFid(serviceParams.getAccount());
        sendTo.setAmount(payValue);
        sendToList.add(sendTo);

        String txHex = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, null);

        String result = apipClient.broadcastTx(txHex, HttpRequestMethod.GET, AuthType.FREE);
        if(result==null){
            log.error("Failed to buy APIP service. Failed to broadcast TX.");
            return 0;
        }

        log.debug("Paid for APIP service: " + payValue + " f to " + serviceParams.getAccount() + ". \nWait for confirmation for a few minutes.");
//        log.debug("RawTx: " + txHex);
        log.debug("TxId: " + result);
        BytesTools.clearByteArray(priKey);
        if(payments==null)payments=new HashMap<>();
        payments.put(result,payValue);
        return payValue;
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

        apipClient = (ApipClient) client;
        apipClient.setUrlHead(apiUrl);
        apipClient.setVia(via);

        byte[] sessionKey1 =
                checkSessionKey(symKey, ApiNames.APIP0V1Path,apiProvider.getType());

        if(sessionKey1==null) {
            System.out.println("Failed to get the sessionKey of APIP service from "+apiUrl+". Only free APIs are available.");
        }else sessionKey = sessionKey1;
        log.debug("Connected to the APIP service: " + providerId + " on " + apiUrl);

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
        if(apipClient==null)return null;
        apipClient.setUrlHead(apiUrl);
        apipClient.setVia(via);
        sessionKey = sessionKey1;
        log.debug("Connected to the initial APIP service: " + providerId + " on " + apiUrl);
        System.out.println("Apip client on "+apiUrl +" is created.\n");
        return apipClient;
    }




    public boolean checkApipProvider(ApiProvider apiProvider,String apiUrl) {
        FcReplier replier = ApipClient.getService(apiUrl,ApipParams.class, ApiNames.Version2);
        if(replier==null || replier.getData()==null) {
            System.out.println("Failed to get APIP service from "+apiUrl);
            return false;
        }
        service = (Service) replier.getData();
        serviceParams = (ApipParams) service.getParams();

        providerId = service.getSid();
        if(service.getUrls()!=null && service.getUrls().length>0)
            apiProvider.setOrgUrl(service.getUrls()[0]);
        ApipParams apipParams = (ApipParams) service.getParams();
        if(apipParams!=null && apipParams.getUrlHead()!=null)
            this.apiUrl = apipParams.getUrlHead();
        apiProvider.setOwner(service.getOwner());
        apiProvider.setProtocols(service.getProtocols());
        apiProvider.setTicks(new String[]{"fch"});

        return true;
    }

    public <T> ApiProvider checkFcApiProvider(ApiProvider apiProvider,ApipClient apipClient,ApiType type,Class<T> paramsClass) {
//        String apiUrl =apiProvider.getApiUrl();
        Service service = apipClient.serviceById(apiProvider.getId());
        if(service==null)return null;
        this.service = service;
        this.serviceParams = (Params) Params.getParamsFromService(service,paramsClass);
        this.providerId = service.getSid();
        return ApiProvider.apiProviderFromFcService(service,type);
    }

    public boolean checkDiskProvider(ApiProvider apiProvider,String apiUrl,ApipClient apipClient) {
        String sid = apiProvider.getId();
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
        boolean allowFreeRequest = client1.pingFree();
        ((Client) client).setAllowFreeRequest(allowFreeRequest);
        Long rest = client1.ping();
        if(rest!=null){
            log.info(rest+" KB/requests are available on "+ apiUrl);
            return sessionKey;
        }
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

        Service service1 = client1.serviceById(providerId);
        freshApipService(service1);
        if(isBalanceSufficient()) buyApi(symKey,apipClient);
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

        Session session;
        switch (type){
            case APIP -> {
                ApipClient apipClient = (ApipClient) client;
                session = apipClient.signInEcc(this, RequestBody.SignInMode.NORMAL,symKey);
            }
            case DISK -> {
                DiskClient diskClient = (DiskClient) client;
                session = diskClient.signInEcc(this, RequestBody.SignInMode.NORMAL,symKey);
            }
            default -> {
                byte[] priKey = decryptUserPriKey(userPriKeyCipher,symKey);
                if(priKey==null)return null;
                Client client1 = (Client)client;
                session = client1.signInEcc(this, RequestBody.SignInMode.NORMAL,symKey);
                BytesTools.clearByteArray(priKey);
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
        input = fch.Inputer.inputString(br);
        if (!"".equals(input)) {
            apiUrl = input;
            Service service = getService(apiUrl);
            if (service == null) return;
            providerId = service.getSid();
        }

        String ask = "The via FID is :" + via + "\nInput the new one. Enter to skip:";
        input = fch.Inputer.inputGoodFid(br, ask);
        if (input != null && !"".equals(input)) via = input;


        while (true) {
            System.out.println("The buyerPriKeyCipher:\n" + userPriKeyCipher + ".\nChange it? y/n:");
            input = fch.Inputer.inputString(br);
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
        providerId = service1.getSid();
        service = service1;
        serviceParams = ApipParams.fromObject(service1.getParams());

        if(new File(APIP_Account_JSON).exists())writeApipParamsToFile(this, APIP_Account_JSON);

        System.out.println("APIP service updated to " + apiUrl + " at (sid)" + providerId + ".");
        System.out.println("The buyer is " + userId);
        Menu.anyKeyToContinue(br);
    }

    public void inputApiUrl(BufferedReader br) {
        System.out.println("Input the urlHead of the APIP service. Enter to set as 'https://cid.cash/APIP':");
        String input = fch.Inputer.inputString(br);
        if (input.endsWith("/")) input = input.substring(0, input.length() - 1);
        if ("".equals(input)) {
            this.apiUrl = "https://cid.cash/APIP";
        } else this.apiUrl = input;
    }

    public void inputVia(BufferedReader br) {
        String input;
        String ask = "Input the FID by whom you knew this service. Default: 'FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7'";
        input = fch.Inputer.inputString(br,ask);
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

        if (cryptoDataByte.getCode() != 0) {
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

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
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

    public Map<String, Double> getPayments() {
        return payments;
    }

    public void setPayments(Map<String, Double> payments) {
        this.payments = payments;
    }

    public Double getMinPayment() {
        return minPayment;
    }

    public void setMinPayment(Double minPayment) {
        this.minPayment = minPayment;
    }
}
