package server;

import APIP.apipData.Fcdsl;
import FEIP.feipData.Service;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import config.ApiAccount;
import config.ApiType;
import config.Configure;
import constants.FieldNames;
import constants.Strings;
import constants.Values;
import crypto.cryptoTools.Hash;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.Strings.*;

public abstract class Settings {
    final static Logger log = LoggerFactory.getLogger(Settings.class);
    protected String sid;
    protected transient Configure config;
    protected transient BufferedReader br;
    protected String listenPath;
    protected boolean fromWebhook;
    protected transient ApiAccount apipAccount;
    protected transient ApiAccount esAccount;
    protected transient ApiAccount redisAccount;
    protected String apipAccountId;
    protected String esAccountId;
    protected String redisAccountId;
    private transient JedisPool jedisPool;
    private static String fileName;
    public static String SETTINGS_DOT_JSON = "settings.json";

    public Settings(Configure config, BufferedReader br, JedisPool jedisPool) {
        this.config = config;
        this.br = br;
        this.jedisPool = jedisPool;
    }

    public Settings(Configure config, BufferedReader br) {
        this.config = config;
        this.br = br;
    }

    public Settings() {}

    public static String makeSettingsFileName(String sid){
        if(sid==null)return SETTINGS_DOT_JSON;
        return sid.substring(0,6)+"_" +SETTINGS_DOT_JSON;
    }

    public static String getLocalDataDir(String sid){
        return System.getProperty("user.dir")+"/"+ addSidBriefToName(sid,DATA)+"/";
    }

    public static String addSidBriefToName(String sid, String name) {
        String finalName;
        finalName = (sid.substring(0,6) + "_" + name);
        return finalName;
    }

    public abstract void initiate(byte[] symKey, Configure config);

    public Service loadMyService(byte[] symKey, String[] types){
        System.out.println("Load my services from APIP...");

        List<Service> serviceList = getMyServiceList(true, types);

        if (serviceList == null) {
            System.out.println("Load swap services wrong.");
            return null;
        }
        return selectMyService(serviceList,symKey);
    }

