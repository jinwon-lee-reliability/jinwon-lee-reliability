package core;

import runner.*;
import scenario.*;
import infra.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtil {
    public static void log(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        System.out.println("[XA LOG] " + timestamp + " " + message);
    }
}