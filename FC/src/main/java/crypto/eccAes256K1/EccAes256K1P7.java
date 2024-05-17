package crypto.eccAes256K1;

import crypto.CryptoData;
import crypto.CryptoDataByte;
import fcData.Affair;
import fcData.Algorithm;
import fcData.Op;
import constants.Constants;
import crypto.cryptoTools.Hash;
import crypto.cryptoTools.KeyTools;
import javaTools.BytesTools;
import javaTools.JsonTools;

import com.google.gson.Gson;
import FCH.Inputer;
import javaTools.FileTools;

import org.bitcoinj.core.ECKey;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static fcData.Algorithm.EccAes256K1P7_No1_NrC7;
import static fcData.Algorithm.EccAes256K1v2_No1_NrC7;

/**
 * * ECDH<p>
 * * secp256k1<p>
 * * AES-256-CBC-PKCS7Padding<p>
 * * By No1_NrC7 with the help of chatGPT
 */

public class EccAes256K1P7 {
    private static final Logger log = LoggerFactory.getLogger(EccAes256K1P7.class);
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    public void encrypt(CryptoDataByte cryptoDataByte) {
        if (isErrorAlgAndType(cryptoDataByte)) return;
        switch (cryptoDataByte.getType()) {
            case AsyOneWay, AsyTwoWay -> encryptAsy(cryptoDataByte);
            case SymKey -> encryptWithSymKey(cryptoDataByte);
            case Password -> encryptWithPassword(cryptoDataByte);
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
    }

    public void decrypt(CryptoDataByte cryptoDataByte) {
        if (isErrorAlgAndType(cryptoDataByte)) return;

        switch (cryptoDataByte.getType()) {
            case AsyOneWay, AsyTwoWay -> decryptAsy(cryptoDataByte);
            case SymKey -> decryptWithSymKey(cryptoDataByte);
            case Password -> decryptWithPassword(cryptoDataByte);
        }
    }

    public static String encryptKeyWithPassword(byte[] keyBytes, char[] password) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.Password);
        cryptoDataByte.setData(keyBytes);
        cryptoDataByte.setPassword(BytesTools.utf8CharArrayToByteArray(password));
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
    }
    public static String encryptWithSymKey(byte[] msgBytes, byte[] symKey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.SymKey);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setSymKey(symKey);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public static String encryptWithPubKey(byte[] msgBytes, byte[] pubKey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyOneWay);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setPubKeyB(pubKey);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public static String encryptWithPubKeyPriKey(byte[] msgBytes, byte[] pubKeyB,byte[]priKeyA) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyTwoWay);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setPubKeyB(pubKeyB);
        cryptoDataByte.setPriKeyA(priKeyA);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public void encrypt(CryptoData cryptoData) {
        if (cryptoData == null) return;
        cryptoData.setMessage(null);

        if (cryptoData.getType() == null) {
            cryptoData.setMessage("EccAesType is required.");
            //eccAesData.clearAllSensitiveData();
            return;
        }
        if (!isGoodEncryptParams(cryptoData)) {
            //eccAesData.clearAllSensitiveData();
            return;
        }
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        encrypt(cryptoDataByte);
        CryptoData cryptoData1 = CryptoData.fromCryptoDataByte(cryptoDataByte);
        copyEccAesData(cryptoData1, cryptoData);
    }

    public String encrypt(String msg, String pubKeyB) {
        CryptoData cryptoData = new CryptoData(EccAesType.AsyOneWay, msg, pubKeyB);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(String msg, String pubKeyB, char[] priKeyA) {
        CryptoData cryptoData = new CryptoData(EccAesType.AsyTwoWay, msg, pubKeyB, priKeyA);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(String msg, char[] symKey32OrPassword) {
        CryptoData cryptoData;
        if (symKey32OrPassword.length == 64) {
            boolean isHex = isCharArrayHex(symKey32OrPassword);
            if (isHex) cryptoData = new CryptoData(EccAesType.SymKey, msg, symKey32OrPassword);
            else cryptoData = new CryptoData(EccAesType.Password, msg, symKey32OrPassword);
        } else cryptoData = new CryptoData(EccAesType.Password, msg, symKey32OrPassword);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, String pubKeyB) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyOneWay);
        cryptoDataByte.setData(BytesTools.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        cryptoDataByte.setPubKeyB(BytesTools.hexToByteArray(pubKeyB));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, String pubKeyB, char[] priKeyA) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyTwoWay);
        cryptoDataByte.setData(BytesTools.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        cryptoDataByte.setPubKeyB(BytesTools.hexToByteArray(pubKeyB));
        cryptoDataByte.setPriKeyA(BytesTools.hexCharArrayToByteArray(priKeyA));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, char[] symKey32OrPassword) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        if (symKey32OrPassword.length == 64) {
            boolean isHex = isCharArrayHex(symKey32OrPassword);
            if (isHex) {
                cryptoDataByte.setType(EccAesType.SymKey);
                cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(symKey32OrPassword));
            } else {
                cryptoDataByte.setType(EccAesType.Password);
                cryptoDataByte.setPassword(BytesTools.charArrayToByteArray(symKey32OrPassword, StandardCharsets.UTF_8));
            }
        } else {
            cryptoDataByte.setType(EccAesType.Password);
            cryptoDataByte.setPassword(BytesTools.charArrayToByteArray(symKey32OrPassword, StandardCharsets.UTF_8));
        }

        cryptoDataByte.setData(BytesTools.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoData.fromCryptoDataByte(cryptoDataByte).toJson();
    }


    public static byte[] decryptJsonBytes(String keyCipherJson, byte[] keyOrPassword) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
