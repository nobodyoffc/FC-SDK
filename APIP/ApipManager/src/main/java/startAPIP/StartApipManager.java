package startAPIP;


import apip.apipData.WebhookInfo;
import appTools.Menu;
import clients.esClient.EsTools;
import clients.redisClient.RedisTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.ApiType;
import config.Configure;
import constants.Strings;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import feip.feipData.serviceParams.Params;
import mempool.MempoolCleaner;
import mempool.MempoolScanner;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Counter;
import server.Settings;
import server.balance.BalanceInfo;
import server.balance.BalanceManager;
import server.order.Order;
import server.order.OrderManager;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.reward.Rewarder;
import swap.SwapAffair;
import swap.SwapLpData;
import swap.SwapPendingData;
import swap.SwapStateData;
import webhook.Pusher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static constants.IndicesNames.ORDER;
import static constants.IndicesNames.WEBHOOK;
import static constants.IndicesNames.*;
import static constants.Strings.*;
import static server.Counter.checkUserBalance;

public class StartApipManager {

	private static final Logger log = LoggerFactory.getLogger(StartApipManager.class);
	public static Service service;
	private static ElasticsearchClient esClient = null;
	private static MempoolScanner mempoolScanner =null;
	private static Counter counter  =null;
//	private static OrderScanner orderScanner=null;
private static Pusher pusher = null;
	private static MempoolCleaner mempoolCleaner=null;
	private static BufferedReader br;
	private static IndicesApip indicesAPIP;
	public static JedisPool jedisPool;
	public static NaSaRpcClient naSaRpcClient;
	private static String sid;
	public static ApipParams params;
	private static ApipManagerSettings settings;


