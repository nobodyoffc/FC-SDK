package start;

import FCH.Inputer;
import appTools.Menu;
import config.ApiType;
import config.Configure;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1P7.EccAes256K1P7;
import org.bitcoinj.core.ECKey;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

public class WorldSettings extends Settings {
    private Map<String,String> fidPriKeyCipherMap;

    public WorldSettings(Configure config, BufferedReader br) {
        this.config = config;
        this.br = br;
    }

    @Override
    public void initiate(byte[] symKey, Configure config) {
        if(fidPriKeyCipherMap==null)fidPriKeyCipherMap = new HashMap<>();
        System.out.println("Import IDs:");
        while(true){
            ECKey ecKey = Inputer.inputPriKey(br);
            if(ecKey==null || ecKey.getPrivKeyBytes()==null){
                System.out.println("Failed to get priKey. Try again.");
                continue;
            }
            byte[] priKeyBytes = ecKey.getPrivKeyBytes();
            String fid = KeyTools.priKeyToFid(priKeyBytes);
            String priKeyCipher = EccAes256K1P7.encryptWithSymKey(priKeyBytes,symKey);
            fidPriKeyCipherMap.put(fid,priKeyCipher);
            if(!Inputer.askIfYes(br,"Add more ID?"))break;
        }
        saveSettings();
    }

    @Override
    public void inputAll(BufferedReader br) {

    }

    @Override
    public void updateAll(BufferedReader br) {

    }

    @Override
    public void saveSettings() {
        writeToFile();
    }

    @Override
    public void resetLocalSettings(byte[] symKey) {
        Menu menu = new Menu();
        menu.add("Add ID");
        menu.add("Remove ID");
        menu.add("Reset API services");
        menu.add("Refresh API Sessions");
        int choice = menu.choose(br);
        menu.show();
        switch (choice){
//            case 1 -> addId(br);
//            case 2 -> removeId(br);
//            case 3 -> resetApi(br);
//            case 4 -> refreshApi(br);
        }
    }

    @Override
    public Object resetDefaultApi(byte[] symKey, ApiType apiType) {
        return null;
    }

    @Override
    public void resetApis(byte[] symKey, JedisPool jedisPool) {
    }

    @Override
    public void close() {
    }

    public Map<String, String> getFidPriKeyCipherMap() {
        return fidPriKeyCipherMap;
    }

    public void setFidPriKeyCipherMap(Map<String, String> fidPriKeyCipherMap) {
        this.fidPriKeyCipherMap = fidPriKeyCipherMap;
    }
}
