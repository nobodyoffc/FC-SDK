package crypto;

import fcData.AlgorithmId;
import javaTools.BytesTools;
import javaTools.Hex;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static javaTools.JsonTools.readOneJsonFromFile;

public class CryptoDataByte {

    private EncryptType type;
    private AlgorithmId alg;
    private transient byte[] data;
    private transient byte[] did;
    private transient byte[] symKey;
    private transient byte[] password;
    private transient byte[] pubKeyA;
    private transient byte[] pubKeyB;
    private transient byte[] priKeyA;
    private transient byte[] priKeyB;
    private transient byte[] iv;
    private transient byte[] sum;
    private transient byte[] cipher;
    private transient byte[] cipherId;
    private transient InputStream msgInputStream;
    private transient InputStream cipherInputStream;
    private transient OutputStream msgOutputStream;
    private transient OutputStream cipherOutputStream;
    private transient String message;
    private transient Integer code;


    public CryptoDataByte() {
    }

    public String toNiceJson() {
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromCryptoDataByte(this);
        return cryptoDataStr.toNiceJson();
    }

    public String toJson() {
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromCryptoDataByte(this);
        return cryptoDataStr.toJson();
    }

    public static CryptoDataByte readFromFileStream(FileInputStream fis) throws IOException {
        byte[] jsonBytes = readOneJsonFromFile(fis);
        if (jsonBytes == null) return null;
        return fromJson(new String(jsonBytes));
    }
    public static CryptoDataByte fromCryptoData(CryptoDataStr cryptoDataStr) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        if (cryptoDataStr.getType() != null)
            cryptoDataByte.setType(cryptoDataStr.getType());
        if (cryptoDataStr.getAlg() != null)
            cryptoDataByte.setAlg(cryptoDataStr.getAlg());
        if (cryptoDataStr.getCipher() != null)
            cryptoDataByte.setCipher(Base64.getDecoder().decode(cryptoDataStr.getCipher()));
        if (cryptoDataStr.getIv() != null)
            cryptoDataByte.setIv(HexFormat.of().parseHex(cryptoDataStr.getIv()));
        if (cryptoDataStr.getData() != null)
            cryptoDataByte.setData(cryptoDataStr.getData().getBytes(StandardCharsets.UTF_8));
        if (cryptoDataStr.getPassword() != null)
            cryptoDataByte.setPassword(BytesTools.utf8CharArrayToByteArray(cryptoDataStr.getPassword()));
        if (cryptoDataStr.getPubKeyA() != null)
            cryptoDataByte.setPubKeyA(HexFormat.of().parseHex(cryptoDataStr.getPubKeyA()));
        if (cryptoDataStr.getPubKeyB() != null)
            cryptoDataByte.setPubKeyB(HexFormat.of().parseHex(cryptoDataStr.getPubKeyB()));
        if (cryptoDataStr.getPriKeyA() != null)
            cryptoDataByte.setPriKeyA(BytesTools.hexCharArrayToByteArray(cryptoDataStr.getPriKeyA()));
        if (cryptoDataStr.getPriKeyB() != null)
            cryptoDataByte.setPriKeyB(BytesTools.hexCharArrayToByteArray(cryptoDataStr.getPriKeyB()));
        if (cryptoDataStr.getSymKey() != null)
            cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(cryptoDataStr.getSymKey()));
        if (cryptoDataStr.getSum() != null)
            cryptoDataByte.setSum(HexFormat.of().parseHex(cryptoDataStr.getSum()));
        if (cryptoDataStr.getMessage() != null)
            cryptoDataByte.setMessage(cryptoDataStr.getMessage());
        if(cryptoDataStr.getDid()!=null)
            cryptoDataByte.setDid(Hex.fromHex(cryptoDataStr.getDid()));
        if(cryptoDataStr.getCipherId()!=null)
            cryptoDataByte.setCipherId(Hex.fromHex(cryptoDataStr.getCipherId()));
