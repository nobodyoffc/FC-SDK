package clients.apipClient;

import apip.apipData.Fcdsl;
import constants.ApiNames;
import constants.FieldNames;
import constants.ReplyCodeMessage;
import crypto.KeyTools;

import javax.annotation.Nullable;

public class IdentityAPIs {

    public static ApipClientTask cidInfoByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.CidInfoByIdsAPI;

        apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        return apipClientData;
    }

    public static ApipClientTask cidInfoSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.CidInfoSearchAPI;

        apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

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

        apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        return apipClientData;
    }

    public static ApipClientTask homepageHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.HomepageHistoryAPI;

        apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

        return apipClientData;
    }

    public static ApipClientTask noticeFeeHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.NoticeFeeHistoryAPI;

        apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

        return apipClientData;
    }

    public static ApipClientTask reputationHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.ReputationHistoryAPI;

         apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

        return apipClientData;
    }

    public static ApipClientTask fidCidSeekPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {

        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        if (fcdsl.getQuery().getPart() == null) {
            System.out.println("This API needs a 'part' query.");
            apipClientData.setCode(ReplyCodeMessage.Code1012BadQuery);
            apipClientData.setMessage(ReplyCodeMessage.Msg1012BadQuery);
            return null;
        }
        if (fcdsl.getQuery().getPart().getFields() == null) {
            System.out.println("This API needs a 'part' query.");
            apipClientData.setCode(ReplyCodeMessage.Code1012BadQuery);
            apipClientData.setMessage(ReplyCodeMessage.Msg1012BadQuery);
            return null;
        }

        String urlTail = ApiNames.APIP3V1Path + ApiNames.FidCidSeekAPI;

         apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

        return apipClientData;
    }

    public static ApipClientTask fidCidSeekPost(String urlHead, String searchStr, @Nullable String via, byte[] sessionKey) {

        ApipClientTask apipClientData = new ApipClientTask();


        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.CID,FieldNames.FID).addNewValue(searchStr);

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.FidCidSeekAPI;

         apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

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

         apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

        return apipClientData;
    }

    public static ApipClientTask nobodySearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.NobodySearchAPI;

         apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

        return apipClientData;
    }

    public static ApipClientTask avatarsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP17V1Path + ApiNames.AvatarsAPI;

         apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);

        return apipClientData;
    }

}
