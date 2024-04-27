package clients.apipClient;

import APIP.apipData.Fcdsl;
import constants.ApiNames;
import constants.FieldNames;
import constants.ReplyInfo;
import crypto.cryptoTools.KeyTools;

import javax.annotation.Nullable;

public class IdentityAPIs {

    public static ApipClientData cidInfoByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.CidInfoByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData cidInfoSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.CidInfoSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData cidInfoSearchPost(String urlHead, String str, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");
        Fcdsl fcdsl = new Fcdsl();

        fcdsl.addNewQuery()
                .addNewPart()
                .addNewFields("fid", "usedCids", "pubKey", "btcAddr", "ethAddr", "dogeAddr", "ltcAddr", "trxAddr")
                .addNewValue(str);

        return cidInfoSearchPost(urlHead, fcdsl, via, sessionKey);
    }


    public static ApipClientData cidHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");


        String urlTail = ApiNames.APIP3V1Path + ApiNames.CidHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData homepageHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.HomepageHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData noticeFeeHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.NoticeFeeHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData reputationHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.ReputationHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData fidCidSeekPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {

        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");
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

    public static ApipClientData fidCidSeekPost(String urlHead, String searchStr, @Nullable String via, byte[] sessionKey) {

        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(FieldNames.CID,FieldNames.FID).addNewValue(searchStr);

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.FidCidSeekAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData fidCidGetFree(String urlHead, String id) {
        ApipClientData apipClientData = new ApipClientData();

        if (!id.contains("_")) {
            if (!KeyTools.isValidFchAddr(id)) {
                System.out.println("Bad id.");
                return null;
            }
        }
        apipClientData.setSn("3");
        apipClientData.addNewApipUrl(urlHead, ApiNames.APIP3V1Path + ApiNames.GetFidCidAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData nobodyByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.NobodyByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData nobodySearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("3");
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP3V1Path + ApiNames.NobodySearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData avatarsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("17");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP17V1Path + ApiNames.AvatarsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

}
