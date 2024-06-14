package clients.apipClient;

import apip.apipData.Fcdsl;
import constants.ApiNames;

import javax.annotation.Nullable;

public class PersonalAPIs {
    public static ApipClientTask boxByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP10V1Path + ApiNames.BoxByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask boxSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP10V1Path + ApiNames.BoxSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask boxHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP10V1Path + ApiNames.BoxHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask contactByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP11V1Path + ApiNames.ContactByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask contactsPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP11V1Path + ApiNames.ContactsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask contactsDeletedPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP11V1Path + ApiNames.ContactsDeletedAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask secretByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP12V1Path + ApiNames.SecretByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask secretsPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP12V1Path + ApiNames.SecretsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask secretsDeletedPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP12V1Path + ApiNames.SecretsDeletedAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask mailByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP13V1Path + ApiNames.MailByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask mailsPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP13V1Path + ApiNames.MailsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask mailsDeletedPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP13V1Path + ApiNames.MailsDeletedAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask mailThreadPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP13V1Path + ApiNames.MailThreadAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }
}
