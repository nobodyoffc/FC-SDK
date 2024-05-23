package crypto;

import com.google.common.hash.Hashing;
import crypto.Algorithm.AesCbc256;
import crypto.old.EccAes256K1P7;
import fcData.AlgorithmType;
import javaTools.BytesTools;
import javaTools.FileTools;
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
import java.security.*;
import java.util.HexFormat;

public class DecryptorSym {

    AlgorithmType algorithmType;


    public DecryptorSym() {

    }

    public DecryptorSym(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
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

    public byte[] decryptBundleBySymKey(byte[]bundle, byte[]key, AlgorithmType alg) {
        byte[] iv = new byte[16];
        byte[] sum = new byte[4];
        System.arraycopy(bundle,0,iv,0,16);
        int cipherLength = bundle.length - 16 - 4;
        byte[] cipher = new byte[cipherLength];
        System.arraycopy(bundle,16,cipher,0,cipherLength);
        System.arraycopy(bundle,16+cipher.length,sum,0,4);
        return decryptBySymKey(cipher,iv,key,sum,alg);
    }

    public byte[] decryptBySymKey(byte[]cipher, byte[]iv, byte[]key, byte[] sum, AlgorithmType alg) {
        try(ByteArrayInputStream bisCipher = new ByteArrayInputStream(cipher);
            ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
            CryptoDataByte cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setSymKey(key);
            cryptoDataByte.setIv(iv);
            cryptoDataByte.setSum(sum);
            cryptoDataByte.setCipher(cipher);
            if(alg==null)alg=AlgorithmType.EccAes256K1P7_No1_NrC7;
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
        byte[] symKey = EncryptorSym.passwordToSymKey(password, cryptoDataByte.getIv());
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
                    key = EncryptorSym.passwordToSymKey(password,cryptoDataByte.getIv());
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
        switch (algorithmType){
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

        AlgorithmType alg = null;
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

    public static byte[] sha256(byte[] b) {
        return Hashing.sha256().hashBytes(b).asBytes();
    }

    public AlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public void setAlgorithmType(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }
}
