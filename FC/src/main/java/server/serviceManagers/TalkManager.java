package server.serviceManagers;

import clients.apipClient.ApipClient;
import config.ApiAccount;
import feip.feipData.Service;
import feip.feipData.serviceParams.TalkParams;
import feip.feipData.serviceParams.Params;

import java.io.BufferedReader;
import java.io.IOException;

public class TalkManager extends ServiceManager {

    public TalkManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass) {
        super(service, apipAccount, br, symKey, paramsClass);
    }

    @Override
    protected Params inputParams(byte[] symKey, BufferedReader br) {
        TalkParams talkParams = new TalkParams();
        talkParams.inputParams(br, symKey,(ApipClient) apipAccount.getClient());
        return talkParams;
    }

    @Override
    protected void updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
        TalkParams talkParams = new TalkParams();
        talkParams.updateParams(br, symKey,(ApipClient) apipAccount.getClient());
    }

}
