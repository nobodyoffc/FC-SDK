package startManager;

import FEIP.feipData.serviceParams.Params;
import clients.diskClient.DiskDataInfo;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.FieldNames;
import javaTools.FileTools;
import server.Counter;
import server.Settings;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import static constants.Strings.DATA;

public class DiskCounter extends Counter {


    public DiskCounter(Settings settings, Params params) {
        super(settings, params);
    }

    @Override
    public void localTask() {
        deleteExpiredFiles(sid);
    }

    private static void deleteExpiredFiles(String sid) {
        Date date = new Date();
        SearchResponse<DiskDataInfo> result;
        try {
            result = StartDiskManager.esClient.search(s -> s.index(Settings.addSidBriefToName(sid,DATA)).query(q -> q.range(r -> r.field(FieldNames.EXPIRE).lt(JsonData.of(date)))), DiskDataInfo.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(result==null || result.hits().total().value()==0) return;

        for(Hit<DiskDataInfo> hit:result.hits().hits()){
            DiskDataInfo source = hit.source();
            if(source==null)continue;
            String did = source.getDid();
            String subDir = FileTools.getSubPathForFreeDisk(did);
            File file = new File(StartDiskManager.STORAGE_DIR+subDir,did);
            if(file.exists())file.delete();
        }
    }
}
