package APIP6V1_Service;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Service;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;


@WebServlet(name = ApiNames.ServiceSearchAPI, value = "/"+ApiNames.SN_6+"/"+ApiNames.Version2 +"/"+ApiNames.ServiceSearchAPI)
public class ServiceSearch extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList("active",false,"tRate",false,"sid",true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.SERVICE, defaultSort,Service.class,request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList("active",false,"tRate",false,"sid",true);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.SERVICE, defaultSort,Service.class,request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}