package FEIP.feipClient;

import clients.apipClient.ApipClient;
import FCH.TxCreator;
import constants.Constants;
import constants.UpStrings;
import crypto.eccAes256K1P7.EccAes256K1P7;
import FEIP.feipData.Feip;
import FEIP.feipData.MasterData;
import fcData.Algorithm;
import javaTools.JsonTools;
import crypto.cryptoTools.KeyTools;
import FCH.fchData.SendTo;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static constants.Constants.Dust;

public class IdentityFEIPs {
    public static String promise = "The master owns all my rights.";

    public static String setMaster(byte[] priKey, String masterPubKey) {
        Feip feip = new Feip(Constants.FEIP, "6", "6", UpStrings.MASTER);

        MasterData masterData = new MasterData();
        masterData.setMaster(KeyTools.pubKeyToFchAddr(masterPubKey));
        masterData.setAlg(Algorithm.EccAes256K1P7_No1_NrC7.getName());
        byte[] priKeyCipher = new EccAes256K1P7().encryptAsyOneWayBundle(priKey.clone(), HexFormat.of().parseHex(masterPubKey));
        masterData.setCipherPriKey(Base64.getEncoder().encodeToString(priKeyCipher));
        masterData.setPromise(promise);

        feip.setData(masterData);

        return JsonTools.getString(feip);
    }

    public static String setMaster(String priKeyCipher, String ownerOrItsPubKey, ApipClient apipClient) {

        String ownerPubKey;
        if (KeyTools.isValidFchAddr(ownerOrItsPubKey)) {
            ownerPubKey = apipClient.getPubKey(ownerOrItsPubKey);
        } else if (KeyTools.isValidPubKey(ownerOrItsPubKey)) {
            ownerPubKey = ownerOrItsPubKey;
        } else return null;

        byte[] priKey = EccAes256K1P7.decryptJsonBytes(priKeyCipher,apipClient.getSymKey());
        if (priKey == null) return null;
        String masterJson = setMaster(priKey, ownerPubKey);
        SendTo sendTo = new SendTo();
        sendTo.setFid(ownerOrItsPubKey);
        sendTo.setAmount(Dust);
        List<SendTo> sendToList = new ArrayList<>();
        sendToList.add(sendTo);
        String txId = TxCreator.sendTxForMsgByAPIP(apipClient.getApiAccount(), apipClient.getSymKey(), priKey, sendToList, masterJson);
        return txId;
    }
}
