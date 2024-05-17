package crypto;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import crypto.eccAes256K1.EccAes256K1P7;
import crypto.eccAes256K1.EccAesType;
import fcData.Algorithm;
import javaTools.BytesTools;
import javaTools.Hex;


import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static javaTools.BytesTools.byteArrayToUtf8CharArray;
import static javaTools.Hex.byteArrayToHexCharArray;


public class CryptoData {
    private EccAesType type;
    private Algorithm alg;
    private String data;
    private transient String did;
    private String cipher;
    private transient String cipherId;
    private transient char[] symKey;
    private transient char[] password;
    private String pubKeyA;
    private String pubKeyB;
    private transient char[] priKeyA;
    private transient char[] priKeyB;
    private String iv;
    private String sum;
    private Boolean badSum;
    private String message;
    private Integer code;

    public CryptoData() {
    }

    /**
     * For all types and operations. From Json string without sensitive data.
     */
    public CryptoData(String eccAesDataJson) {
        fromJson(eccAesDataJson);
    }

    /**
     * For all type. Encrypt or Decrypt.
     */
    public CryptoData(String eccAesDataJson, char[] key) {
        fromJson(eccAesDataJson);
        switch (this.type) {
            case AsyOneWay -> priKeyB = key;
            case AsyTwoWay -> checkKeyPairAndSetPriKey(this, key);
            case SymKey -> symKey = key;
            case Password -> password = key;
        }
    }

    /**
     * For AsyOneWay encrypt. The classic encrypting mode.
     */
    public CryptoData(EccAesType asyOneWay, String data, String pubKeyB) {
        if (asyOneWay == EccAesType.AsyOneWay) {
            this.type = asyOneWay;
            this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubKeyB = pubKeyB;
        } else {
            this.message = "Constructing wrong. " + EccAesType.AsyOneWay + " is required for this constructor. ";
        }
    }
    public CryptoData(Algorithm alg, EccAesType asyOneWay, String data, String pubKeyB) {
        if (asyOneWay == EccAesType.AsyOneWay) {
            this.type = asyOneWay;
            if(alg!=null)this.alg = alg;
            else this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubKeyB = pubKeyB;
        } else {
            this.message = "Constructing wrong. " + EccAesType.AsyOneWay + " is required for this constructor. ";
        }
    }

    /**
     * For AsyTwoWay encrypt
     */
    public CryptoData(EccAesType asyTwoWay, String data, String pubKeyB, char[] priKeyA) {
        if (asyTwoWay == EccAesType.AsyTwoWay) {
            this.type = asyTwoWay;
            this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubKeyB = pubKeyB;
            this.priKeyA = priKeyA;
        } else {
            this.message = "Constructing wrong. " + EccAesType.AsyTwoWay + " is needed for this constructor. ";
        }
    }
    public CryptoData(Algorithm alg, EccAesType asyTwoWay, String data, String pubKeyB, char[] priKeyA) {
        if (asyTwoWay == EccAesType.AsyTwoWay) {
            this.type = asyTwoWay;
            if(alg!=null)this.alg = alg;
            else this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
            this.data = data;
            this.pubKeyB = pubKeyB;
            this.priKeyA = priKeyA;
        } else {
            this.message = "Constructing wrong. " + EccAesType.AsyTwoWay + " is needed for this constructor. ";
        }
    }

    /**
     * For SymKey or Password encrypt
     */
    public CryptoData(EccAesType symKeyOrPasswordType, String data, char[] symKeyOrPassword) {
        this.type = symKeyOrPasswordType;
        switch (symKeyOrPasswordType) {
            case SymKey -> symKey = symKeyOrPassword;
            case Password -> password = symKeyOrPassword;
            default ->
                    this.message = "Constructing wrong. " + EccAesType.SymKey + " or " + EccAesType.Password + " is required for this constructor. ";
        }
        this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
        this.data = data;
    }
    public CryptoData(Algorithm alg, EccAesType symKeyOrPasswordType, String data, char[] symKeyOrPassword) {
        this.type = symKeyOrPasswordType;
        switch (symKeyOrPasswordType) {
            case SymKey -> symKey = symKeyOrPassword;
            case Password -> password = symKeyOrPassword;
            default ->
                    this.message = "Constructing wrong. " + EccAesType.SymKey + " or " + EccAesType.Password + " is required for this constructor. ";
        }
        if(alg!=null)this.alg=alg;
        else this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
        this.data = data;
    }

