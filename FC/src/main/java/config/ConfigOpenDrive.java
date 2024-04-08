package config;

import FCH.Inputer;
import config.Config;

import java.io.BufferedReader;
import java.io.IOException;

public class ConfigOpenDrive extends Config {
    private String listenPath;
    private String account;
    private String minPayment;

//    public ConfigOpenDrive(String listenPath, String account, String minPayment) {
//        this.listenPath = listenPath;
//        this.account = account;
//        this.minPayment = minPayment;
//    }

    public String getListenPath() {
        return listenPath;
    }

    public void setListenPath(String listenPath) {
        this.listenPath = listenPath;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getMinPayment() {
        return minPayment;
    }

    public void setMinPayment(String minPayment) {
        this.minPayment = minPayment;
    }

    public void updateListenPath(BufferedReader br) {
        try {
            Inputer.promptAndUpdate(br,"listenPath",listenPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
