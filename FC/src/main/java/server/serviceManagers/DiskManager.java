package server.serviceManagers;

import feip.feipData.Service;
import clients.apipClient.ApipClient;
import feip.feipData.serviceParams.DiskParams;
import feip.feipData.serviceParams.Params;
import config.ApiAccount;

import java.io.BufferedReader;

public class DiskManager extends ServiceManager {


    public DiskManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass) {
        super(service, apipAccount, br, symKey, paramsClass);
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
