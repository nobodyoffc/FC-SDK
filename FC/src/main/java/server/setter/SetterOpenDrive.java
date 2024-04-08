package server.setter;

import appTools.Menu;
import config.Config;
import config.ConfigOpenDrive;

import java.io.BufferedReader;

public class SetterOpenDrive extends Setter{
    private ConfigOpenDrive configOpenDrive;

    public SetterOpenDrive(ConfigOpenDrive configOpenDrive,BufferedReader br) {
        super(configOpenDrive,br);
        configOpenDrive = (ConfigOpenDrive) config;
    }

    @Override
    public void resetOtherParams(byte[] symKey) {
        Menu menu = new Menu();
        menu.add("Reset listenPath");
        menu.add("Reset account");
        menu.add("minPayment");
        int choice = menu.choose(br);
        menu.show();
        switch (choice){
            case 1 -> configOpenDrive.updateListenPath(br);
        }
    }
}
