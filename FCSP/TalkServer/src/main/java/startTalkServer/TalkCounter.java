package startTalkServer;

import feip.feipData.serviceParams.Params;
import server.Counter;
import server.Settings;

public class TalkCounter extends Counter {


    public TalkCounter(Settings settings, Params params, byte[] symKey) {
        super(settings, params, symKey);
    }

    @Override
    public void localTask() {
    }
}
