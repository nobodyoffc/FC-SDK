package APIP.apipClient;

import APIP.apipData.Fcdsl;
import constants.ApiNames;

import javax.annotation.Nullable;

public class OrganizeAPIs {


    public static ApipClientData groupByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("8");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);


        String urlTail = ApiNames.APIP8V1Path + ApiNames.GroupByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData groupSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("8");


        String urlTail = ApiNames.APIP8V1Path + ApiNames.GroupSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData groupOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("8");

        String urlTail = ApiNames.APIP8V1Path + ApiNames.GroupOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData groupMembersPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("8");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP8V1Path + ApiNames.GroupMembersAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData myGroupsPost(String urlHead, String fid, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("8");
        Fcdsl fcdsl = new Fcdsl();

        fcdsl.addNewQuery().addNewTerms().addNewFields("members").addNewValues(fid);

        String urlTail = ApiNames.APIP8V1Path + ApiNames.MyGroupsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }


    public static ApipClientData teamByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("9");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData teamSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("9");

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData teamOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("9");

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData teamRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("9");

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData teamMembersPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("9");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamMembersAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData teamExMembersPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("9");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamExMembersAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData teamOtherPersonsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("9");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.TeamOtherPersonsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData myTeamsPost(String urlHead, String fid, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("9");
        Fcdsl fcdsl = new Fcdsl();

        fcdsl.addNewQuery().addNewTerms().addNewFields("members").addNewValues(fid);

        String urlTail = ApiNames.APIP9V1Path + ApiNames.MyTeamsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

}
