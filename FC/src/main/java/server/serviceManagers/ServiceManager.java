package server.serviceManagers;

import clients.apipClient.ApipClient;
import clients.apipClient.ApipClientEvent;
import feip.feipData.DataOnChain;
import feip.feipData.Service;
import feip.feipData.ServiceData;
import feip.feipData.serviceParams.Params;
import feip.feipData.serviceParams.SwapParams;
import appTools.Menu;
import appTools.Shower;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ApiAccount;
import constants.OpNames;
import javaTools.JsonTools;
import config.Configure;

import java.io.BufferedReader;
import java.util.ArrayList;

import static clients.apipClient.ApipClient.checkBalance;

//TODO The methods of inputParams and updateParams have to be override.
public abstract class ServiceManager {
    protected Service service;
    protected ApiAccount apipAccount;
    protected Class<?> paramsClass;
    protected BufferedReader br;
    protected byte[] symKey;

    public ServiceManager(Service service,ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass) {
        this.service=service;
        this.br = br;
        this.symKey = symKey;
        this.paramsClass = paramsClass;
        this.apipAccount =apipAccount;
    }
//    public ServiceManager(ApiAccount apipAccount,Class<?> paramsClass) {
//        this.apipAccount = apipAccount;
//        this.paramsClass = paramsClass;
//    }
//
//    public ServiceManager(Class<?> paramsClass) {
//        this.paramsClass = paramsClass;
//    }

    protected abstract Params inputParams(byte[] symKey, BufferedReader br);

    protected abstract void updateParams(Params serviceParams, BufferedReader br, byte[] symKey);


    public void menu() {
        Menu menu = new Menu();
        menu.setName("Service Manager");
        ArrayList<String> menuItemList = new ArrayList<>();

        menuItemList.add("Show service");
        menuItemList.add("Publish service");
        menuItemList.add("Update service");
        menuItemList.add("Stop service");
        menuItemList.add("Recover service");
        menuItemList.add("Close service");

        menu.add(menuItemList);
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> showService();
                case 2 -> {
                    if(apipAccount!=null)publishService();
                    else publishService();
                }
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
        ApipClient apipClient = (ApipClient) apipAccount.getClient();
        Service service1 = apipClient.serviceById(sid);
        if(service1==null)return;
        service = service1;
        checkBalance(apipAccount, apipClient.getClientData(), symKey, apipClient);
    }

    private void showService() {
        System.out.println(JsonTools.toNiceJson(service));
    }

    public void publishService() {
        System.out.println("Publish service services...");

        if (Menu.askIfToDo("Get the OpReturn text to publish a new service service?", br)) return;

        DataOnChain dataOnChain = setFcInfoForService();

        ServiceData data = new ServiceData();

        data.setOp(OpNames.PUBLISH);

        data.inputTypes(br);

        if(symKey!=null && apipAccount!=null)
            data.inputServiceHead(br,symKey, apipAccount.getApipClient());
        else data.inputServiceHead(br);

        System.out.println("Set the service parameters...");

        Params serviceParams = inputParams(symKey, br);

        data.setParams(serviceParams);

        dataOnChain.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Shower.printUnderline(10);
        String opReturnJson = gson.toJson(dataOnChain);
//        opReturnJson = opReturnJson.replaceAll(",\n {4}\"rate\": 0", "");
        System.out.println(opReturnJson);
        Shower.printUnderline(10);
        System.out.println("Check, and edit if you want, the JSON text above. Send it in a TX by the owner of the service to freecash blockchain:");
        Menu.anyKeyToContinue(br);
    }

    private static DataOnChain setFcInfoForService() {
        DataOnChain dataOnChain = new DataOnChain();
        dataOnChain.setType("feip");
        dataOnChain.setSn("5");
        dataOnChain.setVer("2");
        dataOnChain.setName("Service");
        return dataOnChain;
    }


    public void updateService(byte[] symKey,BufferedReader br) {
        System.out.println("Update service services...");
        if(service==null)return;
        showService();

        if (Menu.askIfToDo("Get the OpReturn text to update a service service?", br)) return;

        DataOnChain dataOnChain = setFcInfoForService();

        ServiceData data = new ServiceData();

        serviceToServiceData(service,data);

        data.setOp(OpNames.UPDATE);

        data.updateTypes(br);

        if(apipAccount.getClient()!=null && symKey!=null)
            data.updateServiceHead(br,symKey, (ApipClient) apipAccount.getClient());
        else data.updateServiceHead(br);

        SwapParams serviceParams = (SwapParams) data.getParams();

        updateParams(serviceParams,br,symKey);


        dataOnChain.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        System.out.println("Check the JSON text below. Send it in a TX by the owner of the service to freecash blockchain:");
        System.out.println(gson.toJson(dataOnChain));

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

        data.setParams(Configure.parseMyServiceParams(service, paramsClass));
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

        DataOnChain dataOnChain = setFcInfoForService();

        ServiceData data = new ServiceData();

        data.setOp(op);
        data.setSid(service.getSid());
        dataOnChain.setData(data);

        System.out.println("The owner can send a TX with below json in OpReturn to "+op+" the service: "+service.getSid());
        System.out.println(JsonTools.toNiceJson(dataOnChain));

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
