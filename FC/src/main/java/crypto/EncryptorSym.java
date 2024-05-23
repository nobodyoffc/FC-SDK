package crypto;

import com.google.common.hash.Hashing;
import crypto.Algorithm.AesCbc256;
import crypto.Algorithm.aesCbc256.CipherInputStreamWithHash;
import fcData.AlgorithmType;
import javaTools.BytesTools;
import javaTools.FileTools;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

import static fcData.AlgorithmType.*;

public class EncryptorSym {
    AlgorithmType algorithmType;

    public EncryptorSym() {
        this.algorithmType = FC_Aes256Cbc_No1_NrC7;
    }

    public EncryptorSym(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }

    public CryptoDataByte encryptByPassword(byte[] msg, char[] password){
        byte[] iv = BytesTools.getRandomBytes(16);
        byte[] symKey = passwordToSymKey(password, iv);
        CryptoDataByte cryptoDataByte = encryptBySymKey(msg,symKey,iv);
        cryptoDataByte.setType(EncryptType.Password);
        return cryptoDataByte;
    }
    public CryptoDataByte encryptStrByPassword(String msgStr, char[] password){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        return encryptByPassword(msg,password);
    }
    public String encryptStrToJsonBySymKey(String msgStr, String symKeyHex){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        byte[] key;
        try {
            key = HexFormat.of().parseHex(symKeyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(7, CryptoCodeMessage.getMessage(7));
            return cryptoDataStr.toNiceJson();
        }

        return encryptToJsonBySymKey(msg,key);
    }
    public String encryptToJsonBySymKey(byte[] msg, byte[] key){
        byte[] iv = BytesTools.getRandomBytes(16);
        CryptoDataByte cryptoDataByte = encryptBySymKey(msg,key, iv);
        return cryptoDataByte.toNiceJson();
    }
    public CryptoDataByte encryptFileByPassword(String dataFileName, String cipherFileName, char[]password){
        FileTools.createFileWithDirectories(cipherFileName);
        byte[] iv = BytesTools.getRandomBytes(16);
        byte[] key = passwordToSymKey(password, iv);


        CryptoDataByte cryptoDataByte = encryptFileBySymKey(dataFileName,cipherFileName,key,iv);
//        cryptoDataByte.setSymKey(key);
        cryptoDataByte.setType(EncryptType.Password);

        return cryptoDataByte;
    }
    public CryptoDataByte encryptFileBySymKey(String dataFileName, String cipherFileName, byte[]key){
        return encryptFileBySymKey(dataFileName,cipherFileName,key,null);
    }
    public CryptoDataByte encryptFileBySymKey(String dataFileName, String cipherFileName, byte[]key, byte[] iv){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        if(iv==null)iv = BytesTools.getRandomBytes(16);
        cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setAlg(algorithmType);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setSymKey(key);

        String tempFile = FileTools.getTempFileName();
        try (FileInputStream fis = new FileInputStream(dataFileName);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            switch (cryptoDataByte.getAlg()) {
                default -> AesCbc256.encrypt(fis, fos, cryptoDataByte);
            }
        } catch (FileNotFoundException e) {
            cryptoDataByte.setCodeMessage(11);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }

        try (FileInputStream fis = new FileInputStream(tempFile);
             FileOutputStream fos = new FileOutputStream(cipherFileName)) {
            fos.write(cryptoDataByte.toJson().getBytes());
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fis.close();
            Files.delete(Paths.get(tempFile));
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(6);
        }

        return cryptoDataByte;
    }
    public byte[] encryptStrToBundleBySymKey(String msgStr, String keyHex){
        byte[] msg = msgStr.getBytes(StandardCharsets.UTF_8);
        byte[] key;
        try {
            key = HexFormat.of().parseHex(keyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(7,CryptoCodeMessage.getMessage(7));
            return null;
        }
        return encryptToBundleBySymKey(msg,key);
    }
    public byte[] encryptToBundleBySymKey(byte[] msg, byte[] key){
        byte[] iv = BytesTools.getRandomBytes(16);
        CryptoDataByte cryptoDataByte = encryptBySymKey(msg,key, iv);
        if(cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.toBundle();
    }
    public CryptoDataByte encryptBySymKey(byte[] msg, byte[] symKey){
        byte[] iv = BytesTools.getRandomBytes(16);
        return encryptBySymKey(msg,symKey,iv,null);
    }
    public CryptoDataByte encryptBySymKey(byte[] msg, byte[] symKey, byte[] iv){
        return encryptBySymKey(msg,symKey,iv,null);
    }

    private CryptoDataByte encryptBySymKey(byte[] msg, byte[] key, byte[] iv, @Nullable CryptoDataByte cryptoDataByte){
        try(ByteArrayInputStream bisMsg = new ByteArrayInputStream(msg);
            ByteArrayOutputStream bosCipher = new ByteArrayOutputStream()) {

            switch (algorithmType){
                case FC_Aes256Cbc_No1_NrC7 ->  cryptoDataByte = AesCbc256.encrypt(bisMsg, bosCipher, key,iv, cryptoDataByte);
            }

            byte[] cipher = bosCipher.toByteArray();
            if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCipher(cipher);
            cryptoDataByte.set0CodeMessage();
            return cryptoDataByte;
        } catch (IOException e) {
            if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(10);
            return cryptoDataByte;
        }
    }

    public CryptoDataByte encryptStreamBySymKey(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        switch (algorithmType){
            case FC_Aes256Cbc_No1_NrC7 -> {
                return AesCbc256.encrypt(inputStream,outputStream,key,iv,cryptoDataByte);
            }
        }
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setCodeMessage(1);
        return cryptoDataByte;
    }

    public static CryptoDataByte encryptBySymKeyBase(String algo, String transformation, String provider, InputStream inputStream, OutputStream outputStream, CryptoDataByte cryptoDataByte) {
        AlgorithmType alg = null;
        if(cryptoDataByte.getAlg()!=null){
            alg = cryptoDataByte.getAlg();
        }

        byte[] key= cryptoDataByte.getSymKey();
        if(key.length!=32){
            cryptoDataByte.setCodeMessage(14);
            return cryptoDataByte;
        }

        Security.addProvider(new BouncyCastleProvider());

        SecretKeySpec keySpec = new SecretKeySpec(key, algo);
        byte[] iv=cryptoDataByte.getIv();
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher;
        var hashFunction = Hashing.sha256();
        var hasherIn = hashFunction.newHasher();
        var hasherOut = hashFunction.newHasher();
        try {
            cipher = Cipher.getInstance(transformation, provider);
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
        byte[] cipherId = DecryptorSym.sha256(hasherOut.hash().asBytes());
        byte[] did = DecryptorSym.sha256(hasherIn.hash().asBytes());
        cryptoDataByte.setCipherId(cipherId);
        cryptoDataByte.setDid(did);
        if(cryptoDataByte.getType()==null)
            cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setSymKey(key);
        cryptoDataByte.setAlg(alg);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.makeSum4();
        cryptoDataByte.set0CodeMessage();
        return cryptoDataByte;
    }
    public static byte[] passwordToSymKey(char[] password, byte[] iv) {
        byte[] passwordBytes = BytesTools.charArrayToByteArray(password, StandardCharsets.UTF_8);
        return DecryptorSym.sha256(BytesTools.addByteArray(DecryptorSym.sha256(passwordBytes), iv));
    }
    public AlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public void setAlgorithmType(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }
}
