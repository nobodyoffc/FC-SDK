package FCH;

import APIP.apipClient.ApipClientData;
import APIP.apipClient.ApipDataGetter;
import APIP.apipClient.WalletAPIs;
import FCH.fchData.SendTo;
import NaSa.data.TxInput;
import NaSa.data.TxOutput;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.google.common.base.Preconditions;
import config.ApiAccount;
import constants.Constants;
import constants.IndicesNames;
import crypto.cryptoTools.KeyTools;
import javaTools.JsonTools;
import org.bitcoinj.crypto.SchnorrSignature;
import org.bitcoinj.fch.FchMainNetwork;
import FCH.fchData.Cash;
import FCH.fchData.P2SH;
import javaTools.BytesTools;
import javaTools.Hex;
import org.bitcoinj.core.*;

import org.bitcoinj.script.Script;
import org.bitcoinj.core.Coin;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.*;

import static constants.Constants.COIN_TO_SATOSHI;
import static crypto.cryptoTools.KeyTools.priKeyToFid;
import static org.bitcoinj.script.ScriptBuilder.createMultiSigInputScriptBytes;

/**
 * 工具类
 */
public class TxCreator {

    public static final double DEFAULT_FEE_RATE = 0.00001;

    static {
        fixKeyLength();
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void fixKeyLength() {
        String errorString = "Failed manually overriding key-length permissions.";
        int newMaxKeyLength;
        try {
            if ((newMaxKeyLength = Cipher.getMaxAllowedKeyLength("AES")) < 256) {
                Class c = Class.forName("javax.crypto.CryptoAllPermissionCollection");
                Constructor con = c.getDeclaredConstructor();
                con.setAccessible(true);
                Object allPermissionCollection = con.newInstance();
                Field f = c.getDeclaredField("all_allowed");
                f.setAccessible(true);
                f.setBoolean(allPermissionCollection, true);

                c = Class.forName("javax.crypto.CryptoPermissions");
                con = c.getDeclaredConstructor();
                con.setAccessible(true);
                Object allPermissions = con.newInstance();
                f = c.getDeclaredField("perms");
                f.setAccessible(true);
                ((Map) f.get(allPermissions)).put("*", allPermissionCollection);

                c = Class.forName("javax.crypto.JceSecurityManager");
                f = c.getDeclaredField("defaultPolicy");
                f.setAccessible(true);
                Field mf = Field.class.getDeclaredField("modifiers");
                mf.setAccessible(true);
                mf.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                f.set(null, allPermissions);

                newMaxKeyLength = Cipher.getMaxAllowedKeyLength("AES");
            }
        } catch (Exception e) {
            throw new RuntimeException(errorString, e);
        }
        if (newMaxKeyLength < 256)
            throw new RuntimeException(errorString); // hack failed
    }

    /**
     * 创建签名
     *
     * @param inputs
     * @param outputs
     * @param opReturn
     * @param returnAddr
     * @return
     */

    public static String createTransactionSignFch(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr) {
        FchMainNetwork mainnetwork = FchMainNetwork.MAINNETWORK;
        return createTransactionSignClassic(mainnetwork, inputs, outputs, opReturn, returnAddr, 0);
    }

    public static String createTransactionSignFch(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr, double feeRateDouble) {
        FchMainNetwork mainnetwork = FchMainNetwork.MAINNETWORK;
        return createTransactionSignClassic(mainnetwork, inputs, outputs, opReturn, returnAddr, feeRateDouble);
    }

    public static String createTransactionSignClassic(NetworkParameters networkParameters, List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr, double feeRateDouble) {

        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);

        long feeRateLong;
        if (feeRateDouble != 0) {
            feeRateLong = (long) (feeRateDouble / 1000 * COIN_TO_SATOSHI);
        } else feeRateLong = (long) (DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);
        long fee = feeRateLong * txSize;
        Transaction transaction = new Transaction(networkParameters);

        long totalMoney = 0;
        long totalOutput = 0;

        List<ECKey> ecKeys = new ArrayList<>();
        for (TxOutput output : outputs) {
            totalOutput += output.getAmount();
            transaction.addOutput(Coin.valueOf(output.getAmount()), Address.fromBase58(networkParameters, output.getAddress()));
        }

        if (opReturn != null && !"".equals(opReturn)) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (TxInput input : inputs) {
            totalMoney += input.getAmount();

            ECKey eckey = ECKey.fromPrivate(input.getPriKey32());

            ecKeys.add(eckey);
            UTXO utxo = new UTXO(Sha256Hash.wrap(input.getTxId()), input.getIndex(), Coin.valueOf(input.getAmount()), 0, false, ScriptBuilder.createP2PKHOutputScript(eckey));
            TransactionOutPoint outPoint = new TransactionOutPoint(networkParameters, utxo.getIndex(), utxo.getHash());
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }
        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }
        long change = totalMoney - totalOutput - fee;

