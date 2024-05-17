package clients.apipClient;

import APIP.apipData.*;

import clients.ApiUrl;
import clients.ClientTask;
import com.google.gson.Gson;
import javaTools.http.AuthType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ApipClientTask extends ClientTask {
    public ApipClientTask() {
    }
    public ApipClientTask(String url) {
        super(url);
    }
    public ApipClientTask(String urlHead, String urlTail) {
        super(urlHead, urlTail);
    }

    public ApipClientTask(String urlHead, String urlTailPath, String apiName) {
        super(urlHead, urlTailPath, apiName);
    }

    public ApipClientTask(String urlHead, String type, String sn, String version, String apiName) {
        super(urlHead,type, sn, version, apiName);
    }
    public ApipClientTask(String urlHead, String type, String sn, String ver, String apiName, Map<String,String>paramMap) {
        super( urlHead, type, sn, ver, apiName, paramMap);
    }
    public ApipClientTask(byte[] authKey, String urlHead, String type, String sn, String ver, String apiName, String via, AuthType authType, Map<String,String>paramMap) {
        super(authKey, urlHead, type, sn, ver, apiName, via, authType, paramMap);
    }
    public ApipClientTask(String urlHead, String urlTailPath, String apiName, Map<String,String>paramMap) {
        super(urlHead, urlTailPath, apiName,paramMap);
    }
    public ApipClientTask(byte[] authKey, String urlHead, String urlTailPath, String apiName, Fcdsl fcdsl, AuthType authType, String via) {
        super(authKey,urlHead,urlTailPath,apiName,fcdsl,authType,via);
    }


    public void addNewApipUrl(String urlHead, String urlTail) {
        apiUrl=new ApiUrl();
        apiUrl.setUrl(ApiUrl.makeUrl(urlHead,urlTail));
    }

    public void asySignPost(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl, byte[] priKey, @Nullable RequestBody.SignInMode mode) throws IOException {
        makeFcdslRequest(urlHead, urlTail, via, fcdsl, mode);
        makeHeaderAsySign(priKey);
        post();
        if(responseBody!=null){
            code = responseBody.getCode();
            message = responseBody.getMessage();
        }
    }
    public boolean post(String urlHead, String urlTail, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        rawFcdsl = fcdsl;
        makeFcdslRequest(urlHead, urlTail, via, fcdsl);
        makeHeaderSession(sessionKey,requestBodyBytes);
        return post(sessionKey, RequestBodyType.STRING);
    }
//    private void makeHeaderAsySign(byte[] priKey) {
//        if (priKey == null) return;
//
//        ECKey ecKey = ECKey.fromPrivate(priKey);
//
//        requestBodyStr = new String(requestBodyBytes, StandardCharsets.UTF_8);
//        String sign = ecKey.signMessage(requestBodyStr);
//        String fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
//
//        signatureRequest = new Signature(fid, requestBodyStr, sign, Algorithm.EccAes256K1P7_No1_NrC7.name());
//        requestHeaderMap.put(UpStrings.FID, fid);
//        requestHeaderMap.put(UpStrings.SIGN, signatureRequest.getSign());
//    }

    private void makeFcdslRequest(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl, @Nullable RequestBody.SignInMode mode) {
        String url = ApiUrl.makeUrl(urlHead,urlTail);
        requestBody = new RequestBody(url, via, mode);
        requestBody.setFcdsl(fcdsl);

        Gson gson = new Gson();
        String requestBodyJson = gson.toJson(requestBody);
        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

//    public void doPost() {
//        CloseableHttpResponse httpResponse;
//
//        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
//
//            HttpPost httpPost = new HttpPost(url);
//            if (requestHeaderMap != null) {
//                for (String key : requestHeaderMap.keySet()) {
//                    httpPost.setHeader(key, requestHeaderMap.get(key));
//                }
//            }
//
//            StringEntity entity = new StringEntity(new String(requestBodyBytes));
//
//            httpPost.setEntity(entity);
//
//            httpResponse = httpClient.execute(httpPost);
//
//            if (httpResponse == null) {
//                log.debug("httpResponse == null.");
//                code = ReplyInfo.Code3001ResponseIsNull;
//                message = ReplyInfo.Msg3001ResponseIsNull;
//                return;
//            }
//
//            if (httpResponse.getStatusLine().getStatusCode() != 200) {
//                log.debug("Post response status: {}.{}", httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase());
//                code = ReplyInfo.Code3006ResponseStatusWrong;
//                message = ReplyInfo.Msg3006ResponseStatusWrong + ": " + httpResponse.getStatusLine().getStatusCode();
//                return;
//            }
//
//            responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
//
//            responseBodyStr = new String(responseBodyBytes);
//            parseApipResponse(httpResponse);
//        } catch (Exception e) {
//            e.printStackTrace();
//            log.error("Error when requesting post.", e);
//            code = ReplyInfo.Code3007ErrorWhenRequestingPost;
//            message = ReplyInfo.Msg3007ErrorWhenRequestingPost;
//        }
//    }

//    private void parseApipResponse(HttpResponse response) {
//        if (response == null) return;
//        Gson gson = new Gson();
//        String sign;
//        try {
//            this.responseBody = gson.fromJson(responseBodyStr, ResponseBody.class);
//        } catch (JsonSyntaxException ignore) {
//        }
//
//        if (response.getHeaders(UpStrings.SIGN) != null && response.getHeaders(UpStrings.SIGN).length > 0) {
//            sign = response.getHeaders(UpStrings.SIGN)[0].getValue();
//            this.responseHeaderMap = new HashMap<>();
//            this.responseHeaderMap.put(UpStrings.SIGN, sign);
//            String symKeyName = null;
//            if (response.getHeaders(UpStrings.SESSION_NAME) != null && response.getHeaders(UpStrings.SESSION_NAME).length > 0) {
//                symKeyName = response.getHeaders(UpStrings.SESSION_NAME)[0].getValue();
//                this.responseHeaderMap.put(UpStrings.SESSION_NAME, symKeyName);
//            }
//            this.signatureResponse = new Signature(sign, symKeyName);
//        }
//    }

//    public boolean checkResponseSign(byte[] symKey) {
//        return isGoodSign(responseBodyBytes, signatureResponse.getSign(), symKey);
//    }
//
//    public boolean checkRequestSign(byte[] symKey) {
//        return isGoodSign(requestBodyBytes, signatureRequest.getSign(), symKey);
//    }


//    public String getType() {
//        return Constants.APIP;
//    }

//    public String getSn() {
//        return sn;
//    }
//
//    public void setSn(String sn) {
//        this.sn = sn;
//    }
//
//    public String getVer() {
//        return Constants.V1;
//    }
//
//    public Map<String, String> getRequestHeaderMap() {
//        return requestHeaderMap;
//    }
//
//    public void setRequestHeaderMap(Map<String, String> requestHeaderMap) {
//        this.requestHeaderMap = requestHeaderMap;
//    }
//
//    public RequestBody getRequestBody() {
//        return requestBody;
//    }
//
//    public void setRequestBody(RequestBody requestBody) {
//        this.requestBody = requestBody;
//    }
//    public Map<String, String> getResponseHeaderMap() {
//        return responseHeaderMap;
//    }
//
//    public void setResponseHeaderMap(Map<String, String> responseHeaderMap) {
//        this.responseHeaderMap = responseHeaderMap;
//    }
//
//    public ResponseBody getResponseBody() {
//        return responseBody;
//    }
//
//    public void setResponseBody(ResponseBody responseBody) {
//        this.responseBody = responseBody;
//    }
//
//    public Signature getSignatureRequest() {
//        return signatureRequest;
//    }
//
//    public void setSignatureRequest(Signature signatureRequest) {
//        this.signatureRequest = signatureRequest;
//    }
//
//    public Signature getSignatureResponse() {
//        return signatureResponse;
//    }
//
//    public void setSignatureResponse(Signature signatureResponse) {
//        this.signatureResponse = signatureResponse;
//    }
//
//    public byte[] getRequestBodyBytes() {
//        return requestBodyBytes;
//    }
//
//    public void setRequestBodyBytes(byte[] requestBodyBytes) {
//        this.requestBodyBytes = requestBodyBytes;
//    }
//
//    public byte[] getResponseBodyBytes() {
//        return responseBodyBytes;
//    }
//
//    public void setResponseBodyBytes(byte[] responseBodyBytes) {
//        this.responseBodyBytes = responseBodyBytes;
//    }
//
//    public String getResponseBodyStr() {
//        return responseBodyStr;
//    }
//
//    public void setResponseBodyStr(String responseBodyStr) {
//        this.responseBodyStr = responseBodyStr;
//    }
//
//    public int getCode() {
//        return code;
//    }
//
//    public void setCode(int code) {
//        this.code = code;
//    }
//
//    public String getMessage() {
//        return message;
//    }
//
//    public void setMessage(String message) {
//        this.message = message;
//    }
//
//    public String getRequestBodyStr() {
//        return requestBodyStr;
//    }
//
//    public void setRequestBodyStr(String requestBodyStr) {
//        this.requestBodyStr = requestBodyStr;
//    }

//    public void set1017NoSuchMethod() {
//        code = ReplyInfo.Code1017MethodNotAvailable;
//        message = ReplyInfo.Msg1017MethodNotAvailable;
//    }
//
//    public String getUrl() {
//        return url;
//    }
//
//    public void setUrl(String url) {
//        this.url = url;
//    }
}
