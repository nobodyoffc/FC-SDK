package crypto;

import constants.Constants;
import crypto.cryptoTools.Hash;
import crypto.cryptoTools.KeyTools;
import crypto.eccAes256K1.Aes256CbcP7;
import crypto.eccAes256K1.EccAes256K1P7;
import crypto.eccAes256K1.EccAesType;
import fcData.Algorithm;
import javaTools.BytesTools;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.InputStream;
import java.security.*;
import java.util.Arrays;

import static javaTools.BytesTools.clearByteArray;

public class Encryptor {
    private static final Logger log = LoggerFactory.getLogger(EccAes256K1P7.class);
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
//    private void encrypt(CryptoDataByte cryptoDataByte) {
//        switch (cryptoDataByte.getType()) {
//            case AsyOneWay -> encryptAsyOneWay(cryptoDataByte);
//            case AsyTwoWay -> encryptAsyTwoWay(cryptoDataByte);
//            case SymKey -> encryptBySymKey(cryptoDataByte);
//            case Password -> encryptByPassword(cryptoDataByte);
//            default -> cryptoDataByte.setError("Wrong type: " + cryptoDataByte.getType());
//        }
//    }

    private void encryptAsyTwoWay(Algorithm alg, InputStream msgInputStream, byte[] priKey, byte[]pubKey) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte(EccAesType.AsyTwoWay,alg,msgInputStream,null,null,null,null,priKey,pubKey,null,null,null,null);

    }

    public void encryptAsyOneWay(Algorithm alg,InputStream msgInputStream,byte[]pubKey){
        CryptoDataByte cryptoDataByte = new CryptoDataByte(EccAesType.AsyOneWay,alg,msgInputStream,null,null,null,null,null,pubKey,null,null,null,null);
    }

    public void encryptBySymKey(Algorithm alg, InputStream msgInputStream, byte[]symKey){
        CryptoDataByte cryptoDataByte = new CryptoDataByte(EccAesType.SymKey,alg,msgInputStream,null,null,null,null,null,null,null,symKey,null,null);
    }

    public void encryptByPassword(Algorithm alg, InputStream msgInputStream, byte[]password){
        CryptoDataByte cryptoDataByte = new CryptoDataByte(EccAesType.Password,alg,msgInputStream,null,null,null,null,null,null,null,null,password,null);
        byte[] symKey = Hash.Sha256x2(password);
        encryptBySymKey(alg,msgInputStream,symKey);
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

    private byte[] makeRandomKeyPair(CryptoDataByte cryptoDataByte) {
        byte[] priKey;
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH(), spec.getSeed());

        // Generate EC key pair for sender
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(domainParameters, new SecureRandom()));

        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

        ECPrivateKeyParameters newPriKey = (ECPrivateKeyParameters) keyPair.getPrivate();
        byte[] newPubKey = KeyTools.pubKeyToBytes(KeyTools.pubKeyFromPriKey(newPriKey));


        priKey = KeyTools.priKeyToBytes(newPriKey);
        cryptoDataByte.setPubKeyA(newPubKey);
        return priKey;
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

    private byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {

        ECPrivateKeyParameters priKey = KeyTools.priKeyFromBytes(priKeyBytes);
        ECPublicKeyParameters pubKey = KeyTools.pubKeyFromBytes(pubKeyBytes);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(priKey);
        return agreement.calculateAgreement(pubKey).toByteArray();
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

    private boolean isGoodPasswordEncryptParams(CryptoDataByte cryptoData) {

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

    private boolean isGoodSymKeyEncryptParams(CryptoDataByte cryptoData) {

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

        if (cryptoDataByte.getPubKeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH ) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameter pubKeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH  + " characters. It is " + cryptoDataByte.getPubKeyB().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPriKeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EccAesType.AsyTwoWay.name() + " parameter priKeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " characters. It is " + cryptoDataByte.getPriKeyA().length + " now.");
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
    private byte[] getSum4(MessageDigest sha256, byte[] symKey, byte[] iv, byte[] cipher) {
        byte[] sum32 = sha256.digest(BytesTools.addByteArray(symKey, BytesTools.addByteArray(iv, cipher)));
        return BytesTools.getPartOfBytes(sum32, 0, 4);
    }

    public static byte[] getRandomIv() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        return iv;
    }
}
