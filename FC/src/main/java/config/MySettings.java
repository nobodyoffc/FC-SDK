package config;

import FCH.Inputer;
import constants.FieldNames;
import javaTools.JsonTools;
import server.Starter;

import java.io.BufferedReader;
import java.io.IOException;

public class MySettings {
    private String listenPath;
    private boolean fromWebhook;
    private final transient String fileName = Starter.addSidBriefToName(SETTINGS_DOT_JSON);

    public static String SETTINGS_DOT_JSON = "settings.json";

    public void updateListenPath(BufferedReader br) {
        try {
            Inputer.promptAndUpdate(br,"listenPath",listenPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static MySettings loadFromFile(){
        try {
            return JsonTools.readObjectFromJsonFile(null, Starter.addSidBriefToName(SETTINGS_DOT_JSON),MySettings.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static MySettings checkMySettings(BufferedReader br){
        MySettings mySettings=MySettings.loadFromFile();
        if(mySettings==null){
            mySettings = new MySettings();
            mySettings.inputAll(br);
            mySettings.writeToFile();
            return mySettings;
        }
        if(mySettings.getListenPath()==null)mySettings.inputListenPath(br);
        mySettings.writeToFile();
        return mySettings;
    }
    public void inputAll(BufferedReader br){
        try {
            fromWebhook = appTools.Inputer.promptAndSet(br, FieldNames.FROM_WEBHOOK,this.fromWebhook);
            if(!fromWebhook)listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.listenPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void inputListenPath(BufferedReader br){
        try {
            listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.listenPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void inputFromWebhook(BufferedReader br){
        try {
            fromWebhook = appTools.Inputer.promptAndSet(br, FieldNames.FROM_WEBHOOK,this.fromWebhook);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateAll(BufferedReader br){
        try {
            fromWebhook = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.fromWebhook);
            if(!fromWebhook)listenPath = appTools.Inputer.promptAndUpdate(br, FieldNames.LISTEN_PATH,this.listenPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeToFile(){
        JsonTools.writeObjectToJsonFile(this,fileName,false);
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
}
