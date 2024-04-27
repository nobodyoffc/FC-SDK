package startClient;

import clients.apipClient.ApipClient;
import APIP.apipData.RequestBody;
import APIP.apipData.Session;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.DiskParams;
import appTools.Inputer;
import appTools.Menu;
import clients.diskClient.DiskClient;
import config.ApiAccount;
import constants.ApiNames;
import crypto.cryptoTools.Hash;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.FileTools;
import javaTools.Hex;
import config.Configure;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static config.ApiAccount.updateSession;

public class StartDiskClient {
    public static final int DEFAULT_SIZE = 20;
    public static ApipClient apipClient;
    public static DiskClient diskClient;
    private static DiskClientSettings diskClientSetter;
    private static byte[] symKey;
    public Service diskService;
    public DiskParams diskParams;
    private static BufferedReader br;
    public static final String MY_DATA_DIR = System.getProperty("user.home")+"/myData";

    public static void main(String[] args) {
        Menu.welcome("Disk of Freeverse");

        //Load config info from the file of config.json
        Configure configure = Configure.loadConfig(br);
        byte[] symKey = configure.checkPassword(configure);

        configure.initiate(symKey);

        //Load the local settings from the file of localSettings.json
        diskClientSetter = DiskClientSettings.loadFromFile(null,DiskClientSettings.class);
        diskClientSetter.initiate(symKey, configure);
        apipClient = (ApipClient) diskClientSetter.getApipAccount().getClient();
        diskClient = (DiskClient) diskClientSetter.getDiskAccount().getClient();

        Menu menu = new Menu();
        menu.setName("Disk Client");
        menu.add("PUT","GET","CHECK","SignIn","SignIn encrypted","Settings");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> put(diskClient,br);
                case 2 -> get(br);
                case 3 -> check(br);
                case 4 -> {
                    signIn(diskClientSetter.getDiskAccount(),symKey,br);
                    configure.saveConfigToFile();
                }
                case 5 -> {
                    signInEcc(diskClientSetter.getDiskAccount(),symKey,br);
                    configure.saveConfigToFile();
                }
                case 6 -> diskClientSetter.setting(symKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void signIn(ApiAccount diskAccount, byte[] symKey, BufferedReader br) {
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(diskAccount.getUserPriKeyCipher(),symKey);
        Session session = diskClient.signIn(ApiNames.SignInAPI, priKey, RequestBody.SignInMode.NORMAL);
        byte[] sessionKey = Hex.fromHex(session.getSessionKey());
        diskAccount.setSessionKey(sessionKey);
        diskAccount.setSessionExpire(session.getExpireTime());
        diskAccount.setSessionName(Session.makeSessionName(session.getSessionKey()));
        String sessionKeyCipher=EccAes256K1P7.encryptWithSymKey(sessionKey,symKey);
        diskAccount.setSessionKeyCipher(sessionKeyCipher);
    }

    public static void signInEcc(ApiAccount diskAccount, byte[] symKey, BufferedReader br) {
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(diskAccount.getUserPriKeyCipher(),symKey);
        Session session = diskClient.signInEcc(ApiNames.SignInAPI, priKey, RequestBody.SignInMode.NORMAL);
        String sessionKeyCipher1 = session.getSessionKeyCipher();
        diskAccount.setSessionKeyCipher(sessionKeyCipher1);
        byte[] sessionKey = EccAes256K1P7.decryptWithPriKey(sessionKeyCipher1,priKey);
        diskAccount.setSessionKey(sessionKey);
        diskAccount.setSessionExpire(session.getExpireTime());
    }

    public static void put(DiskClient diskClient, BufferedReader br){
        String filename = Inputer.inputPath(br,"Input the path of the file.");
        byte[] bytes = FileTools.readAllBytes(filename);
        String did = Hex.toHex(Hash.Sha256x2(bytes));
        String dataResponse = diskClient.putPost(bytes);
        if(did.equals(dataResponse)) System.out.println("Done:"+did);
        else System.out.println("Failed:"+dataResponse);
    }
    public static void get(BufferedReader br){

    }
    public static void check(BufferedReader br){

    }

    public static void setting(BufferedReader br){

    }

    public static byte[] signInEccPost(byte[] symKey, RequestBody.SignInMode mode) {
        ApiAccount apiAccount = apipClient.getApiAccount();
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(apiAccount.getUserPriKeyCipher(), symKey);
        if (priKey == null) return null;
        System.out.println("Sign in for the priKey encrypted sessionKey...");
        Session session = apipClient.signInEcc(priKey, RequestBody.SignInMode.NORMAL);
        String sessionKeyCipherFromApip = session.getSessionKeyCipher();
        byte[] newSessionKey = EccAes256K1P7.decryptWithPriKey(sessionKeyCipherFromApip, priKey);

        updateSession(apiAccount,symKey, session, newSessionKey);
        return newSessionKey;
    }
}
