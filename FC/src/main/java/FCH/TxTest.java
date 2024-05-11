package FCH;

import clients.apipClient.ApipClientData;
import config.ApiAccount;
import FCH.fchData.SendTo;
import NaSa.data.TxInput;
import NaSa.data.TxOutput;
import clients.apipClient.ApipDataGetter;
import clients.apipClient.BlockchainAPIs;
import clients.apipClient.WalletAPIs;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import constants.Constants;
import crypto.cryptoTools.KeyTools;
import javaTools.Hex;
import crypto.cryptoTools.Hash;
import FCH.fchData.Cash;
import FCH.fchData.P2SH;
import fcData.Signature;
import javaTools.BytesTools;
import javaTools.JsonTools;
import appTools.Inputer;
import appTools.Shower;
import org.bitcoinj.core.*;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;


import static constants.Constants.COIN_TO_SATOSHI;
import static crypto.cryptoTools.KeyTools.getPriKey32;
import static FCH.TxCreator.*;

public class TxTest {

    public static void main(String[] args) throws IOException {

        String priKeyA = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        String priKeyB = "L5DDxf3PkFwi1jArqYokpTsntthLvhDYg44FXyTSgdTx3XEFR1iB";
        String priKeyC = "Kybd6FqL2xBEknFV2rcxvYsTZwqAbk99FyN3EBnWdi2M5UxiJL8A";

        String priKey32A = Hex.toHex(getPriKey32(priKeyA));
        String priKey32B = Hex.toHex(getPriKey32(priKeyB));
        String priKey32C = Hex.toHex(getPriKey32(priKeyC));

        byte[] priKeyBytesA = HexFormat.of().parseHex(priKey32A);
        byte[] priKeyBytesB = HexFormat.of().parseHex(priKey32B);
        byte[] priKeyBytesC = HexFormat.of().parseHex(priKey32C);

        ECKey ecKeyA = ECKey.fromPrivate(priKeyBytesA);
        ECKey ecKeyB = ECKey.fromPrivate(priKeyBytesB);
        ECKey ecKeyC = ECKey.fromPrivate(priKeyBytesC);

        String pubkeyA = ecKeyA.getPublicKeyAsHex();
        String pubkeyB = ecKeyB.getPublicKeyAsHex();
        String pubkeyC = ecKeyC.getPublicKeyAsHex();

        String fidA = KeyTools.pubKeyToFchAddr(pubkeyA);
        String fidB = KeyTools.pubKeyToFchAddr(pubkeyB);
        String fidC = KeyTools.pubKeyToFchAddr(pubkeyC);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        byte[] sessionKey = getSessionKey(br);
        if (sessionKey == null) return;

        //Make multi-sign address
        List<byte[]> pubKeyList = new ArrayList<>();

        pubKeyList.add(HexFormat.of().parseHex(pubkeyA));
        pubKeyList.add(HexFormat.of().parseHex(pubkeyB));
        pubKeyList.add(HexFormat.of().parseHex(pubkeyC));

        P2SH p2SH = genMultiP2sh(pubKeyList, 2);
        String mFid = p2SH.getFid();

        System.out.println("Multisig address:" + mFid);
        //Get multisig address information
        String urlHead = Constants.UrlHead_CID_CASH;
        BlockchainAPIs blockchainAPIs = new BlockchainAPIs();
        ApipClientData apipClientData = blockchainAPIs.p2shByIdsPost(urlHead, new String[]{mFid}, null, sessionKey);
        Object responseData = apipClientData.getResponseBody().getData();
        Map<String, P2SH> p2SHMap = ApipDataGetter.getP2SHMap(responseData);

        P2SH p2sh = p2SHMap.get(mFid);
        JsonTools.gsonPrint(p2sh);

        //Get cashes of the multisig address
        WalletAPIs walletAPIs = new WalletAPIs();

        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();
        sendTo.setFid(mFid);
        sendTo.setAmount(0.1);
        sendToList.add(sendTo);
        SendTo sendTo1 = new SendTo();
        sendTo1.setFid(fidA);
        sendTo1.setAmount(0.2);
        sendToList.add(sendTo1);

        String msg = "hi";

        long fee = calcSizeMultiSign(0, sendToList.size(), msg.length(), 2, 3);

        ApipClientData apipClientData1 = walletAPIs.cashValidForPayPost(urlHead, mFid, 0.1 + ((double) fee / COIN_TO_SATOSHI), null, sessionKey);

        if (apipClientData.checkResponse() != 0) {
            JsonTools.gsonPrint(apipClientData1);
            return;
        }

        responseData = apipClientData1.getResponseBody().getData();
        List<Cash> cashList = ApipDataGetter.getCashList(responseData);

        JsonTools.gsonPrint(cashList);

        //Make raw tx
        byte[] rawTx = createMultiSignRawTx(cashList, sendToList, msg, p2sh, DEFAULT_FEE_RATE);

        System.out.println(HexFormat.of().formatHex(rawTx));
        Shower.printUnderline(10);
        //Sign raw tx
        byte[] redeemScript = HexFormat.of().parseHex(p2sh.getRedeemScript());
        MultiSigData multiSignData = new MultiSigData(rawTx, p2sh, cashList);

        MultiSigData multiSignDataA = signSchnorrMultiSignTx(multiSignData, priKeyBytesA);
        MultiSigData multiSignDataB = signSchnorrMultiSignTx(multiSignData, priKeyBytesB);
        MultiSigData multiSignDataC = signSchnorrMultiSignTx(multiSignData, priKeyBytesC);

        Map<String, List<byte[]>> sig1 = multiSignDataA.getFidSigMap();
        Map<String, List<byte[]>> sig2 = multiSignDataB.getFidSigMap();
        Map<String, List<byte[]>> sig3 = multiSignDataC.getFidSigMap();

        System.out.println("Verify sig3:" + rawTxSigVerify(rawTx, ecKeyC.getPubKey(), sig3.get(fidC).get(0), 0, cashList.get(0).getValue(), redeemScript));

        Map<String, List<byte[]>> sigAll = new HashMap<>();
        sigAll.putAll(sig1);
        sigAll.putAll(sig2);

        for (String fid : sigAll.keySet()) {
            System.out.println(fid + ":");
            List<byte[]> sigList = sigAll.get(fid);
            for (byte[] sig : sigList) {
                System.out.println("    " + HexFormat.of().formatHex(sig));
            }
        }
        Shower.printUnderline(10);
        //build signed tx
        String signedTx = buildSchnorrMultiSignTx(rawTx, sigAll, p2sh);

        System.out.println(signedTx);

        String msC = multiSignDataC.toJson();
        System.out.println(msC);
        MultiSigData multiSignDataD = MultiSigData.fromJson(msC);
        System.out.println("New:" + multiSignDataD.toJson());
    }

