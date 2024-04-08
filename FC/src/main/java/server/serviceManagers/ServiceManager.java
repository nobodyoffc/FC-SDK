package server.serviceManagers;

import APIP.apipClient.ApipClientData;
import APIP.apipClient.ConstructAPIs;
import FEIP.feipData.FcInfo;
import FEIP.feipData.Service;
import FEIP.feipData.ServiceData;
import FEIP.feipData.serviceParams.Params;
import FEIP.feipData.serviceParams.SwapParams;
import appTools.Menu;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ApiAccount;
import constants.OpNames;
import javaTools.JsonTools;
import server.Starter;

import java.io.BufferedReader;
import java.util.ArrayList;

import static config.ApiAccount.checkApipBalance;

//TODO The methods of inputParams and updateParams have to be override.
public abstract class ServiceManager {
    protected Service service;
    protected ApiAccount apipAccount;
    protected Class<?> paramsClass;
    protected abstract Params inputParams(byte[] symKey, BufferedReader br);

    protected abstract void updateParams(Params serviceParams, BufferedReader br, byte[] symKey);

    public ServiceManager(ApiAccount apipAccount,Class<?> paramsClass) {
        this.apipAccount = apipAccount;
        this.paramsClass = paramsClass;
    }

    public void manageService(BufferedReader br,byte[] symKey) {
        System.out.println("Manage service...");

        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("Show service");
        menuItemList.add("Publish service");
        menuItemList.add("Update service");
        menuItemList.add("Stop service");
        menuItemList.add("Recover service");
        menuItemList.add("Close service");

        menu.add(menuItemList);
        System.out.println(" << Manage service>> ");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> showService();
                case 2 -> publishService(symKey,br);
                case 3 -> updateService(symKey,br);
                case 4 -> stopService(br);
                case 5 -> recoverServices(br);
                case 6 -> closeServices(br);
                case 7 -> reloadServices(br,symKey);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private void reloadServices(BufferedReader br, byte[] symKey) {
        String sid = service.getSid();

        ApipClientData apipClientData = ConstructAPIs.serviceByIdsPost(apipAccount.getApiUrl(), new String[]{sid}, apipAccount.getVia(),symKey);
        if(apipClientData ==null)return;
        if(apipClientData.isBadResponse("reload APIP service")){
            Menu.anyKeyToContinue(br);
            return;
        }
        checkApipBalance(apipAccount, apipClientData, symKey);
    }

    private void showService() {
        System.out.println(JsonTools.getNiceString(service));
    }


    public void publishService(byte[] symKey,BufferedReader br) {
        System.out.println("Publish service services...");

        if (Menu.askIfToDo("Get the OpReturn text to publish a new service service?", br)) return;

        FcInfo fcInfo = setFcInfoForService();

        ServiceData data = new ServiceData();

        data.setOp(OpNames.PUBLISH);

        data.inputTypes(br);

        data.inputServicePublish(br);

        Params serviceParams = inputParams(symKey, br);

        data.setParams(serviceParams);

        fcInfo.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        System.out.println(gson.toJson(fcInfo));
        System.out.println("Check, and edit if you want, the JSON text above. Send it in a TX by the owner of the service to freecash blockchain:");
        Menu.anyKeyToContinue(br);
    }

    private FcInfo setFcInfoForService() {
        FcInfo fcInfo = new FcInfo();
        fcInfo.setType("FEIP");
        fcInfo.setSn("5");
        fcInfo.setVer("2");
        fcInfo.setName("Service");
        return fcInfo;
    }


    public void updateService(byte[] symKey,BufferedReader br) {
        System.out.println("Update service services...");
        if(service==null)return;
        showService();

        if (Menu.askIfToDo("Get the OpReturn text to update a service service?", br)) return;

        FcInfo fcInfo = setFcInfoForService();

        ServiceData data = new ServiceData();

        serviceToServiceData(service,data);

        data.setOp(OpNames.UPDATE);

        data.updateTypes(br);

        data.updateServiceHead(br);

        SwapParams serviceParams = (SwapParams) data.getParams();

        updateParams(serviceParams,br,symKey);


        fcInfo.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        System.out.println("Check the JSON text below. Send it in a TX by the owner of the service to freecash blockchain:");
        System.out.println(gson.toJson(fcInfo));

        Menu.anyKeyToContinue(br);
    }



    private void serviceToServiceData(Service service, ServiceData data) {
        data.setTypes(service.getTypes());
        data.setSid(service.getSid());
        data.setUrls(service.getUrls());
        data.setStdName(service.getStdName());
        data.setLocalNames(service.getLocalNames());
        data.setProtocols(service.getProtocols());
        data.setDesc(service.getDesc());
        data.setWaiters(service.getWaiters());

        data.setParams(Starter.parseMyServiceParams(service, paramsClass));
    }

    private void stopService(BufferedReader br) {
        System.out.println("Stop service services...");
        operateService(br,OpNames.STOP);
    }

    private void recoverServices(BufferedReader br) {
        System.out.println("Recover service services...");
        operateService(br,OpNames.RECOVER);
    }

    private void closeServices(BufferedReader br) {
        System.out.println("Close service services...");
        operateService(br,OpNames.CLOSE);
    }

    private void operateService(BufferedReader br,String op) {

        showService();

        if (Menu.askIfToDo("Get the OpReturn text to "+op+" a service service?", br)) return;

        FcInfo fcInfo = setFcInfoForService();

        ServiceData data = new ServiceData();

        data.setOp(op);
        data.setSid(service.getSid());
        fcInfo.setData(data);

        System.out.println("The owner can send a TX with below json in OpReturn to "+op+" the service: "+service.getSid());
        System.out.println(JsonTools.getNiceString(fcInfo));

        System.out.println("you can replace the value of 'data.sid' to "+op+" other your own service services.");
        Menu.anyKeyToContinue(br);
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public ApiAccount getApipAccount() {
        return apipAccount;
    }

    public void setApipAccount(ApiAccount apipAccount) {
        this.apipAccount = apipAccount;
    }

    public Class<?> getParamsClass() {
        return paramsClass;
    }

    public void setParamsClass(Class<?> paramsClass) {
        this.paramsClass = paramsClass;
    }
}