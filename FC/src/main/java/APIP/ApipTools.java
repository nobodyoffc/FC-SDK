package APIP;

import APIP.apipClient.*;
import FCH.fchData.Cash;
import config.ApiAccount;
import APIP.apipData.ApipParams;
import FEIP.feipClient.IdentityFEIPs;
import APIP.apipData.CidInfo;


import FEIP.feipData.Service;
import com.google.gson.Gson;
import constants.ApiNames;
import crypto.eccAes256K1P7.EccAes256K1P7;

import crypto.cryptoTools.KeyTools;
import crypto.cryptoTools.Hash;
import javaTools.BytesTools;
import javaTools.Hex;
import FCH.Inputer;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;


import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static constants.ApiNames.apiList;
import static constants.ApiNames.freeApiList;
import static constants.Strings.*;
import static crypto.cryptoTools.KeyTools.priKeyToFid;

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

    public static String getSessionKeySign(byte[] sessionKeyBytes, byte[] dataBytes) {
        return HexFormat.of().formatHex(Hash.Sha256x2(BytesTools.bytesMerger(dataBytes, sessionKeyBytes)));
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



    public static void checkMaster(String priKeyCipher, String masterFidOrPubKey, byte[] initSymKey, ApiAccount apiAccount, BufferedReader br) {
        byte[] priKey = EccAes256K1P7.decryptJsonBytes(priKeyCipher, initSymKey);
        if (priKey == null) {
            throw new RuntimeException("Failed to decrypt priKey.");
        }
        String fid = priKeyToFid(priKey);
        byte[] sessionKey = apiAccount.decryptSessionKey(apiAccount.getSessionKeyCipher(), initSymKey.clone());
        CidInfo cidInfo = ApipClient.getCidInfo(fid, apiAccount, sessionKey);
        if (cidInfo == null) {
            System.out.println("This fid was never seen on chain. Send some fch to it.");
            if (Inputer.askIfYes(br, "Stop to send? y/n")) System.exit(0);
        }
        if (cidInfo != null) {
            if (cidInfo.getMaster() != null) {
                System.out.println("The master of the dealer is " + cidInfo.getMaster());
                return;
            }
            if (Inputer.askIfYes(br, "Assign a master for " + fid + "? y/n:")) {
                if (getFreeCashes(apiAccount.getApiUrl(), fid) == null) return;
                String master;
                while (true) {
                    if (masterFidOrPubKey != null) {
                        master = masterFidOrPubKey;
                    } else master = Inputer.inputString(br, "Input the master FID or pubKey:");

                    if (KeyTools.isValidPubKey(master) || KeyTools.isValidFchAddr(master)) {
                        String result = IdentityFEIPs.setMasterOnChain(priKeyCipher, master, apiAccount, sessionKey, initSymKey);
                        if (result == null) System.out.println("Failed to set master.");
                        if (master.length() > 34) master = KeyTools.pubKeyToFchAddr(master);
                        if (Hex.isHexString(result))
                            System.out.println("Master " + master + " was set at txId: " + result);
                        else System.out.println(result);
                        break;
                    }
                }
            }
        } else {
            System.out.println("Failed to get CID information of " + fid + ".");

        }
    }
    public static List<Cash> getFreeCashes(String apiUrl, String fid) {
        ApipClientData apipClientData = FreeGetAPIs.getCashes(apiUrl, fid, 0);
        if (apipClientData.isBadResponse("get cashes")) {
            return null;
        }
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
        if(apipClientData.isBadResponse("get service from "+urlHead))return null;
        Gson gson = new Gson();
        Service service = gson.fromJson(gson.toJson(apipClientData.getResponseBody().getData()),Service.class);
        ApipParams apipParams = ApipParams.fromObject(service);
        service.setParams(apipParams);

        return service;
    }

    @Nullable
    public static Map<String, Service> parseApipServiceMap(ApipClientData apipClientData) {
        if (apipClientData.isBadResponse("get service")) {
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
