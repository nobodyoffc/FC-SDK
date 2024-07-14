package APIP17V1_Crypto;

import constants.ApiNames;
import crypto.KeyTools;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = ApiNames.Addresses, value = "/"+ApiNames.SN_17+"/"+ApiNames.Version2 +"/"+ApiNames.Addresses)
public class Addresses extends HttpServlet {
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

            String input = (String)other;

            Map<String, String> addrMap = new HashMap<>();
            String pubKey=null;

            if(input.startsWith("F")||input.startsWith("1")||input.startsWith("D")||input.startsWith("L")){
                byte[] hash160 = KeyTools.addrToHash160(input);
                addrMap = KeyTools.hash160ToAddresses(hash160);
            }else if (input.startsWith("02")||input.startsWith("03")){
                pubKey = input;
                addrMap= KeyTools.pubKeyToAddresses(pubKey);
            }else if(input.startsWith("04")){
                try {
                    pubKey = KeyTools.compressPk65To33(input);
                    addrMap= KeyTools.pubKeyToAddresses(pubKey);
                } catch (Exception e) {
                    replier.replyOtherError("Wrong public key.",null,jedis);
                    return;
                }
            }else{
                replier.replyOtherError("FID or Public Key are needed.",null,jedis);
                return;
            }
            replier.replySingleDataSuccess(addrMap,jedis);
        }
    }
}