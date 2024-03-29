package APIP.apipClient;

import APIP.apipData.Fcdsl;
import constants.ApiNames;

import javax.annotation.Nullable;

public class ConstructAPIs {

    public static ApipClientData protocolByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("4");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData protocolSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("4");

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData protocolOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("4");

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData protocolRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("4");
        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData codeByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("5");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData codeSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("5");

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData codeOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("5");

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData codeRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("5");
        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }


    public static ApipClientData serviceByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("6");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData serviceSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("6");

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData serviceOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("6");

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData serviceRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("6");
        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }


    public static ApipClientData appByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("7");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData appSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("7");

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData appOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("7");

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData appRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("7");
        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData getApps(String urlHead, String id) {
        ApipClientData apipClientData = new ApipClientData();
        if (id == null) apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAppsAPI);
        else apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAppsAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getServices(String urlHead, String id) {
        ApipClientData apipClientData = new ApipClientData();
        if (id == null) apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetServicesAPI);
        else apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetServicesAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }
}
