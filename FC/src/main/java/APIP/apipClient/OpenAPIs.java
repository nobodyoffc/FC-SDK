package APIP.apipClient;

import APIP.apipData.Fcdsl;
import constants.ApiNames;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.IOException;

public class OpenAPIs {
    public static ApipClientData getService(String urlHead) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.APIP0V1Path + ApiNames.GetServiceAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData signInPost(String urlHead, String via, byte[] priKey, String mode) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("0");
        String urlTail = ApiNames.APIP0V1Path + ApiNames.SignInAPI;
        doSignIn(apipClientData, urlHead, via, priKey, urlTail, mode);

        return apipClientData;
    }

    public static ApipClientData signInEccPost(String urlHead, @Nullable String via, byte[] priKey, @Nullable String mode) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("0");
        String urlTail = ApiNames.APIP0V1Path + ApiNames.SignInEccAPI;
        doSignIn(apipClientData, urlHead, via, priKey, urlTail, mode);

        return apipClientData;
    }

    @Nullable
    private static ApipClientData doSignIn(ApipClientData apipClientData, String urlHead, @Nullable String via, byte[] priKey, String urlTail, @Nullable String mode) {

        try {
            apipClientData.asySignPost(urlHead, urlTail, via, null, priKey, mode);
        } catch (IOException e) {
            System.out.println("Do post wrong.");
            return null;
        }

        return apipClientData;
    }

    public static ApipClientData totalsGet(String urlHead) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.FreeGetPath + ApiNames.GetTotalsAPI);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData totalsPost(String urlHead, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("0");
        String urlTail = ApiNames.APIP0V1Path + ApiNames.TotalsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, apipClientData.getRawFcdsl(), via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData generalPost(String index, String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("1");
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