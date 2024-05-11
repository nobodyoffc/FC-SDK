package startClient;

import appTools.Menu;
import clients.apipClient.ApipClient;
import config.ApiAccount;
import config.ApiType;
import redis.clients.jedis.JedisPool;
import config.Configure;
import server.Settings;

import java.io.BufferedReader;

public class DiskClientSettings extends Settings {
    String diskAccountId;
    private transient ApiAccount diskAccount;

    public DiskClientSettings(Configure config, BufferedReader br) {
        super(config, br);
    }

    @Override
    public void initiate(byte[] symKey, Configure config) {
        System.out.println("Initiating APP settings...");
        this.config=config;

        apipAccountId=config.getInitApipAccountId();
        apipAccount = config.getApiAccountMap().get(apipAccountId);
        if(apipAccount.getClient()==null)
            apipAccount.connectApip(config.getApiProviderMap().get(apipAccount.getSid()),symKey);

        if(diskAccountId==null) {
            diskAccount = config.initFcAccount((ApipClient) apipAccount.getClient(), ApiType.DISK,symKey);
            diskAccountId = diskAccount.getId();
        }else {
            diskAccount = config.getApiAccountMap().get(diskAccountId);
            diskAccount.setApipClient((ApipClient) apipAccount.getClient());
            diskAccount.connectApi(config.getApiProviderMap().get(diskAccount.getSid()),symKey);
        }
        saveSettings();
        System.out.println("Service settings initiated.");
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
        menu.add("Reset listenPath");
        menu.add("Reset account");
        menu.add("minPayment");
        int choice = menu.choose(br);
        menu.show();
        switch (choice){
            case 1 -> updateAll(br);
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

    public String getDiskAccountId() {
        return diskAccountId;
    }

    public void setDiskAccountId(String diskAccountId) {
        this.diskAccountId = diskAccountId;
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public ApiAccount getDiskAccount() {
        return diskAccount;
    }

    public void setDiskAccount(ApiAccount diskAccount) {
        this.diskAccount = diskAccount;
    }

}
