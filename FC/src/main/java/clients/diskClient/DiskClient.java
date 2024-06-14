package clients.diskClient;

import apip.apipData.Fcdsl;
import clients.ApiUrl;
import clients.Client;
import clients.ClientTask;
import clients.apipClient.ApipClient;
import clients.apipClient.DataGetter;
import config.ApiAccount;
import config.ApiProvider;
import config.ApiType;
import constants.ApiNames;
import constants.Constants;
import constants.ReplyCodeMessage;
import crypto.*;
import crypto.Hash;
import javaTools.BytesTools;
import javaTools.FileTools;
import javaTools.Hex;
import javaTools.StringTools;
import javaTools.http.AuthType;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.*;
import static constants.Strings.ASC;
import static fcData.AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;

public class DiskClient extends Client {

    public DiskClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider, apiAccount, symKey, apipClient);
        this.signInUrlTailPath= ApiUrl.makeUrlTailPath(ApiNames.DiskApiType,ApiNames.SN_0,ApiNames.VersionV1);
    }

    public static String encryptFile(String fileName, String pubKeyHex) {

        byte[] pubKey = Hex.fromHex(pubKeyHex);
        Encryptor encryptor = new Encryptor(FC_EccK1AesCbc256_No1_NrC7);
        String tempFileName = FileTools.getTempFileName();
        CryptoDataByte result1 = encryptor.encryptFileByAsyOneWay(fileName, tempFileName, pubKey);
        if(result1.getCode()!=0)return null;
        String cipherFileName;
        try {
            cipherFileName = Hash.sha256x2(new File(tempFileName));
            Files.move(Paths.get(tempFileName),Paths.get(cipherFileName));
        } catch (IOException e) {
            return null;
        }
        return cipherFileName;
    }

    @Nullable
    public static String decryptFile(String path, String gotFile,byte[]symKey,String priKeyCipher) {
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymKey(priKeyCipher,symKey);
        if(cryptoDataByte.getCode()!=0){
            log.debug("Failed to decrypt the user priKey.");
            log.debug(cryptoDataByte.getMessage());
            return null;
        }
        byte[] priKey = cryptoDataByte.getData();
        CryptoDataByte cryptoDataByte1 = new Decryptor().decryptFileToDidByAsyOneWay(path, gotFile, path, priKey);
        if(cryptoDataByte1.getCode()!=0){
            log.debug("Failed to decrypt file "+ Path.of(path, gotFile));
            return null;
        }
        BytesTools.clearByteArray(priKey);
        return Hex.toHex(cryptoDataByte1.getDid());
    }

    public String getFree(String did, String localPath) {
        localPath = checkLocalPath(localPath);
        if (localPath == null) return null;
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        clientTask = new ClientTask(apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.GetApi, urlParamMap);
        boolean done = clientTask.get(did, localPath);
        if(done)return (String) clientTask.getResponseBody().getData();
        else return clientTask.getMessage();
    }

    public String get(String did, String localPath) {
        localPath = checkLocalPath(localPath);
        if (localPath == null) return null;
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        clientTask = new ClientTask(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.GetApi, apiAccount.getVia(), AuthType.FC_SIGN_URL, urlParamMap);
        clientTask.get(sessionKey,did,localPath );

//        checkBalance(apiAccount,clientTask,symKey);
//        if(done)return (String) clientTask.getResponseBody().getData();
        Object data = checkResult();
        if(data!=null)return (String) data;
        return clientTask.getMessage();
    }

    public String check(String did) {
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        clientTask = new ClientTask(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.CheckApi, apiAccount.getVia(), AuthType.FC_SIGN_URL, urlParamMap);
        boolean done = clientTask.get(sessionKey);
        if(done)return String.valueOf(clientTask.getResponseBody().getData());
        else return clientTask.getMessage();
    }

    public String checkFree(String did) {
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        clientTask = new ClientTask(apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.CheckApi, urlParamMap);
        boolean done = clientTask.get(sessionKey);
        if(done)return String.valueOf(clientTask.getResponseBody().getData());
        else return clientTask.getMessage();
    }

    public List<DiskDataInfo> list(int size, String sort, String order, String[] last) {
        Map<String,String> urlParamMap= new HashMap<>();
        if(sort!=null)urlParamMap.put(SORT,sort);
        if(sort!=null && order!=null)urlParamMap.put(ORDER,order);
        if(size!=0)urlParamMap.put(SIZE,String.valueOf(size));
        if(last!=null && last.length>0)urlParamMap.put(LAST, StringTools.arrayToString(last));
        clientTask = new ClientTask(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.ListApi, apiAccount.getVia(), AuthType.FC_SIGN_URL, urlParamMap);
        boolean done = clientTask.get(sessionKey);
        if(done) return DataGetter.getDiskDataInfoList(clientTask.getResponseBody().getData());
        else return null;
    }

    public List<DiskDataInfo> list(Fcdsl fcdsl) {
        if(fcdsl.getSort()==null){
            fcdsl.addNewSort(SINCE,ASC)
                    .appendSort(DID,ASC);
        }

        clientTask = new ClientTask(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.ListApi, fcdsl, AuthType.FC_SIGN_BODY, apiAccount.getVia());
        boolean done = clientTask.post(sessionKey);
        if(done) return DataGetter.getDiskDataInfoList(clientTask.getResponseBody().getData());
        else return null;
    }

    public List<DiskDataInfo> listFree(int size,String sort,String order,String[] last) {
        Map<String,String> urlParamMap= new HashMap<>();
        if(sort!=null)urlParamMap.put(SORT,sort);
        if(sort!=null && order!=null)urlParamMap.put(ORDER,order);
        if(size!=0)urlParamMap.put(SIZE,String.valueOf(size));
        if(last!=null && last.length>0)urlParamMap.put(LAST, StringTools.arrayToString(last));
        clientTask = new ClientTask(apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.ListApi, urlParamMap);
        boolean done = clientTask.get(sessionKey);
        if(done) return DataGetter.getDiskDataInfoList(clientTask.getResponseBody().getData());
        else return null;
    }

    public String getPost(String did, String localPath) {
        Map<String,String> urlParamMap= new HashMap<>();

        localPath = checkLocalPath(localPath);
        if (localPath == null) return null;

        urlParamMap.put(DID,did);
        clientTask = new ClientTask(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.GetApi, apiAccount.getVia(), AuthType.FC_SIGN_BODY, urlParamMap);

        boolean done = clientTask.post(sessionKey, ClientTask.ResponseBodyType.FILE,did, localPath);

        if(done){
            return (String) clientTask.getResponseBody().getData();
        }else{
            return clientTask.getMessage();
        }
    }

    @Nullable
    private static String checkLocalPath(String localPath) {
        if(localPath ==null) localPath =System.getProperty(Constants.UserDir);
        Path path = Paths.get(localPath);
        if(Files.notExists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                log.debug("Failed to create path:" + localPath);
                return null;
            }
        }
        return localPath;
    }