    @Nullable
    private static byte[] getSessionKey(BufferedReader br) {
        System.out.println("Confirm or set your password...");
        byte[] passwordBytes = Inputer.getPasswordBytes(br);
        byte[] symKey = Hash.Sha256x2(passwordBytes);
        byte[] sessionKey = new byte[0];
        try {
            ApiAccount apiAccount = ApiAccount.checkApipAccount(br, passwordBytes.clone());
            if (apiAccount == null) return null;
            sessionKey = apiAccount.decryptSessionKey(apiAccount.getSession().getSessionKeyCipher(), Hash.Sha256x2(passwordBytes));
            if (sessionKey == null) return null;
            BytesTools.clearByteArray(passwordBytes);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Wrong password, try again.");
        }
        return sessionKey;
    }

    private static void lockTimeTxTest() {
        String priKey = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        String priKey32 = Hex.toHex(getPriKey32(priKey));
        if (priKey32 == null) return;
        byte[] priKeyBytes = HexFormat.of().parseHex(priKey32);
        ECKey ecKey = ECKey.fromPrivate(priKeyBytes);
        String pubkey = ecKey.getPublicKeyAsHex();
        String fid = KeyTools.pubKeyToFchAddr(pubkey);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        byte[] sessionKey = getSessionKey(br);
        if (sessionKey == null) return;

        WalletAPIs walletAPIs = new WalletAPIs();
        String urlHead = Constants.UrlHead_CID_CASH;

        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();
        sendTo.setFid(fid);
        sendTo.setAmount(0.1);
        sendToList.add(sendTo);

        String msg = "hi";

        long fee = calcTxSize(0, sendToList.size(), msg.length());

        ApipClientData apipClientData = walletAPIs.cashValidForPayPost(urlHead, fid, 0.1 + ((double) fee / COIN_TO_SATOSHI), null, sessionKey);

        Object responseData = apipClientData.getResponseBody().getData();
        Type t = new TypeToken<ArrayList<Cash>>() {
        }.getType();
        Gson gson = new Gson();
        List<Cash> cashList = new Gson().fromJson(gson.toJson(responseData), t);

        String txSigned = createTimeLockedTransaction(cashList, priKeyBytes, sendToList, 1999900, msg);
        System.out.println(txSigned);
    }


