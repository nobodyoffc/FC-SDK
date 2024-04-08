package FEIP.feipData.serviceParams;

import FEIP.feipData.Service;

import java.io.BufferedReader;

public abstract class Params {
//    Params inputParams(BufferedReader br, byte[] symKey){};

    protected abstract Params inputParams(BufferedReader br, byte[] symKey);
    public abstract Params updateParams(BufferedReader br, byte[] symKey);

    protected static Params getParamsFromService(Service service) {
        return null;
    }
}
