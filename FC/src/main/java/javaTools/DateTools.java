package javaTools;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateTools {
    public static String longToTime(long timestamp) {
        Date date = new Date(timestamp);

        // Create a SimpleDateFormat with the desired format
        SimpleDateFormat sdf = new SimpleDateFormat("yy/MM/dd HH:mm:ss");

        // Format the date as a string
        return sdf.format(date);
    }
}
