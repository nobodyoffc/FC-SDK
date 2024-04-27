package server.serviceManagers;

import clients.apipClient.ApipClient;
import FEIP.feipData.serviceParams.DiskParams;
import FEIP.feipData.serviceParams.Params;
import config.ApiAccount;
import server.serviceManagers.ServiceManager;

import java.io.BufferedReader;

public class DiskManager extends ServiceManager {

    public DiskManager(ApiAccount apipAccount, Class<?> paramsClass) {
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