    /**
     * For AsyOneWay or AsyTwoWay decrypt
     */
    public CryptoData(EccAesType asyOneWayOrAsyTwoWayType, String pubKeyA, String pubKeyB, String iv, String cipher, @Nullable String sum, char[] priKey) {
        if (asyOneWayOrAsyTwoWayType == EccAesType.AsyOneWay || asyOneWayOrAsyTwoWayType == EccAesType.AsyTwoWay) {
            byte[] pubKeyBytesA = HexFormat.of().parseHex(pubKeyA);
            byte[] pubKeyBytesB = HexFormat.of().parseHex(pubKeyB);
            byte[] priKeyBytes = BytesTools.hexCharArrayToByteArray(priKey);
            this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
            this.type = asyOneWayOrAsyTwoWayType;
            this.iv = iv;
            this.cipher = cipher;
            this.sum = sum;
            this.pubKeyA = pubKeyA;
            this.pubKeyB = pubKeyB;
            if (EccAes256K1P7.isTheKeyPair(pubKeyBytesA, priKeyBytes)) {
                this.priKeyA = priKey;
            } else if (EccAes256K1P7.isTheKeyPair(pubKeyBytesB, priKeyBytes)) {
                this.priKeyB = priKey;
            } else this.message = "The priKey doesn't match pubKeyA or pubKeyB.";
        } else
            this.message = "Constructing wrong. " + EccAesType.AsyOneWay + " or" + EccAesType.AsyTwoWay + " is required for this constructor. ";

    }
    public CryptoData(Algorithm alg,EccAesType asyOneWayOrAsyTwoWayType, String pubKeyA, String pubKeyB, String iv, String cipher, @Nullable String sum, char[] priKey) {
        if (asyOneWayOrAsyTwoWayType == EccAesType.AsyOneWay || asyOneWayOrAsyTwoWayType == EccAesType.AsyTwoWay) {
            byte[] pubKeyBytesA = HexFormat.of().parseHex(pubKeyA);
            byte[] pubKeyBytesB = HexFormat.of().parseHex(pubKeyB);
            byte[] priKeyBytes = BytesTools.hexCharArrayToByteArray(priKey);
            if(alg!=null)this.alg=alg;
            else this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
            this.type = asyOneWayOrAsyTwoWayType;
            this.iv = iv;
            this.cipher = cipher;
            this.sum = sum;
            this.pubKeyA = pubKeyA;
            this.pubKeyB = pubKeyB;
            if (EccAes256K1P7.isTheKeyPair(pubKeyBytesA, priKeyBytes)) {
                this.priKeyA = priKey;
            } else if (EccAes256K1P7.isTheKeyPair(pubKeyBytesB, priKeyBytes)) {
                this.priKeyB = priKey;
            } else this.message = "The priKey doesn't match pubKeyA or pubKeyB.";
        } else
            this.message = "Constructing wrong. " + EccAesType.AsyOneWay + " or" + EccAesType.AsyTwoWay + " is required for this constructor. ";

    }

