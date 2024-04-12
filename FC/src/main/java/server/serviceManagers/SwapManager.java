package server.serviceManagers;

import FEIP.feipData.serviceParams.Params;
import FEIP.feipData.serviceParams.SwapParams;
import appTools.Inputer;
import config.ApiAccount;
import server.Starter;

import java.io.BufferedReader;

public class SwapManager extends ServiceManager {
    public SwapManager(ApiAccount apipAccount, Class<?> paramsClass) {
        super(apipAccount, paramsClass);
    }

//    public void publishService(byte[] symKey,BufferedReader br) {
//        if(Inputer.askIfYes(br,"Publish a new service? y/n")){
//            SwapManager swapManager = new SwapManager(apipAccount, SwapParams.class);
//            swapManager.publishService(symKey, br);
//            System.out.println("Wait for a few minutes and try to start again.");
//            System.exit(0);
//        }
//    }

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