//    @Nullable
//    public String downloadFileFromHttpResponse(String fileName) {
//        if(clientData.getHttpResponse()==null)
//            return null;
//
//        InputStream inputStream = null;
//        try {
//            inputStream = clientData.getHttpResponse().getEntity().getContent();
//        } catch (IOException e) {
//            clientData.setCode(ReplyInfo.Code1020OtherError);
//            clientData.setMessage("Failed to get inputStream from http response.");
//            return null;
//        }
//        File file = new File(fileName);
//
//        if(!file.exists()) {
//            try {
//                boolean done = file.createNewFile();
//                if(!done){
//                    clientData.setCode(ReplyInfo.Code1020OtherError);
//                    clientData.setMessage("Failed to create file "+fileName);
//                    return null;
//                }
//            } catch (IOException e) {
//                clientData.setCode(ReplyInfo.Code1020OtherError);
//                clientData.setMessage("Failed to create file "+fileName);
//                return null;
//            }
//        }
//        HashFunction hashFunction = Hashing.sha256();
//        Hasher hasher = hashFunction.newHasher();
//        try(FileOutputStream outputStream = new FileOutputStream(fileName)){
//            byte[] buffer = new byte[8192];
//            int bytesRead;
//            while ((bytesRead = inputStream.read(buffer)) != -1) {
//                outputStream.write(buffer, 0, bytesRead);
//                hasher.putBytes(buffer, 0, bytesRead);
//            }
//            inputStream.close();
//        } catch (IOException e) {
//            clientData.setCode(ReplyInfo.Code1020OtherError);
//            clientData.setMessage("Failed to read buffer.");
//            return null;
//        }
//
//
//        String didFromResponse = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));
//
//
//        if(!fileName.equals(didFromResponse)){
//            clientData.setCode(ReplyInfo.Code1020OtherError);
//            clientData.setMessage("The DID of the file from response is not equal to the requested DID.");
//            return null;
//        }
//
//        return didFromResponse;
//    }

    public String put(String fileName) {
        clientTask = new ClientTask(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.PutApi, apiAccount.getVia(), AuthType.FC_SIGN_URL, null);
        clientTask.post(sessionKey, ClientTask.RequestBodyType.FILE,fileName);
        Object data = checkResult( ApiType.DISK);
        if(sessionFreshen)data = checkResult(ApiType.DISK);
        if(data==null)return null;
        String respondDid = DataGetter.getStringMap(data).get(DID);
        String localDid;
        try {
            localDid = Hash.sha256x2(new File(fileName));
        } catch (IOException e) {
            clientTask.setCode(ReplyCodeMessage.Code1020OtherError);
            clientTask.setMessage("Failed to hash local file.");
            return null;
        }
        if(localDid.equals(respondDid))return respondDid;
        else {
            clientTask.setCode(ReplyCodeMessage.Code1020OtherError);
            clientTask.setMessage("Wrong DID."+"\nLocal DID:"+localDid+"\nrespond DID:"+respondDid);
            return null;
        }
    }

    public String putFree(String fileName) {
        clientTask = new ClientTask(apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.PutApi);
        boolean done = clientTask.post(fileName);
        if(done){
            Object data = clientTask.getResponseBody().getData();
            if(data==null)return null;
            String respondDid = DataGetter.getStringMap(data).get(DID);
            String localDid;
            try {
                localDid = Hash.sha256x2(new File(fileName));
            } catch (IOException e) {
                clientTask.setCode(ReplyCodeMessage.Code1020OtherError);
                clientTask.setMessage("Failed to hash local file.");
                return null;
            }
            if(localDid.equals(respondDid))
                return respondDid;
            else {
                clientTask.setCode(ReplyCodeMessage.Code1020OtherError);
                clientTask.setMessage("Wrong DID."+"\nLocal DID:"+localDid+"\nrespond DID:"+respondDid);
                return null;
            }
        }
        else {
         return clientTask.getCode()+" "+ clientTask.getMessage();
        }
    }
}
