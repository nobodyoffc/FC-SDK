package clients.apipClient;

import APIP.apipData.Fcdsl;
import clients.ApiUrl;
import constants.ApiNames;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import static constants.Strings.HEIGHT;

public class BlockchainAPIs {

    public static ApipClientData blockByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.BlockByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData blockByHeightPost(String urlHead, String[] heights, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(HEIGHT).addNewValues(heights);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.BlockByHeightsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData blockSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.BlockSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData cashValidPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.CashValidAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData cashByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.CashByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData cashSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();

        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.CashSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData fidByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.FidByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData fidSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.FidSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData opReturnByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.OpReturnByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData opReturnSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.OpReturnSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData p2shByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.P2shByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData p2shSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.P2shSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData txByIdsPost(String urlHead, String[] ids, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIds(ids);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.APIP2V1Path + ApiNames.TxByIdsAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static ApipClientData txSearchPost(String urlHead, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        

        String urlTail = ApiNames.APIP2V1Path + ApiNames.TxSearchAPI;

        boolean isGood = apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        if (!isGood) return null;
        return apipClientData;
    }

    public static Fcdsl txByFidQuery(String fid, @Nullable String[] last) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery()
                .addNewTerms()
                .addNewFields("inMarks.fid", "outMarks.fid")
                .addNewValues(fid);
        if (last != null) {
            fcdsl.addNewAfter(last);
        }
        return fcdsl;
    }

    public static ApipClientData getUtxo(String urlHead, String id, double amount) {
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put("address",id);
        paramMap.put("amount", String.valueOf(amount));
        ApipClientData apipClientData = new ApipClientData(urlHead, ApiNames.APIP2V1Path,ApiNames.GetUtxoAPI);
        apipClientData.get();
        return apipClientData;
    }
}
