package server.serviceManagers;

import FEIP.feipData.serviceParams.Params;
import FEIP.feipData.serviceParams.SwapParams;
import config.ApiAccount;

import java.io.BufferedReader;

public class SwapManager extends ServiceManager {
    public SwapManager(ApiAccount apipAccount, Class<?> paramsClass) {
        super(apipAccount, paramsClass);
    }
    @Override
    public Params inputParams(byte[] symKey, BufferedReader br) {
        SwapParams swapParams = new SwapParams();
        swapParams.inputParams(br, symKey);
        return swapParams;
    }

    @Override
    public void updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
        serviceParams.updateParams(br,symKey);
    }
}