        if (change > Constants.DustInSatoshi) {
            if (returnAddr == null)
                returnAddr = ECKey.fromPrivate(inputs.get(0).getPriKey32()).toAddress(networkParameters).toBase58();
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(networkParameters, returnAddr));
        }

        for (int i = 0; i < inputs.size(); ++i) {
            TxInput input = inputs.get(i);
            ECKey eckey = ecKeys.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getAmount()), Transaction.SigHash.ALL, false);
            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    public static String createTransactionSignFch(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, String opReturn) {
        return createTransactionSignFch(inputs, priKey, outputs, opReturn, 0);
    }

    public static String createTransactionSignFch(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, String opReturn, double feeRateDouble) {
        String changeToFid = inputs.get(0).getOwner();
//        long fee;
//        if(feeDouble!=0){
//            fee = (long)feeDouble* COIN_TO_SATOSHI;
//        }else if(opReturn!=null){
//            fee = FchTool.calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);
//        }else {
//            fee = FchTool.calcTxSize(inputs.size(), outputs.size(), 0);
//        }

        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);

        long feeRateLong;
        if (feeRateDouble != 0) {
            feeRateLong = (long) (feeRateDouble / 1000 * COIN_TO_SATOSHI);
        } else feeRateLong = (long) (DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);
        long fee = feeRateLong * txSize;

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK);

        long totalMoney = 0;
        long totalOutput = 0;

        ECKey eckey = ECKey.fromPrivate(priKey);

        for (SendTo output : outputs) {
            long value = ParseTools.fchToSatoshi(output.getAmount());
            totalOutput += value;
            transaction.addOutput(Coin.valueOf(value), Address.fromBase58(FchMainNetwork.MAINNETWORK, output.getFid()));
        }

        if (opReturn != null && !"".equals(opReturn)) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Cash input : inputs) {
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(FchMainNetwork.MAINNETWORK, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }
        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(FchMainNetwork.MAINNETWORK, changeToFid));
        }

        for (int i = 0; i < inputs.size(); ++i) {
            Cash input = inputs.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getValue()), Transaction.SigHash.ALL, false);
            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    public static P2SH genMultiP2sh(List<byte[]> pubKeyList, int m) {
        List<ECKey> keys = new ArrayList<>();
        for (byte[] bytes : pubKeyList) {
            ECKey ecKey = ECKey.fromPublicOnly(bytes);
            keys.add(ecKey);
        }

        Script multiSigScript = ScriptBuilder.createMultiSigOutputScript(m, keys);

        byte[] redeemScriptBytes = multiSigScript.getProgram();

        P2SH p2sh;
        try {
            p2sh = P2SH.parseP2shRedeemScript(HexFormat.of().formatHex(redeemScriptBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return p2sh;
    }

    public static byte[] createMultiSignRawTx(List<Cash> inputs, List<SendTo> outputs, String opReturn, P2SH p2SH, double feeRate) {

        String changeToFid = inputs.get(0).getOwner();
        if (!changeToFid.startsWith("3"))
            throw new RuntimeException("It's not a multisig address.");
        ;

        long fee;
        long feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
        if (opReturn != null) {
            fee = feeRateLong * TxCreator.calcSizeMultiSign(inputs.size(), outputs.size(), opReturn.getBytes().length, p2SH.getM(), p2SH.getN());
        } else
            fee = feeRateLong * TxCreator.calcSizeMultiSign(inputs.size(), outputs.size(), 0, p2SH.getM(), p2SH.getN());

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK);

        long totalMoney = 0;
        long totalOutput = 0;

        for (SendTo output : outputs) {
            long value = ParseTools.fchToSatoshi(output.getAmount());
            totalOutput += value;
            transaction.addOutput(Coin.valueOf(value), Address.fromBase58(FchMainNetwork.MAINNETWORK, output.getFid()));
        }

        if (opReturn != null && !"".equals(opReturn)) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Cash input : inputs) {
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(FchMainNetwork.MAINNETWORK, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }
        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(FchMainNetwork.MAINNETWORK, changeToFid));
        }

        return transaction.bitcoinSerialize();
    }

    public static String signSchnorrMultiSignTx(String multiSignDataJson, byte[] priKey) {
        MultiSigData multiSignData = MultiSigData.fromJson(multiSignDataJson);
        return signSchnorrMultiSignTx(multiSignData, priKey).toJson();
    }

    public static MultiSigData signSchnorrMultiSignTx(MultiSigData multiSignData, byte[] priKey) {

        byte[] rawTx = multiSignData.getRawTx();
        byte[] redeemScript = HexFormat.of().parseHex(multiSignData.getP2SH().getRedeemScript());
        List<Cash> cashList = multiSignData.getCashList();

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTx);
        List<TransactionInput> inputs = transaction.getInputs();

        ECKey ecKey = ECKey.fromPrivate(priKey);
        BigInteger priKeyBigInteger = ecKey.getPrivKey();
        List<byte[]> sigList = new ArrayList<>();
        for (int i = 0; i < inputs.size(); ++i) {
            Script script = new Script(redeemScript);
            Sha256Hash hash = transaction.hashForSignatureWitness(i, script, Coin.valueOf(cashList.get(i).getValue()), Transaction.SigHash.ALL, false);
            byte[] sig = SchnorrSignature.schnorr_sign(hash.getBytes(), priKeyBigInteger);
            sigList.add(sig);
        }

        String fid = priKeyToFid(priKey);
        if (multiSignData.getFidSigMap() == null) {
            Map<String, List<byte[]>> fidSigListMap = new HashMap<>();
            multiSignData.setFidSigMap(fidSigListMap);
        }
        multiSignData.getFidSigMap().put(fid, sigList);
        return multiSignData;
    }

    public static boolean rawTxSigVerify(byte[] rawTx, byte[] pubKey, byte[] sig, int inputIndex, long inputValue, byte[] redeemScript) {
        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTx);
        Script script = new Script(redeemScript);
        Sha256Hash hash = transaction.hashForSignatureWitness(inputIndex, script, Coin.valueOf(inputValue), Transaction.SigHash.ALL, false);
        return SchnorrSignature.schnorr_verify(hash.getBytes(), pubKey, sig);
    }

    public static String buildSchnorrMultiSignTx(byte[] rawTx, Map<String, List<byte[]>> sigListMap, P2SH p2sh) {

        if (sigListMap.size() > p2sh.getM())
            sigListMap = dropRedundantSigs(sigListMap, p2sh.getM());

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTx);

        for (int i = 0; i < transaction.getInputs().size(); i++) {
            List<byte[]> sigListByTx = new ArrayList<>();
            for (String fid : p2sh.getFids()) {
                try {
                    byte[] sig = sigListMap.get(fid).get(i);
                    sigListByTx.add(sig);
                } catch (Exception ignore) {
                }
            }

            Script inputScript = createSchnorrMultiSigInputScriptBytes(sigListByTx, HexFormat.of().parseHex(p2sh.getRedeemScript())); // Include all required signatures

            System.out.println(HexFormat.of().formatHex(inputScript.getProgram()));
            TransactionInput input = transaction.getInput(i);
            input.setScriptSig(inputScript);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    private static Map<String, List<byte[]>> dropRedundantSigs(Map<String, List<byte[]>> sigListMap, int m) {
        Map<String, List<byte[]>> newMap = new HashMap<>();
        int i = 0;
        for (String key : sigListMap.keySet()) {
            newMap.put(key, sigListMap.get(key));
            i++;
            if (i == m) return newMap;
        }
        return newMap;
    }

    public static Script createSchnorrMultiSigInputScriptBytes(List<byte[]> signatures, byte[] multisigProgramBytes) {
        if (signatures.size() <= 16) return null;
        ScriptBuilder builder = new ScriptBuilder();
        builder.smallNum(0);
        Iterator var3 = signatures.iterator();
        byte[] sigHashAll = new byte[]{0x41};

        while (var3.hasNext()) {
            byte[] signature = (byte[]) var3.next();
            builder.data(BytesTools.bytesMerger(signature, sigHashAll));
        }

        if (multisigProgramBytes != null) {
            builder.data(multisigProgramBytes);
        }

        return builder.build();
    }

    public static String createTimeLockedTransaction(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, long lockUntil, String opReturn) {

        String changeToFid = inputs.get(0).getOwner();

        long fee;
        if (opReturn != null) {
            fee = TxCreator.calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);
        } else fee = TxCreator.calcTxSize(inputs.size(), outputs.size(), 0);

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK);
//        transaction.setLockTime(nLockTime);

        long totalMoney = 0;
        long totalOutput = 0;

        ECKey eckey = ECKey.fromPrivate(priKey);

        for (SendTo output : outputs) {
            long value = ParseTools.fchToSatoshi(output.getAmount());
            byte[] pubKeyHash = KeyTools.addrToHash160(output.getFid());
            totalOutput += value;

            ScriptBuilder builder = new ScriptBuilder();

            builder.number(lockUntil)
                    .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
                    .op(ScriptOpCodes.OP_DROP);

            builder.op(ScriptOpCodes.OP_DUP)
                    .op(ScriptOpCodes.OP_HASH160)
                    .data(pubKeyHash)
                    .op(ScriptOpCodes.OP_EQUALVERIFY)
                    .op(ScriptOpCodes.OP_CHECKSIG);

            Script cltvScript = builder.build();

            transaction.addOutput(Coin.valueOf(value), cltvScript);
        }

        if (opReturn != null && !"".equals(opReturn)) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Cash input : inputs) {
            totalMoney += input.getValue();

            TransactionOutPoint outPoint = new TransactionOutPoint(FchMainNetwork.MAINNETWORK, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }

        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(FchMainNetwork.MAINNETWORK, changeToFid));
        }

        for (int i = 0; i < inputs.size(); ++i) {
            Cash input = inputs.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getValue()), Transaction.SigHash.ALL, false);

            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    /**
     * 随机私钥
     *
     * @param secret
     * @return
     */
    public static IdInfo createRandomIdInfo(String secret) {
        return IdInfo.genRandomIdInfo();
    }

    /**
     * 公钥转地址
     *
     * @param pukey
     * @return
     */
    public static String pubkeyToAddr(String pukey) {

        ECKey eckey = ECKey.fromPublicOnly(Hex.fromHex(pukey));
        return eckey.toAddress(FchMainNetwork.MAINNETWORK).toString();

    }

    /**
     * 通过wif创建私钥
     *
     * @param wifKey
     * @return
     */
    public static IdInfo createIdInfoFromWIFPrivateKey(byte[] wifKey) {

        return new IdInfo(wifKey);
    }

    /**
     * 消息签名
     *
     * @param msg
     * @param wifkey
     * @return
     */
    public static String signMsg(String msg, byte[] wifkey) {
        IdInfo idInfo = new IdInfo(wifkey);
        return idInfo.signMsg(msg);
    }

    public static String signFullMsg(String msg, byte[] wifkey) {
        IdInfo idInfo = new IdInfo(wifkey);
        return idInfo.signFullMessage(msg);
    }

    public static String signFullMsgJson(String msg, byte[] wifkey) {
        IdInfo idInfo = new IdInfo(wifkey);
        return idInfo.signFullMessageJson(msg);
    }

    /**
     * 签名验证
     *
     * @param msg
     * @return
     */
    public static boolean verifyFullMsg(String msg) {
        String args[] = msg.split("----");
        try {
            ECKey key = ECKey.signedMessageToKey(args[0], args[2]);
            Address targetAddr = key.toAddress(FchMainNetwork.MAINNETWORK);
            return args[1].equals(targetAddr.toString());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verifyFullMsgJson(String msg) {
        FchProtocol.SignMsg signMsg = FchProtocol.parseSignMsg(msg);
        try {
            ECKey key = ECKey.signedMessageToKey(signMsg.getMessage(), signMsg.getSignature());
            Address targetAddr = key.toAddress(FchMainNetwork.MAINNETWORK);
            return signMsg.getAddress().equals(targetAddr.toString());
        } catch (Exception e) {
            return false;
        }
    }

    public static String msgHash(String msg) {
        try {
            byte[] data = msg.getBytes("UTF-8");
            return Hex.toHex(Sha256Hash.hash(data));
        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }

    public static String msgFileHash(String path) {
        try {
            File f = new File(path);
            return Hex.toHex(Sha256Hash.of(f).getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] aesCBCEncrypt(byte[] srcData, byte[] key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] encData = cipher.doFinal(srcData);
        return encData;

    }

    public static byte[] aesCBCDecrypt(byte[] encData, byte[] key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] decbbdt = cipher.doFinal(encData);
        return decbbdt;
    }

    //fee = txSize * (feeRate/1000)*100000000
    public static long calcTxSize(int inputNum, int outputNum, int opReturnBytesLen) {

        long baseLength = 10;
        long inputLength = 141 * (long) inputNum;
        long outputLength = 34 * (long) (outputNum + 1); // Include change output

        int opReturnLen = 0;
        if (opReturnBytesLen != 0)
            opReturnLen = calcOpReturnLen(opReturnBytesLen);

        return baseLength + inputLength + outputLength + opReturnLen;
    }

    private static int calcOpReturnLen(int opReturnBytesLen) {
        int dataLen;
        if (opReturnBytesLen < 76) {
            dataLen = opReturnBytesLen + 1;
        } else if (opReturnBytesLen < 256) {
            dataLen = opReturnBytesLen + 2;
        } else dataLen = opReturnBytesLen + 3;
        int scriptLen;
        scriptLen = (dataLen + 1) + VarInt.sizeOf(dataLen + 1);
        int amountLen = 8;
        return scriptLen + amountLen;
    }

    public static long calcSizeMultiSign(int inputNum, int outputNum, int opReturnBytesLen, int m, int n) {

        /*多签单个Input长度：
            基础字节40（preTxId 32，preIndex 4，sequence 4），
            可变脚本长度：？
            脚本：
                op_0    1
                签名：m * (1+64+1)     // length + pubKeyLength + sigHash ALL
                可变redeemScript 长度：？
                redeem script：
                    op_m    1
                    pubKeys    n * 33
                    op_n    1
                    OP_CHECKMULTISIG    1
         */

        long redeemScriptLength = 1 + (n * 33L) + 1 + 1;
        long redeemScriptVarInt = VarInt.sizeOf(redeemScriptLength);
        long scriptLength = 1 + (m * 66L) + redeemScriptVarInt + redeemScriptLength;
        long scriptVarInt = VarInt.sizeOf(scriptLength);
        long inputLength = 40 + scriptVarInt + scriptLength;

        int opReturnLen = 0;
        if (opReturnBytesLen != 0)
            opReturnLen = calcOpReturnLen(opReturnBytesLen);

        long length;
        if (opReturnBytesLen == 0) {
            length = 10 + inputLength * inputNum + (long) 34 * (outputNum + 1);
        } else {
            length = 10 + inputLength * inputNum + (long) 34 * (outputNum + 1) + opReturnLen;
        }
        return length;
    }


    public static String buildSignedTx(String[] signedData) {
        Map<String, List<byte[]>> fidSigListMap = new HashMap<>();
        byte[] rawTx = null;
        P2SH p2sh = null;

        for (String dataJson : signedData) {
            try {
                System.out.println(dataJson);

                MultiSigData multiSignData = MultiSigData.fromJson(dataJson);

                if (p2sh == null
                        && multiSignData.getP2SH() != null) {
                    p2sh = multiSignData.getP2SH();
                }

                if (rawTx == null
                        && multiSignData.getRawTx() != null
                        && multiSignData.getRawTx().length > 0) {
                    rawTx = multiSignData.getRawTx();
                }

                fidSigListMap.putAll(multiSignData.getFidSigMap());

            } catch (Exception ignored) {
            }
        }
        if (rawTx == null || p2sh == null) return null;

        return buildSchnorrMultiSignTx(rawTx, fidSigListMap, p2sh);
    }

    public static Block getBestBlock(ElasticsearchClient esClient) throws ElasticsearchException, IOException {
        SearchResponse<Block> result = esClient.search(s -> s
                        .index(IndicesNames.BLOCK)
                        .size(1)
                        .sort(so -> so.field(f -> f.field("height").order(SortOrder.Desc)))
                , Block.class);
        return result.hits().hits().get(0).source();
    }

    public static List<TxInput> cashListToTxInputList(List<Cash> cashList, byte[] priKey32) {
        List<TxInput> txInputList = new ArrayList<>();
        for (Cash cash : cashList) {
            TxInput txInput = cashToTxInput(cash, priKey32);
            if (txInput != null) {
                txInputList.add(txInput);
            }
        }
        if (txInputList.isEmpty()) return null;
        return txInputList;
    }

    public static TxInput cashToTxInput(Cash cash, byte[] priKey32) {
        if (cash == null) {
            System.out.println("Cash is null.");
            return null;
        }
        if (!cash.isValid()) {
            System.out.println("Cash has been spent.");
            return null;
        }
        TxInput txInput = new TxInput();

        txInput.setPriKey32(priKey32);
        txInput.setAmount(cash.getValue());
        txInput.setTxId(cash.getBirthTxId());
        txInput.setIndex(cash.getBirthIndex());

        return txInput;
    }

    public static Transaction buildLockedTx() {
        Transaction transaction = new Transaction(new FCH.FchMainNetwork());

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        byte[] hash = KeyTools.addrToHash160("FKi3bRKUPUbUfQuzxT9CfbYwT7m4KEu13R");
        Script script = scriptBuilder.op(169).data(hash).op(135).build();
        return transaction;
    }

    public static Script createP2PKHOutputScript(byte[] hash) {
        Preconditions.checkArgument(hash.length == 20);
        ScriptBuilder builder = new ScriptBuilder();
        builder.op(118);
        builder.op(169);
        builder.data(hash);
        builder.op(136);
        builder.op(172);
        return builder.build();
    }

    public static String sendTxForMsgByAPIP(config.ApiAccount apiAccount, byte[] symKey, byte[] priKey, List<SendTo> sendToList, String msg) {

        byte[] sessionKey = ApiAccount.decryptSessionKey(apiAccount.getSessionKeyCipher(),symKey);

        String sender = priKeyToFid(priKey);

        double sum = 0;
        int sendToSize = 0;
        if (sendToList != null && !sendToList.isEmpty()) {
            sendToSize = sendToList.size();
            for (SendTo sendTo : sendToList) sum += sendTo.getAmount();
        }

        long fee = calcTxSize(0, sendToSize, msg.length());

        String urlHead = apiAccount.getApiUrl();
        System.out.println("Getting cashes from " + urlHead + " ...");
        ApipClientData apipClientData = WalletAPIs.cashValidForPayPost(urlHead, sender, sum + ((double) fee / COIN_TO_SATOSHI), apiAccount.getVia(), sessionKey);
        if (apipClientData.checkResponse() != 0) {
            System.out.println("Failed to get cashes." + apipClientData.getMessage() + apipClientData.getResponseBody().getData());
            JsonTools.gsonPrint(apipClientData);
            return apipClientData.getMessage();
        }

        List<Cash> cashList = ApipDataGetter.getCashList(apipClientData.getResponseBody().getData());

        String txSigned = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, msg);

        System.out.println("Broadcast with " + urlHead + " ...");
        apipClientData = WalletAPIs.broadcastTxPost(urlHead, txSigned, apiAccount.getVia(), sessionKey);
        if (apipClientData.checkResponse() != 0) {
            System.out.println(apipClientData.getCode() + ": " + apipClientData.getMessage());
            if (apipClientData.getResponseBody().getData() != null)
                System.out.println(apipClientData.getResponseBody().getData());
            return apipClientData.getMessage();
        }

        return (String) apipClientData.getResponseBody().getData();
    }
}
