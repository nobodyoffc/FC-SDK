package clients.apipClient;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import constants.ApiNames;

import javax.annotation.Nullable;
import java.io.IOException;

public class OpenAPIs {
    public static ApipClientTask getService(String urlHead) {
        ApipClientTask apipClientData = new ApipClientTask();
        apipClientData.addNewApipUrl(urlHead, ApiNames.APIP0V1Path + ApiNames.GetServiceAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientTask signInPost(String urlHead, String via, byte[] priKey, RequestBody.SignInMode mode) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        String urlTail = ApiNames.APIP0V1Path + ApiNames.SignInAPI;
        doSignIn(apipClientData, urlHead, via, priKey, urlTail, mode);

        return apipClientData;
    }

    public static ApipClientTask signInEccPost(String urlHead, @Nullable String via, byte[] priKey, @Nullable RequestBody.SignInMode modeNullOrRefresh) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        String urlTail = ApiNames.APIP0V1Path + ApiNames.SignInEccAPI;
        doSignIn(apipClientData, urlHead, via, priKey, urlTail, modeNullOrRefresh);

        return apipClientData;
    }

    @Nullable
    private static ApipClientTask doSignIn(ApipClientTask apipClientData, String urlHead, @Nullable String via, byte[] priKey, String urlTail, @Nullable RequestBody.SignInMode mode) {

        try {
            apipClientData.asySignPost(urlHead, urlTail, via, null, priKey, mode);
        } catch (IOException e) {
            System.out.println("Do post wrong.");
            return null;
        }

        return apipClientData;
    }

    public static ApipClientTask totalsGet(String urlHead) {
        ApipClientTask apipClientData = new ApipClientTask();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetTotalsAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientTask totalsPost(String urlHead, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        
        String urlTail = ApiNames.APIP0V1Path + ApiNames.TotalsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, apipClientData.getRawFcdsl(), via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientTask generalPost(String index, String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientTask apipClientData = new ApipClientTask();
        if (index == null) {
            System.out.println("The index name is required.");
            return null;
        }
        if (fcdsl == null) fcdsl = new Fcdsl();

        fcdsl.setIndex(index);

        String urlTail = ApiNames.APIP1V1Path + ApiNames.GeneralAPI;
        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;

        return apipClientData;
    }
}