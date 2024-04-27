package APIP;

import FCH.fchData.Cash;
import clients.apipClient.ApipClientData;
import clients.apipClient.ApipDataGetter;
import clients.apipClient.FreeGetAPIs;
import clients.apipClient.OpenAPIs;
import FEIP.feipData.serviceParams.ApipParams;


import FEIP.feipData.Service;
import com.google.gson.Gson;
import constants.ApiNames;

import crypto.cryptoTools.Hash;
import javaTools.BytesTools;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;


import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static constants.ApiNames.apiList;
import static constants.ApiNames.freeApiList;
import static constants.Strings.*;

public class ApipTools {


    public static String getApiNameFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex != url.length() - 1) {
            String name = url.substring(lastSlashIndex + 1);
            if (apiList.contains(name) || freeApiList.contains(name)) {
                return name;
            }
            return "";
        } else {
            return "";  // Return empty string if '/' is the last character or not found
        }

    }

    public static int getNPrice(String apiName, Jedis jedis) {
        try {
            return Integer.parseInt(jedis.hget(N_PRICE, apiName));
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean isGoodSign(String requestBody, String sign, String symKey) {
        byte[] requestBodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        return isGoodSign(requestBodyBytes, sign, HexFormat.of().parseHex(symKey));
    }

    public static boolean isGoodSign(byte[] requestBodyBytes, String sign, byte[] symKey) {
        if (sign == null || requestBodyBytes == null) return false;
        byte[] signBytes = BytesTools.bytesMerger(requestBodyBytes, symKey);
        String doubleSha256Hash = HexFormat.of().formatHex(Hash.Sha256x2(signBytes));
        return (sign.equals(doubleSha256Hash));
    }

    public static String getSessionName(byte[] sessionKey) {
        if (sessionKey == null) return null;
        return HexFormat.of().formatHex(Arrays.copyOf(sessionKey, 6));
    }

    public static List<Cash> getFreeCashes(String apiUrl, String fid) {
        ApipClientData apipClientData = FreeGetAPIs.getCashes(apiUrl, fid, 0);
        if(apipClientData.checkResponse()!=0)return null;

        List<Cash> cashList = ApipDataGetter.getCashList(apipClientData.getResponseBody().getData());
        if (cashList == null || cashList.isEmpty()) {
            System.out.println("No FCH of " + fid + ". Send at lest 0.001 fch to it.");
            return null;
        }
        return cashList;
    }


    @Nullable


    public static Service getApipService(String urlHead){
        if(urlHead.contains(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI))
            urlHead.replaceAll(ApiNames.APIP0V1Path + ApiNames.GetServiceAPI,"");

        ApipClientData apipClientData = OpenAPIs.getService(urlHead);
        if(apipClientData.checkResponse()!=0)return null;
        Gson gson = new Gson();
        Service service = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()),Service.class);
        ApipParams apipParams = ApipParams.fromObject(service.getParams());
        service.setParams(apipParams);

        return service;
    }

    @Nullable
    public static Map<String, Service> parseApipServiceMap(ApipClientData apipClientData) {
        if(apipClientData.checkResponse()!=0) {
            System.out.println("Failed to buy APIP service. Code:"+ apipClientData.getCode()+", Message:"+ apipClientData.getMessage());
            return null;
        }

        try {
            Map<String, Service> serviceMap = ApipDataGetter.getServiceMap(apipClientData.getResponseBody().getData());
            if(serviceMap==null) return null;
            for(String sid :serviceMap.keySet()) {
                Service service = serviceMap.get(sid);
                ApipParams apipParams = ApipParams.fromObject(service.getParams());
                service.setParams(apipParams);
            }
            return serviceMap;
        } catch (Exception e) {
            System.out.println("Failed to get APIP service.");
            e.printStackTrace();
            return null;
        }
    }
}
