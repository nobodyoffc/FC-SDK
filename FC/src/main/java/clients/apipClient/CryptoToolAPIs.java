package clients.apipClient;

import APIP.apipData.EncryptIn;
import APIP.apipData.Fcdsl;
import com.google.gson.Gson;
import constants.ApiNames;
import javaTools.Hex;
import javaTools.JsonTools;
import FCH.DataForOffLineTx;
import FCH.fchData.SendTo;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CryptoToolAPIs {

    public static ApipClientData addressesPost(String urlHead, String addrOrPubKey, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(addrOrPubKey);

        String urlTail = ApiNames.APIP21V1Path + ApiNames.AddressesAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData encryptPost(String urlHead, String key, String message, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        EncryptIn encryptIn = new EncryptIn();
        encryptIn.setMsg(message);
        int keyLength = key.length();

        if (keyLength == 64) encryptIn.setSymKey(key);
        else if (keyLength == 66) encryptIn.setPubKey(key);
        else return null;

        fcdsl.setOther(encryptIn);

        String urlTail = ApiNames.APIP21V1Path + ApiNames.EncryptAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData verifyPost(String urlHead, String signature, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        Map<String, String> signMap = new HashMap<>();

        try {
            Type t = JsonTools.getMapType(String.class, String.class);
            signMap = new Gson().fromJson(signature, t);
            fcdsl.setOther(signMap);
        } catch (Exception e) {
            fcdsl.setOther(signature);
        }

        String urlTail = ApiNames.APIP21V1Path + ApiNames.VerifyAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData sha256Post(String urlHead, String text, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(text);

        String urlTail = ApiNames.APIP21V1Path + ApiNames.Sha256API;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData sha256x2Post(String urlHead, String text, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(text);

        String urlTail = ApiNames.APIP21V1Path + ApiNames.Sha256x2API;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData sha256BytesPost(String urlHead, String hex, @Nullable String via, byte[] sessionKey) {
        if (!Hex.isHexString(hex)) {
            System.out.println("Error: It's not a hex.");
            return null;
        }

        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(hex);

        String urlTail = ApiNames.APIP21V1Path + ApiNames.Sha256BytesAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData sha256x2BytesPost(String urlHead, String hex, @Nullable String via, byte[] sessionKey) {
        if (!Hex.isHexString(hex)) {
            System.out.println("Error: It's not a hex.");
            return null;
        }
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(hex);

        String urlTail = ApiNames.APIP21V1Path + ApiNames.Sha256x2BytesAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData offLineTxPost(String urlHead, String fromFid, List<SendTo> sendToList, String msg, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        DataForOffLineTx dataForOffLineTx = new DataForOffLineTx();
        dataForOffLineTx.setFromFid(fromFid);
        dataForOffLineTx.setSendToList(sendToList);
        dataForOffLineTx.setMsg(msg);
        fcdsl.setOther(dataForOffLineTx);

        String urlTail = ApiNames.APIP21V1Path + ApiNames.OffLineTxAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData offLineTxByCdPost(String urlHead, String fromFid, List<SendTo> sendToList, String msg, int cd, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("21");
        Fcdsl fcdsl = new Fcdsl();
        DataForOffLineTx dataForOffLineTx = new DataForOffLineTx();
        dataForOffLineTx.setCd(cd);
        dataForOffLineTx.setFromFid(fromFid);
        dataForOffLineTx.setSendToList(sendToList);
        dataForOffLineTx.setMsg(msg);
        fcdsl.setOther(dataForOffLineTx);

        String urlTail = ApiNames.APIP21V1Path + ApiNames.OffLineTxByCdAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }
}
