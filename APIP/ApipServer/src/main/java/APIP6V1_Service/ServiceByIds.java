package APIP6V1_Service;

import apip.apipData.Sort;
import clients.apipClient.DataGetter;
import constants.ApiNames;
import constants.IndicesNames;
import constants.ReplyCodeMessage;
import fcData.FcReplier;
import feip.feipData.Service;
import initial.Initiator;
import javaTools.http.AuthType;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import server.FcdslRequestHandler;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static constants.Strings.SID;


@WebServlet(name = ApiNames.ServiceByIdsAPI, value = "/"+ApiNames.SN_6+"/"+ApiNames.Version2 +"/"+ApiNames.ServiceByIdsAPI)
public class ServiceByIds extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.SERVICE,SID,Service.class,request,response,authType);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.SERVICE,SID,Service.class,request,response,authType);
    }

//    private void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
//        FcReplier replier = new FcReplier(Initiator.sid,response);
//        try (Jedis jedis = Initiator.jedisPool.getResource()) {
//            List<Service> meetList = doIdsRequest(IndicesNames.SERVICE, Service.class, request, response, authType, replier, jedis);
//            if (meetList == null) return;
//            Map<String, Service> meetMap = new HashMap<>();
//            for(Service service :meetList){
//                meetMap.put(service.getSid(),service);
//            }
//            replier.reply0Success(meetMap,jedis);
//        }
//    }

//    @Nullable
//    private static <T> List<T> doIdsRequest(String indexName, Class<T> tClass, HttpServletRequest request, HttpServletResponse response, AuthType authType, FcReplier replier, Jedis jedis) {
//        RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis);
//        if (requestCheckResult == null) {
//            return null;
//        }
//
//        //Check if IDs Request
//        if(requestCheckResult.getRequestBody().getFcdsl()==null || requestCheckResult.getRequestBody().getFcdsl().getIds()==null){
//            replier.reply(ReplyCodeMessage.Code1012BadQuery, null, jedis);
//            return null;
//        }
//        //Set default sort.
//
//        //Request
//        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response, replier, Initiator.esClient);
//        ArrayList<Sort> defaultSortList = null;
//
//        List<T> meetList = fcdslRequestHandler.doRequest(indexName, defaultSortList, tClass, jedis);
//        if(meetList==null|| meetList.isEmpty()) return null;
//        return meetList;
//    }


    @Nullable
    private static <T> void doIdsRequest(String indexName,String keyName, Class<T> tClass, HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        FcReplier replier = new FcReplier(Initiator.sid,response);
        try (Jedis jedis = Initiator.jedisPool.getResource()) {

            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis);
            if (requestCheckResult == null) {
                return ;
            }

            //Check if IDs Request
            if (requestCheckResult.getRequestBody().getFcdsl() == null || requestCheckResult.getRequestBody().getFcdsl().getIds() == null) {
                replier.reply(ReplyCodeMessage.Code1012BadQuery, null, jedis);
                return;
            }
            //Set default sort.

            //Request
            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response, replier, Initiator.esClient);
            ArrayList<Sort> defaultSortList = null;

            List<T> meetList = fcdslRequestHandler.doRequest(indexName, defaultSortList, tClass, jedis);
            if (meetList == null || meetList.isEmpty()) return;

            Map<String, T> meetMap = DataGetter.listToMap(meetList,keyName);

            replier.reply0Success(meetMap, jedis);
        }
    }

}