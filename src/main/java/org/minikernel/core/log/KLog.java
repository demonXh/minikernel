package org.minikernel.core.log;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Kernel log facility, akin to Linux printk.
 *
 * <p>All kernel-level output should go through this class so we can later
 * redirect it to a ring buffer (like the real kernel's {@code __log_buf}).
 */
public final class KLog {

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static volatile Level minLevel = Level.INFO;

    private KLog() {}

    public static void setLevel(Level level) {
        minLevel = level;
    }

    public static void debug(String fmt, Object... args) { log(Level.DEBUG, fmt, args); }
    public static void info (String fmt, Object... args) { log(Level.INFO,  fmt, args); }
    public static void warn (String fmt, Object... args) { log(Level.WARN,  fmt, args); }
    public static void error(String fmt, Object... args) { log(Level.ERROR, fmt, args); }

    private static void log(Level level, String fmt, Object... args) {
        if (level.ordinal() < minLevel.ordinal()) return;
        String msg = (args == null || args.length == 0) ? fmt : String.format(fmt, args);
        System.out.printf("[%s] [%-5s] %s%n", LocalTime.now().format(TS), level, msg);
    }
}
