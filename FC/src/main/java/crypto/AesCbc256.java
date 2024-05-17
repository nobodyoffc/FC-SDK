package crypto;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import crypto.EccSign.CipherInputStreamWithHash;
import crypto.cryptoTools.Hash;
import crypto.eccAes256K1.EccAesType;
import fcData.Algorithm;
import javaTools.BytesTools;
import javaTools.FileTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public class AesCbc256 {

    @Test
    public void test() throws InterruptedException {
        byte[] key = BytesTools.getRandomBytes(32);
        String keyHex = HexFormat.of().formatHex(key);
        System.out.println(Hex.toHex(key));
        String msgStr = "hello world!";

        byte[] msg = msgStr.getBytes();
        System.out.println("real msg id:"+Hex.toHex(Hash.Sha256x2(msg)));

        CryptoDataByte cryptoDataByte;
        System.out.println(msgStr);

        String cipherJson;

        try(ByteArrayInputStream bis = new ByteArrayInputStream(msg); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            long start = System.currentTimeMillis();
            cryptoDataByte = encrypt(bis,bos,key);
            System.out.println("Encrypt time:"+(System.currentTimeMillis()-start));
            byte[] cipher = bos.toByteArray();
            cryptoDataByte.setCipher(cipher);
            CryptoData cryptoData1 = CryptoData.fromCryptoDataByte(cryptoDataByte);
            cipherJson = cryptoData1.toNiceJson();
            System.out.println(cipherJson);
            JsonTools.gsonPrint(cryptoDataByte);
            System.out.println("msgId:"+cryptoData1.getDid());
            System.out.println("cipherId:"+cryptoData1.getCipherId());
            System.out.println("real cipher id:"+Hex.toHex(Hash.Sha256x2(cryptoDataByte.getCipher())));
        } catch (IOException  e) {
            throw new RuntimeException(e);
        }

        try(ByteArrayInputStream bis = new ByteArrayInputStream(cryptoDataByte.getCipher());
            ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            long start = System.currentTimeMillis();
            CryptoDataByte cryptoDataByte1 = decrypt(bis,bos,key, cryptoDataByte.getIv(),null);
            System.out.println("Decrypt time:"+(System.currentTimeMillis()-start));
            byte[] msgNew = bos.toByteArray();
            System.out.println(new String(msgNew));
            cryptoDataByte1.setData(msgNew);

            CryptoData cryptoData = CryptoData.fromCryptoDataByte(cryptoDataByte1);
            System.out.println("msgId:"+cryptoData.getDid());
            System.out.println("cipherId:"+cryptoData.getCipherId());
            System.out.println("msg:"+cryptoData.getData());
            System.out.println("real msg id:"+Hex.toHex(Hash.Sha256x2(cryptoDataByte1.getData())));
            System.out.println("real cipher id:"+Hex.toHex(Hash.Sha256x2(cryptoDataByte.getCipher())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Encrypt string to string
        System.out.println("String to Json:"+encrypt(msgStr,keyHex));
        byte[] bundle = encryptToBundle(msg, key);
        System.out.println("Bundle:"+Hex.toHex(bundle));
        System.out.println("String to bundle:"+Hex.toHex(encryptToBundle(msgStr,keyHex)));
        System.out.println("Decrypt json:"+decrypt(cipherJson,keyHex));

        System.out.println("Decrypt bundle:"+new String(decryptBundle(bundle,key)));

        String fileDataPath = "/Users/liuchangyong/Desktop/a.md";
        String fileCipherPath = "/Users/liuchangyong/Desktop/a.cipher1";
        CryptoDataByte cryptoDataByteFile = encrypt(fileDataPath,fileCipherPath,key);
        cryptoDataByteFile.setSymKey(key);

        fileDataPath = "/Users/liuchangyong/Desktop/aNew.md";
        decrypt(fileCipherPath,fileDataPath,cryptoDataByteFile);
    }

    public static String encrypt(String msgStr,String keyHex){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        byte[] key;
        try {
            key = HexFormat.of().parseHex(keyHex);
        }catch (Exception ignore){
            CryptoData cryptoData = new CryptoData();
            cryptoData.setCodeMessage(7,CryptoCodeMessage.getMessage(7));
            return cryptoData.toNiceJson();
        }
        return encryptToJson(msg,key);
    }

    public static CryptoDataByte encrypt(String dataFileName, String cipherFileName, byte[]key){
        FileTools.createFileWithDirectories(cipherFileName);
        return encrypt(new File(dataFileName),new File(cipherFileName),key);
    }
    public static CryptoDataByte encrypt(File dataFile, File cipherFile, byte[]key){
        try(FileInputStream fis = new FileInputStream(dataFile);
        FileOutputStream fos = new FileOutputStream(cipherFile)){
            return encrypt(fis,fos,key);
        } catch (FileNotFoundException e) {
            CryptoDataByte cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(11);
            return cryptoDataByte;
        } catch (IOException e) {
            CryptoDataByte cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }

    public static byte[] encryptToBundle(String msgStr,String keyHex){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        byte[] key;
        try {
            key = HexFormat.of().parseHex(keyHex);
        }catch (Exception ignore){
            CryptoData cryptoData = new CryptoData();
            cryptoData.setCodeMessage(7,CryptoCodeMessage.getMessage(7));
            return null;
        }
        return encryptToBundle(msg,key);
    }

    public static byte[] encryptToBundle(byte[] msg,byte[] key){
        CryptoDataByte cryptoDataByte = encrypt(msg,key);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] iv = cryptoDataByte.getIv();
        byte[] cipher = cryptoDataByte.getCipher();
        byte[] bundle = new byte[iv.length+ cipher.length];
        System.arraycopy(iv,0,bundle,0,16);
        System.arraycopy(cipher,0,bundle,16,cipher.length);
        return bundle;
    }

    public static String encryptToJson(byte[] msg,byte[] key){
        CryptoDataByte cryptoDataByte = encrypt(msg,key);
        CryptoData cryptoData = CryptoData.fromCryptoDataByte(cryptoDataByte);
        return cryptoData.toNiceJson();
    }

    public static CryptoDataByte encrypt(byte[] msg,byte[] key){

        try(ByteArrayInputStream bisMsg = new ByteArrayInputStream(msg);
            ByteArrayOutputStream bosCipher = new ByteArrayOutputStream()) {
            CryptoDataByte cryptoDataByte = encrypt(bisMsg, bosCipher, key);
            byte[] cipher = bosCipher.toByteArray();

            cryptoDataByte.setCipher(cipher);

            return cryptoDataByte;
        } catch (IOException e) {
            CryptoDataByte cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(10);
            return cryptoDataByte;
        }

    }

    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream, byte[] key) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        byte[] iv = BytesTools.getRandomBytes(16);
        Security.addProvider(new BouncyCastleProvider());

        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = null;
        var hashFunction = Hashing.sha256();
        var hasherIn = hashFunction.newHasher();
        var hasherOut = hashFunction.newHasher();
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC");

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            try (CipherInputStreamWithHash cis = new CipherInputStreamWithHash(inputStream, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = cis.read(buffer, hasherIn, hasherOut)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            cryptoDataByte.setCodeMessage(1,e.getMessage());
            return cryptoDataByte;
        } catch (NoSuchProviderException e) {
            cryptoDataByte.setCodeMessage(2,e.getMessage());
            return cryptoDataByte;
        } catch (NoSuchPaddingException e) {
            cryptoDataByte.setCodeMessage(3,e.getMessage());
            return cryptoDataByte;
        } catch (InvalidAlgorithmParameterException e) {
            cryptoDataByte.setCodeMessage(4,e.getMessage());
            return cryptoDataByte;
        } catch (InvalidKeyException e) {
            cryptoDataByte.setCodeMessage(5,e.getMessage());
            return cryptoDataByte;
        }
        byte[] cipherId = Hash.sha256(hasherOut.hash().asBytes());
        byte[] msgId = Hash.sha256(hasherIn.hash().asBytes());
        cryptoDataByte.setCipherId(cipherId);
        cryptoDataByte.setMsgId(msgId);

        cryptoDataByte.setType(EccAesType.SymKey);
        cryptoDataByte.setAlg(Algorithm.Aes256Cbc);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.set0CodeMessage();

        return cryptoDataByte;
    }
    /*
    Decrypt
     */

    public static String decrypt(String cryptoDataJson,String keyHex) {
        byte[] key;
        try {
            key = HexFormat.of().parseHex(keyHex);
        }catch (Exception ignore){
            CryptoData cryptoData = new CryptoData();
            cryptoData.setCodeMessage(7,CryptoCodeMessage.getMessage(7));
            return CryptoCodeMessage.getErrorStringCodeMsg(7);
        }

        CryptoDataByte cryptoDataByte = decrypt(cryptoDataJson,key);
        if(cryptoDataByte.getCode()!=0)
            return CryptoCodeMessage.getErrorStringCodeMsg(cryptoDataByte.getCode());
        return new String(cryptoDataByte.getData());
    }
    public static byte[] decryptBundle(byte[]bundle, byte[]key) {
        byte[] iv = new byte[16];
        System.arraycopy(bundle,0,iv,0,16);
        byte[] cipher = new byte[bundle.length-16];
        System.arraycopy(bundle,16,cipher,0,bundle.length-16);
        return decrypt(cipher,iv,key);
    }

    public static byte[] decrypt(byte[]cipher,byte[]iv,byte[]key) {
        try(ByteArrayInputStream bisCipher = new ByteArrayInputStream(cipher);
            ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
            CryptoDataByte cryptoDataByte = decrypt(bisCipher,bosData,key, iv,null);
            byte[] data = bosData.toByteArray();
            cryptoDataByte.setData(data);
            return cryptoDataByte.getData();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public static CryptoDataByte decrypt(String cryptoDataJson,byte[]key) {
        CryptoData cryptoData;
        try {
            cryptoData = new Gson().fromJson(cryptoDataJson, CryptoData.class);
        }catch (Exception e){
            CryptoDataByte cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(9);
            return cryptoDataByte;
        }
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        byte[] cipher = cryptoDataByte.getCipher();
        byte[] iv = cryptoDataByte.getIv();

        try(ByteArrayInputStream bisCipher = new ByteArrayInputStream(cipher);
            ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
            cryptoDataByte = decrypt(bisCipher,bosData,key, iv,cryptoDataByte);
            byte[] data = bosData.toByteArray();
            cryptoDataByte.setData(data);
            cryptoDataByte.set0CodeMessage();
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(10);
            return cryptoDataByte;
        }
    }

    public static CryptoDataByte decrypt(String cipherFileName,String dataFileName, CryptoDataByte cryptoDataByte){
        FileTools.createFileWithDirectories(dataFileName);
        return decrypt(new File(cipherFileName),new File(dataFileName),cryptoDataByte);
    }
    public static CryptoDataByte decrypt(String cipherFileName,String dataFileName,byte[]key,byte[] iv){
        FileTools.createFileWithDirectories(dataFileName);
        return decrypt(new File(cipherFileName),new File(dataFileName),key,iv);
    }
    public static CryptoDataByte decrypt(File cipherFile,File dataFile, CryptoDataByte cryptoDataByte){
        return decrypt(cipherFile,dataFile,null,null,cryptoDataByte);
    }
    public static CryptoDataByte decrypt(File cipherFile,File dataFile, byte[]key,byte[] iv){
        return decrypt(cipherFile,dataFile,key,iv,null);
    }

    public static CryptoDataByte decrypt(File cipherFile,File dataFile, byte[]key,byte[] iv, CryptoDataByte cryptoDataByte){
        if(cryptoDataByte==null) cryptoDataByte = new CryptoDataByte();
        else if(key==null)key = cryptoDataByte.getSymKey();
        else if(iv==null)iv=cryptoDataByte.getIv();
        else cryptoDataByte.setIv(iv);

        try(FileOutputStream fos = new FileOutputStream(dataFile);
            FileInputStream fis = new FileInputStream(cipherFile)){
            return decrypt(fis,fos,key,iv,cryptoDataByte);
        } catch (FileNotFoundException e) {
            cryptoDataByte.setCodeMessage(11);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }

    public static CryptoDataByte decrypt(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv, @Nullable CryptoDataByte cryptoDataByte) {
        Security.addProvider(new BouncyCastleProvider());

        if(cryptoDataByte==null) cryptoDataByte = new CryptoDataByte();
        else if(key==null)key = cryptoDataByte.getSymKey();
        else if(iv==null)iv=cryptoDataByte.getIv();
        else cryptoDataByte.setIv(iv);

        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            var hashFunction = Hashing.sha256();
            var hasherIn = hashFunction.newHasher();
            //It's very hard to hash the output.

            try (CipherOutputStream cos = new CipherOutputStream(outputStream, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    hasherIn.putBytes(buffer, 0, bytesRead);
                    cos.write(buffer, 0, bytesRead);
                }
            }
            cryptoDataByte.setCipherId(Hash.sha256(hasherIn.hash().asBytes()));
        } catch (InvalidAlgorithmParameterException e) {
            cryptoDataByte.setCodeMessage(4,e.getMessage());
            return cryptoDataByte;
        } catch (NoSuchPaddingException e) {
            cryptoDataByte.setCodeMessage(3,e.getMessage());
            return cryptoDataByte;
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setCodeMessage(1,e.getMessage());
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(6,e.getMessage());
            return cryptoDataByte;
        } catch (NoSuchProviderException e) {
            cryptoDataByte.setCodeMessage(2,e.getMessage());
            return cryptoDataByte;
        } catch (InvalidKeyException e) {
            cryptoDataByte.setCodeMessage(5,e.getMessage());
            return cryptoDataByte;
        }

        cryptoDataByte.setType(EccAesType.SymKey);
        cryptoDataByte.setAlg(Algorithm.Aes256Cbc);
        return cryptoDataByte;
    }
}
