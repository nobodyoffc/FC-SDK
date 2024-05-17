package crypto;

import crypto.eccAes256K1.EccAesType;
import fcData.Algorithm;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

public class CryptoDataByte {

    private EccAesType type;
    private Algorithm alg;
    private transient byte[] data;
    private transient byte[] msgId;
    private transient byte[] symKey;
    private transient byte[] password;
    private transient byte[] pubKeyA;
    private transient byte[] pubKeyB;
    private transient byte[] priKeyA;
    private transient byte[] priKeyB;
    private transient byte[] iv;
    private transient byte[] sum;
    private Boolean badSum;
    private transient byte[] cipher;
    private transient byte[] cipherId;
    private transient InputStream msgInputStream;
    private transient InputStream cipherInputStream;
    private transient OutputStream msgOutputStream;
    private transient OutputStream cipherOutputStream;
    private String message;
    private Integer code;


    public CryptoDataByte() {
    }

    public CryptoDataByte(EccAesType type, Algorithm alg, InputStream msgInputStream,InputStream cipherInputStream,OutputStream msgOutputStream,OutputStream cipherOutputStream,byte[] pubKeyA,byte[] priKeyA,byte[]pubKeyB,byte[]priKeyB,byte[] symKey,byte[] password,byte[] iv) {
        this.type = type;
        this.alg = alg;
        this.msgInputStream = msgInputStream;
        this.cipherInputStream = cipherInputStream;
        this.msgOutputStream = msgOutputStream;
        this.cipherOutputStream = cipherOutputStream;
        this.pubKeyA = pubKeyA;
        this.priKeyA = priKeyA;
        this.pubKeyB = pubKeyB;
        this.priKeyB = priKeyB;
        this.symKey = symKey;
        this.password = password;
        this.iv = iv;
    }

    public CryptoDataByte(EccAesType type) {
        this.type = type;
    }

    public static CryptoDataByte fromCryptoData(CryptoData cryptoData) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        if (cryptoData.getType() != null)
            cryptoDataByte.setType(cryptoData.getType());
        if (cryptoData.getAlg() != null)
            cryptoDataByte.setAlg(cryptoData.getAlg());
        if (cryptoData.getCipher() != null)
            cryptoDataByte.setCipher(Base64.getDecoder().decode(cryptoData.getCipher()));
        if (cryptoData.getIv() != null)
            cryptoDataByte.setIv(HexFormat.of().parseHex(cryptoData.getIv()));
        if (cryptoData.getData() != null)
            cryptoDataByte.setData(cryptoData.getData().getBytes(StandardCharsets.UTF_8));
        if (cryptoData.getPassword() != null)
            cryptoDataByte.setPassword(BytesTools.utf8CharArrayToByteArray(cryptoData.getPassword()));
        if (cryptoData.getPubKeyA() != null)
            cryptoDataByte.setPubKeyA(HexFormat.of().parseHex(cryptoData.getPubKeyA()));
        if (cryptoData.getPubKeyB() != null)
            cryptoDataByte.setPubKeyB(HexFormat.of().parseHex(cryptoData.getPubKeyB()));
        if (cryptoData.getPriKeyA() != null)
            cryptoDataByte.setPriKeyA(BytesTools.hexCharArrayToByteArray(cryptoData.getPriKeyA()));
        if (cryptoData.getPriKeyB() != null)
            cryptoDataByte.setPriKeyB(BytesTools.hexCharArrayToByteArray(cryptoData.getPriKeyB()));
        if (cryptoData.getSymKey() != null)
            cryptoDataByte.setSymKey(BytesTools.hexCharArrayToByteArray(cryptoData.getSymKey()));
        if (cryptoData.getSum() != null)
            cryptoDataByte.setSum(HexFormat.of().parseHex(cryptoData.getSum()));
        if (cryptoData.getMessage() != null)
            cryptoDataByte.setMessage(cryptoData.getMessage());
        if(cryptoData.getDid()!=null)
            cryptoDataByte.setMsgId(Hex.fromHex(cryptoData.getDid()));
        if(cryptoData.getCipherId()!=null)
            cryptoDataByte.setCipherId(Hex.fromHex(cryptoData.getCipherId()));
        cryptoDataByte.setBadSum(cryptoData.isBadSum());

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

//    public String makeIvAndCipherJson() {
//        String ivStr = HexFormat.of().formatHex(this.iv);
//        String cipherStr = Base64.getEncoder().encodeToString(this.cipher);
//        return "{\"iv\":\""+ivStr+"\",\"cipher\":\""+cipherStr+"\"}";
//    }
//
//    public String makePubKeyIvAndCipherJson() {
//        String pubKeyAStr = HexFormat.of().formatHex(this.pubKeyA);
//        String ivStr = HexFormat.of().formatHex(this.iv);
//        String cipherStr = Base64.getEncoder().encodeToString(this.cipher);
//        return "{\"pubKeyA\":\""+pubKeyAStr +"\",\"iv\":\""+ivStr+"\",\"cipher\":\""+cipherStr+"\"}";
//
//    }

    public String toNiceJson() {
        CryptoData cryptoData = CryptoData.fromCryptoDataByte(this);
        cryptoData.clearAllSensitiveData();
        return JsonTools.getNiceString(cryptoData);
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

    public Algorithm getAlg() {
        return alg;
    }

    public void setAlg(Algorithm alg) {
        this.alg = alg;
    }

    public EccAesType getType() {
        return type;
    }

    public void setType(EccAesType type) {
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

    public Boolean isBadSum() {
        return badSum;
    }

    public void setBadSum(Boolean badSum) {
        this.badSum = badSum;
    }

    public byte[] getMsgId() {
        return msgId;
    }

    public void setMsgId(byte[] msgId) {
        this.msgId = msgId;
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

    public Boolean getBadSum() {
        return badSum;
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
}
