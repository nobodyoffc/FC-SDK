package APIP17V1_Crypto;

import com.google.gson.Gson;
import constants.ApiNames;
import crypto.KeyTools;
import fcData.FcReplier;
import fcData.Signature;
import initial.Initiator;
import javaTools.http.AuthType;
import org.bitcoinj.core.ECKey;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.SignatureException;


@WebServlet(name = ApiNames.Verify, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version2 +"/"+ApiNames.Verify)
public class Verify extends HttpServlet {
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
            Object other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            //Do this request
            String rawSignJson = new Gson().toJson(other);
//        Signature.SignShort signShort = Signature.parseSignature(rawSignJson);
            Signature signature = Signature.parseSignature(rawSignJson);

            if(signature==null){
                replier.replyOtherError("Parse signature wrong.",null,jedis);
                return;
            }

            boolean isGoodSign;
            if(signature.getFid()!=null&& signature.getMsg()!=null && signature.getSign()!=null){
                String sign = signature.getSign().replace("\\u003d", "=");
                try {
                    String signPubKey = ECKey.signedMessageToKey(signature.getMsg(), sign).getPublicKeyAsHex();
                    isGoodSign= signature.getFid().equals(KeyTools.pubKeyToFchAddr(signPubKey));
                } catch (SignatureException e) {
                    isGoodSign = false;
                }
            }else{
                replier.replyOtherError("FID, signature or message missed.",null,jedis);
                return;
            }
            replier.replySingleDataSuccess(isGoodSign,jedis);
        }
    }
}
