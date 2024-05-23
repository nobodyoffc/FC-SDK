package crypto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import crypto.Algorithm.AesCbc256;
import javaTools.BytesTools;
import javaTools.Hex;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HexFormat;

import static fcData.AlgorithmType.FC_Aes256Cbc_No1_NrC7;
import static fcData.AlgorithmType.FC_EccK1AesCbc256_No1_NrC7;

public class Tester {

    @Test
    public void test() throws IOException {

        System.out.println("String did:"+Hex.toHex(DecryptorSym.sha256(DecryptorSym.sha256("hello world!".getBytes()))));
        System.out.println(Hash.sha256x2(new File("/Users/liuchangyong/Desktop/c.md")));
//        CryptoDataByte encryptor = new CryptoDataByte();
//        encryptor.setAlg(FC_EccK1AesCbc256_No1_NrC7);
//        encryptor.setDid("did:example:123".getBytes());


//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
//        String json = gson.toJson(encryptor);
//        System.out.println(encryptor.toJson());
//        CryptoDataByte cryptoDataByte = new CryptoDataByte();
//        cryptoDataByte.setDid(BytesTools.getRandomBytes(16));
//        cryptoDataByte.setAlg(FC_EccK1AesCbc256_No1_NrC7);
//
//        System.out.println(cryptoDataByte.toNiceJson());
//        System.out.println(JsonTools.getNiceString(cryptoDataByte));


//        String sourcePath = null;
//        String sourceFileName = "a.md";
//        String srcFile = Paths.get(sourcePath,sourceFileName).toString();
//        System.out.println(srcFile);
    }

    public class Encryptor {
        public AlgorithmType algo;
        public String did;
        public Date date;

        public enum AlgorithmType {
            FC_EccK1AesCbc256_No1_NrC7("FC/EccK1AesCbc256K1@No1_NrC7"),
            BTC_EcdsaSignMsg_No1_NrC7("BTC/EcdsaSignMsg@No1_NrC7"),
            FC_SchnorrSignTx_No1_NrC7("FC/SchnorrSignTx@No1_NrC7"),
            FC_Aes256Cbc_No1_NrC7("Aes256Cbc@No1_NrC7");

            private final String displayName;

            AlgorithmType(String displayName) {
                this.displayName = displayName;
            }

            @Override
            public String toString() {
                return this.displayName;
            }
        }

        public static void main(String[] args) {
            CryptoDataStr encryptor = new CryptoDataStr();
            encryptor.setAlg(FC_EccK1AesCbc256_No1_NrC7);
            encryptor.setDid("did:example:123");


            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(encryptor);
            System.out.println(json);
        }
    }


