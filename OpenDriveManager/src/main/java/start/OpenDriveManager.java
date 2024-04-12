package start;

import APIP.apipClient.ApipClient;
import FEIP.feipData.serviceParams.Params;
import config.ApiAccount;
import server.serviceManagers.ServiceManager;

import java.io.BufferedReader;

public class OpenDriveManager extends ServiceManager {

    public OpenDriveManager(ApiAccount apipAccount, Class<?> paramsClass) {
        super(apipAccount, paramsClass);
    }
    @Override
    protected Params inputParams(byte[] symKey, BufferedReader br) {
        OpenDriveParams openDriveParams = new OpenDriveParams((ApipClient) apipAccount.getClient());
        openDriveParams.inputParams(br, symKey);
        return openDriveParams;
    }

    @Override
    protected void updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
    }
}
