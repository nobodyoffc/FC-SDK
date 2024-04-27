package clients.apipClient;

import APIP.ApipTools;
import APIP.apipData.Session;
import APIP.apipData.*;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.ReplyInfo;
import constants.Constants;
import constants.UpStrings;
import org.bitcoinj.core.ECKey;
import FCH.FchMainNetwork;
import fcData.Algorithm;
import fcData.Signature;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static APIP.ApipTools.isGoodSign;

public class ApipClientData {
    private static final Logger log = LoggerFactory.getLogger(ApipClientData.class);
    private String sn;
    private Fcdsl rawFcdsl;
    private ApipUrl apipUrl;
    private Map<String, String> requestHeaderMap;
    private Signature signatureRequest;
    private RequestBody requestBody;
    private String requestBodyStr;
    private byte[] requestBodyBytes;
    private Map<String, String> responseHeaderMap;
    private ResponseBody responseBody;
    private String responseBodyStr;
    private byte[] responseBodyBytes;
    private Signature signatureResponse;
    private HttpResponse httpResponse;
    private int code;
    private String message;

//
//    public boolean isBadResponse(String taskName) {
//        if (checkResponse() != 0) {
//            log.debug("Failed to " + taskName);
//            if (responseBody == null) {
//                log.debug("ResponseBody is null.");
//                log.debug(message);
//            } else {
//                log.debug(responseBody.getCode() + ":" + responseBody.getMessage());
//                if (responseBody.getData() != null) System.out.println(JsonTools.getString(responseBody.getData()));
//            }
//            return true;
//        } else return false;
//    }

    public int checkResponse() {
        if (responseBody == null) {
            code = ReplyInfo.Code3001ResponseIsNull;
            message = ReplyInfo.Msg3001ResponseIsNull;
            return code;
        }

        if (responseBody.getCode() != 0) {
            code = responseBody.getCode();
            message = responseBody.getMessage();
            return code;
        }

        if (responseBody.getData() == null) {
            code = ReplyInfo.Code3005ResponseDataIsNull;
            message = ReplyInfo.Msg3005ResponseDataIsNull;
            return code;
        }
        return 0;
    }

    public void makeRequestBody() {
        requestBody = new RequestBody();
        requestBody.makeRequestBody(apipUrl.getUrl(), requestBody.getVia());
    }

    public void addNewApipUrl(String urlHead, String urlTail) {
        this.apipUrl = new ApipUrl(urlHead, urlTail);
    }

    public void addNewApipUrl(String url) {
        this.apipUrl = new ApipUrl(url);
    }

    public void get() {
        get(apipUrl.getUrl(), requestHeaderMap);
        if(responseBody!=null){
            code=responseBody.getCode();
            message=responseBody.getMessage();

        }
    }

