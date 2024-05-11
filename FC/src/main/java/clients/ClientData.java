package clients;

import APIP.ApipTools;
import APIP.apipData.Session;
import APIP.apipData.Fcdsl;
import APIP.apipData.RequestBody;
import APIP.apipData.ResponseBody;
import FCH.FchMainNetwork;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.ApiNames;
import constants.Constants;
import constants.ReplyInfo;
import constants.UpStrings;
import crypto.cryptoTools.Hash;
import fcData.Algorithm;
import fcData.Signature;
import javaTools.Hex;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.apache.http.util.EntityUtils;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static APIP.ApipTools.isGoodSign;

public class ClientData {

    protected static final Logger log = LoggerFactory.getLogger(ClientData.class);
    protected ApiUrl apiUrl;
    protected Fcdsl rawFcdsl;
    protected Map<String, String> requestHeaderMap;
    protected Signature signatureOfRequest;
    protected Map<String,String>requestParamMap;
    protected RequestBody requestBody;
    protected String requestBodyStr;
    protected byte[] requestBodyBytes;
    protected Map<String, String> responseHeaderMap;
    protected ResponseBody responseBody;
    protected String responseBodyStr;
    protected byte[] responseBodyBytes;
    protected Signature signatureOfResponse;
    protected HttpResponse httpResponse;
    protected AuthType authType;
//    protected AuthType authType;
    protected String via;
    protected Integer code;
    protected String message;

    protected void makeFcdslRequest(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl) {
        if(apiUrl==null){
            apiUrl=new ApiUrl(urlHead,urlTail);
        }
        String url = ApiUrl.makeUrl(urlHead, urlTail);
        requestBody = new RequestBody(url, via);
        requestBody.setFcdsl(fcdsl);

        Gson gson = new Gson();
        String requestBodyJson = gson.toJson(requestBody);
        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    public enum AuthType{
        FC_SIGN_URL,
        FC_SIGN_BODY,
        FREE
    }
    public ClientData() {
    }
    public ClientData(String url) {
        apiUrl=new ApiUrl(url);
    }
    public ClientData(String urlHead,String urlTail) {
        apiUrl=new ApiUrl(urlHead,urlTail,null,null,null);
    }
    public ClientData(String urlHead,String urlTailPath, String apiName) {
        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,null,null,null);
    }

    public ClientData(String urlHead,String urlTailPath, String apiName,Map<String,String>paramMap) {
        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,paramMap,false,null);
    }
    public ClientData(String urlHead,String type,String sn, String version, String apiName) {
        apiUrl=new ApiUrl(urlHead,type,sn, version, apiName,null,null,null);
    }

    public ClientData(String urlHead,String type,String sn, String version, String apiName,Map<String,String>paramMap) {
        apiUrl=new ApiUrl(urlHead,type,sn, version, apiName,paramMap,null,null);
    }
    public ClientData(byte[] authKey,String urlHead,String urlTailPath, String apiName,Fcdsl fcdsl,AuthType authType,String via) {
        urlTailPath=ApiUrl.formatUrlPath(urlTailPath);
        apiUrl=new ApiUrl(urlHead,urlTailPath+apiName);
        rawFcdsl=fcdsl;
        makeFcdslRequest(urlHead,apiUrl.getUrlTail(),via,fcdsl);
        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey,requestBodyBytes);
        }
    }

    public ClientData(byte[] authKey, String urlHead, String type, String sn, String ver, String apiName, String via, AuthType authType, Map<String,String>paramMap) {
        this.requestParamMap=paramMap;
        this.via=via;
        this.authType=authType;
        switch (authType){
            case FC_SIGN_URL -> {
                apiUrl=new ApiUrl(urlHead,type,sn,ver,apiName,paramMap, true,via);
                makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
            }
            case FC_SIGN_BODY -> {
                apiUrl=new ApiUrl(urlHead,type,sn,ver,apiName,null, null,null);
                requestBody=new RequestBody(apiUrl.getUrl(),via);
                requestBody.setData(paramMap);
                requestHeaderMap = new HashMap<>();
                makeRequestBodyBytes();
                makeHeaderSession(authKey,requestBodyBytes);
            }
            default -> apiUrl=new ApiUrl(urlHead,type,sn,ver,apiName,null, null,via);
        }
    }

