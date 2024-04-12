package FEIP.feipData.serviceParams;

import APIP.apipClient.ApipClient;
import javaTools.JsonTools;

import java.io.BufferedReader;

public abstract class Params {
    protected transient ApipClient apipClient;


    public Params() {}
    public Params(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public abstract void inputParams(BufferedReader br, byte[] symKey);
    public abstract void updateParams(BufferedReader br, byte[] symKey);
    public String toJson(){
        return JsonTools.getNiceString(this);
    }
}
