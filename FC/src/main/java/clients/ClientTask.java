package clients;

import apip.ApipTools;
import apip.apipData.Session;
import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import fcData.FcReplier;
import fch.FchMainNetwork;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import constants.Constants;
import constants.ReplyCodeMessage;
import constants.UpStrings;
import crypto.Hash;
import fcData.AlgorithmId;
import fcData.Signature;
import javaTools.Hex;
import javaTools.StringTools;
import javaTools.http.AuthType;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static apip.ApipTools.isGoodSign;
import static constants.UpStrings.SESSION_NAME;
import static constants.UpStrings.SIGN;

public class ClientTask {

    protected static final Logger log = LoggerFactory.getLogger(ClientTask.class);
    protected ApiUrl apiUrl;
    protected Fcdsl rawFcdsl;
    protected Map<String, String> requestHeaderMap;
    protected Signature signatureOfRequest;
    protected Map<String,String>requestParamMap;
    protected RequestBody requestBody;
    protected String requestBodyStr;
    protected byte[] requestBodyBytes;
    protected Map<String, String> responseHeaderMap;
    protected FcReplier responseBody;
    protected String responseBodyStr;
    protected byte[] responseBodyBytes;
    protected Signature signatureOfResponse;
    protected HttpResponse httpResponse;
    protected AuthType authType;
    protected String via;
    protected Integer code;
    protected String message;

    public enum RequestBodyType {
        STRING,BYTES,FILE
    }
    public enum ResponseBodyType {
        STRING,BYTES,FILE
    }