//
//
//    public ClientData(String urlHead,String urlTail, Map<String,String>paramMap, String via, AuthType authType) {
//        apiUrl=new ApiUrl(urlHead,urlTail,null,null,null);
//        this.requestParamMap=paramMap;
//    }
//    public ClientData(String urlHead,String urlTailPath, String apiName, Map<String,String>paramMap, String via, AuthType authType) {
//        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,paramMap, authType,via);
//        this.via=via;
//        this.requestParamMap=paramMap;
//    }
//    public ClientData(String urlHead,String type,String sn, String ver, String apiName, Map<String,String>paramMap, String via, AuthType authType) {
//        apiUrl=new ApiUrl(urlHead,type,sn, ver, apiName,null,null,null);
//        this.requestParamMap=paramMap;
//    }
//
//    public ClientData(String urlHead,String urlTail,String via, AuthType authType,Fcdsl fcdsl) {
//        apiUrl=new ApiUrl(urlHead,urlTail,null, null,null);
//        this.rawFcdsl=fcdsl;
//        this.via=via;
//        this.authType=authType;
//    }
//    public ClientData(String urlHead,String urlTailPath, String apiName, String via, AuthType authType,Fcdsl fcdsl) {
//        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,null, null,null);
//        this.rawFcdsl=fcdsl;
//        this.via=via;
//        this.authType=authType;
//    }
//    public ClientData(String urlHead,String type, String sn, String ver, String apiName, String via, AuthType authType,Fcdsl fcdsl) {
//        apiUrl=new ApiUrl(urlHead,type,sn,ver,apiName,null, null,null);
//        this.rawFcdsl=fcdsl;
//        this.via=via;
//        this.authType=authType;
//    }
//
//    public ClientData(String urlHead,String urlTail,String via, AuthType authType,Map<String,String>paramMap) {
//        this.apiUrl=new ApiUrl(urlHead,urlTail,null, null,null);
//        this.requestParamMap=paramMap;
//        this.via=via;
//        this.authType=authType;
//    }
//    public ClientData(String urlHead,String urlTailPath, String apiName, String via, AuthType authType,Map<String,String>paramMap) {
//        this.apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,null, null,null);
//        this.requestParamMap=paramMap;
//        this.via=via;
//        this.authType=authType;
//    }
//

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
        requestBody.makeRequestBody(apiUrl.getUrl(), requestBody.getVia());
    }
    public void signInPost(@Nullable String via, byte[] priKey, @Nullable RequestBody.SignInMode mode){
        prepareRequest(via, null, mode);
        makeHeaderAsySign(priKey);
        doPost(BodyType.STRING);
        if(responseBody!=null){
            code = responseBody.getCode();
            message = responseBody.getMessage();
        }
    }
    public void get() {
        get(apiUrl.getUrl(), requestHeaderMap);
    }
    public void get(String url, Map<String,String> requestHeaderMap){

        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            HttpGet request = new HttpGet(url);

            // add request headers
            if(requestHeaderMap!=null) {
                for (String head : requestHeaderMap.keySet()) {
                    request.addHeader(head, requestHeaderMap.get(head));
                }
            }
            HttpResponse httpResponse = httpClient.execute(request);

            if(httpResponse==null){
                code= ReplyInfo.Code3002GetRequestFailed;
                message = ReplyInfo.Msg3002GetRequestFailed;
                return;
            }

            responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();

            responseBodyStr = new String(responseBodyBytes);

            parseApipResponse(httpResponse);

        } catch (Exception e) {
            e.printStackTrace();
            code= ReplyInfo.Code3003CloseHttpClientFailed;
            message = ReplyInfo.Msg3003CloseHttpClientFailed;
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
                code= ReplyInfo.Code3003CloseHttpClientFailed;
                message = ReplyInfo.Msg3003CloseHttpClientFailed;
            }
        }
    }

    public String getFile(String did){
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            HttpGet request = new HttpGet(apiUrl.getUrl());

            // add request headers
            if(requestHeaderMap!=null) {
                for (String head : requestHeaderMap.keySet()) {
                    request.addHeader(head, requestHeaderMap.get(head));
                }
            }
            httpResponse = httpClient.execute(request);

            if(httpResponse==null){
                code= ReplyInfo.Code3002GetRequestFailed;
                message = ReplyInfo.Msg3002GetRequestFailed;
                return null;
            }
            return downloadFileFromHttpResponse(did);
        } catch (Exception e) {
            e.printStackTrace();
            code= ReplyInfo.Code3003CloseHttpClientFailed;
            message = ReplyInfo.Msg3003CloseHttpClientFailed;
            return null;
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
                code= ReplyInfo.Code3003CloseHttpClientFailed;
                message = ReplyInfo.Msg3003CloseHttpClientFailed;
                return null;
            }
        }
    }

    public String downloadFileFromHttpResponse(String did) {
        if(httpResponse==null)
            return null;
        String finalFileName=did;
        InputStream inputStream = null;
        try {
            inputStream = httpResponse.getEntity().getContent();
        } catch (IOException e) {
            code=ReplyInfo.Code1020OtherError;
            message="Failed to get inputStream from http response.";
            return null;
        }

        while(true) {
            File file = new File(finalFileName);
            if (!file.exists()) {
                try {
                    boolean done = file.createNewFile();
                    if (!done) {
                        code = ReplyInfo.Code1020OtherError;
                        message = "Failed to create file " + finalFileName;
                        return null;
                    }
                    break;
                } catch (IOException e) {
                    code = ReplyInfo.Code1020OtherError;
                    message = "Failed to create file " + finalFileName;
                    return null;
                }
            }else{
                if(finalFileName.contains("_")){
                    try {
                        int order = Integer.parseInt(finalFileName.substring(finalFileName.indexOf("_")+1));
                        order++;
                        finalFileName = did.substring(0,6)+"_"+order;

                    }catch (Exception ignore){};
                }else{
                    finalFileName = did.substring(0,6)+"_"+1;
                }
            }
        }

        HashFunction hashFunction = Hashing.sha256();
        Hasher hasher = hashFunction.newHasher();
//        long bytesLength=0;
        try(FileOutputStream outputStream = new FileOutputStream(finalFileName)){
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                hasher.putBytes(buffer, 0, bytesRead);
//                bytesLength +=bytesRead;
//                System.out.print("."+bytesLength);
            }
            inputStream.close();
        } catch (IOException e) {
            code=ReplyInfo.Code1020OtherError;
            message="Failed to read buffer.";
            return null;
        }


        String didFromResponse = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));

        if(!did.equals(didFromResponse)){
            code=ReplyInfo.Code1020OtherError;
            message="The DID of the file from response is not equal to the requested DID.";
            return null;
        }

        if(!finalFileName.equals(did)){
            try {
                Files.move(Paths.get(finalFileName), Paths.get(did), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                code=ReplyInfo.Code1020OtherError;
                message="Failed to replace the old file.";
                return null;
            }
        }

        return didFromResponse;
    }

    public void getHttpResponse(String url, Map<String, String> requestHeaderMap) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet httpGet = new HttpGet(url);

            // add httpGet headers
            if (requestHeaderMap != null) {
                for (String head : requestHeaderMap.keySet()) {
                    httpGet.addHeader(head, requestHeaderMap.get(head));
                }
            }

            httpResponse = httpClient.execute(httpGet);
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
        return post(sessionKey,null,null);
    }
    public boolean post(byte[] sessionKey,BodyType bodyType) {
        return post(sessionKey,bodyType,null);
    }

    public boolean post(byte[] sessionKey,BodyType bodyType,File file) {
        if(bodyType==null)bodyType=BodyType.STRING;
        if(requestHeaderMap==null)requestHeaderMap = new HashMap<>();
        if (apiUrl.getUrl() == null) {
            code = ReplyInfo.Code3004RequestUrlIsAbsent;
            message = ReplyInfo.Msg3004RequestUrlIsAbsent;
            System.out.println(message);
            return false;
        }

        if(bodyType.equals(BodyType.FILE)){
            if(!file.exists()){
                code = ReplyInfo.Code1020OtherError;
                message = "File "+file.getName()+" don't exist.";
                return false;
            }

            doFilePost(file);
        }else
            doPost(bodyType);

        if (responseHeaderMap != null && responseHeaderMap.get(UpStrings.SIGN) != null) {
            if (!checkResponseSign(sessionKey)) {
                code = ReplyInfo.Code1008BadSign;
                message = ReplyInfo.Msg1008BadSign;
                return false;
            }
        }

        if(responseBody!=null){
            boolean done=false;
            if(responseBody.getCode()!=null) {
                code = responseBody.getCode();
                if(code==0)done=true;
            }
            if(responseBody.getMessage()!=null)
                message = responseBody.getMessage();
            return done;
        }
        return false;
    }

    public boolean postWithFcdsl(byte[] sessionKey) {
        prepareRequest(via, rawFcdsl);
        makeHeaderSession(sessionKey,requestBodyBytes);
        return post(sessionKey,BodyType.STRING);
    }

    public void postWithJsonBody(@Nullable String via, byte[] sessionKey) {
        prepareRequest(via, null);
        makeHeaderSession(sessionKey,requestBodyBytes);
        post(sessionKey,BodyType.STRING);
    }

