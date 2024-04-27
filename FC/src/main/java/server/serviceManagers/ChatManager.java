package server.serviceManagers;

import FEIP.feipData.serviceParams.DiskParams;
import FEIP.feipData.serviceParams.Params;
import clients.apipClient.ApipClient;
import config.ApiAccount;

import java.io.BufferedReader;

public class ChatManager extends ServiceManager {

    public ChatManager(ApiAccount apipAccount, Class<?> paramsClass) {
        super(apipAccount, paramsClass);
    }
    @Override
    protected Params inputParams(byte[] symKey, BufferedReader br) {
        DiskParams diskParams = new DiskParams((ApipClient) apipAccount.getClient());
        diskParams.inputParams(br, symKey);
        return diskParams;
    }

    @Override
    protected void updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
    }
}
