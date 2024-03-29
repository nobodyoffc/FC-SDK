//package starter;
//
//import APIP.apipClient.ApipClient;
//import APIP.apipClient.ConstructAPIs;
//import appTools.Inputer;
//import config.ApiProvider;
//
//public class Setting {
//    public void setting(byte[] symKey){
//
//
//        ApiProvider[] apiProviders = config.getApiProviderMap().values().toArray(new ApiProvider[0]);
//        config.showApiProviders(apiProviders);
//        if(Inputer.askIfYes(br,"Set an APIP service provider? y/n")) {
//            int input = Inputer.inputInteger(br, "Input the number to set provider as the main APIP service:", apiProviders.length);
//            String initApipSid = apiProviders[input - 1].getSid();
//            config.setInitApipSid(initApipSid);
//            initApipAccount = config.getApiAccountMap().get(config.getInitApipSid());
//            ApipClient apipClient = ConstructAPIs.serviceByIdsPost(initApipAccount.getApiUrl(), new String[]{initApipSid}, initApipAccount.getVia(), initApipAccount.getSessionKey());
//            if(initApipAccount.checkApiResponse(apipClient,symKey,br)){
//                showInitApipBalance();
//            }else return;
//        }
//
//        config.showApiProviders(apiProviders);
//        if(Inputer.askIfYes(br,"Set a main database service provider? y/n")) {
//            int input = Inputer.inputInteger(br, "Input the number to set provider as the main database service:", apiProviders.length);
//            String sid = apiProviders[input - 1].getSid();
//            config.setMainDatabaseSid(sid);
//            config.getApiAccountMap().get(sid);
//        }
//
//        config.showApiProviders(apiProviders);
//        if(Inputer.askIfYes(br,"Set a memory database service provider? y/n")) {
//            int input = Inputer.inputInteger(br, "Input the number to set provider as the memory database service:", apiProviders.length);
//            String sid = apiProviders[input - 1].getSid();
//            config.setMainDatabaseSid(sid);
//            config.getApiAccountMap().get(sid);
//        }
//    }
//}
