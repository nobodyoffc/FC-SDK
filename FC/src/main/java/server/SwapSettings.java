package server;

import appTools.Menu;
import config.ApiAccount;
import config.ApiProvider;
import config.Configure;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;

public class SwapSettings extends Settings {
    private String redisAccountId;
    private String esAccountId;
    private String apipAccountId;
    private String naSaNodeAccountId;

    @Override
    public void initiate(byte[] symKey, Configure config) {

    }

    @Override
    public void inputAll(BufferedReader br) {
        System.out.println("Input all.");
    }

    @Override
    public void updateAll(BufferedReader br) {
        System.out.println("Update all.");
    }

    @Override
    public void saveSettings() {

    }

    @Override
    public void resetLocalSettings(byte[] symKey) {
        System.out.println("Reset settings.");
    }

    @Override
    public Object resetDefaultApi(byte[] symKey, ApiProvider.ApiType apiType) {
        System.out.println("Reset API service...");
        ApiProvider apiProvider = config.chooseApiProviderOrAdd();
        ApiAccount apiAccount = config.chooseApiProvidersAccount(apiProvider, symKey);
        Object client = null;
        if (apiAccount != null) {
            client = apiAccount.connectApi(apiProvider, symKey, br, null);
            if (client != null) {
                switch (apiType) {
                    case APIP -> apipAccountId=apiAccount.getId();
                    case NaSaRPC -> naSaNodeAccountId=apiAccount.getId();
                    case ES -> esAccountId=apiAccount.getId();
                    case Redis -> redisAccountId=apiAccount.getId();
                    default -> {
                        return client;
                    }
                }
                System.out.println("Done.");
            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
        } else System.out.println("Failed to get the apiAccount.");
        return client;
    }

    @Override
    public void resetApis(byte[] symKey,JedisPool jedisPool){
        Menu menu = new Menu();
        menu.add("Reset initial APIP");
        menu.add("Reset NaSa node");
        menu.add("Reset main database");
        menu.add("Reset memory database");
        while (true) {
            System.out.println("Reset default API service...");
            ApiProvider apiProvider = config.chooseApiProviderOrAdd();
            ApiAccount apiAccount = config.chooseApiProvidersAccount(apiProvider, symKey);

            if (apiAccount != null) {
                Object client = apiAccount.connectApi(config.getApiProviderMap().get(apiAccount.getSid()), symKey, br, null);
                if (client != null) {
                    menu.show();
                    int choice = menu.choose(br);
                    switch (choice) {
                        case 1 -> apipAccountId=apiAccount.getId();
                        case 2 -> naSaNodeAccountId=apiAccount.getId();
                        case 3 -> esAccountId=apiAccount.getId();
                        case 4 -> redisAccountId=apiAccount.getId();
                        default -> {
                            return;
                        }
                    }
                    config.saveConfig();
                    System.out.println("Done.");
                } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
            } else System.out.println("Failed to get the apiAccount.");
        }
    }

    @Override
    public void close() {

    }

    public String getRedisAccountId() {
        return redisAccountId;
    }

    public void setRedisAccountId(String redisAccountId) {
        this.redisAccountId = redisAccountId;
    }

    public String getEsAccountId() {
        return esAccountId;
    }

    public void setEsAccountId(String esAccountId) {
        this.esAccountId = esAccountId;
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public String getNaSaNodeAccountId() {
        return naSaNodeAccountId;
    }

    public void setNaSaNodeAccountId(String naSaNodeAccountId) {
        this.naSaNodeAccountId = naSaNodeAccountId;
    }
}
