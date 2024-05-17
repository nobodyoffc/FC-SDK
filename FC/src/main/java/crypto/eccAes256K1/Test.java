package crypto.eccAes256K1;

import com.google.gson.Gson;
import crypto.CryptoData;
import crypto.CryptoDataByte;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

public class Test {
    public static void main(String[] args) throws Exception {

        Gson gson = new Gson();

        System.out.println("----------------------");
        System.out.println("Encode: ");
        System.out.println("    message: UTF-8");
        System.out.println("    key: Hex char[]");
        System.out.println("    ciphertext: Base64");
        System.out.println("----------------------");

        String msg = "hello world!";
        System.out.println("msg: " + msg);

        // ECC Test
        System.out.println("----------------------");
        System.out.println("Basic Test");
        System.out.println("----------------------");
        System.out.println("AsyOneWay:");
        System.out.println("----------");

        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte;
        CryptoData cryptoData = new CryptoData();

        String pubKeyB = "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67";
        cryptoData.setData(msg);
        cryptoData.setPubKeyB(pubKeyB);
        cryptoData.setType(EccAesType.AsyOneWay);
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);

        // Encrypt with new keys
        ecc.encrypt(cryptoDataByte);

        char[] symKey;
        System.out.println("SymKey: " + HexFormat.of().formatHex(cryptoDataByte.getSymKey()));
        cryptoDataByte.clearSymKey();
        cryptoData = CryptoData.fromCryptoDataByte(cryptoDataByte);
        System.out.println("Encrypted with a new key pair:" + gson.toJson(cryptoData));

        //Decrypt with new key
        String priKeyB = "ee72e6dd4047ef7f4c9886059cbab42eaab08afe7799cbc0539269ee7e2ec30c";
        cryptoDataByte.setData(null);
        cryptoDataByte.setPriKeyB(HexFormat.of().parseHex(priKeyB));

        ecc.decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();
        System.out.println("Decrypted from bytes:" + gson.toJson(CryptoData.fromCryptoDataByte(cryptoDataByte)));

        cryptoData = CryptoData.fromCryptoDataByte(cryptoDataByte);
        cryptoData.setPriKeyB(priKeyB.toCharArray());
        ecc.decrypt(cryptoData);
        System.out.println("Decrypted from String and char array:" + gson.toJson(cryptoData));

        System.out.println("EccAes JSON without symKey:");
        cryptoData.clearSymKey();
        System.out.println(gson.toJson(cryptoData));


        System.out.println("----------------------");
        System.out.println("AsyTwoWay:");
        System.out.println("----------");

        String pubKeyA = "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a";
        String priKeyA = "a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575";

        cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.AsyTwoWay);
        cryptoData.setData(msg);
        cryptoData.setPubKeyB(pubKeyB);
        ecc.encrypt(cryptoData);
        System.out.println("Lack priKeyA: " + gson.toJson(cryptoData));

        cryptoData.setPriKeyA(priKeyA.toCharArray());

        ecc.encrypt(cryptoData);
        System.out.println("Encrypt: " + gson.toJson(cryptoData));

        cryptoData.setPriKeyB(priKeyB.toCharArray());
        cryptoData.setData(null);
        ecc.decrypt(cryptoData);
        System.out.println("Decrypt by private Key B: " + gson.toJson(cryptoData));
        cryptoData.setPriKeyA(priKeyA.toCharArray());
        cryptoData.setData(null);
        ecc.decrypt(cryptoData);
        System.out.println("Decrypt by private Key A: " + gson.toJson(cryptoData));

        System.out.println("----------------------");
        System.out.println("SymKey:");
        System.out.println("----------");
        cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.SymKey);

        String symKeyStr = "3b7ca1c4925c597083bb94c8e1582a621e4e72510780aa31ef0a769a406c2870";
        symKey = symKeyStr.toCharArray();
        cryptoData.setSymKey(symKey);
        ecc.encrypt(cryptoData);
        System.out.println("Lack msg: " + gson.toJson(cryptoData));

        cryptoData.setData(msg);
        cryptoData.setSymKey(symKey);
        ecc.encrypt(cryptoData);
        System.out.println("SymKey encrypt: " + gson.toJson(cryptoData));

        cryptoData.setData(null);
        cryptoData.setSymKey(symKey);
        ecc.decrypt(cryptoData);
        System.out.println("SymKey decrypt: " + gson.toJson(cryptoData));

        System.out.println("----------------------");
        System.out.println("Password:");
        System.out.println("----------");

        cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.Password);
        cryptoData.setData(msg);
        cryptoData.setSymKey(symKey);
        String passwordStr = "password马云！";
        char[] password = passwordStr.toCharArray();
        cryptoData.setPassword(password);

        System.out.println("password:" + String.valueOf(password));
        ecc.encrypt(cryptoData);
        System.out.println("Password encrypt: \n" + gson.toJson(cryptoData));

        cryptoData.setData(null);
        cryptoData.setSymKey(null);
        password = "password马云！".toCharArray();
        cryptoData.setPassword(password);

        ecc.decrypt(cryptoData);
        System.out.println("Password decrypt: \n" + gson.toJson(cryptoData));
        System.out.println("----------------------");
        System.out.println("----------------------");
        System.out.println("Test Json");
        System.out.println("----------------------");
        System.out.println("AsyOneWay json:");
        System.out.println("----------");

        String encOneWayJson0 = ecc.encrypt(msg, pubKeyB);
        checkResult(cryptoData, "Encrypted: \n" + encOneWayJson0);

        String eccAesData1 = ecc.decrypt(encOneWayJson0, priKeyB.toCharArray());
        checkResult(cryptoData, "Decrypted:\n" + eccAesData1);

        System.out.println("----------");

        System.out.println("AsyTwoWay json:");
        System.out.println("----------");

        cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.AsyTwoWay);
        cryptoData.setData(msg);
        cryptoData.setPubKeyB(pubKeyB);
        String twoWayJson1 = gson.toJson(cryptoData);

        System.out.println("TwoWayJson1:" + twoWayJson1);


        String encTwoWayJson1 = ecc.encrypt(msg, pubKeyB, priKeyA.toCharArray());
        checkResult(cryptoData, "Encrypted: \n" + encTwoWayJson1);
        eccAesData1 = ecc.decrypt(encTwoWayJson1, priKeyB.toCharArray());
        checkResult(cryptoData, "Decrypted:\n" + eccAesData1);

        System.out.println("----------");

        System.out.println("SymKey json:");
        System.out.println("----------");
        cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.SymKey);
        cryptoData.setData(msg);
        symKey = symKeyStr.toCharArray();
        cryptoData.setSymKey(symKey);

        String symKeyJson1 = gson.toJson(cryptoData);
        System.out.println("SymKeyJson1:" + symKeyJson1);
        System.out.println("SymKey: " + Arrays.toString(symKey));
        String encSymKeyJson1 = ecc.encrypt(msg, symKey);
        checkResult(cryptoData, "Encrypted: \n" + encSymKeyJson1);

        String decSymKeyJson = ecc.decrypt(encSymKeyJson1, symKey);
        checkResult(cryptoData, "Decrypted:\n" + decSymKeyJson);
        System.out.println("----------");

        System.out.println("Password json:");
        System.out.println("----------");
        cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.Password);
        cryptoData.setData(msg);
        cryptoData.setPassword(passwordStr.toCharArray());

        String passwordDataJson1 = gson.toJson(cryptoData);
        System.out.println("PasswordJson1:" + passwordDataJson1);

        String encPasswordJson1 = ecc.encrypt(msg, passwordStr.toCharArray());
        checkResult(cryptoData, "Encrypted: \n" + encPasswordJson1);

        String decPasswordJson = ecc.decrypt(encPasswordJson1, passwordStr.toCharArray());
        checkResult(cryptoData, "Decrypted:\n" + decPasswordJson);
        System.out.println("----------------------");

        System.out.println("----------------------");
        System.out.println("Test Constructor");
        System.out.println("----------------------");

        System.out.println("AsyOneWay encrypt Constructor:");
        System.out.println("----------");
        cryptoData = new CryptoData(EccAesType.AsyOneWay, msg, pubKeyB);
        ecc.encrypt(cryptoData);
        System.out.println(gson.toJson(cryptoData));

        System.out.println("----------");
        System.out.println("AsyTwoWay encrypt Constructor:");
        System.out.println("----------");

        cryptoData = new CryptoData(EccAesType.AsyTwoWay, msg, pubKeyB, priKeyA.toCharArray());
        ecc.encrypt(cryptoData);
        System.out.println(gson.toJson(cryptoData));
        System.out.println("----------");
        System.out.println("SymKey encrypt Constructor:");
        System.out.println("----------");
        symKey = symKeyStr.toCharArray();
        cryptoData = new CryptoData(EccAesType.SymKey, msg, symKey);
        ecc.encrypt(cryptoData);
        System.out.println(gson.toJson(cryptoData));
        System.out.println("----------");
        System.out.println("Password encrypt Constructor:");
        System.out.println("----------");
        cryptoData = new CryptoData(EccAesType.Password, msg, password);
        ecc.encrypt(cryptoData);
        System.out.println(gson.toJson(cryptoData));
        System.out.println("----------");
        System.out.println("Asy Decrypt Constructor:");
        System.out.println("----------");
        String cipher = "yu7qzwXoEeKwRsCT/fLxaA==";
        String iv = "988a330ab28e61fa01471bf13ce6cc7d";
        String sum = "346a8033";
        cryptoData = new CryptoData(EccAesType.AsyOneWay, pubKeyA, pubKeyB, iv, cipher, sum, priKeyB.toCharArray());
        ecc.decrypt(cryptoData);
        System.out.println(gson.toJson(cryptoData));
        System.out.println("----------");
        System.out.println("Sym Decrypt Constructor:");
        System.out.println("----------");
        cipher = "6f20f3ukM3ol0KRJHACb0w==";
        iv = "862dc48880b515d589851df25827fbcf";
        sum = "befc5792";
        cryptoData = new CryptoData(EccAesType.SymKey, iv, cipher, sum, symKey);
        ecc.decrypt(cryptoData);
        System.out.println(gson.toJson(cryptoData));
        System.out.println("----------------------");
        System.out.println("Bundle test");
        System.out.println("----------------------");
        System.out.println("String");
        System.out.println("----------");
        System.out.println("AsyOneWay bundle test");
        System.out.println("----------");

        System.out.println("msg:" + msg + ",pubKeyB:" + pubKeyB);
        String bundle = ecc.encryptAsyOneWayBundle(msg, pubKeyB);
        System.out.println("Cipher bundle: " + bundle);
        String msgBundle = ecc.decryptAsyOneWayBundle(bundle, priKeyB.toCharArray());
        System.out.println("Msg from bundle:" + msgBundle);

        System.out.println("----------------------");
        System.out.println("AsyTwoWay bundle test");
        System.out.println("----------");

        bundle = ecc.encryptAsyTwoWayBundle(msg, pubKeyB, priKeyA.toCharArray());
        System.out.println("Cipher bundle: " + bundle);
        msgBundle = ecc.decryptAsyTwoWayBundle(bundle, pubKeyA, priKeyB.toCharArray());
        System.out.println("Msg from PriKeyB:" + msgBundle);
        msgBundle = ecc.decryptAsyTwoWayBundle(bundle, pubKeyB, priKeyA.toCharArray());
        System.out.println("Msg from PriKeyA:" + msgBundle);

        System.out.println("----------------------");
        System.out.println("SymKey bundle test");
        System.out.println("----------");

        bundle = ecc.encryptSymKeyBundle(msg, symKey);
        System.out.println("Cipher bundle: " + bundle);
        msgBundle = ecc.decryptSymKeyBundle(bundle, symKey);
        System.out.println("Msg from bundle:" + msgBundle);
        System.out.println("----------------------");

        System.out.println("byte[]");
        System.out.println("----------");
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] pubKeyBBytes = HexFormat.of().parseHex(pubKeyB);
        byte[] priKeyBBytes = HexFormat.of().parseHex(priKeyB);
        byte[] pubKeyABytes = HexFormat.of().parseHex(pubKeyA);
        byte[] priKeyABytes = HexFormat.of().parseHex(priKeyA);


        System.out.println("AsyOneWay bundle test");
        System.out.println("----------");

        byte[] bundleBytes = ecc.encryptAsyOneWayBundle(msgBytes, pubKeyBBytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        byte[] msgBundleBytes = ecc.decryptAsyOneWayBundle(bundleBytes, priKeyBBytes);
        System.out.println("Msg from bundle:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("AsyTwoWay bundle test");
        System.out.println("----------");

        //Reload sensitive parameters
        priKeyBBytes = HexFormat.of().parseHex(priKeyB);
        priKeyABytes = HexFormat.of().parseHex(priKeyA);

        bundleBytes = ecc.encryptAsyTwoWayBundle(msgBytes, pubKeyBBytes, priKeyABytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        msgBundleBytes = ecc.decryptAsyTwoWayBundle(bundleBytes, pubKeyABytes, priKeyBBytes);
        System.out.println("Msg from PriKeyB:" + new String(msgBundleBytes));

        //Reload sensitive parameters
        priKeyBBytes = HexFormat.of().parseHex(priKeyB);
        priKeyABytes = HexFormat.of().parseHex(priKeyA);
        msgBundleBytes = ecc.decryptAsyTwoWayBundle(bundleBytes, pubKeyBBytes, priKeyABytes);
        System.out.println("Msg from PriKeyA:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("SymKey bundle test");
        System.out.println("----------");

        byte[] symKeyBytes = HexFormat.of().parseHex(symKeyStr);
        bundleBytes = ecc.encryptSymKeyBundle(msgBytes, symKeyBytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        //Reload sensitive parameters
        symKeyBytes = HexFormat.of().parseHex(symKeyStr);
        msgBundleBytes = ecc.decryptSymKeyBundle(bundleBytes, symKeyBytes);
        System.out.println("Msg from bundle:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("Char Array as msg:");
        System.out.println("----------------------");

        System.out.println("msg: " + symKeyStr);
        String cipherAsyOne = ecc.encrypt(symKeyStr.toCharArray(), pubKeyB);
        String cipherAsyTwo = ecc.encrypt(symKeyStr.toCharArray(), pubKeyB, priKeyA.toCharArray());
        String cipherSymKey = ecc.encrypt(symKeyStr.toCharArray(), symKeyStr.toCharArray());
        String cipherPassword = ecc.encrypt(symKeyStr.toCharArray(), passwordStr.toCharArray());

        System.out.println("cipherAsyOne:" + cipherAsyOne);
        System.out.println("cipherAsyTwo:" + cipherAsyTwo);
        System.out.println("cipherSymKey:" + cipherSymKey);
        System.out.println("cipherPassword:" + cipherPassword);

        System.out.println("decrypted AsyOne:" + ecc.decrypt(cipherAsyOne, priKeyB.toCharArray()));
        System.out.println("decrypted AsyTwo:" + ecc.decrypt(cipherAsyTwo, priKeyB.toCharArray()));
        System.out.println("decrypted SymKey:" + ecc.decrypt(cipherSymKey, symKeyStr.toCharArray()));
        System.out.println("decrypted Password:" + ecc.decrypt(cipherPassword, passwordStr.toCharArray()));
    }

    private static void checkResult(CryptoData cryptoData, String s) {
        if (cryptoData.getMessage() != null) {
            System.out.println(cryptoData.getMessage());
        } else {
            System.out.println(s);
        }
    }
}
