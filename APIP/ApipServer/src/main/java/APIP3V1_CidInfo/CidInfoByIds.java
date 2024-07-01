package APIP3V1_CidInfo;

import constants.ApiNames;
import constants.IndicesNames;

import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = ApiNames.CidInfoByIdsAPI, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version2 +"/"+ApiNames.CidInfoByIdsAPI)
public class CidInfoByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        FcdslRequestHandler.doIdsRequest(Initiator.sid, IndicesNames.CID, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        FcdslRequestHandler.doIdsRequest(Initiator.sid, IndicesNames.CID, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

}