    public ClientTask() {
    }
    public ClientTask(String url) {
        apiUrl=new ApiUrl(url);
    }
    public ClientTask(String urlHead, String urlTail) {
        apiUrl=new ApiUrl(urlHead,urlTail,null,null,null);
    }
    public ClientTask(String urlHead, String urlTailPath, String apiName) {
        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,null,null,null);
    }
    public ClientTask(String urlHead, String urlTailPath, String apiName, Map<String,String>paramMap) {
        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,paramMap,false,null);
    }
    public ClientTask(String urlHead, String type, String sn, String version, String apiName) {
        apiUrl=new ApiUrl(urlHead,type,sn, version, apiName,null,null,null);
    }
    public ClientTask(String urlHead, String type, String sn, String version, String apiName, Map<String,String>paramMap) {
        apiUrl=new ApiUrl(urlHead,type,sn, version, apiName,paramMap,false,null);
    }
    public ClientTask(byte[] authKey, String urlHead, String type, String sn, String ver, String apiName, Fcdsl fcdsl, AuthType authType, String via) {
        boolean signUrl= AuthType.FC_SIGN_URL.equals(authType);
        apiUrl=new ApiUrl(urlHead,type,sn, ver, apiName,null,signUrl,via);
        rawFcdsl=fcdsl;
        makeFcdslRequest(urlHead,apiUrl.getUrlTail(),via,fcdsl);
        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey,requestBodyBytes);
        }else if (AuthType.FC_SIGN_URL.equals(authType))makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
    }
    public ClientTask(byte[] authKey, String urlHead, String urlTailPath, String apiName, Fcdsl fcdsl, AuthType authType, String via) {
        urlTailPath=ApiUrl.formatUrlPath(urlTailPath);
        apiUrl=new ApiUrl(urlHead,urlTailPath+apiName);
        rawFcdsl=fcdsl;
        makeFcdslRequest(urlHead,apiUrl.getUrlTail(),via,fcdsl);
        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey,requestBodyBytes);
        }else if (AuthType.FC_SIGN_URL.equals(authType))makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
    }
    public ClientTask(byte[] authKey, String urlHead, String type, String sn, String ver, String apiName, String via, AuthType authType, Map<String,String>paramMap) {
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

    public int checkResponse() {
        if (responseBody == null) {
            code = ReplyCodeMessage.Code3001ResponseIsNull;
            message = ReplyCodeMessage.Msg3001ResponseIsNull;
            return code;
        }

        if (responseBody.getCode() != 0) {
            code = responseBody.getCode();
            message = responseBody.getMessage();
            return code;
        }

        if (responseBody.getData() == null) {
            code = ReplyCodeMessage.Code3005ResponseDataIsNull;
            message = ReplyCodeMessage.Msg3005ResponseDataIsNull;
            return code;
        }
        return 0;
    }

    public void signInPost(@Nullable String via, byte[] priKey, @Nullable RequestBody.SignInMode mode){
        makeSignInRequest(via, mode);
        makeHeaderAsySign(priKey);
        post();
        if(responseBody!=null){
            code = responseBody.getCode();
            message = responseBody.getMessage();
        }
    }

    public boolean get() {
        return get(null,null,null, null);
    }
    public boolean get(ResponseBodyType responseBodyType) {
        return get(null,responseBodyType,null, null);
    }
    public boolean get(String fileName, String responseFilePath){
        return get(null,ResponseBodyType.FILE,fileName, responseFilePath);
    }

    public boolean get(byte[] sessionKey) {
        return get(sessionKey,null,null, null);
    }
    public boolean get(byte[] sessionKey, ResponseBodyType responseBodyType) {
        return get(sessionKey,responseBodyType,null,null );
    }
    public boolean get(byte[] sessionKey, String responseFileName, String responseFilePath) {
        return get(sessionKey,ResponseBodyType.FILE,responseFileName, responseFilePath);
    }

    public boolean get(byte[] sessionKey, ResponseBodyType responseBodyType, String responseFileName, String responseFilePath){
        if(responseBodyType==null)responseBodyType=ResponseBodyType.STRING;
        if (apiUrl.getUrl() == null) {
            code = ReplyCodeMessage.Code3004RequestUrlIsAbsent;
            message = ReplyCodeMessage.Msg3004RequestUrlIsAbsent;
            System.out.println(message);
            return false;
        }

        try(CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(apiUrl.getUrl());

            // add request headers
            if(requestHeaderMap!=null) {
                for (String head : requestHeaderMap.keySet()) {
                    request.addHeader(head, requestHeaderMap.get(head));
                }
            }

            httpResponse = httpClient.execute(request);

            if(httpResponse==null){
                code= ReplyCodeMessage.Code3002GetRequestFailed;
                message = ReplyCodeMessage.Msg3002GetRequestFailed;
                return false;
            }

            parseResponseHeader();

            switch (responseBodyType){
                case STRING ->{
                    responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
                    responseBodyStr = new String(responseBodyBytes);
                    parseFcResponse(httpResponse);
                    try {
                        this.responseBody = new Gson().fromJson(responseBodyStr, FcReplier.class);
                    } catch (JsonSyntaxException ignore) {
                        log.debug("Failed to parse responseBody json.");
                        return false;
                    }
//                    if(!done)return false;
                }
                case BYTES -> {
//                    this.responseHeaderMap = getHeaders(httpResponse);
                    responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();

                }
                case FILE -> {
//                    this.responseHeaderMap = getHeaders(httpResponse);
                    String fileName;
                    if(responseFileName==null)fileName= StringTools.getTempName();
                    else fileName=responseFileName;
                    String gotDid = downloadFileFromHttpResponse(fileName, responseFilePath);
                    if(gotDid==null){
                        code= ReplyCodeMessage.Code1020OtherError;
                        message = "Failed to download file from HttpResponse.";
                        return false;
                    }
                    if(responseFileName==null){
                        Files.move(Paths.get(fileName),Paths.get(gotDid), StandardCopyOption.REPLACE_EXISTING);
                    }
                    if(responseBody==null)responseBody=new FcReplier();
                    responseBody.setCode(ReplyCodeMessage.Code0Success);
                    responseBody.setMessage(ReplyCodeMessage.Msg0Success);
                    responseBody.setData(gotDid);

                }
                default -> {
                    code = ReplyCodeMessage.Code1020OtherError;
                    message = "ResponseBodyType is null.";
                    return false;
                }
            }
        } catch (IOException e) {
            log.error("Error when requesting post.", e);
            code = ReplyCodeMessage.Code3007ErrorWhenRequestingPost;
            message = ReplyCodeMessage.Msg3007ErrorWhenRequestingPost+":"+e.getMessage();
            return false;
        }

        if (responseHeaderMap != null && responseHeaderMap.get(SIGN) != null) {
            if (sessionKey==null || !checkResponseSign(sessionKey)) {
                code = ReplyCodeMessage.Code1008BadSign;
                message = ReplyCodeMessage.Msg1008BadSign;
                return false;
            }
        }

        return checkResponseCode();
    }

    private boolean checkResponseCode() {
        if(responseBody!=null){
            if(responseBody.getMessage()!=null)
                message = responseBody.getMessage();
            if(responseBody.getCode()!=null) {
                code = responseBody.getCode();
            }
            return code==0;
        }
        return false;
    }
//
//    public String get(String did){
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//
//        try {
//            HttpGet request = new HttpGet(apiUrl.getUrl());
//
//            // add request headers
//            if(requestHeaderMap!=null) {
//                for (String head : requestHeaderMap.keySet()) {
//                    request.addHeader(head, requestHeaderMap.get(head));
//                }
//            }
//            httpResponse = httpClient.execute(request);
//
//            if(httpResponse==null){
//                code= ReplyInfo.Code3002GetRequestFailed;
//                message = ReplyInfo.Msg3002GetRequestFailed;
//                return null;
//            }
//            return downloadFileFromHttpResponse(did);
//        } catch (Exception e) {
//            e.printStackTrace();
//            code= ReplyInfo.Code3003CloseHttpClientFailed;
//            message = ReplyInfo.Msg3003CloseHttpClientFailed;
//            return null;
//        } finally {
//            try {
//                httpClient.close();
//            } catch (Exception e) {
//                e.printStackTrace();
//                code= ReplyInfo.Code3003CloseHttpClientFailed;
//                message = ReplyInfo.Msg3003CloseHttpClientFailed;
//                return null;
//            }
//        }
//    }

    public String downloadFileFromHttpResponse(String did, String responseFilePath) {
        if(responseFilePath==null)responseFilePath=System.getProperty(Constants.UserDir);
        if(httpResponse==null)
            return null;
        String finalFileName=did;
        InputStream inputStream = null;
        try {
            inputStream = httpResponse.getEntity().getContent();
        } catch (IOException e) {
            code= ReplyCodeMessage.Code1020OtherError;
            message="Failed to get inputStream from http response.";
            return null;
        }

        while(true) {
            File file = new File(responseFilePath,finalFileName);
            if (!file.exists()) {
                try {
                    boolean done = file.createNewFile();
                    if (!done) {
                        code = ReplyCodeMessage.Code1020OtherError;
                        message = "Failed to create file " + finalFileName;
                        return null;
                    }
                    break;
                } catch (IOException e) {
                    code = ReplyCodeMessage.Code1020OtherError;
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

        if(!responseFilePath.endsWith("/"))responseFilePath=responseFilePath+"/";
        try(FileOutputStream outputStream = new FileOutputStream(responseFilePath+finalFileName)){
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                hasher.putBytes(buffer, 0, bytesRead);
            }
            inputStream.close();
        } catch (IOException e) {
            code= ReplyCodeMessage.Code1020OtherError;
            message="Failed to read buffer.";
            return null;
        }

        String didFromResponse = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));

        if(!did.equals(didFromResponse)){
            code= ReplyCodeMessage.Code1020OtherError;
            message="The DID of the file from response is not equal to the requested DID.";
            return null;
        }

        if(!finalFileName.equals(did)){
            try {
                Files.move(Paths.get(responseFilePath,finalFileName), Paths.get(responseFilePath,did), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                code= ReplyCodeMessage.Code1020OtherError;
                message="Failed to replace the old file.";
                return null;
            }
        }

        return didFromResponse;
    }
    public boolean post() {
        return post(null,null,null,null,null,null);
    }
    public boolean post(String requestFileName) {
        return post(null,RequestBodyType.FILE,null,requestFileName,null,null);
    }
    public boolean post(byte[] sessionKey) {
        return post(sessionKey,null,null,null,null,null);
    }
    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType) {
        return post(sessionKey, requestBodyType,null,null,null,null);
    }
    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType,ResponseBodyType responseBodyType) {
        return post(sessionKey, requestBodyType,responseBodyType,null,null,null);
    }
    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType, String fileName) {
        return post(sessionKey, requestBodyType,null,fileName,null,null);
    }
    public boolean post(byte[] sessionKey, ResponseBodyType responseBodyType, String responseFileName, String responseFilePath) {
        return post(sessionKey, null,responseBodyType,null,responseFileName,responseFilePath);
    }

    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType, ResponseBodyType responseBodyType, String requestFileName, String responseFileName,String responseFilePath) {
        if(requestBodyType==null)requestBodyType=RequestBodyType.STRING;
        if(responseBodyType==null)responseBodyType=ResponseBodyType.STRING;

        if (apiUrl.getUrl() == null) {
            code = ReplyCodeMessage.Code3004RequestUrlIsAbsent;
            message = ReplyCodeMessage.Msg3004RequestUrlIsAbsent;
            System.out.println(message);
            return false;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(apiUrl.getUrl());
            if (requestHeaderMap != null) {
                for (String key : requestHeaderMap.keySet()) {
                    httpPost.setHeader(key, requestHeaderMap.get(key));
                }
            }

            switch (requestBodyType){
                case STRING ->{
                    StringEntity entity = new StringEntity(new String(requestBodyBytes));
                    httpPost.setEntity(entity);
                }
                case BYTES -> {
                    ByteArrayEntity entity = new ByteArrayEntity(requestBodyBytes);
                    httpPost.setEntity(entity);
                }
                case FILE -> {
                    File file = new File(requestFileName);
                    if(!file.exists()){
                        code = ReplyCodeMessage.Code1020OtherError;
                        message = "File "+requestFileName+" doesn't exist.";
                        return false;
                    }
                    FileInputStream fileInputStream = new FileInputStream(file);
                    HttpEntity entity = new InputStreamEntity(
                            fileInputStream,
                            file.length(),
                            ContentType.APPLICATION_OCTET_STREAM
                    );
                    httpPost.setEntity(entity);
                }
                default -> {
                    return false;
                }
            }

            try {
                httpResponse = httpClient.execute(httpPost);
            }catch (HttpHostConnectException e){
                log.debug("Failed to connect "+apiUrl.getUrl()+". Check the URL.");
                code = ReplyCodeMessage.Code3001ResponseIsNull;
                message = ReplyCodeMessage.Msg3001ResponseIsNull;
                return false;
            }

            if (httpResponse == null) {
                log.debug("httpResponse == null.");
                code = ReplyCodeMessage.Code3001ResponseIsNull;
                message = ReplyCodeMessage.Msg3001ResponseIsNull;
                return false;
            }

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                log.debug("Post response status: {}.{}", httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase());
                if(httpResponse.getHeaders(UpStrings.CODE)!=null&&httpResponse.getHeaders(UpStrings.CODE).length>0){
                    if(httpResponse.getHeaders(UpStrings.CODE)[0]!=null) {
                        code = Integer.valueOf(httpResponse.getHeaders(UpStrings.CODE)[0].getValue());
                        message = ReplyCodeMessage.getMsg(code);
                        log.debug("Code:{}. Message:{}",code,message);
                    }
                }else {
                    code = ReplyCodeMessage.Code3006ResponseStatusWrong;
                    message = ReplyCodeMessage.Msg3006ResponseStatusWrong + ": " + httpResponse.getStatusLine().getStatusCode();
                    log.debug("Code:{}. Message:{}",code,message);
                }
                return false;
            }

            parseResponseHeader();

            switch (responseBodyType){
                case STRING ->{
                    responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
                    responseBodyStr = new String(responseBodyBytes);
                    try {
                        this.responseBody = new Gson().fromJson(responseBodyStr, FcReplier.class);
                    } catch (JsonSyntaxException ignore) {
                        log.debug("Failed to parse responseBody json.");
                        return false;
                    }
                }
                case BYTES -> responseBodyBytes = httpResponse.getEntity().getContent().readAllBytes();
                case FILE -> {
                    String fileName;
                    if(responseFileName==null)fileName= StringTools.getTempName();
                    else fileName=responseFileName;
                    String gotDid = downloadFileFromHttpResponse(fileName,responseFilePath);
                    if(responseFileName==null){
                        Files.move(Paths.get(fileName),Paths.get(gotDid), StandardCopyOption.REPLACE_EXISTING);
                    }
                    if(responseBody==null)responseBody=new FcReplier();
                    responseBody.setCode(ReplyCodeMessage.Code0Success);
                    responseBody.setMessage(ReplyCodeMessage.Msg0Success);
                    responseBody.setData(gotDid);
                }
                default -> {
                    code = ReplyCodeMessage.Code1020OtherError;
                    message = "ResponseBodyType is null.";
                    log.debug("Code:{}. Message:{}",code,message);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("Error when requesting post.", e);
            code = ReplyCodeMessage.Code3007ErrorWhenRequestingPost;
            message = ReplyCodeMessage.Msg3007ErrorWhenRequestingPost+":"+e.getMessage();
            log.debug("Code:{}. Message:{}",code,message);
            return false;
        }


        if (responseHeaderMap != null && responseHeaderMap.get(SIGN) != null) {
            if (sessionKey==null || !checkResponseSign(sessionKey)) {
                code = ReplyCodeMessage.Code1008BadSign;
                message = ReplyCodeMessage.Msg1008BadSign;
                log.debug("Code:{}. Message:{}",code,message);
                return false;
            }
        }
        return checkResponseCode();
    }

    private void parseResponseHeader() {
        this.responseHeaderMap = getHeaders(httpResponse);
        String sign = this.responseHeaderMap.get(SIGN);
        String sessionName = this.responseHeaderMap.get(SESSION_NAME);
        this.signatureOfResponse = new Signature(sign, sessionName);
    }

    public void makeRequestBody() {
        requestBody = new RequestBody();
        requestBody.makeRequestBody(apiUrl.getUrl(), requestBody.getVia());
    }

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
//    public boolean post(byte[] sessionKey, RequestBodyType requestBodyType, ResponseBodyType responseBodyType, String requestFileName,String responseFileName) {
//
////        if(requestHeaderMap==null)
////            requestHeaderMap = new HashMap<>();
//        if (apiUrl.getUrl() == null) {
//            code = ReplyInfo.Code3004RequestUrlIsAbsent;
//            message = ReplyInfo.Msg3004RequestUrlIsAbsent;
//            System.out.println(message);
//            return false;
//        }
//
//        post(sessionKey,requestBodyType,responseBodyType, requestFileName, responseFileName);
//
//        if (responseHeaderMap != null && responseHeaderMap.get(UpStrings.SIGN) != null) {
//            if (sessionKey==null || !checkResponseSign(sessionKey)) {
//                code = ReplyInfo.Code1008BadSign;
//                message = ReplyInfo.Msg1008BadSign;
//                return false;
//            }
//        }
//
//        if(responseBody!=null){
//            boolean done=false;
//            if(responseBody.getCode()!=null) {
//                code = responseBody.getCode();
//                if(code==0)done=true;
//            }
//            if(responseBody.getMessage()!=null)
//                message = responseBody.getMessage();
//            return done;
//        }
//        return false;
//    }

    protected void makeHeaderAsySign(byte[] priKey) {
        if (priKey == null) return;

        ECKey ecKey = ECKey.fromPrivate(priKey);

        requestBodyStr = new String(requestBodyBytes, StandardCharsets.UTF_8);
        String sign = ecKey.signMessage(requestBodyStr);
        String fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();

        signatureOfRequest = new Signature(fid, requestBodyStr, sign, AlgorithmId.EccAes256K1P7_No1_NrC7.name());
        requestHeaderMap.put(UpStrings.FID, fid);
        requestHeaderMap.put(SIGN, signatureOfRequest.getSign());
    }
    protected void makeFcdslRequest(@Nullable String via, @Nullable Fcdsl fcdsl) {
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

    protected void makeSignInRequest(@Nullable String via, @Nullable RequestBody.SignInMode mode) {
        requestBody = new RequestBody(apiUrl.getUrl(), via, mode);
        makeRequestBodyBytes();
        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    public void makeHeaderSession(byte[] sessionKey,byte[] dataBytes) {
        String sign = Session.getSessionKeySign(sessionKey, dataBytes);
        signatureOfRequest = new Signature(sign, ApipTools.getSessionName(sessionKey));
        if(requestHeaderMap==null)requestHeaderMap=new HashMap<>();
        requestHeaderMap.put(UpStrings.SESSION_NAME, signatureOfRequest.getSymKeyName());
        requestHeaderMap.put(SIGN, signatureOfRequest.getSign());
    }

    //
//    public void doFilePost(File file) {
//
//        requestHeaderMap.put("Content-Type", "application/octet-stream");
//        try (CloseableHttpClient httpClient = HttpClients.createDefault();
//             FileInputStream fileInputStream = new FileInputStream(file)) {
//            HttpPost httpPost = new HttpPost(apiUrl.getUrl());
//            if (requestHeaderMap != null) {
//                for (String key : requestHeaderMap.keySet()) {
//                    httpPost.setHeader(key, requestHeaderMap.get(key));
//                }
//            }
//            // Set the InputStreamEntity to stream the contents of the file
//            HttpEntity fileEntity = new InputStreamEntity(
//                    fileInputStream,
//                    file.length(),
//                    ContentType.APPLICATION_OCTET_STREAM
//            );
//            httpPost.setEntity(fileEntity);
//
//            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
//                if (response == null) {
//                    log.debug("httpResponse == null.");
//                    code = ReplyInfo.Code3001ResponseIsNull;
//                    message = ReplyInfo.Msg3001ResponseIsNull;
//                    return;
//                }
//                if (response.getStatusLine().getStatusCode() != 200) {
//                    log.debug("Post response status: {}.{}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
//                    if(response.getHeaders((UpStrings.CODE))!=null){
//                        try {
//                            code = Integer.valueOf(response.getHeaders(UpStrings.CODE)[0].getValue());
//                        }catch (Exception ignore){}
//                    }
//                    else code = ReplyInfo.Code3006ResponseStatusWrong;
//                    message = ReplyInfo.Msg3006ResponseStatusWrong + ": " + response.getStatusLine().getStatusCode();
//                    return;
//                }
//                HttpEntity responseEntity = response.getEntity();
//                responseBodyBytes = responseEntity.getContent().readAllBytes();
//
//                responseBodyStr = new String(responseBodyBytes);
//                parseFcResponse(response);
//                EntityUtils.consume(responseEntity);
//
//            }catch (HttpHostConnectException e){
//                log.debug("Failed to connect "+apiUrl.getUrl()+". Check the URL.");
//                code = ReplyInfo.Code3001ResponseIsNull;
//                message = ReplyInfo.Msg3001ResponseIsNull;
//                return;
//            }
//        } catch (FileNotFoundException e){
//            code = ReplyInfo.Code1020OtherError;
//            message = "It's a directory instead of a file.";
//        } catch (Exception e) {
//            e.printStackTrace();
//            log.error("Error when requesting post.", e);
//            code = ReplyInfo.Code3007ErrorWhenRequestingPost;
//            message = ReplyInfo.Msg3007ErrorWhenRequestingPost;
//        }
//    }

    protected boolean parseFcResponse(HttpResponse response) {
        if (response == null) return false;
        Gson gson = new Gson();
        String sign;
        try {
            this.responseBody = gson.fromJson(responseBodyStr, FcReplier.class);
        } catch (JsonSyntaxException ignore) {
            return false;
        }

        if (response.getHeaders(SIGN) != null && response.getHeaders(SIGN).length > 0) {
            sign = response.getHeaders(SIGN)[0].getValue();
            this.responseHeaderMap.put(SIGN, sign);
            String symKeyName = null;
            if (response.getHeaders(UpStrings.SESSION_NAME) != null && response.getHeaders(UpStrings.SESSION_NAME).length > 0) {
                symKeyName = response.getHeaders(UpStrings.SESSION_NAME)[0].getValue();
                this.responseHeaderMap.put(UpStrings.SESSION_NAME, symKeyName);
            }
            this.signatureOfResponse = new Signature(sign, symKeyName);
        }
        return true;
    }

    public static Map<String, String> getHeaders(HttpResponse response) {
        Map<String, String> headersMap = new HashMap<>();
        Header[] headers = response.getAllHeaders();

        for (Header header : headers) {
            headersMap.put(header.getName(), header.getValue());
        }

        return headersMap;
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

    public FcReplier getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(FcReplier responseBody) {
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
