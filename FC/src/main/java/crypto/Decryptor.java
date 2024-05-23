package crypto;

import com.google.common.hash.Hashing;
import crypto.Algorithm.AesCbc256;
import crypto.Algorithm.Ecc256K1;
import crypto.old.EccAes256K1P7;
import fcData.AlgorithmId;
import javaTools.BytesTools;
import javaTools.FileTools;
import javaTools.Hex;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.util.Arrays;
import java.util.HexFormat;

public class Decryptor {

    AlgorithmId algorithmId;


    public Decryptor() {

    }

    public Decryptor(AlgorithmId algorithmId) {
        this.algorithmId = algorithmId;
    }
    public String decryptJsonBySymKey(String cryptoDataJson, String symKeyHex) {
        byte[] key;
        try {
            key = HexFormat.of().parseHex(symKeyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(7,CryptoCodeMessage.getMessage(7));
            return CryptoCodeMessage.getErrorStringCodeMsg(7);
        }

        CryptoDataByte cryptoDataByte = decryptJsonBySymKey(cryptoDataJson,key);
        if(cryptoDataByte.getCode()!=0)
            return CryptoCodeMessage.getErrorStringCodeMsg(cryptoDataByte.getCode());
        return new String(cryptoDataByte.getData());
    }

    public byte[] decryptBundleBySymKey(byte[]bundle, byte[]key, AlgorithmId alg) {
        byte[] iv = new byte[16];
        byte[] sum = new byte[4];
        System.arraycopy(bundle,0,iv,0,16);
        int cipherLength = bundle.length - 16 - 4;
        byte[] cipher = new byte[cipherLength];
        System.arraycopy(bundle,16,cipher,0,cipherLength);
        System.arraycopy(bundle,16+cipher.length,sum,0,4);
        return decryptBySymKey(cipher,iv,key,sum,alg);
    }

    public byte[] decryptBySymKey(byte[]cipher, byte[]iv, byte[]key, byte[] sum, AlgorithmId alg) {
        try(ByteArrayInputStream bisCipher = new ByteArrayInputStream(cipher);
            ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
            CryptoDataByte cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setSymKey(key);
            cryptoDataByte.setIv(iv);
            cryptoDataByte.setSum(sum);
            cryptoDataByte.setCipher(cipher);
            if(alg==null)alg= AlgorithmId.EccAes256K1P7_No1_NrC7;
            switch (alg) {
                case FC_Aes256Cbc_No1_NrC7 -> AesCbc256.decryptStream(bisCipher,bosData,cryptoDataByte);
                default -> new EccAes256K1P7().decrypt(cryptoDataByte);
            }

            byte[] data = bosData.toByteArray();
            cryptoDataByte.setData(data);
            cryptoDataByte.makeDid();
            if(cryptoDataByte.checkSum(cryptoDataByte.getDid()))
                return cryptoDataByte.getData();
            else {
                cryptoDataByte.setCodeMessage(20);
                return null;
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }
    public CryptoDataByte decryptJsonByPassword(String cryptoDataJson, char[]password) {
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(9);
            return cryptoDataByte;
        }
        return decryptByPassword(cryptoDataByte, password);
    }

    @NotNull
    private static CryptoDataByte decryptByPassword(CryptoDataByte cryptoDataByte, char[] password) {
        byte[] symKey = Encryptor.passwordToSymKey(password, cryptoDataByte.getIv());
        cryptoDataByte.setType(EncryptType.SymKey);
        cryptoDataByte.setSymKey(symKey);
        decryptBySymKey(cryptoDataByte);
        cryptoDataByte.setType(EncryptType.Password);
        return cryptoDataByte;
    }

    public CryptoDataByte decryptJsonBySymKey(String cryptoDataJson, byte[]symKey) {
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(9);
            return cryptoDataByte;
        }
        cryptoDataByte.setSymKey(symKey);
        return decryptBySymKey(cryptoDataByte);
    }

    @NotNull
    private static CryptoDataByte decryptBySymKey(CryptoDataByte cryptoDataByte) {
        if(cryptoDataByte==null)return null;
        switch (cryptoDataByte.getAlg()) {
            case FC_Aes256Cbc_No1_NrC7 -> AesCbc256.decrypt(cryptoDataByte);
            default -> new EccAes256K1P7().decrypt(cryptoDataByte);
        }
        return cryptoDataByte;
    }

    public CryptoDataByte decryptFileByPassword(String cipherFileName, String dataFileName, char[] password){
        FileTools.createFileWithDirectories(dataFileName);


        CryptoDataByte cryptoDataByte = decryptFileBySymKey(new File(cipherFileName), new File(dataFileName), null, password);
        cryptoDataByte.setPassword(BytesTools.charArrayToByteArray(password, StandardCharsets.UTF_8));
        cryptoDataByte.setType(EncryptType.Password);

        return cryptoDataByte;
    }

    public CryptoDataByte decryptFileBySymKey(String cipherFileName, String dataFileName, byte[]key){
        FileTools.createFileWithDirectories(dataFileName);
        return decryptFileBySymKey(new File(cipherFileName),new File(dataFileName),key, null);
    }

    private CryptoDataByte decryptFileBySymKey(File cipherFile, File dataFile, byte[]key, char[] password){

        CryptoDataByte cryptoDataByte;
        try(FileOutputStream fos = new FileOutputStream(dataFile);
            FileInputStream fis = new FileInputStream(cipherFile)){

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
            if(key==null){
                if(password!=null){
                    key = Encryptor.passwordToSymKey(password,cryptoDataByte.getIv());
                }else {
                    cryptoDataByte.setCodeMessage(12);
                    return cryptoDataByte;
                }
            }
            cryptoDataByte.setSymKey(key);

            switch (cryptoDataByte.getAlg()){
                case FC_Aes256Cbc_No1_NrC7 -> AesCbc256.decryptStream(fis,fos,cryptoDataByte);
                default -> AesCbc256.decryptStream(fis,fos,cryptoDataByte);
            }
//            if(cryptoDataByte==null){
//                cryptoDataByte = new CryptoDataByte();
//                cryptoDataByte.setCodeMessage(1);
//                return decryptStream(fis,fos,key,cryptoDataByte.getIv(),cryptoDataByte);
//            }
        } catch (FileNotFoundException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(11);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
        if(cryptoDataByte.getCode()==0) {
            byte[] did;
            try {
                did = Hash.sha256x2Bytes(dataFile);
            } catch (IOException e) {
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(6);
                return cryptoDataByte;
            }
            cryptoDataByte.setDid(did);
            cryptoDataByte.checkSum(did);
            return cryptoDataByte;
        }
        return cryptoDataByte;
    }

    public CryptoDataByte decryptStreamBySymKey(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv, @Nullable CryptoDataByte cryptoDataByte) {
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        if(key!=null)cryptoDataByte.setSymKey(key);
        if(iv!=null)cryptoDataByte.setIv(iv);
        switch (algorithmId){
            case FC_Aes256Cbc_No1_NrC7 -> {
                AesCbc256.decryptStream(inputStream,outputStream,cryptoDataByte);
            }
            default -> AesCbc256.decryptStream(inputStream,outputStream,cryptoDataByte);
        }
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setCodeMessage(1);
        return cryptoDataByte;
    }

    public static void decryptBySymKeyBase(String algo, String transformation, String provider, InputStream inputStream, OutputStream outputStream, @Nullable CryptoDataByte cryptoDataByte) {
        Security.addProvider(new BouncyCastleProvider());
        if(cryptoDataByte==null)return ;
        if(cryptoDataByte.getSymKey()==null){
            cryptoDataByte.setCodeMessage(12);
            return;
        }
        byte[] key = cryptoDataByte.getSymKey();
        byte[] iv = cryptoDataByte.getIv();

        AlgorithmId alg = null;
        if(cryptoDataByte.getAlg()!=null)
            alg = cryptoDataByte.getAlg();

        if(key.length!=32){
            cryptoDataByte.setCodeMessage(14);
            return;
        }

        SecretKeySpec keySpec = new SecretKeySpec(key, algo);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        try {

            Cipher cipher = Cipher.getInstance(transformation, provider);
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
            cryptoDataByte.setCipherId(sha256(hasherIn.hash().asBytes()));

        } catch (InvalidAlgorithmParameterException e) {
            cryptoDataByte.setCodeMessage(4,e.getMessage());
            return;
        } catch (NoSuchPaddingException e) {
            cryptoDataByte.setCodeMessage(3,e.getMessage());
            return;
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setCodeMessage(1,e.getMessage());
            return;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(6,e.getMessage());
            return;
        } catch (NoSuchProviderException e) {
            cryptoDataByte.setCodeMessage(2,e.getMessage());
            return;
        } catch (InvalidKeyException e) {
            cryptoDataByte.setCodeMessage(5,e.getMessage());
            return;
        }
        if(cryptoDataByte.getType()==null)
            cryptoDataByte.setType(EncryptType.SymKey);
        if(cryptoDataByte.getAlg()==null)
            cryptoDataByte.setAlg(alg);
        if(cryptoDataByte.getCode()==null)
            cryptoDataByte.set0CodeMessage();
    }


    public CryptoDataByte decryptByAsyOneWay(String cryptoDataJson, byte[]priKey){
        return decryptByAsyTwoWay(cryptoDataJson,priKey,null);
    }

    public CryptoDataByte decryptBundle(byte[] bundle, byte[]priKey, AlgorithmId algorithm){
        return decryptBundle(bundle,priKey,null,algorithm );
    }

    public CryptoDataByte decryptBundle(byte[] bundle, byte[]priKeyX, byte[]pubKeyY, AlgorithmId algorithm){

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

    public CryptoDataByte decryptByAsyTwoWay(String cryptoDataJson, byte[]priKey, byte[] pubKey){
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

    @org.jetbrains.annotations.Nullable
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
        AlgorithmId algo = cryptoDataByte.getAlg();
        if(algo==null){
            algo = AlgorithmId.EccAes256K1P7_No1_NrC7;
            cryptoDataByte.setAlg(algo);
        }

        byte[] symKey;
        switch (algo){
            case FC_EccK1AesCbc256_No1_NrC7 -> {
                symKey = Ecc256K1.asyKeyToSymKey(priKeyX,pubKeyY, iv);
                cryptoDataByte.setSymKey(symKey);
                cryptoDataByte.setType(EncryptType.SymKey);
                cryptoDataByte.setAlg(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
            default -> {
                symKey = EccAes256K1P7.asyKeyToSymKey(priKeyX,pubKeyY,iv);
                cryptoDataByte.setSymKey(symKey);
                cryptoDataByte.setType(EncryptType.SymKey);
                cryptoDataByte.setAlg(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
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
    public static void checkSum(CryptoDataByte cryptoDataByte) {
        byte[] newSum = CryptoDataByte.makeSum4(cryptoDataByte.getSymKey(), cryptoDataByte.getIv(), cryptoDataByte.getDid());
        if(cryptoDataByte.getSum()!=null && !Arrays.equals(newSum, cryptoDataByte.getSum())){
            cryptoDataByte.setCodeMessage(20);
        }else cryptoDataByte.set0CodeMessage();
    }

    public static byte[] sha256(byte[] b) {
        return Hashing.sha256().hashBytes(b).asBytes();
    }

    public AlgorithmId getAlgorithmType() {
        return algorithmId;
    }

    public void setAlgorithmType(AlgorithmId algorithmId) {
        this.algorithmId = algorithmId;
    }
}
