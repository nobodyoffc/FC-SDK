package server.setter;

import appTools.Inputer;
import appTools.Menu;
import config.ApiAccount;
import config.ApiProvider;
import config.Config;
import crypto.cryptoTools.Hash;
import crypto.eccAes256K1P7.EccAes256K1P7;
import javaTools.BytesTools;
import javaTools.Hex;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

public abstract class Setter {
    protected Config config;
    protected BufferedReader br;

    public Setter(Config config, BufferedReader br) {
        this.config = config;
        this.br = br;
    }

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
                    "Reset Default APIs",
                    "Reset other parameters"
            );
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> {
                    byte[] newSymKey=resetPassword();
                    if(newSymKey==null)break;
                    symKey = newSymKey;
                }
                case 2 -> addApiProvider(symKey);
                case 3 -> addApiAccount(symKey);
                case 4 -> updateApiProvider();
                case 5 -> updateApiAccount(chooseApiProvider(),symKey);
                case 6 -> deleteApiProvider();
                case 7 -> deleteApiAccount(symKey);
                case 8 -> resetDefaultApi(symKey);
                case 9 -> resetOtherParams(symKey);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public byte[] resetPassword(){
        System.out.println("Reset password...");
        byte[] oldSymKey;
        byte[] oldRandomBytes;
        byte[] oldPasswordBytes;
        while(true) {
            oldPasswordBytes = Inputer.getPasswordBytes(br);
            oldRandomBytes = Hex.fromHex(config.getNonce());
            oldSymKey = Hash.Sha256x2(BytesTools.bytesMerger(oldPasswordBytes, oldRandomBytes));
            byte[] oldNonce = EccAes256K1P7.decryptJsonBytes(config.getNonceCipher(), oldSymKey);
            if (oldNonce==null || ! config.getNonce().equals(Hex.toHex(oldNonce))) {
                System.out.println("Password wrong. Reset it.");
                config.setNonce(null);
                continue;
            }

            byte[] newPasswordBytes = Inputer.resetNewPassword(br);
            if(newPasswordBytes==null)return null;
            byte[] newRandomBytes = BytesTools.getRandomBytes(16);
            byte[] newSymKey = Hash.Sha256x2(BytesTools.bytesMerger(newPasswordBytes, newRandomBytes));

            String newNonceCipher = EccAes256K1P7.encryptWithSymKey(newRandomBytes, newSymKey);
            config.setNonce(Hex.toHex(newRandomBytes));
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
                if(apiAccount.getSessionKeyCipher()!=null){
                    String cipher = apiAccount.getSessionKeyCipher();
                    String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                    apiAccount.setSessionKeyCipher(newCipher);
                }
            }

            config.saveConfig();

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

    public void addApiAccount(byte[] symKey){
        System.out.println("Add API accounts...");
        ApiProvider apiProvider = chooseApiProvider();
        if(apiProvider!=null) {
            ApiAccount apiAccount = config.addApiAccount(apiProvider, symKey, br);
            config.saveConfig();
            System.out.println("Add API account "+apiAccount.getId()+" is added.");
        }
    }

    public void addApiProvider(byte[] symKey){
        System.out.println("Add API providers...");
        ApiProvider apiProvider = config.addApiProvider(br,null);
        if(apiProvider!=null) {
            ApiAccount apiAccount = config.addApiAccount(apiProvider, symKey, br);
            if(apiAccount!=null) apiAccount.connectApi(apiProvider, symKey, br);
            config.saveConfig();
        }
        System.out.println("Add API provider "+apiProvider.getSid()+" is added.");
    }

    public void updateApiAccount(ApiProvider apiProvider,byte[] symKey){
        System.out.println("Update API accounts...");
        ApiAccount apiAccount;
        if(apiProvider==null)apiAccount = chooseApiAccount(symKey);
        else apiAccount = chooseApiProvidersAccount(apiProvider, symKey);
        if(apiAccount!=null) {
            System.out.println("Update API account: "+apiAccount.getSid()+"...");
            apiAccount.updateAll(symKey, apiProvider, br);
            config.getApiAccountMap().put(apiAccount.getId(), apiAccount);
            config.saveConfig();
        }
        System.out.println("Api account "+apiAccount.getId()+" is updated.");
    }
    public void updateApiProvider(){
        System.out.println("Update API providers...");
        ApiProvider apiProvider = chooseApiProvider();
        if(apiProvider!=null) {
            apiProvider.updateAll(br);
            config.getApiProviderMap().put(apiProvider.getSid(), apiProvider);
            config.saveConfig();
            System.out.println("Api provider "+apiProvider.getSid()+" is updated.");
        }
    }

    public void deleteApiProvider(){
        System.out.println("Deleting API provider...");
        ApiProvider apiProvider = chooseApiProvider();
        if(apiProvider==null) return;
        for(ApiAccount apiAccount:config.getApiAccountMap().values()){
            if(apiAccount.getSid().equals(apiProvider.getSid())){
                if(Inputer.askIfYes(br,"There is the API account "+apiAccount.getId()+" of "+apiProvider.getSid()+". \nDelete it? y/n ")){
                    config.getApiAccountMap().remove(apiAccount.getId());
                    System.out.println("Api account "+apiAccount.getId()+" is deleted.");
                    config.saveConfig();
                }
            }
        }
        if(Inputer.askIfYes(br,"Delete API provider "+apiProvider.getSid()+"? y/n")){
            config.getApiProviderMap().remove(apiProvider.getSid());
            System.out.println("Api provider " + apiProvider.getSid() + " is deleted.");
            config.saveConfig();
        }
    }
    public void deleteApiAccount(byte[] symKey){
        System.out.println("Deleting API Account...");
        ApiAccount apiAccount = chooseApiAccount(symKey);
        if(apiAccount==null) return;
        if(Inputer.askIfYes(br,"Delete API account "+apiAccount.getId()+"? y/n")) {
            config.getApiAccountMap().remove(apiAccount.getId());
            System.out.println("Api account " + apiAccount.getId() + " is deleted.");
            config.saveConfig();
        }
    }

    public void resetDefaultApi(byte[] symKey){
        Menu menu = new Menu();
        menu.add("Reset initial APIP");
        menu.add("Reset NaSa node");
        menu.add("Reset main database");
        menu.add("Reset memory database");
        while (true) {
            System.out.println("Reset default API service...");
            ApiProvider apiProvider = chooseApiProvider();
            ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider, symKey);

            if (apiAccount != null) {
                Object client = apiAccount.connectApi(config.getApiProviderMap().get(apiAccount.getSid()), symKey, br);
                if (client != null) {
                    menu.show();
                    int choice = menu.choose(br);
                    switch (choice) {
                        case 1 -> config.setInitApipAccountId(apiAccount.getId());
                        case 2 -> config.setNaSaNodeAccountId(apiAccount.getId());
                        case 3 -> config.setMainDatabaseAccountId(apiAccount.getId());
                        case 4 -> config.setMemDatabaseAccountId(apiAccount.getId());
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

    public ApiAccount chooseApiAccount(byte[] symKey){
        ApiAccount apiAccount = null;
        config.showAccounts(config.getApiAccountMap());
        int input = Inputer.inputInteger(br, "Input the number of the account you want. Enter to add a new one:", config.getApiAccountMap().size());
        if (input == 0) {
            if(Inputer.askIfYes(br,"Add a new API account? y/n")) {
                ApiProvider apiProvider = chooseApiProvider();
                apiAccount = config.addApiAccount(apiProvider, symKey, br);
            }
        } else {
            apiAccount = (ApiAccount) config.getApiAccountMap().values().toArray()[input - 1];
        }
        return apiAccount;
    }

    public ApiProvider chooseApiProvider(){

        ApiProvider apiProvider;
        if (config.getApiProviderMap() == null) {
            System.out.println("No any API provider yet.");
            config.setApiProviderMap(new HashMap<>());
        }
        if (config.getApiProviderMap().size() == 0) {
            System.out.println("No any API provider yet.");
            apiProvider = config.addApiProvider(br, null);
        } else {
            config.showApiProviders(config.getApiProviderMap());
            int input = Inputer.inputInteger(br, "Input the number of the API provider you want:", config.getApiProviderMap().size());
            if (input == 0) {
                apiProvider = config.addApiProvider(br, ApiProvider.ApiType.APIP);
            } else apiProvider = (ApiProvider) config.getApiProviderMap().values().toArray()[input - 1];
        }

        return apiProvider;
    }

    public ApiAccount chooseApiProvidersAccount(ApiProvider apiProvider, byte[] symKey) {
        ApiAccount apiAccount = null;
        Map<String, ApiAccount> hitApiAccountMap = new HashMap<>();
        if (config.getApiAccountMap() == null) config.setApiAccountMap(new HashMap<>());
        if (config.getApiAccountMap().size() == 0) {
            System.out.println("No API accounts yet. Add new one...");
            apiAccount = config.addApiAccount(apiProvider, symKey, br);
        } else {
            for (ApiAccount apiAccount1 : config.getApiAccountMap().values()) {
                if (apiAccount1.getSid().equals(apiProvider.getSid()))
                    hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
            }
            if (hitApiAccountMap.size() == 0) {
                apiAccount = config.addApiAccount(apiProvider, symKey, br);
            } else {
                config.showAccounts(hitApiAccountMap);
                int input = Inputer.inputInteger(br, "Input the number of the account you want. Enter to add new one:", hitApiAccountMap.size());
                if (input == 0) {
                } else {
                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
                }
            }
        }
        return apiAccount;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public abstract void resetOtherParams(byte[] symKey) ;
}
