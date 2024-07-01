package initial;

import constants.ApiNames;
import fcData.FcReplier;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/"+ApiNames.PingApi)
public class Ping extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {

        FcReplier replier = new FcReplier(Initiator.sid,response);

        AuthType authType = AuthType.FC_SIGN_BODY;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis);
            if (requestCheckResult==null) return;
            replier.reply0Success(null, jedis);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response){
        FcReplier replier = new FcReplier(Initiator.sid,response);
        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis);
            if (requestCheckResult==null){
                return;
            }
            replier.reply0Success(null, jedis);
        }
    }
}