    public CryptoData(EccAesType symKeyOrPasswordType, String iv, String cipher, @Nullable String sum, char[] symKeyOrPassword) {
        this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
        this.type = symKeyOrPasswordType;
        this.iv = iv;
        this.cipher = cipher;
        this.sum = sum;
        if (symKeyOrPasswordType == EccAesType.SymKey) {
            this.symKey = symKeyOrPassword;
        } else if (symKeyOrPasswordType == EccAesType.Password) {
            this.password = symKeyOrPassword;
        } else {
            this.message = "Constructing wrong. " + EccAesType.SymKey + " or" + EccAesType.Password + " is required for this constructor. ";
        }
    }
    public CryptoData(Algorithm alg,EccAesType symKeyOrPasswordType, String iv, String cipher, @Nullable String sum, char[] symKeyOrPassword) {
        if(alg!=null)this.alg =alg;
        else this.alg = Algorithm.EccAes256K1P7_No1_NrC7;
        this.type = symKeyOrPasswordType;
        this.iv = iv;
        this.cipher = cipher;
        this.sum = sum;
        if (symKeyOrPasswordType == EccAesType.SymKey) {
            this.symKey = symKeyOrPassword;
        } else if (symKeyOrPasswordType == EccAesType.Password) {
            this.password = symKeyOrPassword;
        } else {
            this.message = "Constructing wrong. " + EccAesType.SymKey + " or" + EccAesType.Password + " is required for this constructor. ";
        }
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

    public static CryptoData fromCryptoDataByte(CryptoDataByte cryptoDataByte) {
        CryptoData cryptoData = new CryptoData();

        if (cryptoDataByte.getType() != null)
            cryptoData.setType(cryptoDataByte.getType());
        if (cryptoDataByte.getAlg() != null)
            cryptoData.setAlg(cryptoDataByte.getAlg());
        if (cryptoDataByte.getCipher() != null)
            cryptoData.setCipher(Base64.getEncoder().encodeToString(cryptoDataByte.getCipher()));
        if (cryptoDataByte.getIv() != null)
            cryptoData.setIv(HexFormat.of().formatHex(cryptoDataByte.getIv()));
        if (cryptoDataByte.getData() != null)
            cryptoData.setData(new String(cryptoDataByte.getData(), StandardCharsets.UTF_8));
        if (cryptoDataByte.getPassword() != null)
            cryptoData.setPassword(byteArrayToUtf8CharArray(cryptoDataByte.getPassword()));
        if (cryptoDataByte.getPubKeyA() != null)
            cryptoData.setPubKeyA(HexFormat.of().formatHex(cryptoDataByte.getPubKeyA()));
        if (cryptoDataByte.getPubKeyB() != null)
            cryptoData.setPubKeyB(HexFormat.of().formatHex(cryptoDataByte.getPubKeyB()));
        if (cryptoDataByte.getPriKeyA() != null)
            cryptoData.setPriKeyA(byteArrayToHexCharArray(cryptoDataByte.getPriKeyA()));
        if (cryptoDataByte.getPriKeyB() != null)
            cryptoData.setPriKeyB(byteArrayToHexCharArray(cryptoDataByte.getPriKeyB()));
        if (cryptoDataByte.getSymKey() != null)
            cryptoData.setSymKey(byteArrayToHexCharArray(cryptoDataByte.getSymKey()));
        if (cryptoDataByte.getSum() != null)
            cryptoData.setSum(HexFormat.of().formatHex(cryptoDataByte.getSum()));
        if (cryptoDataByte.getMessage() != null)
            cryptoData.setMessage(cryptoDataByte.getMessage());
        if(cryptoDataByte.getMsgId()!=null)
            cryptoData.setDid(Hex.toHex(cryptoDataByte.getMsgId()));
        if(cryptoDataByte.getCipherId()!=null)
            cryptoData.setCipherId(Hex.toHex(cryptoDataByte.getCipherId()));
        cryptoData.setBadSum(cryptoDataByte.isBadSum());

        return cryptoData;
    }

    private void checkKeyPairAndSetPriKey(CryptoData cryptoData, char[] key) {
        byte[] keyBytes = BytesTools.hexCharArrayToByteArray(key);
        if (cryptoData.getPubKeyA() != null) {
            byte[] pubKey = HexFormat.of().parseHex(cryptoData.getPubKeyA());
            if (EccAes256K1P7.isTheKeyPair(pubKey, keyBytes)) {
                cryptoData.setPriKeyA(key);
                return;
            } else cryptoData.setPriKeyB(key);
        }
        if (cryptoData.getPubKeyB() != null) {
            byte[] pubKey = HexFormat.of().parseHex(cryptoData.getPubKeyB());
            if (EccAes256K1P7.isTheKeyPair(pubKey, keyBytes)) {
                cryptoData.setPriKeyB(key);
                return;
            } else cryptoData.setPriKeyA(key);
        }
        cryptoData.setMessage("No pubKeyA or pubKeyB.");
    }

    public void fromJson(String json) {
        CryptoData cryptoData = new Gson().fromJson(json, CryptoData.class);
        this.type = cryptoData.getType();
        this.alg = cryptoData.getAlg();
        this.data = cryptoData.getData();
        this.cipher = this.getCipher();
        this.pubKeyA = cryptoData.getPubKeyA();
        this.pubKeyB = cryptoData.getPubKeyB();
        this.sum = cryptoData.getSum();
        this.badSum = cryptoData.isBadSum();
        this.message = cryptoData.getMessage();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String toNiceJson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();

        Gson gson = gsonBuilder.create();
        return gson.toJson(this);
    }

    public void clearCharArray(char[] array) {
        if (array != null) {
            Arrays.fill(array, '\0');
            array = null;
        }
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

    public EccAesType getType() {
        return type;
    }

    public void setType(EccAesType type) {
        this.type = type;
    }

    public Algorithm getAlg() {
        return alg;
    }

    public void setAlg(Algorithm alg) {
        this.alg = alg;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public char[] getSymKey() {
        return symKey;
    }

    public void setSymKey(char[] symKey) {
        this.symKey = symKey;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public String getPubKeyA() {
        return pubKeyA;
    }

    public void setPubKeyA(String pubKeyA) {
        this.pubKeyA = pubKeyA;
    }

    public String getPubKeyB() {
        return pubKeyB;
    }

    public void setPubKeyB(String pubKeyB) {
        this.pubKeyB = pubKeyB;
    }

    public char[] getPriKeyA() {
        return priKeyA;
    }

    public void setPriKeyA(char[] priKeyA) {
        this.priKeyA = priKeyA;
    }

    public char[] getPriKeyB() {
        return priKeyB;
    }

    public void setPriKeyB(char[] priKeyB) {
        this.priKeyB = priKeyB;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getSum() {
        return sum;
    }

    public void setSum(String sum) {
        this.sum = sum;
    }

    public void clearPassword() {
        clearCharArray(password);
        this.password = null;
    }

    public void clearSymKey() {
        clearCharArray(symKey);
        this.symKey = null;
    }

    public void clearPriKeyA() {
        clearCharArray(priKeyA);
        this.priKeyA = null;
    }

    public void clearPriKeyB() {
        clearCharArray(priKeyB);
        this.priKeyB = null;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean isBadSum() {
        return badSum;
    }

    public void setBadSum(Boolean badSum) {
        this.badSum = badSum;
    }

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public String getCipherId() {
        return cipherId;
    }

    public void setCipherId(String cipherId) {
        this.cipherId = cipherId;
    }

    public Boolean getBadSum() {
        return badSum;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }
}
