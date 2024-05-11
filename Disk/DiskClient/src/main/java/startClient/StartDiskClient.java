package startClient;

import appTools.Inputer;
import clients.ClientData;
import clients.apipClient.ApipClient;
import APIP.apipData.RequestBody;
import APIP.apipData.Session;
import FEIP.feipData.Service;
import FEIP.feipData.serviceParams.DiskParams;
import appTools.Menu;
import clients.diskClient.DiskClient;
import config.ApiAccount;
import config.ApiType;
import constants.ApiNames;
import crypto.eccAes256K1P7.EccAes256K1P7;
import config.Configure;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static config.ApiAccount.updateSession;

public class StartDiskClient {
    public static final int DEFAULT_SIZE = 20;
    private static BufferedReader br;
    public static ApipClient apipClient;
    public static DiskClient diskClient;
    private static DiskClientSettings diskClientSettings;
    private static byte[] symKey;
    public Service diskService;
    public DiskParams diskParams;

    public static final String MY_DATA_DIR = System.getProperty("user.home")+"/myData";

    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("Disk");

        //Load config info from the file of config.json
        Configure configure = Configure.loadConfig(br);
        byte[] symKey = configure.checkPassword(configure);

        configure.initiate(symKey);

        //Load the local settings from the file of localSettings.json
        diskClientSettings = DiskClientSettings.loadFromFile("DiskClient",DiskClientSettings.class);
        if(diskClientSettings ==null) diskClientSettings =new DiskClientSettings(configure,br);
        diskClientSettings.initiate(symKey, configure);
        apipClient = (ApipClient) diskClientSettings.getApipAccount().getClient();
        diskClient = (DiskClient) diskClientSettings.getDiskAccount().getClient();


        Menu menu = new Menu();
        menu.setName("Disk Client");
        menu.add("Ping","Ping with authority","PUT","GetFree","GET","GetPost","CHECK","SignIn","SignIn encrypted","Settings");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> ping(br);
                case 2 -> pingWithAuth(br);
                case 3 -> put(br);
                case 4 -> getFree(br);
                case 5 -> get(br);
                case 6 -> getPost(br);
                case 7 -> check(br);
                case 8 -> list(br);
                case 9 -> {
                    diskClient.signIn(diskClientSettings.getDiskAccount(), ApiType.DISK,RequestBody.SignInMode.NORMAL,symKey);
                    configure.saveConfigToFile();
                }
                case 10 -> {
                    diskClient.signInEcc(diskClientSettings.getDiskAccount(), ApiType.DISK,RequestBody.SignInMode.NORMAL,symKey);
                    configure.saveConfigToFile();
                }
                case 11 -> diskClientSettings.setting(symKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }
    public static void ping(BufferedReader br){
        boolean done = diskClient.ping(ApiType.DISK);
        if(done) System.out.println("OK!");
        else System.out.println("Failed!");
        Menu.anyKeyToContinue(br);
    }

    public static void pingWithAuth(BufferedReader br){
        boolean done = diskClient.pingWithAuth(ApiType.DISK, ClientData.AuthType.FC_SIGN_BODY);
        if(done) System.out.println("OK!");
        else System.out.println("Failed!");
        Menu.anyKeyToContinue(br);
    }

    public static void put(BufferedReader br){
        String filename = Inputer.inputPath(br,"Input the path of the file:");
        String dataResponse = diskClient.put(filename);
        if(dataResponse==null || !javaTools.Hex.isHexString(dataResponse)) System.out.println("Request failed.");
        else System.out.println("Done. \nDID:"+dataResponse);
        Menu.anyKeyToContinue(br);
    }
    public static void getFree(BufferedReader br){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String dataResponse = diskClient.getFree(filename);
        if(dataResponse==null || !javaTools.Hex.isHexString(dataResponse)) {
            System.out.println("Request failed.");
            System.out.println(diskClient.getClientData().getMessage());
        }
        else System.out.println("Done. \nDID:"+dataResponse);
        Menu.anyKeyToContinue(br);
    }
    public static void get(BufferedReader br){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String dataResponse = diskClient.get(filename);
        if(dataResponse==null || !javaTools.Hex.isHexString(dataResponse)) {
            System.out.println("Request failed.");
            System.out.println(diskClient.getClientData().getMessage());
        }
        else System.out.println("Done. \nDID:"+dataResponse);
        Menu.anyKeyToContinue(br);
    }

    public static void getPost(BufferedReader br){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String dataResponse = diskClient.getPost(filename);
        if(dataResponse==null || !javaTools.Hex.isHexString(dataResponse)) {
            System.out.println("Request failed.");
            System.out.println(diskClient.getClientData().getMessage());
        }
        else System.out.println("Done. \nDID:"+dataResponse);
        Menu.anyKeyToContinue(br);
    }
    public static void check(BufferedReader br){
        System.out.println("Check...");
        Menu.anyKeyToContinue(br);
    }
    public static void list(BufferedReader br){
        System.out.println("List...");
        Menu.anyKeyToContinue(br);
    }
    public static void setting(BufferedReader br){
        System.out.println("Setting...");
        Menu.anyKeyToContinue(br);
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
