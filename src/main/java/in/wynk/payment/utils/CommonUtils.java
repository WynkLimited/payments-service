package in.wynk.payment.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CommonUtils {
    public static String getFormattedDate(long dateInMs, String format){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
        return simpleDateFormat.format(new Date(dateInMs));
    }
}