    private List<Service> getMyServiceList(boolean onlyActive, String[] types) {

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPES).setValues(types);
        fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.OWNER).setValues(config.getOwner());
        if(onlyActive)fcdsl.addNewExcept().addNewTerms().addNewFields(Strings.ACTIVE).addNewValues(Values.FALSE);
        fcdsl.addSize(100);

        fcdsl.addNewSort(FieldNames.LAST_HEIGHT,Strings.DESC);
        return config.getInitApipClient().serviceSearch(fcdsl);
    }

    public Service selectMyService(List<Service> serviceList, byte[] symKey){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServiceList(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            return null;
        }
        return serviceList.get(choice-1);
    }
    public static void showServiceList(List<Service> serviceList) {
        String title = "Services";
        String[] fields = new String[]{"",FieldNames.STD_NAME,FieldNames.SID};
        int[] widths = new int[]{2,24,64};
        List<List<Object>> valueListList = new ArrayList<>();
        int i=1;
        for(Service service : serviceList){
            List<Object> valueList = new ArrayList<>();
            valueList.add(i);
            valueList.add(service.getStdName());
            valueList.add(service.getSid());
            valueListList.add(valueList);
            i++;
        }
        Shower.showDataTable(title,fields,widths,valueListList);
    }

    public static <T> T loadFromFile(String sid,Class<T> tClass){
        try {
            fileName = makeSettingsFileName(sid);
            return JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, tClass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T loadFromFile(Class<T> tClass){
        return loadFromFile(null,tClass);
    }

    public abstract void inputAll(BufferedReader br);

    public abstract void updateAll(BufferedReader br);

    public void writeToFile(){
        JsonTools.writeObjectToJsonFile(this,Configure.getConfDir(),fileName,false);
    }

    public abstract void saveSettings();

    public void setting( byte[] symKey, BufferedReader br) {
        System.out.println("Setting...");
        while (true) {
            Menu menu = new Menu();
            menu.add("Reset password",
                    "Add API provider",
                    "Add API account",
                    "Update API provider",
                    "Update API account",
                    "Delete API provider",
                    "Delete API account",
                    "Reset my local settings",
                    "Reset Default APIs"
            );
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> {
                    byte[] newSymKey=resetPassword();
                    if(newSymKey==null)break;
                    symKey = newSymKey;
                }
                case 2 -> config.addApiProviderAndConnect(symKey);
                case 3 -> config.addApiAccount(symKey);
                case 4 -> config.updateApiProvider(symKey);
                case 5 -> config.updateApiAccount(config.chooseApiProviderOrAdd(),symKey);
                case 6 -> config.deleteApiProvider(symKey);
                case 7 -> config.deleteApiAccount(symKey);
                case 8 -> resetLocalSettings(symKey);
                case 9 -> resetApis(symKey,jedisPool);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public byte[] resetPassword(){
        System.out.println("Reset password...");
        byte[] oldSymKey;
        byte[] oldNonceBytes;
        byte[] oldPasswordBytes;
        while(true) {
            oldPasswordBytes = Inputer.getPasswordBytes(br);
            oldNonceBytes = Hex.fromHex(config.getNonce());
            oldSymKey = Hash.Sha256x2(BytesTools.bytesMerger(oldPasswordBytes, oldNonceBytes));
            byte[] oldNonce = EccAes256K1P7.decryptJsonBytes(config.getNonceCipher(), oldSymKey);
            if (oldNonce==null || ! config.getNonce().equals(Hex.toHex(oldNonce))) {
                System.out.println("Password wrong. Reset it.");
                config.setNonce(null);
                continue;
            }

            byte[] newPasswordBytes = Inputer.resetNewPassword(br);
            if(newPasswordBytes==null)return null;
            byte[] newNonce = BytesTools.getRandomBytes(16);
            byte[] newSymKey = Hash.Sha256x2(BytesTools.bytesMerger(newPasswordBytes, newNonce));

            String newNonceCipher = EccAes256K1P7.encryptWithSymKey(newNonce, newSymKey);
            config.setNonce(Hex.toHex(newNonce));
            config.setNonceCipher(newNonceCipher);

            if(config.getApiAccountMap()==null||config.getApiAccountMap().isEmpty())return newSymKey;
            for(ApiAccount apiAccount : config.getApiAccountMap().values()){
                if(apiAccount.getPasswordCipher()!=null){
                    String cipher = apiAccount.getPasswordCipher();
                    String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                    apiAccount.setPasswordCipher(newCipher);
                }
                if(apiAccount.getUserPriKeyCipher()!=null){
                    String cipher = apiAccount.getUserPriKeyCipher();
                    String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                    apiAccount.setUserPriKeyCipher(newCipher);
                }
                if(apiAccount.getSession().getSessionKeyCipher()!=null){
                    String cipher = apiAccount.getSession().getSessionKeyCipher();
                    String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                    apiAccount.getSession().setSessionKeyCipher(newCipher);
                }
            }

            if(jedisPool!=null){
                try(Jedis jedis = jedisPool.getResource()){
                    String oldSymKeyCipher = jedis.hget(addSidBriefToName(sid,CONFIG),INIT_SYM_KEY_CIPHER);
                    if(oldSymKeyCipher!=null) {
                        String newCipher = replaceCipher(oldSymKeyCipher, oldNonce, newNonce);
                        jedis.hset(addSidBriefToName(sid,CONFIG), INIT_SYM_KEY_CIPHER, newCipher);
                    }
                }
                config.saveConfig();
            }

            BytesTools.clearByteArray(oldPasswordBytes);
            BytesTools.clearByteArray(newPasswordBytes);
            BytesTools.clearByteArray(oldSymKey);

            return newSymKey;
        }
    }

    private String replaceCipher(String oldCipher, byte[] oldSymKey, byte[] newSymKey) {
        byte[] msg = EccAes256K1P7.decryptJsonBytes(oldCipher, oldSymKey);
        return EccAes256K1P7.encryptWithSymKey(msg,newSymKey);
    }

    public abstract void resetLocalSettings(byte[] symKey);

    public abstract Object resetDefaultApi(byte[] symKey, ApiType apiType);
    public abstract void resetApis(byte[] symKey,JedisPool jedisPool);

    public abstract void close();

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public Configure getConfig() {
        return config;
    }

    public void setConfig(Configure config) {
        this.config = config;
    }

    public BufferedReader getBr() {
        return br;
    }

    public void setBr(BufferedReader br) {
        this.br = br;
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getListenPath() {
        return listenPath;
    }

    public void setListenPath(String listenPath) {
        this.listenPath = listenPath;
    }

    public boolean isFromWebhook() {
        return fromWebhook;
    }

    public void setFromWebhook(boolean fromWebhook) {
        this.fromWebhook = fromWebhook;
    }

    public ApiAccount getApipAccount() {
        return apipAccount;
    }

    public void setApipAccount(ApiAccount apipAccount) {
        this.apipAccount = apipAccount;
    }

    public ApiAccount getEsAccount() {
        return esAccount;
    }

    public void setEsAccount(ApiAccount esAccount) {
        this.esAccount = esAccount;
    }

    public ApiAccount getRedisAccount() {
        return redisAccount;
    }

    public void setRedisAccount(ApiAccount redisAccount) {
        this.redisAccount = redisAccount;
    }

}
