package clients.apipClient;

import apip.apipData.Fcdsl;
import constants.ApiNames;

import javax.annotation.Nullable;

public class OrganizeAPIs {


    public static ApipClientTask groupByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);


        String urlTail = ApiNames.APIP8V1Path + ApiNames.GroupByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask groupSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        


        String urlTail = ApiNames.APIP8V1Path + ApiNames.GroupSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask groupOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP8V1Path + ApiNames.GroupOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask groupMembersPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP8V1Path + ApiNames.GroupMembersAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask myGroupsPost(String urlHead, String fid, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();

        fcdsl.addNewQuery().addNewTerms().addNewFields("members").addNewValues(fid);

        String urlTail = ApiNames.APIP8V1Path + ApiNames.MyGroupsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }


    public static ApipClientTask teamByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask teamSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask teamOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask teamRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask teamMembersPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamMembersAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask teamExMembersPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamExMembersAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask teamOtherPersonsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamOtherPersonsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask myTeamsPost(String urlHead, String fid, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();

        fcdsl.addNewQuery().addNewTerms().addNewFields("members").addNewValues(fid);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.MyTeamsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

}
