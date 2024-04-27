package clients.diskClient;

import clients.Client;
import clients.ClientData;
import clients.apipClient.ApipClient;
import clients.apipClient.ApipDataGetter;
import config.ApiAccount;
import config.ApiProvider;
import constants.ApiNames;
import constants.FieldNames;

public class DiskClient extends Client {

    public DiskClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symKey, ApipClient apipClient) {
        super(apiProvider,apiAccount,symKey,apipClient);
    }

    public String putPost(byte[] dataPut) {
        clientData = new ClientData();
        String url = apiAccount.getApiUrl()+ ApiNames.FreeDiskApiPath + ApiNames.putApi;
        clientData.setUrl(url);
        clientData.postBinaryWithUrlSign(apipClient.getSessionKey(), dataPut);
        Object data = checkResult("put");
        if(data==null)return null;
        return ApipDataGetter.getStringMap(data).get(FieldNames.DID);
    }
}
