package clients.apipClient;

import APIP.apipData.Fcdsl;
import constants.ApiNames;
import constants.FieldNames;
import javaTools.NumberTools;


import javax.annotation.Nullable;

public class WalletAPIs {

    public static ApipClientTask broadcastTxPost(String urlHead, String txHex, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(txHex);

        String urlTail = ApiNames.APIP18V1Path + ApiNames.BroadcastTxAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask decodeRawTxPost(String urlHead, String rawTx, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(rawTx);

        String urlTail = ApiNames.APIP18V1Path + ApiNames.DecodeRawTxAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask cashValidForPayPost(String urlHead, String fid, double amount, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(fid);
        amount = NumberTools.roundDouble8(amount);
        fcdsl.setOther(String.valueOf(amount));
        String urlTail = ApiNames.APIP18V1Path + ApiNames.CashValidForPayAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask cashValidForCdPost(String urlHead, String fid, int cd, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(fid);
        fcdsl.setOther(String.valueOf(cd));
        String urlTail = ApiNames.APIP18V1Path + ApiNames.CashValidForCdAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask unconfirmedPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP18V1Path + ApiNames.UnconfirmedAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }
}
