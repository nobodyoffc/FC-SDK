package javaTools;

public class StringTools {
    public static String arrayToString(String[] array) {
        if (array == null || array.length == 0) {
            return "";
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            stringBuilder.append(array[i]);
            if (i < array.length - 1) {
                stringBuilder.append(",");
            }
        }

        return stringBuilder.toString();
    }
}