//        cryptoDataByte.setBadSum(cryptoData.isBadSum());

        return cryptoDataByte;
    }
    public static CryptoDataByte fromJson(String json){
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromJson(json);
        return CryptoDataByte.fromCryptoData(cryptoDataStr);
    }
    public byte[] toBundle() {
        return makeBundle(pubKeyA, iv, cipher, sum, type);
    }
    public static byte[] makeBundle(byte[] pubKeyA, byte[] iv, byte[] cipher, byte[] sum, EncryptType type) {
        byte[] bundle;
        switch (type){
            case AsyOneWay ->  {
                if(pubKeyA==null||iv==null||cipher==null)return null;
                bundle = new byte[pubKeyA.length+iv.length+ sum.length+ cipher.length];
                System.arraycopy(pubKeyA,0,bundle,0, pubKeyA.length);
                System.arraycopy(iv,0,bundle,pubKeyA.length, iv.length);
                System.arraycopy(cipher,0,bundle, pubKeyA.length+iv.length, cipher.length);
                System.arraycopy(sum,0,bundle, pubKeyA.length+iv.length+cipher.length, sum.length);
            }
            default -> {

                bundle = new byte[iv.length+ sum.length+ cipher.length];
                System.arraycopy(iv,0,bundle,0, iv.length);
                System.arraycopy(cipher,0,bundle, iv.length, cipher.length);
                System.arraycopy(sum,0,bundle, iv.length+ cipher.length, sum.length);
            }

        }
        return bundle;
    }

    public static CryptoDataByte fromBundle(byte[] bundle,boolean isTwoWay) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        byte[] iv = new byte[16];
        byte[] pubKeyA = new byte[33];
        byte[] sum = new byte[4];
        byte[] cipher;
        if(isTwoWay)
            cipher = new byte[bundle.length -iv.length-sum.length];
            else cipher = new byte[bundle.length -iv.length-pubKeyA.length-sum.length];
        if(isTwoWay){
            cryptoDataByte.setType(EncryptType.AsyTwoWay);
            System.arraycopy(bundle,0,iv,0,iv.length);
            cryptoDataByte.setIv(iv);
            System.arraycopy(bundle,iv.length,cipher,0,cipher.length);
            cryptoDataByte.setCipher(cipher);
            System.arraycopy(bundle,iv.length+cipher.length,sum,0,sum.length);
            cryptoDataByte.setSum(sum);
        }
        else {
            cryptoDataByte.setType(EncryptType.AsyOneWay);
            System.arraycopy(bundle, 0, pubKeyA, 0, pubKeyA.length);
            cryptoDataByte.setPubKeyA(pubKeyA);
            System.arraycopy(bundle,pubKeyA.length,iv,0,iv.length);
            cryptoDataByte.setIv(iv);
            System.arraycopy(bundle,pubKeyA.length+iv.length,cipher,0,cipher.length);
            cryptoDataByte.setCipher(cipher);
            System.arraycopy(bundle,pubKeyA.length+iv.length+cipher.length,sum,0,sum.length);
            cryptoDataByte.setSum(sum);
        }
        return cryptoDataByte;
    }

    public void set0CodeMessage() {
        this.code = 0;
        message = CryptoCodeMessage.getMessage(0);
    }

    public void setCodeMessage(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    public void setCodeMessage(Integer code) {
        this.code = code;
        message = CryptoCodeMessage.getMessage(code);
    }
    public void setOtherCodeMessage(String message) {
        code = 9;
        this.message = message;
    }
    public void clearAllSensitiveData() {
        clearPassword();
        clearSymKey();
        clearPriKeyA();
        clearPriKeyB();
    }

    public void clearAllSensitiveDataButSymKey() {
        clearPassword();
        clearPriKeyA();
        clearPriKeyB();
    }

    public void clearSymKey() {
        BytesTools.clearByteArray(this.symKey);
        this.symKey = null;
    }

    public void clearPassword() {
        BytesTools.clearByteArray(this.password);
        this.password = null;
    }

    public void clearPriKeyA() {
        BytesTools.clearByteArray(this.priKeyA);
        this.priKeyA = null;
    }

    public void clearPriKeyB() {
        BytesTools.clearByteArray(this.priKeyB);
        this.priKeyB = null;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AlgorithmId getAlg() {
        return alg;
    }

    public void setAlg(AlgorithmId alg) {
        this.alg = alg;
    }

    public EncryptType getType() {
        return type;
    }

    public void setType(EncryptType type) {
        this.type = type;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public byte[] getSymKey() {
        return symKey;
    }

    public void setSymKey(byte[] symKey) {
        this.symKey = symKey;
    }

    public byte[] getPassword() {
        return password;
    }

    public void setPassword(byte[] password) {
        this.password = password;
    }

    public byte[] getSum() {
        return sum;
    }

    public void setSum(byte[] sum) {
        this.sum = sum;
    }

    public byte[] getCipher() {
        return cipher;
    }

    public void setCipher(byte[] cipher) {
        this.cipher = cipher;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getPubKeyA() {
        return pubKeyA;
    }

    public void setPubKeyA(byte[] pubKeyA) {
        this.pubKeyA = pubKeyA;
    }

    public byte[] getPubKeyB() {
        return pubKeyB;
    }

    public void setPubKeyB(byte[] pubKeyB) {
        this.pubKeyB = pubKeyB;
    }

    public byte[] getPriKeyA() {
        return priKeyA;
    }

    public void setPriKeyA(byte[] priKeyA) {
        this.priKeyA = priKeyA;
    }

    public byte[] getPriKeyB() {
        return priKeyB;
    }

    public void setPriKeyB(byte[] priKeyB) {
        this.priKeyB = priKeyB;
    }

    public byte[] getDid() {
        return did;
    }

    public void setDid(byte[] did) {
        this.did = did;
    }

    public byte[] getCipherId() {
        return cipherId;
    }

    public void setCipherId(byte[] cipherId) {
        this.cipherId = cipherId;
    }

    public InputStream getMsgInputStream() {
        return msgInputStream;
    }

    public void setMsgInputStream(InputStream msgInputStream) {
        this.msgInputStream = msgInputStream;
    }

    public InputStream getCipherInputStream() {
        return cipherInputStream;
    }

    public void setCipherInputStream(InputStream cipherInputStream) {
        this.cipherInputStream = cipherInputStream;
    }

    public OutputStream getMsgOutputStream() {
        return msgOutputStream;
    }

    public void setMsgOutputStream(OutputStream msgOutputStream) {
        this.msgOutputStream = msgOutputStream;
    }

    public OutputStream getCipherOutputStream() {
        return cipherOutputStream;
    }

    public void setCipherOutputStream(OutputStream cipherOutputStream) {
        this.cipherOutputStream = cipherOutputStream;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public static byte[] makeSum4(byte[] symKey, byte[] iv, byte[] did) {
        if(symKey!=null && iv!=null && did!=null) {
            byte[] sum32 = Hash.sha256(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, did)));
            return BytesTools.getPartOfBytes(sum32, 0, 4);
        }
        return null;
    }

    public void makeSum4() {
        if(symKey==null){
            setCodeMessage(12);
            return;
        }
        if(iv==null){
            setCodeMessage(13);
            return;
        }
        if(did==null){
            setCodeMessage(23);
            return;
        }
        byte[] sum32 = Hash.sha256(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, did)));
        sum = BytesTools.getPartOfBytes(sum32, 0, 4);

    }

    public void makeDid() {
        if(code==0 && this.data!=null){
            this.did = Hash.sha256x2(data);
        }
    }

    public boolean checkSum(byte[] did) {
        byte[] newSum = CryptoDataByte.makeSum4(symKey,iv,did);
        String sumHex = Hex.toHex(sum);
        String newSumHex = Hex.toHex(newSum);
        if(!newSumHex.equals(sumHex)){
            setCodeMessage(20);
            return false;
        }
        return true;
    }
}