//        System.out.println("Decrypt key...");
        CryptoDataByte result = ecc.decrypt(keyCipherJson, keyOrPassword);
        if (result.getMessage() != null) {
            log.debug("Decrypting wrong: " + result.getMessage());
            return null;
        }
        return result.getData();
    }

    public static String inputPriKeyCipher(BufferedReader br, byte[] initSymKey) {
        ECKey ecKey = Inputer.inputPriKey(br);
        if (ecKey == null) return null;
        byte[] priKeyBytes = ecKey.getPrivKeyBytes();
        return encryptWithSymKey(priKeyBytes, initSymKey);
    }

    public static CryptoDataByte encryptWithPassword(byte[] initSymKey, byte[] passwordBytes) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.Password);
        cryptoDataByte.setData(initSymKey);
        cryptoDataByte.setPassword(passwordBytes);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) {
            System.out.println("Failed to encrypt key: " + cryptoDataByte.getMessage());
            return null;
        }
        return cryptoDataByte;
    }

    public static CryptoDataByte makeIvCipherToEccAesDataByte(byte[] ivCipherBytes) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(Algorithm.Aes256Cbc);
        cryptoDataByte.setType(EccAesType.SymKey);
        byte[] iv = Arrays.copyOfRange(ivCipherBytes, 0, 16);
        byte[] cipher = Arrays.copyOfRange(ivCipherBytes, 16, ivCipherBytes.length);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);
        return cryptoDataByte;
    }

    public static ECPrivateKeyParameters priKeyFromHex(String privateKeyHex) {
        BigInteger privateKeyValue = new BigInteger(privateKeyHex, 16); // Convert hex to BigInteger
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1"); // Use the same curve name as in key pair generation
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());
        return new ECPrivateKeyParameters(privateKeyValue, domainParameters);
    }

    public static ECPrivateKeyParameters priKeyFromBytes(byte[] privateKey) {
        return priKeyFromHex(HexFormat.of().formatHex(privateKey));
    }

    public static ECPublicKeyParameters pubKeyFromPriKey(ECPrivateKeyParameters privateKey) {
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECPoint Q = domainParameters.getG().multiply(privateKey.getD()); // Scalar multiplication of base point (G) and private key

        return new ECPublicKeyParameters(Q, domainParameters);
    }

    public static byte[] pubKeyToBytes(ECPublicKeyParameters publicKey) {
        return publicKey.getQ().getEncoded(true);
    }

    public static byte[] getRandomIv() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        return iv;
    }

    public static boolean isTheKeyPair(byte[] pubKeyByte, byte[] priKeyByte) {
        ECPrivateKeyParameters priKey = priKeyFromBytes(priKeyByte);
        byte[] pubKeyFromPriKey = pubKeyToBytes(pubKeyFromPriKey(priKey));
        return Arrays.equals(pubKeyByte, pubKeyFromPriKey);
    }

    public static byte[] decryptWithPriKey(String cipher, byte[] priKey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataBytes = ecc.decrypt(cipher, priKey);
        if (cryptoDataBytes.getMessage() != null) {
            System.out.println("Decrypt sessionKey wrong: " + cryptoDataBytes.getMessage());
            BytesTools.clearByteArray(priKey);
            return null;
        }
        return cryptoDataBytes.getData();
    }

    private boolean isCharArrayHex(char[] symKey32OrPassword) {
        boolean isHex = false;
        for (char c : symKey32OrPassword) {
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                isHex = true;
            } else {
                return false;
            }
        }
        return isHex;
    }

    public String encrypt(File originalFile, char[] symKey) {
        CryptoData cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.SymKey);
        cryptoData.setAlg(EccAes256K1v2_No1_NrC7);
        cryptoData.setSymKey(symKey);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        byte[] symKeyBytes = BytesTools.hexCharArrayToByteArray(symKey);
        return encrypt(originalFile, cryptoDataByte);
    }

    public String encrypt(File originalFile, String pubKeyB) {
        CryptoData cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.AsyOneWay);
        cryptoData.setAlg(EccAes256K1v2_No1_NrC7);
        cryptoData.setPubKeyB(pubKeyB);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        return encrypt(originalFile, cryptoDataByte);
    }

    public String encrypt(File originalFile, String pubKeyB, char[] priKeyA) {
        CryptoData cryptoData = new CryptoData();
        cryptoData.setType(EccAesType.AsyTwoWay);
        cryptoData.setAlg(EccAes256K1v2_No1_NrC7);
        cryptoData.setPubKeyB(pubKeyB);
        cryptoData.setPriKeyA(priKeyA);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        return encrypt(originalFile, cryptoDataByte);
    }

    private String encrypt(File originalFile, CryptoDataByte cryptoDataByte) {
        byte[] msgBytes;
        try (FileInputStream fis = new FileInputStream(originalFile)) {
            msgBytes = fis.readAllBytes();
        } catch (IOException e) {
            return "FileInputStream wrong.";
        }

        if (msgBytes != null && msgBytes.length != 0)
            cryptoDataByte.setData(msgBytes);

        EccAes256K1P7 ecc = new EccAes256K1P7();
        ecc.encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();

        if (cryptoDataByte.getMessage() != null) {
            return cryptoDataByte.getMessage();
        } else {
            String parentPath = originalFile.getParent();
            String originalFileName = originalFile.getName();
            int endIndex = originalFileName.lastIndexOf('.');
            String suffix = "_" + originalFileName.substring(endIndex + 1);
            String encryptedFileName = originalFileName.substring(0, endIndex) + suffix + Constants.DOT_FV;
            File encryptedFile = FileTools.getNewFile(parentPath, encryptedFileName, FileTools.CreateNewFileMode.REWRITE);
            if (encryptedFile == null) return "Error:Creating encrypted file wrong.";

            try (FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                byte[] cipherBytes;
                cipherBytes = cryptoDataByte.getCipher();
                cryptoDataByte.setCipher(null);

                Affair affair = new Affair();
                affair.setOp(Op.encrypt);

                if (cryptoDataByte.getType() == EccAesType.AsyOneWay || cryptoDataByte.getType() == EccAesType.AsyTwoWay)
                    affair.setFid(KeyTools.pubKeyToFchAddr(cryptoDataByte.getPubKeyB()));
                affair.setOid(Hash.Sha256x2(originalFile));
                CryptoData cryptoData = CryptoData.fromCryptoDataByte(cryptoDataByte);
                affair.setData(cryptoData);
                fos.write(new Gson().toJson(affair).getBytes());
                fos.write(cipherBytes);
            } catch (IOException e) {
                return "Error: Writing encrypted file wrong.";
            }
        }
        return "Done.";
    }

    public void decrypt(CryptoData cryptoData) {
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        decrypt(cryptoDataByte);
        CryptoData cryptoData1 = CryptoData.fromCryptoDataByte(cryptoDataByte);
        copyEccAesData(cryptoData1, cryptoData);
    }
    public String decrypt(String eccAesDataJson, char[] key){
        return decrypt(eccAesDataJson,key,null);
    }
    public String decrypt(String eccAesDataJson, char[] key, @Nullable String pubKeyA) {
        Gson gson = new Gson();
        CryptoData cryptoData = gson.fromJson(eccAesDataJson, CryptoData.class);
        switch (cryptoData.getType()) {
            case AsyOneWay -> cryptoData.setPriKeyB(key);
            case AsyTwoWay -> {
                cryptoData.setPriKeyB(key);
                cryptoData.setPubKeyA(pubKeyA);
            }
            case SymKey -> cryptoData.setSymKey(key);
            case Password -> cryptoData.setPassword(key);
            default -> cryptoData.setMessage("Wrong EccAesType type" + cryptoData.getType());
        }
        if (cryptoData.getMessage() != null) {
            return "Error:" + cryptoData.getMessage();
        }
        decrypt(cryptoData);
        return cryptoData.getData();
    }

    public byte[] decryptForBytes(String eccAesDataJson, char[] key) {
        Gson gson = new Gson();
        CryptoData cryptoData = gson.fromJson(eccAesDataJson, CryptoData.class);

        switch (cryptoData.getType()) {
            case AsyOneWay -> cryptoData.setPriKeyB(key);
            case AsyTwoWay -> cryptoData.setPriKeyB(key);
            case SymKey -> cryptoData.setSymKey(key);
            case Password -> cryptoData.setPassword(key);
            default -> cryptoData.setMessage("Wrong EccAesType type" + cryptoData.getType());
        }
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        if (cryptoDataByte.getMessage() != null) {
            return null;
        }
        decrypt(cryptoDataByte);
        return cryptoDataByte.getData();
    }

    public CryptoDataByte decrypt(String eccAesDataJson, byte[] key) {
        Gson gson = new Gson();
        CryptoData cryptoData = gson.fromJson(eccAesDataJson, CryptoData.class);
        CryptoDataByte cryptoDataBytes = CryptoDataByte.fromCryptoData(cryptoData);
        switch (cryptoDataBytes.getType()) {
            case AsyOneWay, AsyTwoWay -> cryptoDataBytes.setPriKeyB(key);
            case SymKey -> cryptoDataBytes.setSymKey(key);
            case Password -> cryptoDataBytes.setPassword(key);
            default -> cryptoDataBytes.setMessage("Wrong EccAesType type" + cryptoDataBytes.getType());
        }
        if (cryptoDataBytes.getMessage() != null) {
            return cryptoDataBytes;
        }
        decrypt(cryptoDataBytes);
        return cryptoDataBytes;
    }

    private String decrypt(File encryptedFile, CryptoDataByte cryptoDataByte) {

        EccAes256K1P7 ecc = new EccAes256K1P7();
        ecc.decrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();

        if (cryptoDataByte.getMessage() != null) {
            return cryptoDataByte.getMessage();
        } else {
            String parentPath = encryptedFile.getParent();
            String encryptedFileName = encryptedFile.getName();
            int endIndex1 = encryptedFileName.lastIndexOf('_');
            int endIndex2 = encryptedFileName.lastIndexOf('.');
            String oldSuffix;
            String originalFileName;
            if(endIndex1!=-1) {
                oldSuffix = encryptedFileName.substring(endIndex1 + 1, endIndex2);
                originalFileName = encryptedFileName.substring(0, endIndex1) + "." + oldSuffix;
            }else originalFileName = encryptedFileName+ Constants.DOT_DECRYPTED;

            File originalFile = FileTools.getNewFile(parentPath, originalFileName, FileTools.CreateNewFileMode.REWRITE);
            if (originalFile == null) return "Create recovered file failed.";
            try (FileOutputStream fos = new FileOutputStream(originalFile)) {
                fos.write(cryptoDataByte.getData());
                return "Done";
            } catch (IOException e) {
                return "Write file wrong";
            }
        }
    }
    @Test
    public void test(){
        String oFileName1 = "a.png";
        String oFileName2 = "/user/t/a.png";

        String eFileName1 = getEncryptedFileName(oFileName1);
        String eFileName2 = getEncryptedFileName(oFileName2);
        System.out.println(eFileName1);
        System.out.println(eFileName2);
        System.out.println(getDecryptedFileName(eFileName1));
        System.out.println(getDecryptedFileName(eFileName2));

        String eFileName3 = "uwhkfdksau4232l3jl";
        String originalFileName;
        int endIndex1 = eFileName3.lastIndexOf('_');
        int endIndex2 = eFileName3.lastIndexOf('.');
        if(endIndex1!=-1) {
            String oldSuffix = eFileName3.substring(endIndex1 + 1, endIndex2);
            originalFileName = eFileName3.substring(0, endIndex1) + "." + oldSuffix;
        }else originalFileName = eFileName3+ Constants.DOT_DECRYPTED;
        System.out.println(originalFileName);

    }
    public static String getEncryptedFileName(String originalFileFullName){
        File file = new File(originalFileFullName);
        String parentPath = file.getParent();
        String originalFileName = file.getName();
        int endIndex = originalFileName.lastIndexOf('.');
        String suffix = "_" + originalFileName.substring(endIndex + 1);
        String encryptedFileName = originalFileName.substring(0, endIndex) + suffix + Constants.DOT_FV;
        if(parentPath==null)return encryptedFileName;
        else return parentPath+"/"+encryptedFileName;
    }
    public static String getDecryptedFileName(String encryptedFileFullName){
        File encryptedFile=new File(encryptedFileFullName);
        String parentPath = encryptedFile.getParent();
        String encryptedFileName = encryptedFile.getName();
        int endIndex1 = encryptedFileName.lastIndexOf('_');
        int endIndex2 = encryptedFileName.lastIndexOf('.');
        String oldSuffix;
        String originalFileName;
        if(endIndex1!=-1) {
            oldSuffix = encryptedFileName.substring(endIndex1 + 1, endIndex2);
            originalFileName = encryptedFileName.substring(0, endIndex1) + "." + oldSuffix;
        }else originalFileName = encryptedFileName + Constants.DOT_DECRYPTED;
        if(parentPath==null)return originalFileName;
        else return parentPath+"/"+originalFileName;
    }

    public String decrypt(File encryptedFile, byte[] priKeyBBytes) {
        byte[] cipherBytes;
        Affair affair;
        Gson gson = new Gson();
        CryptoData cryptoData;
        CryptoDataByte cryptoDataByte;
        try (FileInputStream fis = new FileInputStream(encryptedFile)) {
            affair = JsonTools.readObjectFromJsonFile(fis, Affair.class);
            cipherBytes = fis.readAllBytes();
            if (affair == null) return "Affair is null.";
            if (affair.getData() == null) return "Affair.data is null.";
            cryptoData = gson.fromJson(gson.toJson(affair.getData()), CryptoData.class);
            if (cryptoData == null) return "Got eccAesData null.";
        } catch (IOException e) {
            return "Reading file wrong.";
        }
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        cryptoDataByte.setPriKeyB(priKeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);
        return decrypt(encryptedFile, cryptoDataByte);
    }

    public String decrypt(File encryptedFile, char[] symKey) {
        byte[] cipherBytes;
        Affair affair;
        Gson gson = new Gson();
        CryptoData cryptoData;
        CryptoDataByte cryptoDataByte;
        try (FileInputStream fis = new FileInputStream(encryptedFile)) {
            affair = JsonTools.readObjectFromJsonFile(fis, Affair.class);
            cipherBytes = fis.readAllBytes();
            if (affair == null) return "Error:affair is null.";
            if (affair.getData() == null) return "Error:affair.data is null.";
            cryptoData = gson.fromJson(gson.toJson(affair.getData()), CryptoData.class);
            if (cryptoData == null) return "Got eccAesData null.";
        } catch (IOException e) {
            return "Read file wrong.";
        }
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);
        cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(symKey));
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        cryptoDataByte.setCipher(cipherBytes);
        return decrypt(encryptedFile, cryptoDataByte);
    }

    private void decryptAsy(CryptoDataByte cryptoDataByte) {
        if (!isGoodDecryptParams(cryptoDataByte)) {
            return;
        }

        if (cryptoDataByte.getPubKeyB() == null && cryptoDataByte.getPubKeyA() == null) {
            cryptoDataByte.setMessage("No any public key found.");
            return;
        }

        byte[] priKeyBytes = new byte[0];
        byte[] pubKeyBytes = new byte[0];

        Result result = checkPriKeyAndPubKey(cryptoDataByte, priKeyBytes, pubKeyBytes);
        if (result == null) return;

        byte[] symKey =
                asyKeyToSymKey(result.priKeyBytes(), result.pubKeyBytes(),cryptoDataByte);
        if(symKey==null){
            cryptoDataByte.setMessage("Failed to make symKey from the priKey and the pubKey of another party.");
            return;
        }
        cryptoDataByte.setSymKey(symKey);

        if (cryptoDataByte.getSum() != null) {
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setBadSum(true);
                cryptoDataByte.setMessage("Bad sum which is from sha256(symKey+vi+cipher).");
                return;
            }
        }

        byte[] msgBytes = new byte[0];
        try {
            msgBytes = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), symKey, cryptoDataByte.getIv());
        } catch (NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException |
                 NoSuchAlgorithmException | NoSuchProviderException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt message wrong: " + e.getMessage());
        }
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setSymKey(symKey);

        cryptoDataByte.clearAllSensitiveDataButSymKey();
    }

    @org.jetbrains.annotations.Nullable
    private static Result checkPriKeyAndPubKey(CryptoDataByte cryptoDataByte, byte[] priKeyBytes, byte[] pubKeyBytes) {
        if (cryptoDataByte.getType() == EccAesType.AsyOneWay) {
            priKeyBytes = cryptoDataByte.getPriKeyB();
            if (priKeyBytes == null || BytesTools.isFilledKey(priKeyBytes)) {
                cryptoDataByte.setMessage("The private key is null or filled with 0.");
                return null;
            }
            pubKeyBytes = cryptoDataByte.getPubKeyA();
        } else if (cryptoDataByte.getType() == EccAesType.AsyTwoWay) {
            boolean found = false;
            if (cryptoDataByte.getPriKeyB() != null && !BytesTools.isFilledKey(cryptoDataByte.getPriKeyB())) {
                if (cryptoDataByte.getPubKeyA() != null) {
                    if (isTheKeyPair(cryptoDataByte.getPubKeyA(), cryptoDataByte.getPriKeyB())) {
                        if (isTheKeyPair(cryptoDataByte.getPubKeyB(), cryptoDataByte.getPriKeyB())) {
                            found = false;
                        } else {
                            found = true;
                            priKeyBytes = cryptoDataByte.getPriKeyB();
                            pubKeyBytes = cryptoDataByte.getPubKeyB();
                        }
                    } else {
                        found = true;
                        priKeyBytes = cryptoDataByte.getPriKeyB();
                        pubKeyBytes = cryptoDataByte.getPubKeyA();
                    }
                }
            } else if (cryptoDataByte.getPubKeyA() != null && !BytesTools.isFilledKey(cryptoDataByte.getPriKeyA())) {
                if (isTheKeyPair(cryptoDataByte.getPubKeyA(), cryptoDataByte.getPriKeyA())) {
                    if (isTheKeyPair(cryptoDataByte.getPubKeyB(), cryptoDataByte.getPriKeyA())) {
                        found = false;
                    } else {
                        found = true;
                        priKeyBytes = cryptoDataByte.getPriKeyA();
                        pubKeyBytes = cryptoDataByte.getPubKeyB();
                    }
                } else {
                    found = true;
                    priKeyBytes = cryptoDataByte.getPriKeyA();
                    pubKeyBytes = cryptoDataByte.getPubKeyA();
                }
            }

            if (!found) {
                cryptoDataByte.setMessage("Private key or public key absent, or the private key and the public key is a pair.");
                return null;
            }
        } else {
            cryptoDataByte.setMessage("Wrong type:" + cryptoDataByte.getType());
            return null;
        }
        return new Result(priKeyBytes, pubKeyBytes);
    }

    private record Result(byte[] priKeyBytes, byte[] pubKeyBytes) {
    }

    private byte[] asyKeyToSymKey(byte[] priKeyBytes, byte[] pubKeyBytes, CryptoDataByte cryptoDataByte) {
        byte[] symKey;
        MessageDigest sha256 = null;
        MessageDigest sha512 = null;
        try {
            switch (cryptoDataByte.getAlg()){
                case EccAes256K1v2_No1_NrC7 ->sha512 = MessageDigest.getInstance("SHA-512");
                default -> sha256 = MessageDigest.getInstance("SHA-256");
            }
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setMessage("Get sha256 digester failed.");
            return priKeyBytes;
        }

        byte[] sharedSecret = getSharedSecret(priKeyBytes, pubKeyBytes);

        byte[] sharedSecretHash;


        switch (cryptoDataByte.getAlg()){
            case EccAes256K1v2_No1_NrC7 ->{
                byte[] secretHashWithIv = BytesTools.addByteArray(sharedSecret, cryptoDataByte.getIv());
                byte[] hash = sha512.digest(sha512.digest(secretHashWithIv));
                symKey = new byte[32];
                System.arraycopy(hash,0,symKey,0,32);
            }
            default -> {
                sharedSecretHash = sha256.digest(sharedSecret);
                byte[] secretHashWithIv = BytesTools.addByteArray(sharedSecretHash, cryptoDataByte.getIv());
                symKey = sha256.digest(sha256.digest(secretHashWithIv));
            }
        }
        clearByteArray(sharedSecret);
        return symKey;
    }

    private void decryptWithPassword(CryptoDataByte cryptoDataByte) {
        if (!isGoodPasswordDecryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] symKeyBytes = makeSymKeyFromPassword(cryptoDataByte, sha256, cryptoDataByte.getIv());
            cryptoDataByte.setSymKey(symKeyBytes);
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setBadSum(true);
                cryptoDataByte.setMessage("Bad sum which is from sha256(symKey+vi+cipher).");
                return;
            }
            byte[] msg = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), cryptoDataByte.getSymKey(), cryptoDataByte.getIv());
            cryptoDataByte.setData(msg);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt with password wrong: " + e.getMessage());
        }
    }

    public String encryptAsyOneWayBundle(String msg, String pubKeyB) {
        CryptoData cryptoData = new CryptoData(EccAesType.AsyOneWay, msg, pubKeyB);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        byte[] bundleBytes = BytesTools.addByteArray(BytesTools.addByteArray(cryptoDataByte.getPubKeyA(), cryptoDataByte.getIv()), cryptoDataByte.getCipher());
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        return Base64.getEncoder().encodeToString(bundleBytes);
    }

    public String decryptAsyOneWayBundle(String bundle, char[] priKeyB) {
        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        byte[] pubKeyABytes = new byte[33];
        byte[] ivBytes = new byte[16];
        byte[] cipherBytes = new byte[bundleBytes.length - pubKeyABytes.length - ivBytes.length];
        byte[] priKeyBBytes = BytesTools.hexCharArrayToByteArray(priKeyB);

        pubKeyABytes = Arrays.copyOfRange(bundleBytes, 0, 33);
        ivBytes = Arrays.copyOfRange(bundleBytes, 33, 49);
        cipherBytes = Arrays.copyOfRange(bundleBytes, 33 + 16, bundleBytes.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyOneWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubKeyA(pubKeyABytes);
        cryptoDataByte.setPriKeyB(priKeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public String encryptAsyTwoWayBundle(String msg, String pubKeyB, char[] priKeyA) {
        CryptoData cryptoData = new CryptoData(EccAesType.AsyTwoWay, msg, pubKeyB, priKeyA);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoData);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        byte[] bundleBytes = BytesTools.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        return Base64.getEncoder().encodeToString(bundleBytes);
    }

    public String decryptAsyTwoWayBundle(String bundle, String pubKeyA, char[] priKeyB) {
        byte[] priKeyBBytes = BytesTools.hexCharArrayToByteArray(priKeyB);
        byte[] pubKeyABytes = HexFormat.of().parseHex(pubKeyA);

        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        byte[] ivBytes = Arrays.copyOfRange(bundleBytes, 0, 16);
        byte[] cipherBytes = Arrays.copyOfRange(bundleBytes, 16, bundleBytes.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyTwoWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubKeyA(pubKeyABytes);
        cryptoDataByte.setPriKeyB(priKeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public String encryptSymKeyBundle(String msg, char[] symKey) {
        CryptoData cryptoData = new CryptoData(EccAesType.SymKey, msg, symKey);

        encrypt(cryptoData);
        String iv = cryptoData.getIv();
        String cipher = cryptoData.getCipher();
        byte[] ivBytes = HexFormat.of().parseHex(iv);
        byte[] cipherBytes = Base64.getDecoder().decode(cipher);

        byte[] ivCipherBytes = BytesTools.addByteArray(ivBytes, cipherBytes);
        String bundle = Base64.getEncoder().encodeToString(ivCipherBytes);
        //eccAesData.clearAllSensitiveData();
        if (cryptoData.getMessage() != null) return "Error:" + cryptoData.getMessage();
        return bundle;
    }

    public String encryptPasswordBundle(String msg, char[] password) {
        CryptoData cryptoData = new CryptoData(EccAesType.Password, msg, password);

        encrypt(cryptoData);
        String iv = cryptoData.getIv();
        String cipher = cryptoData.getCipher();
        byte[] ivBytes = HexFormat.of().parseHex(iv);
        byte[] cipherBytes = Base64.getDecoder().decode(cipher);

        byte[] ivCipherBytes = BytesTools.addByteArray(ivBytes, cipherBytes);
        String bundle = Base64.getEncoder().encodeToString(ivCipherBytes);
        //eccAesData.clearAllSensitiveData();
        if (cryptoData.getMessage() != null) return "Error:" + cryptoData.getMessage();
        return bundle;
    }

    public String decryptSymKeyBundle(String bundle, char[] symKey) {
        CryptoDataByte cryptoDataByte = makeIvCipherToEccAesDataByte(Base64.getDecoder().decode(bundle));
        cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(symKey));
        decrypt(cryptoDataByte);

        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();

        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public byte[] encryptAsyOneWayBundle(byte[] msg, byte[] pubKeyB) {

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyOneWay);
        cryptoDataByte.setData(msg);
        cryptoDataByte.setPubKeyB(pubKeyB);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) return null;
        return BytesTools.addByteArray(BytesTools.addByteArray(cryptoDataByte.getPubKeyA(), cryptoDataByte.getIv()), cryptoDataByte.getCipher());
    }

    public byte[] decryptAsyOneWayBundle(byte[] bundle, byte[] priKeyB) {

        byte[] pubKeyA = Arrays.copyOfRange(bundle, 0, 33);
        byte[] ivBytes = Arrays.copyOfRange(bundle, 33, 49);
        byte[] cipherBytes = Arrays.copyOfRange(bundle, 33 + 16, bundle.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyOneWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubKeyA(pubKeyA);
        cryptoDataByte.setPriKeyB(priKeyB);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptAsyTwoWayBundle(byte[] msg, byte[] pubKeyB, byte[] priKeyA) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyTwoWay);
        cryptoDataByte.setData(msg);
        cryptoDataByte.setPubKeyB(pubKeyB);
        cryptoDataByte.setPriKeyA(priKeyA);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) return null;
        return BytesTools.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptAsyTwoWayBundle(byte[] bundle, byte[] pubKeyA, byte[] priKeyB) {

        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EccAesType.AsyTwoWay);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);
        cryptoDataByte.setPubKeyA(pubKeyA);
        cryptoDataByte.setPriKeyB(priKeyB);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymKey();

        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptSymKeyBundle(byte[] msg, byte[] symKey) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        cryptoDataByte.setType(EccAesType.SymKey);
        cryptoDataByte.setSymKey(symKey);
        cryptoDataByte.setData(msg);

        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return BytesTools.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptSymKeyBundle(byte[] bundle, byte[] symKey) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);
        cryptoDataByte.setType(EccAesType.SymKey);
        cryptoDataByte.setSymKey(symKey);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);

        decrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptPasswordBundle(byte[] msg, byte[] password) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        cryptoDataByte.setType(EccAesType.Password);
        cryptoDataByte.setPassword(password);
        cryptoDataByte.setData(msg);

        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return BytesTools.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptPasswordBundle(byte[] bundle, byte[] password) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);
        cryptoDataByte.setType(EccAesType.Password);
        cryptoDataByte.setPassword(password);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);

        decrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    private void encryptAsy(CryptoDataByte cryptoDataByte) {
        if (!isGoodEncryptParams(cryptoDataByte)) {
            return;
        }

        // Generate IV
        byte[] iv = getRandomIv();
        cryptoDataByte.setIv(iv);

        //Make sharedSecret
        byte[] priKey;
        byte[] pubKey = cryptoDataByte.getPubKeyB();

        byte[] priKeyABytes = cryptoDataByte.getPriKeyA();
        if (priKeyABytes != null) {
            priKey=priKeyABytes;
        } else {
            priKey = makeRandomKeyPair(cryptoDataByte);
        }

        byte[] symKey =
                asyKeyToSymKey(priKey, pubKey, cryptoDataByte);
        if(symKey==null){
            cryptoDataByte.setMessage("Failed to make symKey from the priKey and the pubKey of another party.");
            return;
        }

        cryptoDataByte.setSymKey(symKey);

        // Encrypt the original AES key with the shared secret key
        aesEncrypt(cryptoDataByte);
    }

    private byte[] makeRandomKeyPair(CryptoDataByte cryptoDataByte) {
        byte[] priKey;
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH(), spec.getSeed());

        // Generate EC key pair for sender
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(domainParameters, new SecureRandom()));

        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

        ECPrivateKeyParameters newPriKey = (ECPrivateKeyParameters) keyPair.getPrivate();
        byte[] newPubKey = pubKeyToBytes(pubKeyFromPriKey(newPriKey));


        priKey = priKeyToBytes(newPriKey);
        cryptoDataByte.setPubKeyA(newPubKey);
        return priKey;
    }

    private void encryptWithPassword(CryptoDataByte cryptoDataByte) {

        cryptoDataByte.setType(EccAesType.Password);
        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }

        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setMessage("Create sha256 digester wrong:" + e.getMessage());
            return;
        }
        byte[] iv = getRandomIv();
        cryptoDataByte.setIv(iv);
        byte[] symKeyBytes = makeSymKeyFromPassword(cryptoDataByte, sha256, iv);
        cryptoDataByte.setSymKey(symKeyBytes);
        aesEncrypt(cryptoDataByte);
    }

    private void encryptWithSymKey(CryptoDataByte cryptoDataByte) {

        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        cryptoDataByte.setType(EccAesType.SymKey);
        isGoodEncryptParams(cryptoDataByte);
        cryptoDataByte.setIv(getRandomIv());
        aesEncrypt(cryptoDataByte);
        cryptoDataByte.clearAllSensitiveDataButSymKey();
    }

    private void decryptWithSymKey(CryptoDataByte cryptoDataByte) {
        if (!isGoodSymKeyDecryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        try {
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setBadSum(true);
                cryptoDataByte.setMessage("Bad sum which is from sha256(symKey+vi+cipher).");
                return;
            }
            byte[] msg = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), cryptoDataByte.getSymKey(), cryptoDataByte.getIv());
            cryptoDataByte.setData(msg);
            cryptoDataByte.clearAllSensitiveDataButSymKey();
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt with symKey wrong: " + e.getMessage());
        }
    }

    private void aesEncrypt(CryptoDataByte cryptoDataByte) {

        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }

        byte[] iv = cryptoDataByte.getIv();
        byte[] msgBytes = cryptoDataByte.getData();
        byte[] symKeyBytes = cryptoDataByte.getSymKey();
        MessageDigest sha256;
        byte[] cipher;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
            cipher = Aes256CbcP7.encrypt(msgBytes, symKeyBytes, iv);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            cryptoDataByte.setMessage("Aes encrypting wrong: " + e.getMessage());
            return;
        }
        byte[] sum4 = getSum4(sha256, symKeyBytes, iv, cipher);
        cryptoDataByte.setCipher(cipher);
        cryptoDataByte.setSum(sum4);
        cryptoDataByte.setData(null);
        cryptoDataByte.clearAllSensitiveDataButSymKey();
    }
