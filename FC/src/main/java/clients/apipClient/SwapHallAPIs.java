package clients.apipClient;

import APIP.apipData.Fcdsl;
import clients.ClientData;
import constants.ApiNames;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static clients.ClientData.AuthType.FC_SIGN_BODY;
import static constants.FieldNames.*;
import static javaTools.StringTools.arrayToString;

public class SwapHallAPIs {

    public static ApipClientData swapRegisterPost(String urlHead, String sid, @Nullable String via, byte[] sessionKey) {
        Fcdsl fcdsl = new Fcdsl();
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put(SID, sid);
        fcdsl.setOther(dataMap);
        ApipClientData apipClientData = new ApipClientData(sessionKey,urlHead,ApiNames.SwapHallPath,ApiNames.SwapRegisterAPI, fcdsl, FC_SIGN_BODY, via);
        apipClientData.postWithFcdsl(sessionKey);
        return apipClientData;
    }

    public static ApipClientData swapUpdatePost(String urlHead, Map<String, Object> uploadMap, @Nullable String via, byte[] sessionKey) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(uploadMap);
        ApipClientData apipClientData = new ApipClientData(sessionKey,urlHead,ApiNames.SwapHallPath,ApiNames.SwapUpdateAPI, fcdsl,  FC_SIGN_BODY, via);
        apipClientData.postWithFcdsl(sessionKey);
        return apipClientData;
    }

    public static ApipClientData getSwapInfo(String urlHead, @Nullable String[] sid, @Nullable String[] last) {

        Map<String,String>paramMap = new HashMap<>();
        if (sid != null) {
            String sidStr = arrayToString(sid);
            paramMap.put(SID,sidStr);
        } else if (last != null) {
            String lastStr = arrayToString(last);
            paramMap.put(LAST,lastStr);
        }

        ApipClientData apipClientData=new ApipClientData(urlHead, ApiNames.SwapHallPath,ApiNames.SwapInfoAPI);
        if(!paramMap.isEmpty()) apipClientData=new ApipClientData(urlHead, ApiNames.SwapHallPath,ApiNames.SwapInfoAPI,paramMap);

        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapState(String urlHead, @NotNull String sid) {
        Map<String,String>paramMap = new HashMap<>();
        paramMap.put(SID,sid);
        ApipClientData apipClientData=new ApipClientData(urlHead, ApiNames.SwapHallPath,ApiNames.SwapStateAPI,paramMap);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapLp(String urlHead, @NotNull String sid) {
        Map<String,String>paramMap = new HashMap<>();
        paramMap.put(SID,sid);
        ApipClientData apipClientData=new ApipClientData(urlHead, ApiNames.SwapHallPath,ApiNames.SwapLpAPI,paramMap);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapFinished(String urlHead, @Nullable  String sid, @Nullable String[] last) {

        Map<String,String>paramMap = new HashMap<>();
        if (sid != null) {
            paramMap.put(SID,sid);
        } else if (last != null) {
            String lastStr = arrayToString(last);
            paramMap.put(LAST,lastStr);
        }

        ApipClientData apipClientData=new ApipClientData(urlHead, ApiNames.SwapHallPath,ApiNames.SwapFinishedAPI);
        if(!paramMap.isEmpty()) apipClientData=new ApipClientData(urlHead, ApiNames.SwapHallPath,ApiNames.SwapFinishedAPI,paramMap);

        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapPending(String urlHead, @NotNull String sid) {
        Map<String,String>paramMap = new HashMap<>();
        paramMap.put(SID,sid);
        ApipClientData apipClientData=new ApipClientData(urlHead, ApiNames.SwapHallPath,ApiNames.SwapPendingAPI,paramMap);

        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapPrice(String urlHead, String sid, String gTick, String mTick, String[] last) {

        Map<String,String>paramMap = new HashMap<>();

//        StringBuilder urlTailBuilder = new StringBuilder();
//        urlTailBuilder.append(ApiNames.SwapHallPath + ApiNames.SwapInfoAPI);

        if (sid != null) {
            paramMap.put(SID,sid);
        } else {
            if (gTick != null)
                paramMap.put(G_TICK,gTick);
            if (mTick != null) {
                paramMap.put(M_TICK,mTick);
            }
        }
        if (last != null) {
            String lastStr = arrayToString(last);
            paramMap.put(LAST,lastStr);
        }
        ApipClientData apipClientData=new ApipClientData(urlHead, ApiNames.SwapHallPath,ApiNames.SwapPendingAPI,paramMap);

        apipClientData.get();
        return apipClientData;
    }

}
