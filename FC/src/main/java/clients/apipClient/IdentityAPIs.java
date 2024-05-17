package clients.apipClient;

import APIP.apipData.Fcdsl;
import constants.ApiNames;
import constants.FieldNames;
import constants.ReplyInfo;
import crypto.cryptoTools.KeyTools;

import javax.annotation.Nullable;

public class IdentityAPIs {

    public static ApipClientTask cidInfoByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.CidInfoByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask cidInfoSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.CidInfoSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask cidInfoSearchPost(String urlHead, String str, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        Fcdsl fcdsl = new Fcdsl();

        fcdsl.addNewQuery()
                .addNewPart()
                .addNewFields("fid", "usedCids", "pubKey", "btcAddr", "ethAddr", "dogeAddr", "ltcAddr", "trxAddr")
                .addNewValue(str);

        return cidInfoSearchPost(urlHead, fcdsl, via, sessionKey);
    }


    public static ApipClientTask cidHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();



        String urlTail = ApiNames.APIP3V1Path + ApiNames.CidHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask homepageHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.HomepageHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask noticeFeeHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.NoticeFeeHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask reputationHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.ReputationHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask fidCidSeekPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {

        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        if (fcdsl.getQuery().getPart() == null) {
            System.out.println("This API needs a 'part' query.");
            apipClientData.setCode(ReplyInfo.Code1012BadQuery);
            apipClientData.setMessage(ReplyInfo.Msg1012BadQuery);
            return null;
        }
        if (fcdsl.getQuery().getPart().getFields() == null) {
            System.out.println("This API needs a 'part' query.");
            apipClientData.setCode(ReplyInfo.Code1012BadQuery);
            apipClientData.setMessage(ReplyInfo.Msg1012BadQuery);
            return null;
        }

        String urlTail = ApiNames.APIP3V1Path + ApiNames.FidCidSeekAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask fidCidSeekPost(String urlHead, String searchStr, @Nullable String via, byte[] sessionKey) {

        ApipClientTask apipClientData = new ApipClientTask();


        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.CID,FieldNames.FID).addNewValue(searchStr);

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.FidCidSeekAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask fidCidGetFree(String urlHead, String id) {
        ApipClientTask apipClientData = new ApipClientTask();

        if (!id.contains("_")) {
            if (!KeyTools.isValidFchAddr(id)) {
                System.out.println("Bad id.");
                return null;
            }
        }

        apipClientData.addNewApipUrl(urlHead, ApiNames.APIP3V1Path + ApiNames.GetFidCidAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientTask nobodyByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.NobodyByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask nobodySearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.NobodySearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask avatarsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP17V1Path + ApiNames.AvatarsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

}
