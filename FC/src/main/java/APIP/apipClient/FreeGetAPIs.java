package APIP.apipClient;

import constants.ApiNames;

public class FreeGetAPIs {

    public static ApipClientData broadcast(String urlHead, String rawTx) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.BroadcastAPI + "?rawTx=" + rawTx);
        apipClientData.get();
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

    public static ApipClientData getAvatar(String urlHead, String fid) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAvatarAPI + "?fid=" + fid);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getCashes(String urlHead, String id, double amount) {
        ApipClientData apipClientData = new ApipClientData();
        String urlTail = ApiNames.FreeGetPath + ApiNames.GetCashesAPI;
        if (id != null) urlTail = urlTail + "?fid=" + id;
        if (amount != 0) urlTail = urlTail + "&amount=" + amount;
        apipClientData.addNewApipUrl(urlHead, urlTail);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getFidCid(String urlHead, String id) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetFidCidAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getFreeService(String urlHead) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetFreeServiceAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getTotals(String urlHead) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetTotalsAPI);
        apipClientData.get();
        return apipClientData;
    }
}
