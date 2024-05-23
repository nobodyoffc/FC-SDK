package clients.diskClient;

import APIP.apipData.Fcdsl;
import clients.ApiUrl;
import clients.Client;
import clients.ClientTask;
import clients.apipClient.ApipClient;
import clients.apipClient.DataGetter;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import config.ApiType;
import constants.ApiNames;
import constants.Constants;
import constants.ReplyInfo;
import crypto.*;
import crypto.Hash;
import javaTools.FileTools;
import javaTools.Hex;
import javaTools.JsonTools;
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
        super(apiProvider,apiAccount,symKey,apipClient);
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
            cipherFileName = Hex.toHex(result1.getDid());
            Files.move(Paths.get(tempFileName),Paths.get(cipherFileName));
        } catch (IOException e) {
            return null;
        }
        return cipherFileName;

//        EccAes256K1P7 ecc = new EccAes256K1P7();
//
//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(priKeyCipher, symKey);
//        byte[] pubKey = KeyTools.priKeyToPubKey(priKey);
//        File file = new File(fileName);
//        ecc.encrypt(file, Hex.toHex(pubKey));
//        fileName = EccAes256K1P7.getEncryptedFileName(fileName);
//
//        return fileName;
    }

    public static boolean decryptFile(String sourceFileName, String sourcePath, String destFileName, String destPath, byte[] symKey, String priKeyCipher) {
        try {
            Gson gson = new Gson();
//            Affair affair = JsonTools.readOneJsonFromFile(sourcePath, sourceFileName,Affair.class);
//            if (affair == null) {
//                return false;
//            }

//            if (affair.getData() == null){
//                System.out.println("Failed to decrypt. Affair.data is null.");
//                return false;
//            }
            CryptoDataStr cryptoDataStr = JsonTools.readOneJsonFromFile(sourcePath, sourceFileName, CryptoDataStr.class);
            if (cryptoDataStr == null){
                return false;
            }
        } catch (IOException ignore) {
            return false;
        }
        System.out.println("Decrypting...");
        Decryptor decryptorSym = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptorSym.decryptJsonBySymKey(priKeyCipher, symKey);

        Decryptor decryptor = new Decryptor();
        byte[] priKey = cryptoDataByte.getData();

        CryptoDataByte cryptoDataByte1 =
                decryptor.decryptFileByAsyOneWay(sourcePath, sourceFileName, destPath, destFileName, priKey);

        if(cryptoDataByte1.getCode()!=0){
            System.out.println(CryptoCodeMessage.getErrorStringCodeMsg(cryptoDataByte1.getCode()));
            return false;
        }
        System.out.println("Original DID:"+Hex.toHex(cryptoDataByte1.getDid()));
        return true;
//
//        File file = new File(sourcePath, sourceFileName);
//
//        EccAes256K1P7 ecc = new EccAes256K1P7();
//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(priKeyCipher, symKey);
//        ecc.decrypt(file,priKey);
//        File encryptedFile = new File(sourcePath,EccAes256K1P7.getDecryptedFileName(destFileName));
//        String newDid;
//        try {
//            newDid = Hash.sha256(encryptedFile);
//            System.out.println("Original DID:"+newDid);
//        } catch (IOException e) {
//            System.out.println("Failed to hash new file.");
//            return false;
//        }
//        File dest = new File(destPath,newDid);
//        boolean done = encryptedFile.renameTo(dest);
//        if(done){
//            System.out.println("Decrypted to: "+dest.getAbsolutePath());
//            file.delete();
//        }
//        else{
//            System.out.println("Failed to rename the file.");
//            return false;
//        }
//        return true;
    }


//    public String putPost(byte[] dataPut) {
//        clientData = new ClientData();
//
//        String url = makeUrlForSign();
//        clientData.setUrl(url);
//
//        clientData.postBinaryWithUrlSign(sessionKey, dataPut);
//        Object data = checkResult("put", ApiType.DISK);
//        if(sessionFreshen)data = checkResult("put", ApiType.DISK);
//        if(data==null)return null;
//        return ApipDataGetter.getStringMap(data).get(FieldNames.DID);
//    }

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
        boolean done = clientTask.get(sessionKey,did,localPath );
        if(done)return (String) clientTask.getResponseBody().getData();
        else return clientTask.getMessage();
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
            clientTask.setCode(ReplyInfo.Code1020OtherError);
            clientTask.setMessage("Failed to hash local file.");
            return null;
        }
        if(localDid.equals(respondDid))return respondDid;
        else {
            clientTask.setCode(ReplyInfo.Code1020OtherError);
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
                clientTask.setCode(ReplyInfo.Code1020OtherError);
                clientTask.setMessage("Failed to hash local file.");
                return null;
            }
            if(localDid.equals(respondDid))
                return respondDid;
            else {
                clientTask.setCode(ReplyInfo.Code1020OtherError);
                clientTask.setMessage("Wrong DID."+"\nLocal DID:"+localDid+"\nrespond DID:"+respondDid);
                return null;
            }
        }
        else {
         return clientTask.getCode()+" "+ clientTask.getMessage();
        }
    }
}
