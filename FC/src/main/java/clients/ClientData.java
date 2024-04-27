package clients;

import APIP.ApipTools;
import APIP.apipData.Session;
import APIP.apipData.Fcdsl;
import APIP.apipData.RequestBody;
import APIP.apipData.ResponseBody;
import FCH.FchMainNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.Constants;
import constants.ReplyInfo;
import constants.UpStrings;
import fcData.Algorithm;
import fcData.Signature;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static APIP.ApipTools.isGoodSign;

public class ClientData {
    private static final Logger log = LoggerFactory.getLogger(ClientData.class);
    private String url;
    private Map<String, String> requestHeaderMap;
    private Signature signatureOfRequest;
    private RequestBody requestBody;
    private String requestBodyStr;
    private byte[] requestBodyBytes;
    private Map<String, String> responseHeaderMap;
    private ResponseBody responseBody;
    private String responseBodyStr;
    private byte[] responseBodyBytes;
    private Signature signatureOfResponse;
    private int code;
    private String message;

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
        requestBody.makeRequestBody(url, requestBody.getVia());
    }
    public void signInPost(@Nullable String via, byte[] priKey, @Nullable RequestBody.SignInMode mode){
        prepareRequest(via, null, mode);
        makeHeaderAsySign(priKey);
        doPost();
        if(responseBody!=null){
            code = responseBody.getCode();
            message = responseBody.getMessage();
        }
    }
    public void get() {
        get(url, requestHeaderMap);
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
        if (url == null) {
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

    public boolean postWithFcdsl(@Nullable Fcdsl fcdsl, @Nullable String via, byte[] sessionKey) {
        prepareRequest(via, fcdsl);
        makeHeaderSession(sessionKey,requestBodyBytes);
        return post(sessionKey);
    }

    public void postWithJsonBody(@Nullable String via, byte[] sessionKey) {
        prepareRequest(via, null);
        makeHeaderSession(sessionKey,requestBodyBytes);
        post(sessionKey);
    }

    public void postBinaryWithUrlSign(byte[] sessionKey, byte[]dataBytes) {
        requestBodyBytes = dataBytes;
        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/octet-stream");
        makeHeaderSession(sessionKey,url.getBytes());
        post(sessionKey);
    }

    private void makeHeaderAsySign(byte[] priKey) {
        if (priKey == null) return;

        ECKey ecKey = ECKey.fromPrivate(priKey);

        requestBodyStr = new String(requestBodyBytes, StandardCharsets.UTF_8);
        String sign = ecKey.signMessage(requestBodyStr);
        String fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();

        signatureOfRequest = new Signature(fid, requestBodyStr, sign, Algorithm.EccAes256K1P7_No1_NrC7.name());
        requestHeaderMap.put(UpStrings.FID, fid);
        requestHeaderMap.put(UpStrings.SIGN, signatureOfRequest.getSign());
    }
    private void prepareRequest(@Nullable String via, @Nullable Fcdsl fcdsl) {
        requestBody = new RequestBody(url, via);
        if(fcdsl!=null)requestBody.setFcdsl(fcdsl);

        Gson gson = new Gson();
        String requestBodyJson = gson.toJson(requestBody);
        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }
    private void prepareRequest(@Nullable String via, Fcdsl fcdsl, @Nullable RequestBody.SignInMode mode) {
        requestBody = new RequestBody(url, via, mode);
        requestBody.setFcdsl(fcdsl);

        Gson gson = new Gson();
        String requestBodyJson = gson.toJson(requestBody);
        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    private void makeHeaderSession(byte[] sessionKey,byte[] dataBytes) {
        String sign = Session.getSessionKeySign(sessionKey, dataBytes);
        signatureOfRequest = new Signature(sign, ApipTools.getSessionName(sessionKey));
        requestHeaderMap.put(UpStrings.SESSION_NAME, signatureOfRequest.getSymKeyName());
        requestHeaderMap.put(UpStrings.SIGN, signatureOfRequest.getSign());
    }

    public void doPost() {
        CloseableHttpResponse httpResponse;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(url);
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
            this.signatureOfResponse = new Signature(sign, symKeyName);
        }
    }

    public boolean checkResponseSign(byte[] symKey) {
        return isGoodSign(responseBodyBytes, signatureOfResponse.getSign(), symKey);
    }

    public boolean checkRequestSign(byte[] symKey) {
        return isGoodSign(requestBodyBytes, signatureOfRequest.getSign(), symKey);
    }

    public String getType() {
        return Constants.APIP;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getRequestHeaderMap() {
        return requestHeaderMap;
    }

    public void setRequestHeaderMap(Map<String, String> requestHeaderMap) {
        this.requestHeaderMap = requestHeaderMap;
    }

    public Signature getSignatureOfRequest() {
        return signatureOfRequest;
    }

    public void setSignatureOfRequest(Signature signatureOfRequest) {
        this.signatureOfRequest = signatureOfRequest;
    }

    public RequestBody getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(RequestBody requestBody) {
        this.requestBody = requestBody;
    }

    public String getRequestBodyStr() {
        return requestBodyStr;
    }

    public void setRequestBodyStr(String requestBodyStr) {
        this.requestBodyStr = requestBodyStr;
    }

    public byte[] getRequestBodyBytes() {
        return requestBodyBytes;
    }

    public void setRequestBodyBytes(byte[] requestBodyBytes) {
        this.requestBodyBytes = requestBodyBytes;
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

    public String getResponseBodyStr() {
        return responseBodyStr;
    }

    public void setResponseBodyStr(String responseBodyStr) {
        this.responseBodyStr = responseBodyStr;
    }

    public byte[] getResponseBodyBytes() {
        return responseBodyBytes;
    }

    public void setResponseBodyBytes(byte[] responseBodyBytes) {
        this.responseBodyBytes = responseBodyBytes;
    }

    public Signature getSignatureOfResponse() {
        return signatureOfResponse;
    }

    public void setSignatureOfResponse(Signature signatureOfResponse) {
        this.signatureOfResponse = signatureOfResponse;
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
}
