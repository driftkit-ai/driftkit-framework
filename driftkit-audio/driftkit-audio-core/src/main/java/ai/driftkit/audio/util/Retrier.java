package ai.driftkit.audio.util;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Utility class for retrying operations with exponential backoff
 */
public class Retrier {
    private static final int DEFAULT_TRIALS = 3;
    private static final int DEFAULT_DELAY = 3000;

    private Retrier() {
    }

    public static void retry(Runnable runnable) throws Exception {
        retry(runnable, 3L);
    }

    public static <R> R retry(Callable<R> callable) throws Exception {
        return retry(callable, 3L);
    }

    public static void retry(Runnable runnable, long delay) throws Exception {
        retry(runnable, 3, delay / 3L, 1);
    }

    public static <R> R retry(Callable<R> callable, long delay) throws Exception {
        return retry(callable, 3, delay / 3L, 1);
    }

    public static void retry(Runnable runnable, int trials, long delay) throws Exception {
        retry(runnable, trials, delay, 1);
    }

    public static <R> R retry(Callable<R> callable, int trials, long delay) throws Exception {
        return retry(callable, trials, delay, 1);
    }

    public static void retryQuietly(Runnable runnable, Consumer<Exception> log) {
        try {
            retry(runnable, 3L);
        } catch (Exception e) {
            log.accept(e);
        }
    }

    public static <R> R retryQuietly(Callable<R> callable, Consumer<Exception> log) {
        try {
            return retry(callable, 3000L);
        } catch (Exception e) {
            log.accept(e);
            return null;
        }
    }

    public static void retryQuietly(Runnable runnable, Consumer<Exception> log, int trials, long delay, int multiplier) {
        try {
            retry(runnable, trials, delay, multiplier);
        } catch (Exception e) {
            log.accept(e);
        }
    }

    public static <R> R retryQuietly(Callable<R> callable, Consumer<Exception> log, int trials, long delay, int multiplier) {
        try {
            return retry(callable, trials, delay, multiplier);
        } catch (Exception e) {
            log.accept(e);
            return null;
        }
    }

    public static <R> R retry(Callable<R> callable, int trials, long delay, int multiplier) throws Exception {
        for(int trial = 0; trial < trials; delay *= (long)multiplier) {
            ++trial;

            try {
                return callable.call();
            } catch (Exception e) {
                if (trial >= trials) {
                    throw e;
                }

                Thread.sleep(delay);
            }
        }

        return null;
    }

    public static void retry(Runnable runnable, int trials, long delay, int multiplier) throws Exception {
        for(int trial = 0; trial < trials; delay *= (long)multiplier) {
            ++trial;

            try {
                runnable.run();
                return;
            } catch (Exception e) {
                if (trial >= trials) {
                    throw e;
                }

                Thread.sleep(delay);
            }
        }
    }
}