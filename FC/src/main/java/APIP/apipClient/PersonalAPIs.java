package APIP.apipClient;

import APIP.apipData.Fcdsl;
import constants.ApiNames;

import javax.annotation.Nullable;

public class PersonalAPIs {
    public static ApipClientData boxByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("10");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP10V1Path + ApiNames.BoxByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData boxSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("10");

        String urlTail = ApiNames.APIP10V1Path + ApiNames.BoxSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData boxHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("10");

        String urlTail = ApiNames.APIP10V1Path + ApiNames.BoxHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData contactByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("11");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP11V1Path + ApiNames.ContactByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData contactsPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("11");

        String urlTail = ApiNames.APIP11V1Path + ApiNames.ContactsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData contactsDeletedPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("11");

        String urlTail = ApiNames.APIP11V1Path + ApiNames.ContactsDeletedAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData secretByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("12");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP12V1Path + ApiNames.SecretByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData secretsPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("12");

        String urlTail = ApiNames.APIP12V1Path + ApiNames.SecretsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData secretsDeletedPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("12");

        String urlTail = ApiNames.APIP12V1Path + ApiNames.SecretsDeletedAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData mailByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("13");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP13V1Path + ApiNames.MailByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData mailsPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("13");

        String urlTail = ApiNames.APIP13V1Path + ApiNames.MailsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData mailsDeletedPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("13");

        String urlTail = ApiNames.APIP13V1Path + ApiNames.MailsDeletedAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData mailThreadPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("13");

        String urlTail = ApiNames.APIP13V1Path + ApiNames.MailThreadAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }
}
