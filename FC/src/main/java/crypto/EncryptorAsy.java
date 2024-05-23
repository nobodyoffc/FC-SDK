package crypto;

import com.google.common.hash.Hashing;
import crypto.Algorithm.Ecc256K1;
import crypto.old.EccAes256K1P7;
import fcData.AlgorithmType;
import javaTools.BytesTools;
import javaTools.FileTools;
import javaTools.Hex;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static fcData.AlgorithmType.FC_Aes256Cbc_No1_NrC7;
import static fcData.AlgorithmType.FC_EccK1AesCbc256_No1_NrC7;

public class EncryptorAsy {

    AlgorithmType algorithmType;

    public EncryptorAsy() {
        this.algorithmType = FC_EccK1AesCbc256_No1_NrC7;
    }

    public EncryptorAsy(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }

    public CryptoDataByte encryptStrByAsyOneWay(String data, String pubKeyBHex){
        return encryptByAsyOneWay(data.getBytes(), Hex.fromHex(pubKeyBHex));
    }

    public CryptoDataByte encryptByAsyOneWay(byte[] data, byte[] pubKeyB){
        return encryptByAsyTwoWay(data, null, pubKeyB);
    }

    public CryptoDataByte encryptByAsyTwoWay(byte[] data, byte[]priKeyA, byte[] pubKeyB){
        CryptoDataByte cryptoDataByte;
        if(priKeyA==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(15);
            return cryptoDataByte;
        }
        if(pubKeyB==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(16);
            return cryptoDataByte;
        }

        try(ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            cryptoDataByte
                    = encryptStreamByAsyTwoWay(bis, bos, priKeyA, pubKeyB);
            cryptoDataByte.setCipher(bos.toByteArray());
            cryptoDataByte.makeSum4();
            cryptoDataByte.setType(EncryptType.AsyTwoWay);

            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }

    public CryptoDataByte encryptFileByAsyOneWay(String dataFileName, String cipherFileName, @NotNull byte[] pubKeyB){
        CryptoDataByte cryptoDataByte = encryptFileByAsyTwoWay(dataFileName, cipherFileName, pubKeyB, null);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        return cryptoDataByte;
    }
    public CryptoDataByte encryptFileByAsyTwoWay(String dataFileName, String cipherFileName, @NotNull byte[] pubKeyB,byte[]priKeyA){
        FileTools.createFileWithDirectories(cipherFileName);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(algorithmType);

        checkKeysMakeType(pubKeyB, priKeyA, cryptoDataByte);

        byte[] iv = BytesTools.getRandomBytes(16);
        cryptoDataByte.setIv(iv);

        String tempFile = FileTools.getTempFileName();
        try(FileInputStream fis = new FileInputStream(dataFileName);
            FileOutputStream fos = new FileOutputStream(tempFile)){

            encryptStreamByAsy(fis, fos, cryptoDataByte);

        } catch (FileNotFoundException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(11);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
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
        //            fos.write(cryptoDataStr.toJson().getBytes());


        return cryptoDataByte;
    }
    public CryptoDataByte encryptStreamByAsyTwoWay(InputStream is, OutputStream os, @NotNull byte[]priKeyX, @NotNull byte[]pubKeyY){
        return encryptStreamByAsy(is,os,priKeyX,pubKeyY,null);
    }
    public CryptoDataByte encryptStreamByAsyOneWay(InputStream is, OutputStream os, byte[]pubKeyY){
        return encryptStreamByAsy(is,os,null,pubKeyY,null);
    }

    public CryptoDataByte encryptStreamByAsy(InputStream is, OutputStream os,CryptoDataByte cryptoDataByte){
        return encryptStreamByAsy(is,os,null,null,cryptoDataByte);
    }
    private CryptoDataByte encryptStreamByAsy(InputStream is, OutputStream os, byte[]priKeyX, byte[]pubKeyY, CryptoDataByte cryptoDataByte){
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(algorithmType);
        checkKeysMakeType(pubKeyY, priKeyX, cryptoDataByte);

        EncryptType type = cryptoDataByte.getType();

        priKeyX = cryptoDataByte.getPriKeyA();
        if(priKeyX==null){
            cryptoDataByte.setCodeMessage(15);
            return cryptoDataByte;
        }

        pubKeyY = cryptoDataByte.getPubKeyB();
        if(pubKeyY==null) {
            cryptoDataByte.setCodeMessage(16);
            return cryptoDataByte;
        }

        byte[] iv;
        if(cryptoDataByte.getIv()!=null){
            iv = cryptoDataByte.getIv();
        }else {
            iv = BytesTools.getRandomBytes(16);
            cryptoDataByte.setIv(iv);
        }

        byte[] symKey;
        switch (algorithmType) {
            case EccAes256K1P7_No1_NrC7 -> {
                symKey = EccAes256K1P7.asyKeyToSymKey(priKeyX, pubKeyY,cryptoDataByte.getIv());
                cryptoDataByte.setSymKey(symKey);
                EccAes256K1P7 ecc = new EccAes256K1P7();
                ecc.aesEncrypt(cryptoDataByte);
            }
            default -> {
                symKey = Ecc256K1.asyKeyToSymKey(priKeyX, pubKeyY, iv);
                cryptoDataByte.setSymKey(symKey);
//TODO
                EncryptorSym encryptorSym = new EncryptorSym(FC_Aes256Cbc_No1_NrC7);
                encryptorSym.encryptStreamBySymKey(is,os,symKey,iv,cryptoDataByte);
            }
        }

        cryptoDataByte.setAlg(algorithmType);

        cryptoDataByte.setType(type);

        cryptoDataByte.set0CodeMessage();

        return cryptoDataByte;
    }

    public void checkKeysMakeType(byte[] pubKeyB, byte[] priKeyA, CryptoDataByte cryptoDataByte) {
        byte[] pubKeyA;
        if(priKeyA !=null || cryptoDataByte.getPriKeyA()!=null){
            cryptoDataByte.setType(EncryptType.AsyTwoWay);
            if(cryptoDataByte.getPriKeyA()==null)
                cryptoDataByte.setPriKeyA(priKeyA);
            if(pubKeyB!=null)
                cryptoDataByte.setPubKeyB(pubKeyB);
        }else {
            cryptoDataByte.setType(EncryptType.AsyOneWay);
            ECKey ecKey = new ECKey();
            priKeyA = ecKey.getPrivKeyBytes();
            pubKeyA = ecKey.getPubKey();
            cryptoDataByte.setPubKeyA(pubKeyA);
            cryptoDataByte.setPriKeyA(priKeyA);
            if(pubKeyB!=null)
                cryptoDataByte.setPubKeyB(pubKeyB);
        }
    }
    public static byte[] sha512(byte[] b) {
        return Hashing.sha512().hashBytes(b).asBytes();
    }

    public AlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public void setAlgorithmType(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }
}
