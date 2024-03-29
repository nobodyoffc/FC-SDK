package config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigTools {

    private static final Logger log = LoggerFactory.getLogger(ConfigTools.class);

    //    public void setInitSymKeyCipher(byte[] passwordBytes) {
//        byte[] randomSymKey = BytesTools.getRandomBytes(32);
//        EccAesDataByte eccAesDataByte = encryptInitSymKey(passwordBytes, randomSymKey);
//        if (eccAesDataByte == null) return;
//        StartMake.initSymKey=randomSymKey;
//        this.initSymKeyCipher = EccAesData.fromEccAesDataByte(eccAesDataByte).toJson();
//    }
}
