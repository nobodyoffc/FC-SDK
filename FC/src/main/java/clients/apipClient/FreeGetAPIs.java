package clients.apipClient;

import constants.ApiNames;
import constants.ReplyInfo;

public class FreeGetAPIs {

    public static ApipClientTask broadcast(String urlHead, String rawTx) {
        ApipClientTask apipClientData = new ApipClientTask();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.BroadcastAPI + "?rawTx=" + rawTx);
        apipClientData.get();
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

    public static ApipClientTask getAvatar(String urlHead, String fid) {
        ApipClientTask apipClientData = new ApipClientTask();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetAvatarAPI + "?fid=" + fid);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientTask getCashes(String urlHead, String id, double amount) {
        ApipClientTask apipClientData = new ApipClientTask();
        String urlTail = ApiNames.FreeGetPath + ApiNames.GetCashesAPI;
        if (id != null) urlTail = urlTail + "?fid=" + id;
        if (amount != 0) {
            if(id==null){
                apipClientData.setCode(ReplyInfo.Code1021FidIsRequired);
                apipClientData.setMessage(ReplyInfo.Msg1021FidIsRequired);
                return apipClientData;
            }
            urlTail = urlTail + "&amount=" + amount;
        }
        apipClientData.addNewApipUrl(urlHead, urlTail);
        apipClientData.get();

        return apipClientData;
    }

    public static ApipClientTask getFidCid(String urlHead, String id) {
        ApipClientTask apipClientData = new ApipClientTask();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetFidCidAPI + "?id=" + id);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientTask getFreeService(String urlHead) {
        ApipClientTask apipClientData = new ApipClientTask();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetFreeServiceAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientTask getTotals(String urlHead) {
        ApipClientTask apipClientData = new ApipClientTask();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetTotalsAPI);
        apipClientData.get();
        return apipClientData;
    }
}
