package APIP18V1_Wallet;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Sort;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import constants.ApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import fcData.FcReplier;
import fch.CashListReturn;
import fch.fchData.Cash;
import initial.Initiator;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FcdslRequestHandler;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.*;
import static constants.IndicesNames.CASH;
import static constants.Strings.FID;
import static fch.ParseTools.coinToSatoshi;
import static fch.Wallet.getCashForCd;
import static fch.Wallet.getCashListForPay;


@WebServlet(name = ApiNames.CashValid, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version2 +"/"+ApiNames.CashValid)
public class CashValid extends HttpServlet {

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
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis);
            if (requestCheckResult == null) {
                return;
            }
            //Do this request
            doCashValidRequest(replier, jedis,  request, response, authType);
        }
    }


    protected void doCashValidRequest(FcReplier replier, Jedis jedis, HttpServletRequest request, HttpServletResponse response, AuthType authType) {

        RequestBody requestBody = replier.getRequestCheckResult().getRequestBody();
        replier.setNonce(requestBody.getNonce());

        String fid = null;
        Long amount = null;
        Long cd = null;
        if(requestBody.getFcdsl()!=null) {
            try {
                Object other = requestBody.getFcdsl().getOther();
                Map<String, String> otherMap;
                if (other != null) {
                   otherMap =  ObjectTools.objectToMap(other, String.class, String.class);
                   if(!otherMap.isEmpty()) {
                       fid = otherMap.get(FID);
                       String amountStr = otherMap.get(FieldNames.AMOUNT);
                       if(amountStr!=null)amount = coinToSatoshi(Double.parseDouble(amountStr));
                       String cdStr = otherMap.get(FieldNames.CD);
                       if(cdStr!=null)cd = Long.parseLong(cdStr);
                   }
                }
            }catch (Exception ignored){
            }
        }

        if(fid==null){
            ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_TIME,false,CASH_ID,true,null,null);
            FcdslRequestHandler.doSearchRequest(Initiator.sid, CASH, Cash.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
        }else if(amount!=null && amount>0){
            CashListReturn cashListReturn = getCashListForPay(amount,fid,Initiator.esClient);
            if(cashListReturn.getCode()!=0){
                replier.replyOtherError(cashListReturn.getMsg(),null,jedis);
                return;
            }
            List<Cash> meetList = cashListReturn.getCashList();
            replier.setGot((long) meetList.size());
            replier.setTotal(cashListReturn.getTotal());
            replier.reply0Success(meetList, jedis);
        }else if(cd!=null && cd!=0){
            CashListReturn cashListReturn = getCashForCd(fid, cd,Initiator.esClient);
            if(cashListReturn.getCode()!=0){
                replier.replyOtherError(cashListReturn.getMsg(),null,jedis);
                return;
            }
            List<Cash> meetList = cashListReturn.getCashList();
            replier.setGot((long) meetList.size());
            replier.setTotal(cashListReturn.getTotal());
            replier.reply0Success(meetList, jedis);
        }else {
            ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_TIME,false,CASH_ID,true,null,null);
            Fcdsl fcdsl = requestBody.getFcdsl();
            fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestBody, response, replier, Initiator.esClient);
            List<Cash> meetList = fcdslRequestHandler.doRequest(CASH, defaultSort,Cash.class, jedis);
            replier.reply0Success(meetList, jedis);
        }
    }
}