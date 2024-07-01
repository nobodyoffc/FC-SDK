package apipV1Client;

import apip.apipData.Fcdsl;
import apip.apipData.WebhookRequestBody;
import clients.FcClientEvent;
import constants.ApiNames;

public class WebhookAPIs {
    public static FcClientEvent newCashList(String urlHead, String via, WebhookRequestBody webhookRequestBody, byte[] sessionKey) {
        ApipClientEvent apipClientTask = new ApipClientEvent();
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(webhookRequestBody);
        apipClientTask.setFcdsl(fcdsl);

        String urlTail = ApiNames.APIP20V1Path + ApiNames.NewCashByFidsAPI;

        boolean isGood = apipClientTask.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientTask;
    }
}
