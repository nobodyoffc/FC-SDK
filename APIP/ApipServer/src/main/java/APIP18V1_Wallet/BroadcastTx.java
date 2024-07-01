package APIP18V1_Wallet;

import apip.apipData.RequestBody;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.Hex;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FcdslRequestHandler;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@WebServlet(name = ApiNames.BroadcastTxAPI, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version2 +"/"+ApiNames.BroadcastTxAPI)
public class BroadcastTx extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }

    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            Object other = FcdslRequestHandler.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            //Do this request
            doBroadcastRequest(replier, jedis, other);
        }
    }

    private void doBroadcastRequest(FcReplier replier, Jedis jedis,Object other) {
        String rawTx = (String) other;
        String result = Initiator.naSaRpcClient.sendRawTransaction(rawTx);
        if(result.startsWith("\""))result=result.substring(1);
        if(result.endsWith("\""))result=result.substring(0,result.length()-1);

        if(!Hex.isHexString(result))
            replier.replyOtherError(result,null,jedis);
        else replier.reply0Success(result, jedis);
    }
}
