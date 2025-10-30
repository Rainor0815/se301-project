package org.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prints legacy-style progress lines. Guarantees:
 *  - One immediate line at start (0%),
 *  - Periodic in-place updates,
 *  - One final 100% line on stop.
 */
public class StatusReporter {
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicLong hashesComputed;
    private final AtomicLong passwordsFound;
    private final long totalTasks;
    private final ScheduledExecutorService scheduler;

    private volatile boolean running = false;

    public StatusReporter(AtomicLong hashesComputed, AtomicLong passwordsFound, long totalTasks, ScheduledExecutorService scheduler) {
        this.hashesComputed = hashesComputed;
        this.passwordsFound = passwordsFound;
        this.totalTasks = totalTasks;
        this.scheduler = scheduler;
    }

    public void start() {
        if (running) return;
        running = true;

        System.out.println("Starting attack with " + totalTasks + " total tasks...");

        // Immediate first print so ultra-fast runs still show progress once.
        printLine(false);

        // Frequent updates so progress is visible in short runs too.
        scheduler.scheduleAtFixedRate(() -> printLine(false), 0, 50, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (!running) return;
        running = false;
        try {
            // Print a final 100% line before shutting down.
            printLine(true);

            scheduler.shutdownNow();
            scheduler.awaitTermination(250, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            System.out.println(""); // move cursor to the next line after the \r line
        }
    }

    private void printLine(boolean force100) {
        long h = hashesComputed.get();
        long p = passwordsFound.get();

        long remaining = Math.max(0, totalTasks - h);
        double progressPercent = (totalTasks <= 0) ? 0.0 : (h * 100.0 / totalTasks);

        if (force100) {
            remaining = 0;
            progressPercent = 100.00;
        }

        String timestamp = LocalDateTime.now().format(TS_FMT);
        // Legacy format with visible remaining (no zero-padding limits)
        System.out.printf("\r[%s] %.2f%% complete | Passwords Found: %d | Tasks Remaining: %d",
                timestamp, progressPercent, p, remaining);
        System.out.flush();
    }
}
