package initial;

import config.ApiAccount;
import config.ApiProvider;
import javaTools.JsonTools;

import java.io.File;
import java.io.FileInputStream;

public class ConfigOpenDrive {
    public static final String CONFIG_JSON = "config.json";
    private ApiProvider apiProvider;
    private ApiAccount apiAccount;
    private LocalDateBase localDateBase;
    private String openDriveSid;

    public void initiate(){
        openDriveSid="";
//        apiProvider.initiate();
//        apiAccount.initiate();
        localDateBase.initiate();
    }
    public static boolean isBadConfig(ConfigOpenDrive configOpenDrive) {
        if(configOpenDrive==null){
            configOpenDrive = new ConfigOpenDrive();
            configOpenDrive.initiate();
            ConfigOpenDrive.writeConfigSwapToFile(configOpenDrive);
            System.out.println("Set the config file of "+ConfigOpenDrive.CONFIG_JSON);
            return true;
        }
        return false;
    }
    public static void writeConfigSwapToFile(ConfigOpenDrive configOpenDrive) {
        JsonTools.writeObjectToJsonFile(configOpenDrive, CONFIG_JSON, false);
    }
    public static ConfigOpenDrive readConfigJsonFromFile() {
        File file = new File(CONFIG_JSON);
        ConfigOpenDrive configOpenDrive;
        try(FileInputStream fis = new FileInputStream(file)) {
            configOpenDrive = JsonTools.readObjectFromJsonFile(fis, ConfigOpenDrive.class);
            if (configOpenDrive == null) {
                System.out.println("Read config from file wrong.");
                return null;
            }
        }catch (Exception e){
            System.out.println("Failed to read swap information: "+ e.getMessage());
            System.out.println("It will be created.");
            return null;
        }
        return configOpenDrive;
    }

    public ApiAccount getApiAccount() {
        return apiAccount;
    }

    public void setApiAccount(ApiAccount apiAccount) {
        this.apiAccount = apiAccount;
    }

    public ApiProvider getApiProvider() {
        return apiProvider;
    }

    public void setApiProvider(ApiProvider apiProvider) {
        this.apiProvider = apiProvider;
    }

    public LocalDateBase getLocalDateBase() {
        return localDateBase;
    }

    public void setLocalDateBase(LocalDateBase localDateBase) {
        this.localDateBase = localDateBase;
    }

    public String getOpenDriveSid() {
        return openDriveSid;
    }

    public void setOpenDriveSid(String openDriveSid) {
        this.openDriveSid = openDriveSid;
    }
}
