package com.vypeensoft.ftpserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Log {
    private static File sLogFile = null;
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd.HHmmss", Locale.US);
    private static final SimpleDateFormat LOG_ENTRY_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    public static void init() {
        if (sLogFile != null) return;
        try {
            File logsDir = new File("/sdcard/Vypeensoft/FTP_Server/logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            String fileName = "ftpserver_" + FILE_DATE_FORMAT.format(new Date()) + ".log";
            sLogFile = new File(logsDir, fileName);
            writeLog("INFO", "AppLogger", "Logger initialized. File: " + sLogFile.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("AppLogger", "Failed to initialize Log: " + e.getMessage());
        }
    }

    private static void writeLog(final String level, final String tag, final String msg) {
        sExecutor.execute(() -> {
            if (sLogFile == null) return;
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(sLogFile, true))) {
                String timestamp = LOG_ENTRY_FORMAT.format(new Date());
                writer.write(String.format(Locale.US, "%s [%s] %s: %s\n", timestamp, level, tag, msg));
                writer.flush();
            } catch (IOException e) {
                android.util.Log.e("AppLogger", "Failed to write log to file: " + e.getMessage());
            }
        });
    }

    public static int d(String tag, String msg) {
        android.util.Log.d(tag, msg);
        writeLog("DEBUG", tag, msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        android.util.Log.i(tag, msg);
        writeLog("INFO", tag, msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        android.util.Log.w(tag, msg);
        writeLog("WARN", tag, msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        android.util.Log.e(tag, msg);
        writeLog("ERROR", tag, msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        android.util.Log.e(tag, msg, tr);
        writeLog("ERROR", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return 0;
    }
}