//    public void postBinaryWithUrlSign(byte[] sessionKey, byte[]dataBytes) {
//        requestBodyBytes = dataBytes;
//        requestHeaderMap = new HashMap<>();
//        requestHeaderMap.put("Content-Type", "application/octet-stream");
//        makeHeaderSession(sessionKey,url.getBytes());
//        post(sessionKey,BodyType.BYTES);
//    }
//    public void getFileWithUrlSign(byte[] sessionKey) {
//        makeHeaderSession(sessionKey,apiUrl.getUrl().getBytes());
//        get();
//    }
//    public void postFileWithUrlSign(byte[] sessionKey, String filename) {
//
//
//
//    }

    protected void makeHeaderAsySign(byte[] priKey) {
        if (priKey == null) return;

        ECKey ecKey = ECKey.fromPrivate(priKey);

        requestBodyStr = new String(requestBodyBytes, StandardCharsets.UTF_8);
        String sign = ecKey.signMessage(requestBodyStr);
        String fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();

        signatureOfRequest = new Signature(fid, requestBodyStr, sign, Algorithm.EccAes256K1P7_No1_NrC7.name());
        requestHeaderMap.put(UpStrings.FID, fid);
        requestHeaderMap.put(UpStrings.SIGN, signatureOfRequest.getSign());
    }
    protected void prepareRequest(@Nullable String via, @Nullable Fcdsl fcdsl) {
        requestBody = new RequestBody(apiUrl.getUrl(), via);
        if(fcdsl!=null)requestBody.setFcdsl(fcdsl);

        makeRequestBodyBytes();

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    private void makeRequestBodyBytes() {
        Gson gson = new Gson();
        String requestBodyJson = gson.toJson(requestBody);
        requestBodyBytes = requestBodyJson.getBytes(StandardCharsets.UTF_8);
    }

    protected void prepareRequest(@Nullable String via, Fcdsl fcdsl, @Nullable RequestBody.SignInMode mode) {
        requestBody = new RequestBody(apiUrl.getUrl(), via, mode);
        requestBody.setFcdsl(fcdsl);

        makeRequestBodyBytes();

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    public void makeHeaderSession(byte[] sessionKey,byte[] dataBytes) {
        String sign = Session.getSessionKeySign(sessionKey, dataBytes);
        signatureOfRequest = new Signature(sign, ApipTools.getSessionName(sessionKey));
        if(requestHeaderMap==null)requestHeaderMap=new HashMap<>();
        requestHeaderMap.put(UpStrings.SESSION_NAME, signatureOfRequest.getSymKeyName());
        requestHeaderMap.put(UpStrings.SIGN, signatureOfRequest.getSign());
    }

//    protected void makeHeaderSession(byte[] sessionKey) {
//        String sign = Session.getSessionKeySign(sessionKey, url.getBytes());
//        signatureOfRequest = new Signature(sign, ApipTools.getSessionName(sessionKey));
//        requestHeaderMap.put(UpStrings.SESSION_NAME, signatureOfRequest.getSymKeyName());
//        requestHeaderMap.put(UpStrings.SIGN, signatureOfRequest.getSign());
//    }
    public enum BodyType{
        STRING,BYTES,FILE
    }

    public void doPost(BodyType bodyType) {
        CloseableHttpResponse httpResponse;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(apiUrl.getUrl());
            if (requestHeaderMap != null) {
                for (String key : requestHeaderMap.keySet()) {
                    httpPost.setHeader(key, requestHeaderMap.get(key));
                }
            }

            if(bodyType.equals(BodyType.STRING)) {
                StringEntity entity = new StringEntity(new String(requestBodyBytes));
                httpPost.setEntity(entity);
            }else {
                ByteArrayEntity entity = new ByteArrayEntity(requestBodyBytes);
                httpPost.setEntity(entity);
            }

            try {
                httpResponse = httpClient.execute(httpPost);
            }catch (HttpHostConnectException e){
                log.debug("Failed to connect "+apiUrl.getUrl()+". Check the URL.");
                code = ReplyInfo.Code3001ResponseIsNull;
                message = ReplyInfo.Msg3001ResponseIsNull;
                return;
            }

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

    public void doFilePost(File file) {

        requestHeaderMap.put("Content-Type", "application/octet-stream");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             FileInputStream fileInputStream = new FileInputStream(file)) {
            HttpPost httpPost = new HttpPost(apiUrl.getUrl());
            if (requestHeaderMap != null) {
                for (String key : requestHeaderMap.keySet()) {
                    httpPost.setHeader(key, requestHeaderMap.get(key));
                }
            }
            // Set the InputStreamEntity to stream the contents of the file
            HttpEntity fileEntity = new InputStreamEntity(
                    fileInputStream,
                    file.length(),
                    ContentType.APPLICATION_OCTET_STREAM
            );
            httpPost.setEntity(fileEntity);
//            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
//            // This is to ensure the file does not load into memory
//            builder.addBinaryBody(
//                    "file",
//                    new FileInputStream(file),
//                    ContentType.APPLICATION_OCTET_STREAM,
//                    file.getName()
//            );
//
//            HttpEntity multipart = builder.build();
//            httpPost.setEntity(multipart);



            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response == null) {
                    log.debug("httpResponse == null.");
                    code = ReplyInfo.Code3001ResponseIsNull;
                    message = ReplyInfo.Msg3001ResponseIsNull;
                    return;
                }
                if (response.getStatusLine().getStatusCode() != 200) {
                    log.debug("Post response status: {}.{}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
                    if(response.getHeaders((UpStrings.CODE))!=null){
                        try {
                            code = Integer.valueOf(response.getHeaders(UpStrings.CODE)[0].getValue());
                        }catch (Exception ignore){}
                    }
                    else code = ReplyInfo.Code3006ResponseStatusWrong;
                    message = ReplyInfo.Msg3006ResponseStatusWrong + ": " + response.getStatusLine().getStatusCode();
                    return;
                }
                HttpEntity responseEntity = response.getEntity();
                responseBodyBytes = responseEntity.getContent().readAllBytes();

                responseBodyStr = new String(responseBodyBytes);
                parseApipResponse(response);
                EntityUtils.consume(responseEntity);

            }catch (HttpHostConnectException e){
                log.debug("Failed to connect "+apiUrl.getUrl()+". Check the URL.");
                code = ReplyInfo.Code3001ResponseIsNull;
                message = ReplyInfo.Msg3001ResponseIsNull;
                return;
            }
        } catch (FileNotFoundException e){
            code = ReplyInfo.Code1020OtherError;
            message = "It's a directory instead of a file.";
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error when requesting post.", e);
            code = ReplyInfo.Code3007ErrorWhenRequestingPost;
            message = ReplyInfo.Msg3007ErrorWhenRequestingPost;
        }
    }

    protected void parseApipResponse(HttpResponse response) {
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

    public Fcdsl getRawFcdsl() {
        return rawFcdsl;
    }

    public void setRawFcdsl(Fcdsl rawFcdsl) {
        this.rawFcdsl = rawFcdsl;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    public ApiUrl getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(ApiUrl apiUrl) {
        this.apiUrl = apiUrl;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Map<String, String> getRequestParamMap() {
        return requestParamMap;
    }

    public void setRequestParamMap(Map<String, String> requestParamMap) {
        this.requestParamMap = requestParamMap;
    }
}
