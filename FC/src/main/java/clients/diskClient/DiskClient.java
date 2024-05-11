package clients.diskClient;

import clients.ApiUrl;
import clients.Client;
import clients.ClientData;
import clients.apipClient.ApipClient;
import clients.apipClient.ApipDataGetter;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import config.ApiAccount;
import config.ApiProvider;
import config.ApiType;
import constants.ApiNames;
import constants.ReplyInfo;
import crypto.cryptoTools.Hash;
import javaTools.Hex;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static constants.FieldNames.DID;

public class DiskClient extends Client {

    public DiskClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider,apiAccount,symKey,apipClient);
        this.signInUrlTailPath= ApiUrl.makeUrlTailPath(ApiNames.DiskApiType,ApiNames.SN_0,ApiNames.VersionV1);
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

    public String getFree(String did) {
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        clientData = new ClientData(apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.GetApi, urlParamMap);
        return clientData.getFile(did);
    }

    public String get(String did) {
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        clientData = new ClientData(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.GetApi, apiAccount.getVia(), ClientData.AuthType.FC_SIGN_URL, urlParamMap);
        return clientData.getFile(did);
    }

    public String getPost(String did) {
        Map<String,String> urlParamMap= new HashMap<>();
        urlParamMap.put(DID,did);
        clientData = new ClientData(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.GetApi, apiAccount.getVia(), ClientData.AuthType.FC_SIGN_BODY, urlParamMap);
        clientData.postWithJsonBody(apiAccount.getVia(),sessionKey);
        return clientData.getFile(did);
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
        clientData = new ClientData(sessionKey, apiAccount.getApiUrl(), ApiNames.DiskApiType, ApiNames.SN_0, ApiNames.VersionV1, ApiNames.PutApi, apiAccount.getVia(), ClientData.AuthType.FC_SIGN_URL, null);
        clientData.post(sessionKey, ClientData.BodyType.FILE,new File(fileName));
        Object data = checkResult( ApiType.DISK);
        if(sessionFreshen)data = checkResult(ApiType.DISK);
        if(data==null)return null;
        String respondDid = ApipDataGetter.getStringMap(data).get(DID);
        String localDid;
        try {
            localDid = Hash.Sha256x2(new File(fileName));
        } catch (IOException e) {
            clientData.setCode(ReplyInfo.Code1020OtherError);
            clientData.setMessage("Failed to hash local file.");
            return null;
        }
        if(localDid.equals(respondDid))return respondDid;
        else {
            clientData.setCode(ReplyInfo.Code1020OtherError);
            clientData.setMessage("Wrong DID."+"\nLocal DID:"+localDid+"\nrespond DID:"+respondDid);
            return null;
        }
    }


}