    @org.junit.jupiter.api.Test
    public void testSym() throws InterruptedException {
        System.out.println("\n# Info");
        byte[] key = BytesTools.getRandomBytes(32);
        String keyHex = HexFormat.of().formatHex(key);
        System.out.println("SymKey:"+Hex.toHex(key));
        String dataStr = "hello world!";
        byte[] data = dataStr.getBytes();
        System.out.println("Data:"+dataStr);
        System.out.println("DID:"+Hex.toHex(DecryptorSym.sha256(DecryptorSym.sha256(data))));

        String cipherJson;
        EncryptorSym encryptorSym = new EncryptorSym(FC_Aes256Cbc_No1_NrC7);
        DecryptorSym decryptorSym = new DecryptorSym();
        //Basic encrypt
        System.out.println("\n# Basic encrypt");
        CryptoDataByte cryptoDataByte;
        try(ByteArrayInputStream bis = new ByteArrayInputStream(data); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            long start = System.currentTimeMillis();
            byte[] iv = BytesTools.getRandomBytes(16);
            cryptoDataByte = encryptorSym.encryptStreamBySymKey(bis,bos,key, iv, null);
            System.out.println("Encrypt time:"+(System.currentTimeMillis()-start)+" milliSec");
            byte[] cipher = bos.toByteArray();
            cryptoDataByte.setCipher(cipher);
            CryptoDataStr cryptoDataStr1 = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
            cipherJson = cryptoDataStr1.toNiceJson();
            System.out.println(cipherJson);
            System.out.println("did:"+ cryptoDataStr1.getDid());
            System.out.println("cipherId:"+ cryptoDataStr1.getCipherId());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Basic decrypt
        System.out.println("\n# Basic decrypt");
        cryptoDataByte.setData(null);
        cryptoDataByte.setDid(null);
        try(ByteArrayInputStream bis = new ByteArrayInputStream(cryptoDataByte.getCipher());
            ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            long start = System.currentTimeMillis();
            AesCbc256.decryptStream(bis,bos,cryptoDataByte);
            System.out.println("Decrypt time:"+(System.currentTimeMillis()-start));
            byte[] decryptedData = bos.toByteArray();
            cryptoDataByte.setData(decryptedData);
            cryptoDataByte.makeDid();
            if(!cryptoDataByte.checkSum(cryptoDataByte.getDid()))
                System.out.println("Bad Sum.");
            else System.out.println("Good sum.");

            System.out.println("data:"+new String(decryptedData));
            cryptoDataByte.setData(decryptedData);
            CryptoDataStr cryptoDataStr = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
            System.out.println("cipherId:"+ cryptoDataStr.getCipherId());
            System.out.println("did:"+ cryptoDataStr.getDid());
            System.out.println("data:"+ cryptoDataStr.getData());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("\n# Json");
        //Json
        System.out.println("Encrypt, String to Json:"+ encryptorSym.encryptStrToJsonBySymKey(dataStr,keyHex));
        System.out.println("Decrypt json:"+ decryptorSym.decryptJsonBySymKey(cipherJson,keyHex));

        //Bundle
        System.out.println("\n# Bundle");
        byte[] bundle = encryptorSym.encryptToBundleBySymKey(data, key);
        System.out.println("Encrypt, bytes to Bundle:"+Hex.toHex(bundle));
        System.out.println("Encrypt, String to bundle:"+Hex.toHex(encryptorSym.encryptStrToBundleBySymKey(dataStr,keyHex)));
        System.out.println("Decrypt bundle:"+new String(decryptorSym.decryptBundleBySymKey(bundle,key,FC_Aes256Cbc_No1_NrC7)));

        //password
        System.out.println("\n# Password");
        String password = "password";
        String paCipher = encryptorSym.encryptStrByPassword(dataStr,password.toCharArray()).toNiceJson();
        System.out.println("cipher by password:"+paCipher);
        CryptoDataByte cryptoDataByte1 = decryptorSym.decryptJsonByPassword(paCipher,password.toCharArray());
        System.out.println("Decrypt by password:"+ new String(cryptoDataByte1.getData()));
        System.out.println(cryptoDataByte1.toNiceJson());

        //File
        System.out.println("\n# File");
        String fileDataPath = "/Users/liuchangyong/Desktop/a.md";
        String fileCipherPath = "/Users/liuchangyong/Desktop/f.cipher";
        CryptoDataByte cryptoDataByteFile = encryptorSym.encryptFileBySymKey(fileDataPath,fileCipherPath,key);
        System.out.println("File encrypted:"+cryptoDataByteFile.getMessage()+"\n"+cryptoDataByteFile.toNiceJson());
        cryptoDataByteFile.setSymKey(key);
        String fileNewDataPath = "/Users/liuchangyong/Desktop/c.md";
        CryptoDataByte cryptoDataByteFile1 = decryptorSym.decryptFileBySymKey(fileCipherPath, fileNewDataPath, key);
        System.out.println("File decrypted:"+cryptoDataByteFile1.getMessage()+"\n"+cryptoDataByteFile1.toNiceJson());
        CryptoDataByte cryptoDataByteFile2 = encryptorSym.encryptFileByPassword(fileDataPath,fileCipherPath,password.toCharArray());
        System.out.println("File encrypted by password:"+cryptoDataByteFile2.getMessage()+"\n"+cryptoDataByteFile2.toNiceJson());
        cryptoDataByteFile.setSymKey(key);
        String fileNewDataPath1 = "/Users/liuchangyong/Desktop/cPass.md";
        CryptoDataByte cryptoDataByteFile3 = decryptorSym.decryptFileByPassword(fileCipherPath, fileNewDataPath1, password.toCharArray());
        System.out.println("File decrypted by password:"+cryptoDataByteFile3.getMessage()+"\n"+cryptoDataByteFile3.toNiceJson());
    }

    @org.junit.jupiter.api.Test
    public void testAsy() throws IOException {
        //Info
        System.out.println("\n# Info");
        String dataStr = "hello world!";
        byte[] data = dataStr.getBytes();
        System.out.println("Data:" + dataStr);
        System.out.println("DID:" + Hex.toHex(Hash.sha256x2(data)));

        String priKeyAWif = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        String pubKeyAHex = "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a";
        String priKeyBWif = "L5DDxf3PkFwi1jArqYokpTsntthLvhDYg44FXyTSgdTx3XEFR1iB";
        String pubKeyBHex = "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67";

        byte[] priKeyA = KeyTools.getPriKey32(priKeyAWif);
        byte[] priKeyB = KeyTools.getPriKey32(priKeyBWif);
        byte[] pubKeyA = Hex.fromHex(pubKeyAHex);
        byte[] pubKeyB = Hex.fromHex(pubKeyBHex);

        byte[] symKey;
        String symKeyHex;
        EncryptorAsy encryptorAsy = new EncryptorAsy(FC_EccK1AesCbc256_No1_NrC7);
        DecryptorAsy decryptorAsy = new DecryptorAsy();
        //Basic test
        //By key
        System.out.println();
        System.out.println("# Encrypt");
        CryptoDataByte cryptoDataByte;
        try(ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            cryptoDataByte = encryptorAsy.encryptStreamByAsyTwoWay(bis, bos, priKeyA, pubKeyB);
            cryptoDataByte.setCipher(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Basic two way encrypt:" + cryptoDataByte.toNiceJson());
        System.out.println("symKey:" + Hex.toHex(cryptoDataByte.getSymKey()));

        CryptoDataByte cryptoDataByte9;
        try(ByteArrayInputStream bis9 = new ByteArrayInputStream(data);
        ByteArrayOutputStream bos9 = new ByteArrayOutputStream()) {
            cryptoDataByte9 = encryptorAsy.encryptStreamByAsyOneWay(bis9, bos9, pubKeyB);
            cryptoDataByte9.setCipher(bos9.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Basic one way encrypt:" + cryptoDataByte9.toNiceJson());
        System.out.println("symKey:" + Hex.toHex(cryptoDataByte9.getSymKey()));

        //By cryptoData
        CryptoDataByte cryptoDataByte0;
        try(ByteArrayInputStream bis0 = new ByteArrayInputStream(data);
        ByteArrayOutputStream bos0 = new ByteArrayOutputStream()) {
            cryptoDataByte0 = new CryptoDataByte();
            cryptoDataByte0.setPubKeyB(pubKeyB);
            cryptoDataByte0
                    = encryptorAsy.encryptStreamByAsy(bis0, bos0, cryptoDataByte0);
            cryptoDataByte0.setCipher(bos0.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Basic encrypt one way with cryptoDataByte:" + cryptoDataByte0.toNiceJson());
        symKey = cryptoDataByte0.getSymKey();
        symKeyHex = Hex.toHex(symKey);
        System.out.println("symKey:" + symKeyHex);
        //Decrypt
        System.out.println("# Decrypt");
        cryptoDataByte.setSymKey(null);
        CryptoDataByte cryptoDataByte1;
        try(ByteArrayInputStream bis1 = new ByteArrayInputStream(cryptoDataByte.getCipher());
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream()) {
            cryptoDataByte1 = decryptorAsy.decryptStreamByAsy(bis1, bos1, priKeyB, pubKeyA, cryptoDataByte.getIv(), cryptoDataByte.getSum());
            cryptoDataByte1.setData(bos1.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        cryptoDataByte1.makeDid();
        cryptoDataByte1.setCipher(cryptoDataByte.getCipher());
        cryptoDataByte1.setType(cryptoDataByte.getType());
        if(cryptoDataByte1.checkSum(cryptoDataByte1.getDid()))
            System.out.println("Basic decrypt:"+cryptoDataByte1.getMessage()+"\n"+ cryptoDataByte1.toNiceJson());
        else System.out.println("Basic decrypt failed.");

        //decrypt by cryptoData
        CryptoDataByte cryptoDataByte2 = new CryptoDataByte();
        cryptoDataByte2.setPriKeyB(priKeyB);
        cryptoDataByte2.setPubKeyA(pubKeyA);
        cryptoDataByte2.setIv(cryptoDataByte.getIv());
        cryptoDataByte2.setSum(cryptoDataByte.getSum());
        cryptoDataByte2.setType(cryptoDataByte.getType());
        cryptoDataByte2.setAlg(FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataByte2.setCipher(cryptoDataByte.getCipher());

        try(ByteArrayInputStream bis2 = new ByteArrayInputStream(cryptoDataByte2.getCipher());
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream()) {
            decryptorAsy.decryptStreamByAsy(bis2, bos2, cryptoDataByte2);
            cryptoDataByte2.setData(bos2.toByteArray());
            cryptoDataByte2.makeDid();
            cryptoDataByte2.checkSum(cryptoDataByte2.getDid());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Basic decrypt with crypto data:" + cryptoDataByte2.toNiceJson());
        symKey = cryptoDataByte2.getSymKey();
        symKeyHex = Hex.toHex(symKey);
        System.out.println("symKey:" + symKeyHex);


        //Bundle
        System.out.println();
        System.out.println("# Bundle");
        //One way
        byte[] bundleOneWay = cryptoDataByte9.toBundle();
        System.out.println("Encrypted:"+ Hex.toHex(bundleOneWay));

        //Two way
        byte[] bundleTwoWay = cryptoDataByte.toBundle();
        System.out.println("Encrypted:"+ Hex.toHex(bundleTwoWay));


        //One way
        CryptoDataByte cryptoDataByte8
                = decryptorAsy.decryptBundle(bundleOneWay, priKeyB, FC_EccK1AesCbc256_No1_NrC7);
        System.out.println("Data from bundle oneway:" + cryptoDataByte8.toNiceJson());

        //two-way
        CryptoDataByte cryptoDataByte10
                = decryptorAsy.decryptBundle(bundleTwoWay, priKeyB,pubKeyA, FC_EccK1AesCbc256_No1_NrC7);
        System.out.println("Data from bundle two way BA:" + cryptoDataByte10.toNiceJson());
        CryptoDataByte cryptoDataByte11
                = decryptorAsy.decryptBundle(bundleTwoWay, priKeyA,pubKeyB, FC_EccK1AesCbc256_No1_NrC7);
        System.out.println("Data from bundle two way AB:" + cryptoDataByte11.toNiceJson());

        //File
        System.out.println("\n# File");
        String dataFilePath = "/Users/liuchangyong/Desktop/a.md";
        String cipherFilePath1 = "/Users/liuchangyong/Desktop/b1.cipher";
        String cipherFilePath2 = "/Users/liuchangyong/Desktop/b2.cipher";
        System.out.println("FileHash:"+Hash.sha256x2(new File(dataFilePath)));

        //One way
        CryptoDataByte cryptoDataByte3 = encryptorAsy.encryptFileByAsyOneWay(dataFilePath, cipherFilePath1, pubKeyB);
        System.out.println("Encrypt file one way:" + cryptoDataByte3.getMessage() + "\n" + cryptoDataByte3.toNiceJson());
        System.out.println("symKey:" + Hex.toHex(cryptoDataByte3.getSymKey()));
        System.out.println("Did:"+Hex.toHex(cryptoDataByte3.getDid()));
        System.out.println("CipherId:"+Hex.toHex(cryptoDataByte3.getCipherId()));
        System.out.println();

        CryptoDataByte cryptoDataByte4 = decryptorAsy.decryptFileByAsyOneWay(cipherFilePath1, dataFilePath+"1", priKeyB);
        System.out.println("Decrypt file AsyOneWay: " + cryptoDataByte4.getMessage() + "\n" + cryptoDataByte4.toNiceJson());
        System.out.println("symKey:" + Hex.toHex(cryptoDataByte4.getSymKey()));
        System.out.println("Did:"+Hex.toHex(cryptoDataByte4.getDid()));
        System.out.println("CipherId:"+Hex.toHex(cryptoDataByte4.getCipherId()));
        System.out.println();

        //Two way
        CryptoDataByte cryptoDataByte5 = encryptorAsy.encryptFileByAsyTwoWay(dataFilePath, cipherFilePath2, pubKeyB, priKeyA);
        System.out.println("Encrypt file two way:" + cryptoDataByte5.getMessage() + ":" + cryptoDataByte5.toNiceJson());
        System.out.println("symKey:" + Hex.toHex(cryptoDataByte5.getSymKey()));
        System.out.println("Did:"+Hex.toHex(cryptoDataByte5.getDid()));
        System.out.println("CipherId:"+Hex.toHex(cryptoDataByte5.getCipherId()));
        System.out.println();

        CryptoDataByte cryptoDataByte6 = decryptorAsy.decryptFileByAsyTwoWay(cipherFilePath2, dataFilePath+"AB", priKeyA, pubKeyB);
        System.out.println("Decrypt file AB. " + cryptoDataByte6.getMessage() + ":" + cryptoDataByte6.toNiceJson());
        System.out.println("symKey:" + Hex.toHex(cryptoDataByte6.getSymKey()));
        System.out.println("Did:"+Hex.toHex(cryptoDataByte6.getDid()));
        System.out.println("CipherId:"+Hex.toHex(cryptoDataByte6.getCipherId()));
        System.out.println();

        CryptoDataByte cryptoDataByte7 = decryptorAsy.decryptFileByAsyTwoWay(cipherFilePath2, dataFilePath + "BA", priKeyB, pubKeyA);
        System.out.println("Decrypt file BA. " + cryptoDataByte7.getMessage() + ":" + cryptoDataByte7.toNiceJson());
        System.out.println("symKey:" + Hex.toHex(cryptoDataByte7.getSymKey()));
        System.out.println("Did:"+Hex.toHex(cryptoDataByte7.getDid()));
        System.out.println("CipherId:"+Hex.toHex(cryptoDataByte7.getCipherId()));

    }
}
