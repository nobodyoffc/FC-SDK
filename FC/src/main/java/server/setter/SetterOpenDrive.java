package server.setter;

import appTools.Menu;
import config.Config;
import config.MySettings;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;

public class SetterOpenDrive extends Setter{
    private MySettings mySettings;

    public SetterOpenDrive(Config config, MySettings mySettings, BufferedReader br, JedisPool jedisPool) {
        super(config, br, jedisPool);
        this.mySettings = mySettings;
    }

    @Override
    public void resetMyLocalSettings(byte[] symKey) {
        Menu menu = new Menu();
        menu.add("Reset listenPath");
        menu.add("Reset account");
        menu.add("minPayment");
        int choice = menu.choose(br);
        menu.show();
        switch (choice){
            case 1 -> mySettings.updateAll(br);
        }
    }
}
