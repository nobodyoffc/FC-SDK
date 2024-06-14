package clients.apipClient;

import apip.apipData.Fcdsl;
import apip.apipData.WebhookRequestBody;
import clients.ClientTask;
import constants.ApiNames;

public class WebhookAPIs {
    public static ClientTask newCashList(String urlHead, String via, WebhookRequestBody webhookRequestBody, byte[] sessionKey) {
        ApipClientTask apipClientTask = new ApipClientTask();
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(webhookRequestBody);
        apipClientTask.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP20V1Path + ApiNames.NewCashByFidsAPI;

        boolean isGood = apipClientTask.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientTask;
    }
}
