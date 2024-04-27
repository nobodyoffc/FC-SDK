package clients.apipClient;

import APIP.apipData.Fcdsl;
import constants.ApiNames;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static constants.FieldNames.SID;
import static javaTools.StringTools.arrayToString;

public class SwapHallAPIs {

    public static ApipClientData swapRegisterPost(String urlHead, String sid, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("");
        Fcdsl fcdsl = new Fcdsl();
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put(SID, sid);
        fcdsl.setOther(dataMap);
        apipClientData.setRawFcdsl(fcdsl);

        String urlTail = ApiNames.SwapHallPath + ApiNames.SwapRegisterAPI;

        apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        return apipClientData;
    }

    public static ApipClientData swapUpdatePost(String urlHead, Map<String, Object> uploadMap, @Nullable String via, byte[] sessionKey) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.setSn("");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setOther(uploadMap);
        apipClientData.setRawFcdsl(fcdsl);
        String urlTail = ApiNames.SwapHallPath + ApiNames.SwapUpdateAPI;
        apipClientData.post(urlHead, urlTail, fcdsl, via, sessionKey);
        return apipClientData;
    }

    public static ApipClientData getSwapInfo(String urlHead, String[] sid, String[] last) {
        ApipClientData apipClientData = new ApipClientData();
        if (sid != null) {
            String sidStr = arrayToString(sid);
            apipClientData.addNewApipUrl(urlHead, ApiNames.SwapHallPath + ApiNames.SwapInfoAPI + "?sid=" + sidStr);
        } else if (last != null) {
            String lastStr = arrayToString(last);
            apipClientData.addNewApipUrl(urlHead, ApiNames.SwapHallPath + ApiNames.SwapInfoAPI + "?last=" + lastStr);
        } else
            apipClientData.addNewApipUrl(urlHead, ApiNames.SwapHallPath + ApiNames.SwapInfoAPI);

        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapState(String urlHead, @NotNull String sid) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.SwapHallPath + ApiNames.SwapStateAPI + "?sid=" + sid);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapLp(String urlHead, @NotNull String sid) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.SwapHallPath + ApiNames.SwapLpAPI + "?sid=" + sid);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapFinished(String urlHead, @NotNull String sid, @Nullable String[] last) {
        ApipClientData apipClientData = new ApipClientData();
        if (last == null) {
            apipClientData.addNewApipUrl(urlHead, ApiNames.SwapHallPath + ApiNames.SwapInfoAPI + "?sid=" + sid);
        } else {
            String lastStr = arrayToString(last);
            apipClientData.addNewApipUrl(urlHead, ApiNames.SwapHallPath + ApiNames.SwapInfoAPI + "?sid=" + sid + "&last=" + lastStr);
        }

        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapPending(String urlHead, @NotNull String sid) {
        ApipClientData apipClientData = new ApipClientData();
        apipClientData.addNewApipUrl(urlHead, ApiNames.SwapHallPath + ApiNames.SwapPendingAPI + "?sid=" + sid);
        apipClientData.get();
        return apipClientData;
    }

    public static ApipClientData getSwapPrice(String urlHead, String sid, String gTick, String mTick, String[] last) {
        ApipClientData apipClientData = new ApipClientData();

        StringBuilder urlTailBuilder = new StringBuilder();
        urlTailBuilder.append(ApiNames.SwapHallPath + ApiNames.SwapInfoAPI);

        if (sid != null) {
            urlTailBuilder.append("?sid=").append(sid);
            if (last != null) {
                String lastStr = arrayToString(last);
                urlTailBuilder.append("&last=").append(lastStr);
            }
        } else {
            if (gTick != null)
                urlTailBuilder.append("?gTick=").append(gTick);
            if (mTick != null) {
                if (gTick != null)
                    urlTailBuilder.append("&mTick=").append(mTick);
                else urlTailBuilder.append("?mTick=").append(mTick);
            }
        }
        if (last != null) {
            String lastStr = arrayToString(last);
            if (sid == null && gTick == null && mTick == null) {
                urlTailBuilder.append("&last=").append(lastStr);
            } else urlTailBuilder.append("?last=").append(lastStr);
        }

        apipClientData.addNewApipUrl(urlHead, urlTailBuilder.toString());
        apipClientData.get();
        return apipClientData;
    }

}
