package FEIP;

import FCH.fchData.OpReturn;
import FEIP.feipData.FcInfo;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import javaTools.JsonTools;
import org.slf4j.Logger;

public class FeipTools {


    public static FcInfo parseFeip(OpReturn opre, Logger log) {

        if(opre.getOpReturn()==null)return null;

        FcInfo feip = null;
        try {
            String json = JsonTools.strToJson(opre.getOpReturn());
            feip = new Gson().fromJson(json,FcInfo.class);
        }catch(JsonSyntaxException e) {
            log.debug("Bad json on {}. ",opre.getTxId());
        }
        return  feip;
    }
}