	public static void main(String[] args)throws Exception{
		ApiType apiType = ApiType.APIP;
		br = new BufferedReader(new InputStreamReader(System.in));

		//Load config info from the file
		Configure configure = Configure.loadConfig(br);

		byte[] symKey = configure.checkPassword(configure);

		sid = configure.chooseSid(symKey);
		//Load the local settings from the file of localSettings.json
		settings = ApipManagerSettings.loadFromFile(sid,ApipManagerSettings.class);//new ApipClientSettings(configure,br);
		if(settings==null) settings = new ApipManagerSettings();
		//Check necessary APIs and set them if anyone can't be connected.
		service = settings.initiateServer(sid,symKey,configure,br);
		sid = service.getSid();
		params = (ApipParams) service.getParams();

		//Prepare API clients
		esClient = (ElasticsearchClient) settings.getEsAccount().getClient();
		jedisPool = (JedisPool) settings.getRedisAccount().getClient();
		naSaRpcClient = (NaSaRpcClient) settings.getNasaAccount().getClient();

		Configure.checkWebConfig(sid,configure, settings,symKey, apiType,jedisPool,br);

		//Check indices in ES
		checkApipIndices(esClient);

		//However, swap indices and APIs should be moved to SwapHall service from APIP service.
		checkSwapIndices(esClient);

		//Check user balance
		checkUserBalance(sid,jedisPool,esClient,br);

		Rewarder.checkRewarderParams(sid,params,jedisPool,br);

		checkPublicSessionKey();

//		counter  = new Counter(settings,params,symKey);
//		counter.run();
		startCounterThread(symKey);
		startMempoolScan();
		startPusher(esClient);

		if(counter.isRunning().get()) System.out.println("Order scanner is running...");
		if(mempoolScanner!=null && mempoolScanner.getRunning().get()) System.out.println("Mempool scanner is running...");
		if(mempoolCleaner!=null && mempoolCleaner.getRunning().get()) System.out.println("Mempool cleaner is running...");
		if(pusher!=null && pusher.isRunning().get()) System.out.println("Webhook pusher is running.");
		System.out.println();

		while(true) {

			Menu menu = new Menu();

			ArrayList<String> menuItemList = new ArrayList<>();

			menuItemList.add("Manage service");
			menuItemList.add("Manage order");
			menuItemList.add("Manage balance");
			menuItemList.add("Manage reward");
			menuItemList.add("Manage indices");
			menuItemList.add("Settings");

			menu.add(menuItemList);
			menu.setName("APIP Manager");
			menu.show();

			int choice = menu.choose(br);
			switch (choice) {
				case 1 -> new ApipManager(service,null,br,symKey,ApipParams.class).menu();
				case 2 -> new OrderManager(service, counter, br, esClient, jedisPool).menu();
				case 3 -> new BalanceManager(service, br, esClient,jedisPool).menu();
				case 4 -> manageReward(sid, params,esClient,naSaRpcClient, jedisPool, br);
				case 5 -> manageIndices();
				case 6 -> settings.setting(symKey,br);
				case 0 -> {
					if (counter != null && counter.isRunning().get())
						System.out.println("Order scanner is running.");
					if (mempoolScanner != null && mempoolScanner.getRunning().get())
						System.out.println("Mempool scanner is running.");
					if (mempoolCleaner != null && mempoolCleaner.getRunning().get())
						System.out.println("Mempool cleaner is running.");
					if (pusher != null && pusher.isRunning().get()) System.out.println("Webhook pusher is running.");
					System.out.println("Do you want to quit? 'q' to quit.");
					String input = br.readLine();
					if ("q".equals(input)) {
						if (mempoolScanner != null) mempoolScanner.shutdown();
						if (counter != null) counter.shutdown();
						if (mempoolCleaner != null) mempoolCleaner.shutdown();
						if (pusher != null) pusher.shutdown();
						br.close();
						if (counter == null || !counter.isRunning().get())
							System.out.println("Order scanner is set to stop.");
						if (mempoolScanner == null || !mempoolScanner.getRunning().get())
							System.out.println("Mempool scanner is set to stop.");
						if (mempoolCleaner == null || !mempoolCleaner.getRunning().get())
							System.out.println("Mempool cleaner is set to stop.");
						if (pusher == null || !pusher.isRunning().get())
							System.out.println("Webhook pusher is set to stop.");
						System.out.println("Exited, see you again.");
						System.exit(0);
						return;
					}
				}
				default -> {
				}
			}
		}
	}

	private static void startCounterThread(byte[] symKey) {
		counter  = new Counter(settings,params,symKey);
		Thread thread = new Thread(counter);
		thread.start();
	}
//
//	private static void setParamsToRedis(Configure configure, byte[] symKey, Service myService, ApipParams myServiceParams) {
//		try(Jedis jedis = jedisPool.getResource()) {
//			jedis.hset(FieldNames.APIP_INFO, SID,myService.getSid());
//			jedis.hset(FieldNames.APIP_INFO, CONFIG_FILE_PATH,Configure.getConfDir());
//			jedis.hset(FieldNames.APIP_INFO,LOCAL_DATA_PATH, server.Settings.getLocalDataDir(myService.getSid()));
//
//			myServiceParams.writeParamsToRedis(server.Settings.addSidBriefToName(sid, Strings.PARAMS), jedis);
//
//			Map<String, String> nPrice = jedis.hgetAll(server.Settings.addSidBriefToName(sid,Strings.N_PRICE));
//			if (nPrice == null) {
//				String[] apiNames = ApiNames.apiList.toArray(new String[ApiNames.ListApi.length()]);
//				setNPrices(sid, apiNames,jedis,br);
//			}
//
//			jedis.hset(server.Settings.addSidBriefToName(sid, Strings.SETTINGS),ES_ACCOUNT_ID, settings.getEsAccountId());
//			jedis.hset(server.Settings.addSidBriefToName(sid, Strings.SETTINGS),WINDOW_TIME, String.valueOf(settings.getWindowTime()));
//
//			CryptoDataByte eccDateBytes = EccAes256K1P7.encryptWithPassword(symKey, Hex.fromHex(configure.getNonce()));
//			if(eccDateBytes==null)
//				throw new RuntimeException("Failed to encrypt symKey.");
//			String symKeyCipher = eccDateBytes.toNiceJson();
//			jedis.hset(server.Settings.addSidBriefToName(sid, Strings.SETTINGS),INIT_SYM_KEY_CIPHER,symKeyCipher);
//
//			if(!jedis.exists(server.Settings.addSidBriefToName(sid, Strings.N_PRICE))) {
//				String[] apiNames = ApiNames.apiList.toArray(new String[ApiNames.ListApi.length()]);
//				setNPrices(sid, apiNames, jedis, br);
//			}
//		}
//	}

