import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class LogUtil {

    public enum LogLevel { DEBUG, INFO, WARN, ERROR }

    private static LogLevel currentLogLevel = LogLevel.DEBUG;

    public static void setLogLevel(LogLevel level) {
        currentLogLevel = level;
    }

    public static boolean shouldLog(LogLevel level) {
        return level.ordinal() >= currentLogLevel.ordinal();
    }

    public static void logToFile(String logFile, String logMessage) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(logMessage);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String formatLogMessage(LogLevel level, String type, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        return String.format("%s [%s] [%s] %s", timestamp, level.name(), type, message);
    }

    public static void logScenario(String scenarioLogFile, LogLevel level, String message) {
        if (shouldLog(level)) {
            String logMessage = formatLogMessage(level, "SCENARIO LOG", message);
            System.out.println(logMessage);
            logToFile(scenarioLogFile, logMessage);
        }
    }

    public static void logResult(String resultLogFile, LogLevel level, String message) {
        if (shouldLog(level)) {
            String logMessage = formatLogMessage(level, "RESULT LOG", message);
            System.out.println(logMessage);
            logToFile(resultLogFile, logMessage);
        }
    }

    public static void logException(String scenarioLogFile, LogLevel level, Exception e) {
        if (shouldLog(level)) {
            String logMessage = formatLogMessage(level, "EXCEPTION LOG", e.getMessage());
            System.out.println(logMessage);
            logToFile(scenarioLogFile, logMessage);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(scenarioLogFile, true))) {
                for (StackTraceElement element : e.getStackTrace()) {
                    writer.write("	" + element.toString());
                    writer.newLine();
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public static String formatDuration(long durationMillis) {
        long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60;

        if (hours > 0) {
            return String.format("[%d:%02d:%02d] hours", hours, minutes, seconds);
        } else {
            return String.format("[%d:%02d] min", minutes, seconds);
        }
    }
}