//
//    private boolean aesEncryptFile(String sourceFilePathName,String destFilePathName,byte[] key) {
//
//        EccAesDataByte eccAesDataByte = new EccAesDataByte();
//        eccAesDataByte.setType(EccAesType.SymKey);
//        eccAesDataByte.setAlg(Algorithm.EccAes256K1P7_No1_NrC7.name());
//        byte[] iv = BytesTools.getRandomBytes(16);
//        eccAesDataByte.setIv(iv);
//
//        String tempFilePath = FileTools.getTempFileName();
//        try {
//            Aes256CbcP7.encryptFile(sourceFilePathName,tempFilePath,key);
//        } catch (Exception e) {
//            log.debug("Failed to encrypt file:"+e.getMessage());
//            return false;
//        }
//        byte[] cipherDid = Hash.Sha256x2Bytes(new File(destFilePathName));
//        byte[] sum = getSum4(key, iv, cipherDid);
//        eccAesDataByte.setSum(sum);
//
//        FileTools.createFileWithDirectories(destFilePathName);
//        FileOutputStream fos = new FileOutputStream(destFilePathName);
//        FileInputStream fis = new FileInputStream(tempFilePath);
//        EccAesData eccAesData = EccAesData.fromEccAesDataByte(eccAesDataByte);
//        byte[] headBytes = JsonTools.getString(eccAesData).getBytes();
//        fos.write(headBytes);
//
//        byte[] buffer = new byte[8192];
//        int bytesRead;
//        long bytesLength = 0;
//        while ((bytesRead = fis.read(buffer)) != -1) {
//            // Write the bytes read from the request input stream to the output stream
//            fos.write(buffer, 0, bytesRead);
//            hasher.putBytes(buffer, 0, bytesRead);
//            bytesLength +=bytesRead;
//        }
//
//
//        byte[] msgBytes = eccAesDataByte.getMsg();
//        byte[] symKeyBytes = eccAesDataByte.getSymKey();
//        MessageDigest sha256;
//        byte[] cipher;
//        try {
//            sha256 = MessageDigest.getInstance("SHA-256");
//            cipher = Aes256CbcP7.encrypt(msgBytes, symKeyBytes, iv);
//        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException |
//                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
//            eccAesDataByte.setError("Aes encrypting wrong: " + e.getMessage());
//            return;
//        }
//        byte[] sum4 = getSum4(sha256, symKeyBytes, iv, cipher);
//        eccAesDataByte.setCipher(cipher);
//        eccAesDataByte.setSum(sum4);
//        eccAesDataByte.setMsg(null);
//        eccAesDataByte.clearAllSensitiveDataButSymKey();
//    }

    private byte[] makeSymKeyFromPassword(CryptoDataByte cryptoDataByte, MessageDigest sha256, byte[] iv) {
        byte[] symKeyBytes = sha256.digest(BytesTools.addByteArray(sha256.digest(cryptoDataByte.getPassword()), iv));
        return symKeyBytes;
    }

    private byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {

        ECPrivateKeyParameters priKey = priKeyFromBytes(priKeyBytes);
        ECPublicKeyParameters pubKey = pubKeyFromBytes(pubKeyBytes);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(priKey);
        return agreement.calculateAgreement(pubKey).toByteArray();
    }

    public ECPublicKeyParameters pubKeyFromBytes(byte[] publicKeyBytes) {

        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECCurve curve = domainParameters.getCurve();

        ECPoint point = curve.decodePoint(publicKeyBytes);

        return new ECPublicKeyParameters(point, domainParameters);
    }

    public ECPublicKeyParameters pubKeyFromHex(String publicKeyHex) {
        return pubKeyFromBytes(HexFormat.of().parseHex(publicKeyHex));
    }

    public String pubKeyToHex(ECPublicKeyParameters publicKey) {
        return Hex.toHexString(pubKeyToBytes(publicKey));
    }

    public String priKeyToHex(ECPrivateKeyParameters privateKey) {
        BigInteger privateKeyValue = privateKey.getD();
        String hex = privateKeyValue.toString(16);
        while (hex.length() < 64) {  // 64 is for 256-bit key
            hex = "0" + hex;
        }
        return hex;
    }

    public byte[] priKeyToBytes(ECPrivateKeyParameters privateKey) {
        return HexFormat.of().parseHex(priKeyToHex(privateKey));//Hex.decode(priKeyToHex(privateKey));
    }

    public byte[] getPartOfBytes(byte[] original, int offset, int length) {
        byte[] part = new byte[length];
        System.arraycopy(original, offset, part, 0, part.length);
        return part;
    }

    private boolean isErrorAlgAndType(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getMessage() != null) {
            cryptoDataByte.setMessage("There was an error. Check it at first:" + cryptoDataByte.getMessage() + " .");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }

        if (cryptoDataByte.getAlg() == null) {
            cryptoDataByte.setAlg(EccAes256K1P7_No1_NrC7);
        } else if (!cryptoDataByte.getAlg().equals(EccAes256K1P7_No1_NrC7)) {
            cryptoDataByte.setMessage("This method only used by the algorithm " + EccAes256K1P7_No1_NrC7+ " .");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }

        if (cryptoDataByte.getType() == null) {
            cryptoDataByte.setMessage("EccAesType is required.");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }
        return false;
    }

    private boolean isGoodEncryptParams(CryptoDataByte cryptoDataByte) {
        EccAesType type = cryptoDataByte.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayEncryptParams(cryptoDataByte);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayEncryptParams(cryptoDataByte);
            }
            case SymKey -> {
                return isGoodSymKeyEncryptParams(cryptoDataByte);
            }
            case Password -> {
                return isGoodPasswordEncryptParams(cryptoDataByte);
            }
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
        return true;
    }

    private boolean isGoodEncryptParams(CryptoData cryptoData) {
        EccAesType type = cryptoData.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayEncryptParams(cryptoData);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayEncryptParams(cryptoData);
            }
            case SymKey -> {
                return isGoodSymKeyEncryptParams(cryptoData);
            }
            case Password -> {
                return isGoodPasswordEncryptParams(cryptoData);
            }
            default -> cryptoData.setMessage("Wrong type: " + cryptoData.getType());
        }
        return true;
    }

    private boolean isGoodPasswordEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EccAesType.Password.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPassword() == null) {
            cryptoDataByte.setMessage(EccAesType.Password.name() + " parameters lack password.");
            return false;
        }

        return true;
    }

    private boolean isGoodSymKeyEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getSymKey() == null) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameters lack symKey.");
            return false;
        }

        if (cryptoDataByte.getSymKey().length != Constants.SYM_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameter symKey should be " + Constants.SYM_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack pubKeyB.");
            return false;
        }

        if (cryptoDataByte.getPriKeyA() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack priKeyA.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameter pubKeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubKeyB().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameter priKeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPriKeyA().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayEncryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameters lack pubKeyB.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameter symKey should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodPasswordEncryptParams(CryptoData cryptoData) {

        if (cryptoData.getData() == null) {
            cryptoData.setMessage(EccAesType.Password.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoData.getPassword() == null) {
            cryptoData.setMessage(EccAesType.Password.name() + " parameters lack password.");
            return false;
        }

        return true;
    }

    private boolean isGoodSymKeyEncryptParams(CryptoData cryptoData) {

        if (cryptoData.getData() == null) {
            cryptoData.setMessage(EccAesType.SymKey.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoData.getSymKey() == null) {
            cryptoData.setMessage(EccAesType.SymKey.name() + " parameters lack symKey.");
            return false;
        }

        if (cryptoData.getSymKey().length != Constants.SYM_KEY_BYTES_LENGTH * 2) {
            cryptoData.setMessage(EccAesType.SymKey.name() + " parameter symKey should be " + Constants.SYM_KEY_BYTES_LENGTH * 2 + " characters. It is " + cryptoData.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayEncryptParams(CryptoData cryptoData) {

        if (cryptoData.getData() == null) {
            cryptoData.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoData.getPubKeyB() == null) {
            cryptoData.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack pubKeyB.");
            return false;
        }

        if (cryptoData.getPriKeyA() == null) {
            cryptoData.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack priKeyA.");
            return false;
        }

        if (cryptoData.getPubKeyB().length() != Constants.PUBLIC_KEY_BYTES_LENGTH * 2) {
            cryptoData.setMessage(EccAesType.AsyTwoWay.name() + " parameter pubKeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH * 2 + " characters. It is " + cryptoData.getPubKeyB().length() + " now.");
            return false;
        }

        if (cryptoData.getPriKeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH * 2) {
            cryptoData.setMessage(EccAesType.AsyTwoWay.name() + " parameter priKeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " characters. It is " + cryptoData.getPriKeyA().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayEncryptParams(CryptoData cryptoData) {
        if (cryptoData.getData() == null) {
            cryptoData.setMessage(EccAesType.AsyOneWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoData.getPubKeyB() == null) {
            cryptoData.setMessage(EccAesType.AsyOneWay.name() + " parameters lack pubKeyB.");
            return false;
        }

        if (cryptoData.getPubKeyB().length() != Constants.PUBLIC_KEY_BYTES_LENGTH * 2) {
            cryptoData.setMessage(EccAesType.AsyOneWay.name() + " parameter symKey should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " characters. It is " + cryptoData.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAesSum(CryptoDataByte cryptoDataByte) {

        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] sum4 = getSum4(sha256, cryptoDataByte.getSymKey(), cryptoDataByte.getIv(), cryptoDataByte.getCipher());
        if (!Arrays.equals(sum4, cryptoDataByte.getSum())) {
            cryptoDataByte.setMessage("The sum  is not equal to the value of sha256(symKey+iv+cipher).");
            return false;
        }
        return true;
    }

    private boolean isGoodDecryptParams(CryptoDataByte cryptoDataByte) {
        EccAesType type = cryptoDataByte.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayDecryptParams(cryptoDataByte);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayDecryptParams(cryptoDataByte);
            }
            case SymKey -> {
                return isGoodSymKeyDecryptParams(cryptoDataByte);
            }
            case Password -> {
                return isGoodPasswordDecryptParams(cryptoDataByte);
            }
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
        return true;
    }

    private boolean isGoodPasswordDecryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPassword() == null || isZero(cryptoDataByte.getPassword())) {
            cryptoDataByte.setMessage(EccAesType.Password.name() + " parameters lack password.");
            return false;
        }

        if (cryptoDataByte.getIv().length != Constants.IV_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameter iv should be " + Constants.IV_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getIv().length + " now.");
            return false;
        }
        return true;
    }

    private boolean isGoodSymKeyDecryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getSymKey() == null || isZero(cryptoDataByte.getSymKey())) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameters lack symKey.");
            return false;
        }

        if (cryptoDataByte.getIv().length != Constants.IV_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameter iv should be " + Constants.IV_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getIv().length + " now.");
            return false;
        }

        if (cryptoDataByte.getSymKey() != null && cryptoDataByte.getSymKey().length != Constants.SYM_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.SymKey.name() + " parameter symKey should be " + Constants.SYM_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymKey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayDecryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPubKeyA() == null || cryptoDataByte.getPubKeyA() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack pubKeyA and pubKeyB.");
            return false;
        }

        if ((cryptoDataByte.getPriKeyB() == null || isZero(cryptoDataByte.getPriKeyB()))
                && (cryptoDataByte.getPriKeyA() == null || isZero(cryptoDataByte.getPriKeyA()))) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameters lack both priKeyA and priKeyB.");
            return false;
        }

        if (cryptoDataByte.getPubKeyA() != null && cryptoDataByte.getPubKeyA().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameter pubKeyA should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubKeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPubKeyB() != null && cryptoDataByte.getPubKeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameter pubKeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubKeyB().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyA() != null && cryptoDataByte.getPriKeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameter priKeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPriKeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyB() != null && cryptoDataByte.getPriKeyB().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameter priKeyB should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPriKeyB().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayDecryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPubKeyA() == null) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameters lack pubKeyA.");
            return false;
        }

        if (cryptoDataByte.getPriKeyB() == null || isZero(cryptoDataByte.getPriKeyB())) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameters lack priKeyB.");
            return false;
        }

        if (cryptoDataByte.getPubKeyA().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameter pubKeyA should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubKeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyB().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyOneWay.name() + " parameter priKeyB should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPriKeyB().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }

    private byte[] getSum4(MessageDigest sha256, byte[] symKey, byte[] iv, byte[] cipher) {
        byte[] sum32 = sha256.digest(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, cipher)));
        return getPartOfBytes(sum32, 0, 4);
    }

    private byte[] getSum4(byte[] symKey, byte[] iv, byte[] cipher) {
        byte[] sum32 = Hash.Sha256(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, cipher)));
        return getPartOfBytes(sum32, 0, 4);
    }

    public void copyEccAesData(CryptoData fromCryptoData, CryptoData toCryptoData) {
        toCryptoData.setType(fromCryptoData.getType());
        toCryptoData.setAlg(fromCryptoData.getAlg());
        toCryptoData.setData(fromCryptoData.getData());
        toCryptoData.setCipher(fromCryptoData.getCipher());
        toCryptoData.setSymKey(fromCryptoData.getSymKey());
        toCryptoData.setPassword(fromCryptoData.getPassword());
        toCryptoData.setPubKeyA(fromCryptoData.getPubKeyA());
        toCryptoData.setPubKeyB(fromCryptoData.getPubKeyB());
        toCryptoData.setPriKeyA(fromCryptoData.getPriKeyA());
        toCryptoData.setPriKeyB(fromCryptoData.getPriKeyB());
        toCryptoData.setIv(fromCryptoData.getIv());
        toCryptoData.setSum(fromCryptoData.getSum());
        toCryptoData.setMessage(fromCryptoData.getMessage());
    }

    public void clearByteArray(byte[] array) {
        Arrays.fill(array, (byte) 0);
    }

}