	private static void checkApipIndices(ElasticsearchClient esClient) {
		Map<String,String> nameMappingList = new HashMap<>();
		nameMappingList.put(server.Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
		nameMappingList.put(server.Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS);
		nameMappingList.put(server.Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
		nameMappingList.put(server.Settings.addSidBriefToName(sid,WEBHOOK), WebhookInfo.MAPPINGS);
		EsTools.checkEsIndices(esClient,nameMappingList);
	}

	public static void checkSwapIndices(ElasticsearchClient esClient) throws IOException {
		Map<String,String> nameMappingList = new HashMap<>();
		nameMappingList.put(server.Settings.addSidBriefToName(sid,SWAP_STATE), SwapStateData.swapStateJsonStr);
		nameMappingList.put(server.Settings.addSidBriefToName(sid,SWAP_LP), SwapLpData.swapLpMappingJsonStr);
		nameMappingList.put(server.Settings.addSidBriefToName(sid,SWAP_FINISHED), SwapAffair.swapFinishedMappingJsonStr);
		nameMappingList.put(server.Settings.addSidBriefToName(sid,SWAP_PENDING), SwapPendingData.swapPendingMappingJsonStr);
		nameMappingList.put(server.Settings.addSidBriefToName(sid,SWAP_STATE), SwapStateData.swapStateJsonStr);
		EsTools.checkEsIndices(StartApipManager.esClient,nameMappingList);

	}

//	private static void startOrderScan(ApipManagerSettings settings, Params params) throws IOException {
//		log.debug("Start order scanner...");
//		counter  = new Counter(settings,params, null, counterPriKey);
//		Thread thread2 = new Thread(counter);
//		thread2.start();
//		log.debug("Order scanner is running.");
//	}


//	private static void freshServiceFromEsToRedis(ApipService service, ElasticsearchClient esClient, Jedis jedis, ConfigAPIP configAPIP) {
//		ApipService serviceNew = getServiceFromEsById(esClient,service.getSid());
//		if(serviceNew==null)return;
//
//		if(!serviceNew.getStdName().equals(service.getStdName())) {
//			updateAllServiceNameInRedis(jedis, service.getStdName(),serviceNew.getStdName());
//		}
//		serviceName= serviceNew.getStdName();
//		StartAPIP.service = serviceNew;
//		updateServiceParamsInRedisAndConfig(configAPIP);
//		setServiceToRedis(serviceName);
//	}

//	private static ApipService getServiceFromEsById(ElasticsearchClient esClient, String sid) {
//		ApipService serviceNew = null;
//		try {
//			serviceNew = esClient.get(g -> g.index(SERVICE).id(sid), ApipService.class).source();
//		} catch (IOException e) {
//			log.error("Get service from Es wrong. Check Es.");
//		}
//		return serviceNew;
//	}

//	private static final String[] PARAMS_NAMES_IN_REDIS = {
//			PARAMS_ON_CHAIN,
//			CONSUME_VIA,
//			FID_SESSION_NAME,
//			FID_BALANCE,
//			N_PRICE,
//			ORDER_LAST_HEIGHT,
//			BUILDER_SHARE_MAP,
//			ORDER_LAST_BLOCK_ID,
//			SERVICE
//	};

//	private static void updateAllServiceNameInRedis(Jedis jedis, String oldName, String newName) {
//		for(String suffix : PARAMS_NAMES_IN_REDIS) {
//			renameKey(jedis, oldName, newName, suffix);
//		}
//	}

//	private static void renameKey(Jedis jedis, String oldName, String newName, String suffix) {
//		try {
//			jedis.rename(oldName + "_" + suffix, newName + "_" + suffix);
//		}catch (Exception ignore){
//			System.out.println(oldName +" no found.");
//		}
//	}


//	private static Rewarder checkRewardParams() {
//		RewardParams rewardParams = Rewarder.getRewardParams(sid, jedisPool);
//		if (rewardParams == null) {
//			System.out.println("Reward parameters aren't set yet.");
//			rewardParams = new Rewarder(sid,).setRewardParameters( br);
//			Menu.anyKeyToContinue(br);
//		}
//		return rewardParams;
//	}

	private static void manageReward(String sid, Params params, ElasticsearchClient esClient, NaSaRpcClient naSaRpcClient,JedisPool jedisPool, BufferedReader br) {
		RewardManager rewardManager = new RewardManager(sid, params.getAccount(),null,esClient, naSaRpcClient,jedisPool, br);
		rewardManager.menu(params.getConsumeViaShare(),params.getOrderViaShare());
	}

	private static void checkPublicSessionKey() throws IOException {
		try(Jedis jedis = jedisPool.getResource()) {
			if (jedis.hget(Settings.addSidBriefToName(sid,FID_SESSION_NAME), PUBLIC) == null) {
				System.out.println("Public sessionKey for getFreeService API is null. Set it? 'y' to set.");
				String input = StartApipManager.br.readLine();
				if ("y".equals(input)) {
					setPublicSessionKey(br);
				}
			}
		}
	}

	static void setPublicSessionKey(BufferedReader br) {
		try(Jedis jedis = StartApipManager.jedisPool.getResource()) {
			setPublicSessionKey();
			String balance = jedis.hget(Settings.addSidBriefToName(sid, BALANCE), PUBLIC);
			System.out.println("The balance of public session is: " + balance + ". \nWould you reset it? Input a number satoshi to set. Enter to skip.");
			while (true) {
				try {
					String num = br.readLine();
					if ("".equals(num)) return;
					Long.parseLong(num);
					jedis.hset(Settings.addSidBriefToName(sid, BALANCE), PUBLIC, num);
					break;
				} catch (Exception ignore) {
					System.out.println("It's not a integer. Input again:");
				}
			}
		}
	}


	private static void setPublicSessionKey() {
		SecureRandom secureRandom = new SecureRandom();
		byte[] randomBytes = new byte[32];
		secureRandom.nextBytes(randomBytes);
		String sessionKey = HexFormat.of().formatHex(randomBytes);
		String oldSession = null;
		try(Jedis jedis = StartApipManager.jedisPool.getResource()) {
			try {
				oldSession = jedis.hget(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), PUBLIC);
			} catch (Exception ignore) {
			}

			jedis.hset(Settings.addSidBriefToName(sid,Strings.FID_SESSION_NAME), PUBLIC, sessionKey.substring(0, 12));

			jedis.select(1);
			try {
				jedis.del(oldSession);
			} catch (Exception ignore) {
			}

			jedis.hset(sessionKey.substring(0, 12), SESSION_KEY, sessionKey);
			jedis.hset(sessionKey.substring(0, 12), FID, PUBLIC);
			jedis.select(0);
			System.out.println("Public session key set into redis: " + sessionKey);
		}
	}

	private static void manageIndices() throws IOException, InterruptedException {
		indicesAPIP.menu();
	}

	private static void startMempoolClean(ElasticsearchClient esClient) {
		mempoolCleaner = new MempoolCleaner(settings.getListenPath(), esClient,jedisPool);
		Thread thread = new Thread(mempoolCleaner);
		thread.start();
	}

	private static void startPusher(ElasticsearchClient esClient) throws IOException {
		String listenPath = settings.getListenPath();
		pusher = new Pusher(sid,listenPath, esClient);
		Thread thread3 = new Thread(pusher);
		thread3.start();

		log.debug("Webhook pusher is running.");
	}

	private static void startMempoolScan()  {
		startMempoolClean(esClient);
		mempoolScanner = new MempoolScanner(naSaRpcClient,esClient,jedisPool);
		Thread thread1 = new Thread(mempoolScanner);
		thread1.start();
	}

	public static String getNameOfService(String name) {
		String finalName;
		try(Jedis jedis = StartApipManager.jedisPool.getResource()) {
			finalName = (jedis.hget(CONFIG, SERVICE_NAME) + "_" + name).toLowerCase();
		}
		return finalName;
	}

	private static void checkServiceParams() {
		try(Jedis jedis = jedisPool.getResource()) {
			if (jedis.hget(Settings.addSidBriefToName(sid,Strings.PARAMS) , Strings.ACCOUNT) == null) {
				writeParamsToRedis(service,jedis);
			}
		}
	}

	private static void writeParamsToRedis(Service service,Jedis jedis) {

		ApipParams params = (ApipParams) service.getParams();
		String paramsKey = Settings.addSidBriefToName(service.getSid(),PARAMS);
//		String settingsKey = Settings.addSidBriefToName(service.getSid(),SETTINGS);

		RedisTools.writeToRedis(params,paramsKey,jedis,ApipParams.class);
//			jedis.hset(paramsKey, ACCOUNT, params.getAccount());
//			jedis.hset(paramsKey, CURRENCY, params.getCurrency());
//			jedis.hset(paramsKey, URL_HEAD, params.getUrlHead());
//			if (params.getMinPayment() != null)
//				jedis.hset(paramsKey, MIN_PAYMENT, params.getMinPayment());
//			if (params.getPricePerKBytes() != null)
//				jedis.hset(paramsKey, PRICE_PER_K_BYTES, params.getPricePerKBytes());
//			if (params.getPricePerRequest() != null)
//				jedis.hset(paramsKey, PRICE_PER_REQUEST, params.getPricePerRequest());
//			if (params.getSessionDays() != null)
//				jedis.hset(paramsKey, SESSION_DAYS, params.getSessionDays());
//			if (params.getConsumeViaShare() != null)
//				jedis.hset(paramsKey, CONSUME_VIA_SHARE, params.getConsumeViaShare());
//			if (params.getOrderViaShare() != null)
//				jedis.hset(paramsKey, ORDER_VIA_SHARE, params.getOrderViaShare());
//
//			if (params.getPricePerKBytes() == null || "0".equals(params.getPricePerKBytes())) {
//				jedis.hset(settingsKey, PRICE, params.getPricePerRequest());
//				jedis.hset(settingsKey, IS_PRICE_PER_REQUEST, FALSE);
//			} else {
//				jedis.hset(settingsKey, PRICE, params.getPricePerKBytes());
//				jedis.hset(settingsKey, IS_PRICE_PER_REQUEST, TRUE);
//			}

		System.out.println("Service parameters has been wrote into redis.");
	}

//	public static void updateServiceParamsInRedisAndConfig( ConfigAPIP configAPIP){
//		serviceName = service.getStdName();
//
//		setServiceToRedis(serviceName);
//		writeParamsToRedis();
//
//		configAPIP.setServiceName(serviceName);
//		configAPIP.writeConfigToFile();
//	}

//	public static void setServiceToRedis(String serviceName) {
//		Gson gson = new Gson();
//		try(Jedis jedis = StartAPIP.jedisPool.getResource()) {
//			jedis.set(serviceName + "_" + SERVICE, gson.toJson(service));
//			jedis.hset(CONFIG, SERVICE_NAME, serviceName);
//		}
//	}
}