    private static void schnorrMsgTest() throws IOException {
        String priKey = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        String priKey32 = Hex.toHex(getPriKey32(priKey));
        if (priKey32 == null) return;
        byte[] priKeyBytes = HexFormat.of().parseHex(priKey32);
        ECKey ecKey = ECKey.fromPrivate(priKeyBytes);
        String pubkey = ecKey.getPublicKeyAsHex();
        String fid = KeyTools.pubKeyToFchAddr(pubkey);
        String msg = "hello";
        System.out.println(msg);
        String sign = WalletTools.schnorrMsgSign(msg, priKeyBytes);
        System.out.println("sign:" + sign);

        boolean verify = WalletTools.schnorrMsgVerify(msg, sign, fid);
        System.out.println("verify '" + msg + "':" + verify);
        verify = WalletTools.schnorrMsgVerify(msg + " ", sign, fid);
        System.out.println("verify '" + msg + " " + "':" + verify);
        Signature signature = new Signature(fid, msg, sign, Constants.Schnorr_No1_NrC7);
        System.out.println(JsonTools.getNiceString(signature));
    }

    public static void schnorrTxTest() {
        NetworkParameters params = org.bitcoinj.fch.FchMainNetwork.MAINNETWORK;
        Transaction transaction = new Transaction(params);
        int inputIndex = 0;
        String priKey = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        byte[] priKeyBytes = getPriKey32(priKey);
        String priKey32 = Hex.toHex(priKeyBytes);
        System.out.println("priKey32:" + priKey32);

        ECKey ecKey = ECKey.fromPrivate(priKeyBytes);
        System.out.println("PubKey:" + ecKey.getPublicKeyAsHex());
        System.out.println("Address:" + ecKey.toAddress(params));


        Address address = Address.fromKey(params, ecKey);
        String addr = address.toBase58();
        System.out.println("addr to:" + addr);
        Script script = ScriptBuilder.createOutputScript(address);


        Transaction.SigHash sigHash = Transaction.SigHash.ALL;
        boolean anyoneCanPay = false;
        Coin value = Coin.valueOf(100000000);

        List<TxInput> inputs = new ArrayList<>();
        TxInput txInput = new TxInput();
        txInput.setAmount(2 * COIN_TO_SATOSHI);
        txInput.setIndex(0);
        txInput.setPriKey32(priKeyBytes);
        txInput.setTxId("6a8ee1015faedaf31d2742c204ad34120426e656dcffbcaca74b919ce81f8e44");
        inputs.add(txInput);

        List<TxOutput> outputs = new ArrayList<>();
        TxOutput txOutput = new TxOutput();
        txOutput.setAddress(addr);
        txOutput.setAmount((long) (0.9 * COIN_TO_SATOSHI));
        outputs.add(txOutput);


        String opreturn = "text";
        long fee = 1000;
        String signed = createTransactionSignFch(inputs, outputs, opreturn, addr);
        System.out.println(signed);
    }

    private static void testVarInt() {
        System.out.println("0:" + VarInt.sizeOf(0));
        System.out.println("1:" + VarInt.sizeOf(1));
        System.out.println("253:" + VarInt.sizeOf(253));
        System.out.println("65536:" + VarInt.sizeOf(65536));
        System.out.println("4294967296:" + VarInt.sizeOf(4294967296L));


        long inputNum = 1;
        long outputNum = 1;
        long opLen = 4;
        long length = 10 + (long) 141 * inputNum + (long) 34 * (outputNum + 1) + (opLen + VarInt.sizeOf(opLen) + 1 + VarInt.sizeOf(opLen + VarInt.sizeOf(opLen) + 1) + 8);
        System.out.println("4:" + length);
    }
}