    public void get(String url, Map<String, String> requestHeaderMap) {

        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            HttpGet request = new HttpGet(url);

            // add request headers
            if (requestHeaderMap != null) {
                for (String head : requestHeaderMap.keySet()) {
                    request.addHeader(head, requestHeaderMap.get(head));
                }
            }
            HttpResponse httpResponse = httpClient.execute(request);

            if (httpResponse == null) {
                code = ReplyInfo.Code3001ResponseIsNull;
                message = ReplyInfo.Msg3001ResponseIsNull;
                return;
            }

            responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();

            responseBodyStr = new String(responseBodyBytes);

            parseApipResponse(httpResponse);

        } catch (Exception e) {
            e.printStackTrace();
            code = ReplyInfo.Code3002GetRequestFailed;
            message = ReplyInfo.Msg3002GetRequestFailed;
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
                code = ReplyInfo.Code3003CloseHttpClientFailed;
                message = ReplyInfo.Msg3003CloseHttpClientFailed;
            }
        }
    }

    public boolean post(byte[] sessionKey) {
        if (apipUrl.getUrl() == null) {
            code = ReplyInfo.Code3004RequestUrlIsAbsent;
            message = ReplyInfo.Msg3004RequestUrlIsAbsent;
            System.out.println(message);
            return false;
        }
        if (requestBodyBytes == null) {
            code = ReplyInfo.Code1003BodyMissed;
            message = ReplyInfo.Msg1003BodyMissed;
            System.out.println(message);
            return false;
        }

        doPost();

        if (responseHeaderMap != null && responseHeaderMap.get(UpStrings.SIGN) != null) {
            if (!checkResponseSign(sessionKey)) {
                code = ReplyInfo.Code1008BadSign;
                message = ReplyInfo.Msg1008BadSign;
                System.out.println(message);
                return false;
            }
        }
        message = UpStrings.SUCCESS;
        return true;
    }

    public boolean post(String urlHead, String urlTail, Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        rawFcdsl = fcdsl;
        prepare(urlHead, urlTail, via, fcdsl);
        makeHeaderSession(sessionKey);
        return post(sessionKey);
    }

    private void prepare(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl) {
        apipUrl = new ApipUrl(urlHead, urlTail);
        requestBody = new RequestBody(apipUrl.getUrl(), via);
        requestBody.setFcdsl(fcdsl);

        Gson gson = new Gson();
        String requestBodyJson = gson.toJson(requestBody);
        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    public void asySignPost(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl, byte[] priKey, @Nullable RequestBody.SignInMode mode) throws IOException {
        prepare(urlHead, urlTail, via, fcdsl, mode);
        makeHeaderAsySign(priKey);
        doPost();
        if(responseBody!=null){
            code = responseBody.getCode();
            message = responseBody.getMessage();
        }
    }

    private void makeHeaderAsySign(byte[] priKey) {
        if (priKey == null) return;

        ECKey ecKey = ECKey.fromPrivate(priKey);

        requestBodyStr = new String(requestBodyBytes, StandardCharsets.UTF_8);
        String sign = ecKey.signMessage(requestBodyStr);
        String fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();

        signatureRequest = new Signature(fid, requestBodyStr, sign, Algorithm.EccAes256K1P7_No1_NrC7.name());
        requestHeaderMap.put(UpStrings.FID, fid);
        requestHeaderMap.put(UpStrings.SIGN, signatureRequest.getSign());
    }

    private void prepare(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl, @Nullable RequestBody.SignInMode mode) {
        apipUrl = new ApipUrl(urlHead, urlTail);
        requestBody = new RequestBody(apipUrl.getUrl(), via, mode);
        requestBody.setFcdsl(fcdsl);

        Gson gson = new Gson();
        String requestBodyJson = gson.toJson(requestBody);
        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    private void makeHeaderSession(byte[] sessionKey) {
        String sign = Session.getSessionKeySign(sessionKey, requestBodyBytes);
        signatureRequest = new Signature(sign, ApipTools.getSessionName(sessionKey));
        requestHeaderMap.put(UpStrings.SESSION_NAME, signatureRequest.getSymKeyName());
        requestHeaderMap.put(UpStrings.SIGN, signatureRequest.getSign());
    }

    private void doPost() {
        CloseableHttpResponse httpResponse;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(apipUrl.getUrl());
            if (requestHeaderMap != null) {
                for (String key : requestHeaderMap.keySet()) {
                    httpPost.setHeader(key, requestHeaderMap.get(key));
                }
            }

            StringEntity entity = new StringEntity(new String(requestBodyBytes));

            httpPost.setEntity(entity);

            httpResponse = httpClient.execute(httpPost);

            if (httpResponse == null) {
                log.debug("httpResponse == null.");
                code = ReplyInfo.Code3001ResponseIsNull;
                message = ReplyInfo.Msg3001ResponseIsNull;
                return;
            }

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                log.debug("Post response status: {}.{}", httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase());
                code = ReplyInfo.Code3006ResponseStatusWrong;
                message = ReplyInfo.Msg3006ResponseStatusWrong + ": " + httpResponse.getStatusLine().getStatusCode();
                return;
            }

            responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();

            responseBodyStr = new String(responseBodyBytes);
            parseApipResponse(httpResponse);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error when requesting post.", e);
            code = ReplyInfo.Code3007ErrorWhenRequestingPost;
            message = ReplyInfo.Msg3007ErrorWhenRequestingPost;
        }
    }

    private void parseApipResponse(HttpResponse response) {
        if (response == null) return;
        Gson gson = new Gson();
        String sign;
        try {
            this.responseBody = gson.fromJson(responseBodyStr, ResponseBody.class);
        } catch (JsonSyntaxException ignore) {
        }

        if (response.getHeaders(UpStrings.SIGN) != null && response.getHeaders(UpStrings.SIGN).length > 0) {
            sign = response.getHeaders(UpStrings.SIGN)[0].getValue();
            this.responseHeaderMap = new HashMap<>();
            this.responseHeaderMap.put(UpStrings.SIGN, sign);
            String symKeyName = null;
            if (response.getHeaders(UpStrings.SESSION_NAME) != null && response.getHeaders(UpStrings.SESSION_NAME).length > 0) {
                symKeyName = response.getHeaders(UpStrings.SESSION_NAME)[0].getValue();
                this.responseHeaderMap.put(UpStrings.SESSION_NAME, symKeyName);
            }
            this.signatureResponse = new Signature(sign, symKeyName);
        }
    }

    public boolean checkResponseSign(byte[] symKey) {
        return isGoodSign(responseBodyBytes, signatureResponse.getSign(), symKey);
    }

    public boolean checkRequestSign(byte[] symKey) {
        return isGoodSign(requestBodyBytes, signatureRequest.getSign(), symKey);
    }


    public String getType() {
        return Constants.APIP;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public String getVer() {
        return Constants.V1;
    }

    public ApipUrl getApipUrl() {
        return apipUrl;
    }

    public void setApipUrl(ApipUrl apipUrl) {
        this.apipUrl = apipUrl;
    }

    public Map<String, String> getRequestHeaderMap() {
        return requestHeaderMap;
    }

    public void setRequestHeaderMap(Map<String, String> requestHeaderMap) {
        this.requestHeaderMap = requestHeaderMap;
    }

    public RequestBody getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(RequestBody requestBody) {
        this.requestBody = requestBody;
    }
    public Map<String, String> getResponseHeaderMap() {
        return responseHeaderMap;
    }

    public void setResponseHeaderMap(Map<String, String> responseHeaderMap) {
        this.responseHeaderMap = responseHeaderMap;
    }

    public ResponseBody getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(ResponseBody responseBody) {
        this.responseBody = responseBody;
    }

    public Signature getSignatureRequest() {
        return signatureRequest;
    }

    public void setSignatureRequest(Signature signatureRequest) {
        this.signatureRequest = signatureRequest;
    }

    public Signature getSignatureResponse() {
        return signatureResponse;
    }

    public void setSignatureResponse(Signature signatureResponse) {
        this.signatureResponse = signatureResponse;
    }

    public byte[] getRequestBodyBytes() {
        return requestBodyBytes;
    }

    public void setRequestBodyBytes(byte[] requestBodyBytes) {
        this.requestBodyBytes = requestBodyBytes;
    }

    public byte[] getResponseBodyBytes() {
        return responseBodyBytes;
    }

    public void setResponseBodyBytes(byte[] responseBodyBytes) {
        this.responseBodyBytes = responseBodyBytes;
    }

    public String getResponseBodyStr() {
        return responseBodyStr;
    }

    public void setResponseBodyStr(String responseBodyStr) {
        this.responseBodyStr = responseBodyStr;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestBodyStr() {
        return requestBodyStr;
    }

    public void setRequestBodyStr(String requestBodyStr) {
        this.requestBodyStr = requestBodyStr;
    }

    public Fcdsl getRawFcdsl() {
        return rawFcdsl;
    }

    public void setRawFcdsl(Fcdsl rawFcdsl) {
        this.rawFcdsl = rawFcdsl;
    }

    public void set1017NoSuchMethod() {
        code = ReplyInfo.Code1017MethodNotAvailable;
        message = ReplyInfo.Msg1017MethodNotAvailable;
    }
}
