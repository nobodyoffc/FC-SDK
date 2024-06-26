package APIP0V1_OpenAPI;

import apip.apipData.RequestBody;
import apip.apipData.Session;
import clients.redisClient.RedisTools;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import constants.Strings;
import crypto.old.EccAes256K1P7;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.Hex;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;
import server.Settings;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static constants.Strings.*;

@WebServlet(name = ApiNames.SignInEccAPI, value = "/"+ApiNames.SN_0+"/"+ApiNames.Version2 +"/"+ ApiNames.SignInEccAPI)
public class SignInEcc extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        FcReplier replier = new FcReplier(Initiator.sid,response);

        Session session = new Session();
        RequestChecker requestChecker = new RequestChecker();
        String pubKey;
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            Map<String, String> paramsMap = jedis.hgetAll(Settings.addSidBriefToName(Initiator.sid, PARAMS));
            long windowTime = RedisTools.readHashLong(jedis, Settings.addSidBriefToName(Initiator.sid,SETTINGS), WINDOW_TIME);

            RequestCheckResult requestCheckResult = requestChecker.checkSignInRequest(Initiator.sid, request, replier, paramsMap, windowTime, jedis);
            if (requestCheckResult == null) {
                return;
            }
            pubKey = requestCheckResult.getPubKey();
            String fid = requestCheckResult.getFid();
            session.setFid(fid);

            RequestBody.SignInMode mode = requestCheckResult.getRequestBody().getMode();

            String sessionDaysStr = paramsMap.get(SESSION_DAYS);

            long sessionDays;

            if(sessionDaysStr==null)sessionDays=100;
            else sessionDays = Long.parseLong(sessionDaysStr);
            if ((!jedis.hexists(Settings.addSidBriefToName(Initiator.sid,Strings.FID_SESSION_NAME), fid)) || RequestBody.SignInMode.REFRESH.equals(mode)) {
                try {
                    session = new Session().makeSession(Initiator.sid,jedis, fid, sessionDays);
                    String sessionKeyCipher = EccAes256K1P7.encryptWithPubKey(session.getSessionKey().getBytes(), Hex.fromHex(pubKey));
                    session.setSessionKeyCipher(sessionKeyCipher);
                    session.setSessionKey(null);
                } catch (Exception e) {
                    replier.replyOtherError("Some thing wrong when making sessionKey.\n" + e.getMessage(),null,jedis );
                }
            } else {
                jedis.select(0);
                String sessionName = jedis.hget(Settings.addSidBriefToName(Initiator.sid,Strings.FID_SESSION_NAME), fid);
                session.setSessionName(sessionName);
                jedis.select(1);
                String sessionKey = jedis.hget(sessionName, SESSION_KEY);
                if (sessionKey != null) {
                    long expireMillis = jedis.ttl(sessionName);
                    if (expireMillis > 0) {
                        long expireTime = System.currentTimeMillis() + expireMillis * 1000;
                        session.setExpireTime(expireTime);
                    } else {
                        session.setExpireTime(expireMillis);
                    }
                    String sessionKeyCipher = EccAes256K1P7.encryptWithPubKey(sessionKey.getBytes(), Hex.fromHex(pubKey));
                    session.setSessionKeyCipher(sessionKeyCipher);
                } else {
                    try {
                        session = new Session().makeSession(Initiator.sid,jedis, fid,sessionDays);
                    } catch (Exception e) {
                        replier.replyOtherError( "Some thing wrong when making sessionKey.\n" + e.getMessage(),null ,jedis);
                        return;
                    }
                }
            }
            replier.reply0Success(session,jedis);
            replier.clean();
        }
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FcReplier replier =new FcReplier(Initiator.sid,response);
        replier.setCode(ReplyCodeMessage.Code1017MethodNotAvailable);
        replier.setMessage(ReplyCodeMessage.Msg1017MethodNotAvailable);
        response.getWriter().write(replier.toNiceJson());
    }
}
