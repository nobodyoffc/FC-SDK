package startClient;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Session;
import feip.feipData.Service;
import feip.feipData.serviceParams.DiskParams;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import clients.apipClient.ApipClient;
import clients.diskClient.DiskClient;
import clients.diskClient.DiskDataInfo;
import config.ApiAccount;
import config.ApiType;
import config.Configure;
import crypto.old.EccAes256K1P7;
import javaTools.Hex;
import javaTools.JsonTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static config.ApiAccount.updateSession;

public class StartDiskClient {
    public static final int DEFAULT_SIZE = 20;
    private static BufferedReader br;
    public static ApipClient apipClient;
    public static DiskClient diskClient;
    private static DiskClientSettings settings;
    private static byte[] symKey;
    public Service diskService;
    public DiskParams diskParams;
    private static String fid;

    public static final String MY_DATA_DIR = System.getProperty("user.home")+"/myData";

    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("Disk");

        //Load config info from the file of config.json
        Configure configure = Configure.loadConfig(br);
        symKey = configure.checkPassword(configure);

        //Load the local settings from the file of localSettings.json
        fid = configure.chooseMainFid(symKey);

        settings = DiskClientSettings.loadFromFile(fid,DiskClientSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new DiskClientSettings();
        settings.initiateClient(fid,symKey, configure, br);

        apipClient = (ApipClient) settings.getApipAccount().getClient();
        diskClient = (DiskClient) settings.getDiskAccount().getClient();


        Menu menu = new Menu();
        menu.setName("Disk Client");
        menu.add("Ping free","Ping","PUT free","PUT","GET free","GET","GET by Post","CHECK free","CHECK","LIST free","LIST","LIST post","SignIn","SignIn encrypted","Settings");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> pingFree(br);
                case 2 -> ping(br);
                case 3 -> putFree(br);
                case 4 -> put(br);
                case 5 -> getFree(br);
                case 6 -> get(br);
                case 7 -> getPost(br);
                case 8 -> checkFree(br);
                case 9 -> check(br);
                case 10 -> list(br);
                case 11 -> list(br);
                case 12 -> listPost(br);
                case 13 -> signIn(configure);
                case 14 -> signInEcc(configure);
                case 15 -> settings.setting(symKey, br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void signInEcc(Configure configure) {
        Session session = diskClient.signInEcc(settings.getDiskAccount(), ApiType.DISK, RequestBody.SignInMode.NORMAL, symKey);
        JsonTools.gsonPrint(session);
        configure.saveConfigToFile();
        Menu.anyKeyToContinue(br);
    }

    private static void signIn(Configure configure) {
        Session session = diskClient.signIn(settings.getDiskAccount(), ApiType.DISK,RequestBody.SignInMode.NORMAL,symKey);
        JsonTools.gsonPrint(session);
        configure.saveConfigToFile();
        Menu.anyKeyToContinue(br);
    }

    public static void pingFree(BufferedReader br){
        boolean done = diskClient.pingFree(ApiType.DISK);
        if(done) System.out.println("OK!");
        else System.out.println("Failed!");
        Menu.anyKeyToContinue(br);
    }

    public static void ping(BufferedReader br){
        Long rest = diskClient.ping(ApiType.DISK);
        if(rest!=null) System.out.println("OK! "+rest+" KB/requests are available.");
        else System.out.println("Failed!");

        Menu.anyKeyToContinue(br);
    }

    public static void put(BufferedReader br){
        String fileName;
        while(true) {
            fileName = Inputer.inputPath(br, "Input the file path and name:");
            if (new File(fileName).isDirectory()) {
                System.out.println("It is a directory. A file name is required.");
                continue;
            }
            break;
        }
        if(Inputer.askIfYes(br,"Encrypt it?")) {
            fileName = DiskClient.encryptFile(fileName, diskClient.getApiAccount().getUserPubKey());
            System.out.println("Encrypted to: "+fileName);
        }
        String dataResponse = diskClient.put(fileName);
        if(Hex.isHexString(dataResponse)) {
            if(!new File(dataResponse).delete()){
                System.out.println("Failed to delete the local cipher file.");
            };
            System.out.println("Put:" + dataResponse);
        }else System.out.println(dataResponse);
        Menu.anyKeyToContinue(br);
    }

    public static void putFree(BufferedReader br){
        String fileName;
        while(true) {
            fileName = Inputer.inputPath(br, "Input the file path and name:");
            if (new File(fileName).isDirectory()) {
                System.out.println("It is a directory. A file name is required.");
                continue;
            }
            break;
        }
        if(Inputer.askIfYes(br,"Encrypt it?")) {
            fileName = DiskClient.encryptFile(fileName, diskClient.getApiAccount().getUserPubKey());
            System.out.println("Encrypted to: "+fileName);
        }
        String dataResponse = diskClient.putFree(fileName);
        if(Hex.isHexString(dataResponse)) {
            if(!new File(dataResponse).delete()){
                System.out.println("Failed to delete the local cipher file.");
            };
            System.out.println("Put: " + dataResponse);
        }else System.out.println(dataResponse);
        Menu.anyKeyToContinue(br);
    }
    public static void getFree(BufferedReader br){
        String filename = Inputer.inputString(br,"Input the DID of the file");
        String path = Inputer.inputString(br,"Input the destination path");
        String gotFile = diskClient.getFree(filename,path);
        System.out.println("Got:"+Path.of(path,gotFile));
        if(!Hex.isHexString(gotFile))return;

        String did = DiskClient.decryptFile(path, gotFile,symKey,diskClient.getApiAccount().getUserPriKeyCipher());
        if(did!= null) System.out.println("Decrypted to:"+Path.of(path,did));
        Menu.anyKeyToContinue(br);
    }

    public static void get(BufferedReader br){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String path = Inputer.inputString(br,"Input the destination path");
        String gotFile = diskClient.get(filename,path);
        System.out.println("Got:"+Path.of(path,gotFile));
        if(!Hex.isHexString(gotFile))return;
        String did = DiskClient.decryptFile(path, gotFile,symKey,diskClient.getApiAccount().getUserPriKeyCipher());
        if(did!= null) System.out.println("Decrypted to:"+Path.of(path,did));
        Menu.anyKeyToContinue(br);
    }

    public static void getPost(BufferedReader br){
        String filename = Inputer.inputString(br,"Input the DID of the file:");
        String path = Inputer.inputString(br,"Input the destination path");
        String gotFile = diskClient.getPost(filename,path );
        System.out.println("Got:"+gotFile);
        if(!Hex.isHexString(gotFile))return;
        String did = DiskClient.decryptFile(path, gotFile,symKey,diskClient.getApiAccount().getUserPriKeyCipher());
        if(did!= null) System.out.println("Decrypted to:"+Path.of(path,did));
        Menu.anyKeyToContinue(br);
    }
    public static void check(BufferedReader br){
        System.out.println("Check...");
        String did = Inputer.inputString(br,"Input the DID of the file:");
        String dataResponse = diskClient.check(did);
        System.out.println("Got:"+dataResponse);
        Menu.anyKeyToContinue(br);
    }

    public static void checkFree(BufferedReader br){
        System.out.println("Check...");
        String did = Inputer.inputString(br,"Input the DID of the file:");
        String dataResponse = diskClient.checkFree(did);
        System.out.println("Got:"+dataResponse);
        Menu.anyKeyToContinue(br);
    }

    public static void list(BufferedReader br){
        System.out.println("List...");
        String[] last = new String[0];
        String sort = null;
        String order = null;
        int size = 0;
        if(Inputer.askIfYes(br,"Set the last?")){
            last = Inputer.inputStringArray(br,"Set the last values of the sorted fields:",0);
        }
        if(Inputer.askIfYes(br,"Set the sort?")){
            sort = Inputer.inputString(br,"Set the field name of the sort:");
        }
        if(sort!=null && Inputer.askIfYes(br,"Set the order of the sort?")){
            do {
                order = Inputer.inputString(br, "Set the order, asc or desc:");
            } while (!order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc"));
        }
        if(Inputer.askIfYes(br,"Set the size?")){
            size = Inputer.inputInteger(br,"Set the size:",0);
        }

        List<DiskDataInfo> dataResponse = diskClient.list(size,sort,order,last);

        showDiskInfoList(dataResponse);

        Menu.anyKeyToContinue(br);
    }


    public static void listPost(BufferedReader br){
        System.out.println("List with POST...");

        Fcdsl fcdsl = new Fcdsl();

        List<DiskDataInfo> dataResponse = diskClient.list(fcdsl);

        showDiskInfoList(dataResponse);

        Menu.anyKeyToContinue(br);
    }

    private static void showDiskInfoList(List<DiskDataInfo> dataResponse) {
        String title = "Got disk items";
        String[] fields = new String[]{"did","since","expire","size"};
        int[] widths = new int[]{16,20,20,9};
        List<List<Object>> valueListList=new ArrayList<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if(dataResponse==null||dataResponse.isEmpty()){
            System.out.println("Nothing to show.");
            return;
        }
        for(DiskDataInfo diskDataInfo : dataResponse){
            List<Object> valueList = new ArrayList<>();
            valueList.add(diskDataInfo.getDid());
            valueList.add(formatter.format(diskDataInfo.getSince()));
            valueList.add(formatter.format(diskDataInfo.getExpire()));
            valueList.add(String.valueOf(diskDataInfo.getSize()));
            valueListList.add(valueList);
        }
        Shower.showDataTable(title,fields,widths,valueListList);
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
