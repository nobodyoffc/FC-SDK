package APIP18V1_Wallet;

import constants.ApiNames;
import constants.ReplyCodeMessage;
import fcData.FcReplier;
import fch.CashListReturn;
import fch.CryptoSign;
import fch.DataForOffLineTx;
import fch.Wallet;
import fch.fchData.Cash;
import fch.fchData.SendTo;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import static constants.Constants.Dust;
import static constants.Constants.FchToSatoshi;
import static fch.CryptoSign.parseDataForOffLineTxFromOther;
import static fch.Wallet.getCashListForPay;


@WebServlet(name = ApiNames.OffLineTx, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version2 +"/"+ApiNames.OffLineTx)
public class OffLineTx extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid, request, response, authType, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType, Initiator.jedisPool);
    }
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) throws IOException {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            Object other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            DataForOffLineTx dataForSignInCs = parseDataForOffLineTxFromOther(other);

            //Check API
            if(dataForSignInCs==null) {
                replier.reply(ReplyCodeMessage.Code1012BadQuery,null,jedis);
                return;
            }

            String fromFid = dataForSignInCs.getFromFid();

            Long cd = dataForSignInCs.getCd();

            long amount = 0;
            if(dataForSignInCs.getSendToList()!=null && !dataForSignInCs.getSendToList().isEmpty()) {
                for (SendTo sendTo : dataForSignInCs.getSendToList()) {
                    if (sendTo.getAmount() < Dust) {
                        replier.replyOtherError("The amount must be more than "+Dust+"fch.",null,jedis);
                        return;
                    }
                    amount += (long) (sendTo.getAmount() * FchToSatoshi);
                }
            }
            CashListReturn cashListReturn;
            if(cd!=null)
                cashListReturn = Wallet.getCashForCd(fromFid, cd,Initiator.esClient);
            else cashListReturn = getCashListForPay(amount,fromFid,Initiator.esClient);

            List<Cash> meetList = cashListReturn.getCashList();
            if(meetList==null){
                replier.replyOtherError(cashListReturn.getMsg(),null,jedis);
                return;
            }

            long totalValue = 0;
            for(Cash cash :meetList){
                totalValue += cash.getValue();
            }

            if(totalValue<amount+1000){
                replier.replyOtherError("Cashes meeting this cd can not match the total amount of outputs.",null,jedis);
                return;
            }

            String rawTxForCs = CryptoSign.makeRawTxForCs(dataForSignInCs,meetList);
            replier.replySingleDataSuccess(rawTxForCs, jedis);
        }
    }
}