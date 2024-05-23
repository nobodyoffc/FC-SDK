package crypto;

import crypto.Algorithm.AesCbc256;
import crypto.Algorithm.Ecc256K1;
import crypto.old.EccAes256K1P7;
import fcData.AlgorithmType;
import javaTools.FileTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DecryptorAsy {

    public DecryptorAsy() {
    }

    public CryptoDataByte decryptByAscKey(String cryptoDataJson, byte[]priKey){
        return decryptByAscKey(cryptoDataJson,priKey,null);
    }

    public CryptoDataByte decryptBundle(byte[] bundle, byte[]priKey, AlgorithmType algorithm){
        return decryptBundle(bundle,priKey,null,algorithm );
    }

    public CryptoDataByte decryptBundle(byte[] bundle, byte[]priKeyX, byte[]pubKeyY, AlgorithmType algorithm){

        CryptoDataByte cryptoDataByte;

        if(bundle==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(18);
            return cryptoDataByte;
        }
        if(priKeyX==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(18);
            return cryptoDataByte;
        }

        boolean isTwoWay = false;
//        byte[] pubKey=null;
        if(pubKeyY!=null){
//            pubKey=pubKeyY;
            isTwoWay=true;
        }

        cryptoDataByte = CryptoDataByte.fromBundle(bundle,isTwoWay);
        cryptoDataByte.setPriKeyB(priKeyX);
        cryptoDataByte.setAlg(algorithm);
        if(isTwoWay)cryptoDataByte.setPubKeyA(pubKeyY);

        try(ByteArrayInputStream bis = new ByteArrayInputStream(cryptoDataByte.getCipher());
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            decryptStreamByAsy(bis,bos,cryptoDataByte);

            if(cryptoDataByte.getCode()==0) {
                cryptoDataByte.setData(bos.toByteArray());
            }
//            if(pubKeyY==null)cryptoDataByte.setType(EncryptType.AsyOneWay);
//            else cryptoDataByte.setType(EncryptType.AsyTwoWay);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte=new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }

    public CryptoDataByte decryptByAscKey(String cryptoDataJson, byte[]priKey, byte[] pubKey){
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        if(cryptoDataByte.getType().equals(EncryptType.AsyTwoWay)&& pubKey==null){
            cryptoDataByte.setCodeMessage(12);
            return cryptoDataByte;
        }
        cryptoDataByte.setPriKeyA(priKey);
        cryptoDataByte.setPubKeyB(pubKey);
        decryptByAsyKey(cryptoDataByte);
        return cryptoDataByte;
    }

    public void decryptByAsyKey(CryptoDataByte cryptoDataByte){
        byte[] cipher = cryptoDataByte.getCipher();
        if(cipher==null){
            cryptoDataByte.setCodeMessage(17);
            return;
        }
        try(ByteArrayInputStream bis = new ByteArrayInputStream(cipher);
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            decryptStreamByAsy(bis,bos,cryptoDataByte);
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(6);
        }
    }
    public CryptoDataByte decryptFileByAsyOneWay(@NotNull String cipherFile, String dataFile, @NotNull byte[]priKeyX){
        CryptoDataByte cryptoDataByte = decryptFile(null, cipherFile, null, dataFile, priKeyX, null);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        return cryptoDataByte;
    }
    public CryptoDataByte decryptFileByAsyTwoWay(@NotNull String cipherFile, String dataFile, @NotNull byte[]priKeyX, @NotNull byte[] pubKeyY){
        return decryptFile(null, cipherFile, null, dataFile, priKeyX,pubKeyY);
    }
    public CryptoDataByte decryptFile(String srcPath, String srcFileName, String destPath, String destFileName, byte[]priKeyX){
        return decryptFile(srcPath, srcFileName, destPath, destFileName, priKeyX,null);
    }
    public CryptoDataByte decryptFile(String srcPath, String srcFileName, String destPath, String destFileName, byte[]priKeyX, byte[] pubKeyY){
        CryptoDataByte cryptoDataByte;
        String srcFullName = getFileFullName(srcPath, srcFileName);
        String destFullName = getFileFullName(destPath, destFileName);

        if(srcFullName==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(21);
            return cryptoDataByte;
        }

        String tempDestFileName;
        String destFileForFos;
        if(destFullName!=null)
            destFileForFos=destFullName;

        else {
            tempDestFileName = FileTools.getTempFileName();
            destFileForFos = tempDestFileName;
        }
        FileTools.createFileWithDirectories(destFileForFos);

        try(FileOutputStream fos = new FileOutputStream(destFileForFos);
            FileInputStream fis = new FileInputStream(srcFullName)){
            cryptoDataByte = CryptoDataByte.readFromFileStream(fis);
            if(cryptoDataByte ==null){
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(8);
                return cryptoDataByte;
            }

            if(cryptoDataByte.getIv()==null){
                cryptoDataByte.setCodeMessage(13);
                return cryptoDataByte;
            }
            if(priKeyX==null){
                cryptoDataByte.setCodeMessage(15);
                return cryptoDataByte;
            }
            if(cryptoDataByte.getPubKeyA()!=null)
                cryptoDataByte.setPriKeyB(priKeyX);
            else {
                cryptoDataByte.setPriKeyB(priKeyX);
                cryptoDataByte.setPubKeyA(pubKeyY);
            }
            decryptStreamByAsy(fis,fos,cryptoDataByte);

            String did = Hash.sha256x2(new File(destFileForFos));
            cryptoDataByte.setDid(Hex.fromHex(did));
            cryptoDataByte.makeSum4();
            if(destFullName==null){
                Path destPathForFos = Paths.get(destFileForFos);
                if(destPath==null)
                    Files.move(destPathForFos,Paths.get(did));
                else Files.move(destPathForFos,Paths.get(destPath,did));
            }

            return cryptoDataByte;

        } catch (FileNotFoundException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(11);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }

    @Nullable
    private String getFileFullName(String path, String fileName) {
        String destFullName;
        if(fileName !=null){
            if(path ==null)destFullName= fileName;
            else destFullName= Paths.get(path, fileName).toString();
        }else destFullName=null;
        return destFullName;
    }
    public void decryptStreamByAsy(InputStream is, OutputStream os, CryptoDataByte cryptoDataByte){
        byte[] priKeyX;
        byte[] pubKeyY;
        byte[] iv = cryptoDataByte.getIv();

        byte[] priKeyA =cryptoDataByte.getPriKeyA();
        byte[] priKeyB = cryptoDataByte.getPriKeyB();
        byte[] pubKeyA = cryptoDataByte.getPubKeyA();
        byte[] pubKeyB = cryptoDataByte.getPubKeyB();

        if(priKeyA!=null && pubKeyB !=null){
            priKeyX = priKeyA;
            pubKeyY = pubKeyB;
        }else if(priKeyB!=null && pubKeyA!=null){
            priKeyX = priKeyB;
            pubKeyY = pubKeyA;
        }else if((priKeyA==null && priKeyB ==null) || (pubKeyA==null && pubKeyB ==null)) {
                cryptoDataByte.setCodeMessage(12);
                return;
        }else {
            cryptoDataByte.setCodeMessage(19);
            return;
        }

        if(cryptoDataByte.getIv()==null) {
            cryptoDataByte.setCodeMessage(13);
            return;
        }

        if(cryptoDataByte.getSum()==null) {
            cryptoDataByte.setCodeMessage(14);
            return;
        }

        EncryptType type = cryptoDataByte.getType();
        AlgorithmType algo = cryptoDataByte.getAlg();
        if(algo==null){
            algo = AlgorithmType.EccAes256K1P7_No1_NrC7;
            cryptoDataByte.setAlg(algo);
        }

        byte[] symKey;
        switch (algo){
            case FC_EccK1AesCbc256_No1_NrC7 -> {
                symKey = Ecc256K1.asyKeyToSymKey(priKeyX,pubKeyY, iv);
                cryptoDataByte.setSymKey(symKey);
                cryptoDataByte.setType(EncryptType.SymKey);
                cryptoDataByte.setAlg(AlgorithmType.FC_Aes256Cbc_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
            default -> {
                symKey = EccAes256K1P7.asyKeyToSymKey(priKeyX,pubKeyY,iv);
                cryptoDataByte.setSymKey(symKey);
                cryptoDataByte.setType(EncryptType.SymKey);
                cryptoDataByte.setAlg(AlgorithmType.FC_Aes256Cbc_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
        }

        cryptoDataByte.setAlg(algo);
        cryptoDataByte.setType(type);
    }
    public CryptoDataByte decryptStreamByAsy(InputStream is, OutputStream os, byte[]priKeyX, byte[]pubKeyY, byte[] iv, byte[] sum){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setPriKeyA(priKeyX);
        cryptoDataByte.setPubKeyB(pubKeyY);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setSum(sum);

        decryptStreamByAsy(is,os,cryptoDataByte);
        return cryptoDataByte;
    }
//
//    private CryptoDataByte decryptStreamByAsy(InputStream is, OutputStream os, byte[]priKeyX, byte[]pubKeyY, byte[] iv, CryptoDataByte cryptoDataByte){
//        if(cryptoDataByte==null)
//            cryptoDataByte = new CryptoDataByte();
//
//
//
//        if(priKeyX==null){
//            priKeyX=cryptoDataByte.getPriKeyB();
//        }
//        if(priKeyX==null){
//            cryptoDataByte.setCodeMessage(15);
//            return cryptoDataByte;
//        }
//        cryptoDataByte.setPriKeyB(priKeyX);
//
//        if(pubKeyY==null){
//            pubKeyY = cryptoDataByte.getPubKeyA();
//        }
//        if(pubKeyY==null) {
//            cryptoDataByte.setCodeMessage(16);
//            return cryptoDataByte;
//        }
//        cryptoDataByte.setPubKeyA(pubKeyY);
//
//        if(iv ==null){
//            iv = cryptoDataByte.getIv();
//        }
//        if(iv==null){
//            cryptoDataByte.setCodeMessage(13);
//            return cryptoDataByte;
//        }
//        cryptoDataByte.setIv(iv);
//
//
//        if(algorithmType!=null){
//            cryptoDataByte.setAlg(algorithmType);
//        }else if(cryptoDataByte.getAlg()!=null){
//            algorithmType = cryptoDataByte.getAlg();
//        }else {
//            algorithmType = AlgorithmType.EccAes256K1P7_No1_NrC7;
//            cryptoDataByte.setAlg(algorithmType);
//        }
//
//        byte[] symKey;
//        switch (this.algorithmType){
//            case EccAes256K1P7_No1_NrC7 -> symKey = EccAes256K1P7.asyKeyToSymKey(priKeyX,pubKeyY,iv);
//            case FC_EccK1AesCbc256_No1_NrC7 -> symKey = Ecc256K1.asyKeyToSymKey(priKeyX,pubKeyY, iv);
//            default -> symKey = Ecc256K1.asyKeyToSymKey(priKeyX,pubKeyY, iv);
//        }
//
//        cryptoDataByte.setSymKey(symKey);
//
//        EncryptType type = cryptoDataByte.getType();;
//
//        AesCbc256.decryptStream(is,os,cryptoDataByte);
//
//        cryptoDataByte.setAlg(this.algorithmType);
//        cryptoDataByte.setType(type);
//
//        checkSum(cryptoDataByte);
//
//        return cryptoDataByte;
//    }

    public static void checkSum(CryptoDataByte cryptoDataByte) {
        byte[] newSum = CryptoDataByte.makeSum4(cryptoDataByte.getSymKey(), cryptoDataByte.getIv(), cryptoDataByte.getCipherId());
        if(cryptoDataByte.getSum()!=null && !newSum.equals(cryptoDataByte.getSum())){
            cryptoDataByte.setCodeMessage(20);

        }else cryptoDataByte.set0CodeMessage();
    }

}
