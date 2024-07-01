package javaTools;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectionTools {


    public static <K, T> Map<K, T> objectToMap(Object obj, Class<K> kClass, Class<T> tClass) {
        Gson gson = new Gson();
        Type type = TypeToken.getParameterized(Map.class, kClass, tClass).getType();
        String jsonString = gson.toJson(obj);
        Map<K, T> tempMap = gson.fromJson(jsonString, type);
        return new HashMap<>(tempMap);
    }

    public static <T> List<T> objectToList(Object obj, Class<T> tClass) {
        Gson gson = new Gson();
        Type type = TypeToken.getParameterized(ArrayList.class, tClass).getType();
        String jsonString = gson.toJson(obj);
        List<T> tempList = gson.fromJson(jsonString, type);
        return new ArrayList<>(tempList);
    }

    public static <T, K> Map<K, T> listToMap(List<T> list, String keyFieldName) {
        Map<K, T> resultMap = new HashMap<>();
        try {
            if (list != null && !list.isEmpty()) {
                Field keyField = list.get(0).getClass().getDeclaredField(keyFieldName);
                keyField.setAccessible(true);

                for (T item : list) {
                    @SuppressWarnings("unchecked")
                    K key = (K) keyField.get(item);
                    resultMap.put(key, item);
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return resultMap;
    }
}
