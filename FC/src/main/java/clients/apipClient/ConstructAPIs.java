package clients.apipClient;

import apip.apipData.Fcdsl;
import constants.ApiNames;

import javax.annotation.Nullable;

public class ConstructAPIs {

    public static ApipClientTask protocolByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask protocolSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask protocolOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask protocolRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        String urlTail = ApiNames.APIP4V1Path + ApiNames.ProtocolRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask codeByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask codeSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask codeOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask codeRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        String urlTail = ApiNames.APIP5V1Path + ApiNames.CodeRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }


    public static ApipClientTask serviceByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientTask = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceByIdsAPI;

        boolean isGood = apipClientTask.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientTask;
    }

    public static ApipClientTask serviceSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask serviceOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask serviceRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        String urlTail = ApiNames.APIP6V1Path + ApiNames.ServiceRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }


    public static ApipClientTask appByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask appSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask appOpHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        

        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppOpHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask appRateHistoryPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        String urlTail = ApiNames.APIP7V1Path + ApiNames.AppRateHistoryAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask getApps(String urlHead, String id) {
        ApipClientTask apipClientData = new ApipClientTask();
        if (id == null) apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAppsAPI);
        else apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAppsAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientTask getServices(String urlHead, String id) {
        ApipClientTask apipClientData = new ApipClientTask();
        if (id == null) apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetServicesAPI);
        else apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetServicesAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }
}
