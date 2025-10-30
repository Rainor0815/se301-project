package org.example;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrent cracking engine with batching + tuned pool sizing/back-pressure.
 *
 * - Batching reduces per-task overhead (one task handles many candidates).
 * - ThreadPoolExecutor is sized for CPU-bound hashing and uses a bounded queue with CallerRunsPolicy
 *   to apply back-pressure instead of letting the submission thread run far ahead.
 */
public class CrackingEngine {
    private final Set<String> targetHashSet;
    private final Map<String, List<String>> hashToUsernames;
    private final List<String> dictionary;
    private final ConcurrentMap<String, String> crackedUserToPassword = new ConcurrentHashMap<>();

    private final AtomicLong hashesComputed = new AtomicLong(0);
    private final AtomicLong passwordsFound = new AtomicLong(0);

    // === Tuning knobs ===
    private final int batchSize;           // e.g., 512~4096 is usually good; tune with your data
    private final int numThreads;          // CPU-bound: ~= number of cores (or cores+1)
    private final int queueCapacity;       // small multiple of numThreads to limit memory + add back-pressure

    private final ThreadPoolExecutor workers;

    public CrackingEngine(Set<String> targetHashSet,
                          Map<String, List<String>> hashToUsernames,
                          List<String> dictionary,
                          int numThreads) {

        this(targetHashSet, hashToUsernames, dictionary, numThreads,
             chooseBatchSize(dictionary.size()),                    // auto batch
             Math.max(2, numThreads) * 4);                          // queue capacity heuristic
    }

    public CrackingEngine(Set<String> targetHashSet,
                          Map<String, List<String>> hashToUsernames,
                          List<String> dictionary,
                          int numThreads,
                          int batchSize,
                          int queueCapacity) {

        this.targetHashSet = targetHashSet;
        this.hashToUsernames = hashToUsernames;
        this.dictionary = dictionary;

        int cores = Runtime.getRuntime().availableProcessors();
        this.numThreads = Math.max(1, (numThreads > 0 ? numThreads : cores)); // default to cores
        this.batchSize  = Math.max(64, batchSize);                             // floor to avoid too-small batches
        this.queueCapacity = Math.max(this.numThreads * 2, queueCapacity);

        // BOUNDED queue to avoid unbounded memory; CallerRunsPolicy for back-pressure.
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(this.queueCapacity);
        this.workers = new ThreadPoolExecutor(
                this.numThreads,
                this.numThreads,
                0L, TimeUnit.MILLISECONDS,
                workQueue,
                r -> {
                    Thread t = new Thread(r, "crack-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // if queue is full, submitter runs the task (natural throttling)
        );
        this.workers.prestartAllCoreThreads();
    }

    public void runAndWait() throws InterruptedException {
        final int n = dictionary.size();
        final int tasks = (n + batchSize - 1) / batchSize; // ceilDiv

        CompletionService<Integer> cs = new ExecutorCompletionService<>(workers);
        for (int start = 0; start < n; start += batchSize) {
            final int from = start;
            final int to = Math.min(start + batchSize, n);

            cs.submit(() -> {
                // local counters to reduce Atomic contention
                long localHashes = 0;
                long localFound  = 0;

                for (int i = from; i < to; i++) {
                    String candidate = dictionary.get(i);
                    String hash = HashUtil.sha256Hex(candidate);
                    localHashes++;

                    if (targetHashSet.contains(hash)) {
                        List<String> users = hashToUsernames.get(hash);
                        if (users != null) {
                            for (String user : users) {
                                if (crackedUserToPassword.putIfAbsent(user, candidate) == null) {
                                    localFound++;
                                }
                            }
                        }
                    }
                }

                // batch-add to Atomics (much lower contention than per-element)
                hashesComputed.addAndGet(localHashes);
                passwordsFound.addAndGet(localFound);
                return (to - from);
            });
        }

        // wait for all tasks to complete
        for (int i = 0; i < tasks; i++) {
            try {
                cs.take(); // waits for one finished task
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        shutdownWorkers();
    }

    private static int chooseBatchSize(int total) {
        // Heuristic: larger inputs -> larger batches. Tune as needed.
        if (total < 10_000) return 512;
        if (total < 100_000) return 1024;
        if (total < 1_000_000) return 2048;
        return 4096;
    }

    private void shutdownWorkers() {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public long getHashesComputed() {
        return hashesComputed.get();
    }

    public long getPasswordsFound() {
        return passwordsFound.get();
    }

    public AtomicLong getHashesComputedAtomic() {
        return hashesComputed;
    }

    public AtomicLong getPasswordsFoundAtomic() {
        return passwordsFound;
    }

    public Map<String, String> getCrackedMap() {
        return crackedUserToPassword;
    }

    public void writeResults(Path outFile) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : crackedUserToPassword.entrySet()) {
                String user = e.getKey();
                String pass = e.getValue();
                String hash = HashUtil.sha256Hex(pass);
                w.write(user + "," + hash + "," + pass);
                w.newLine();
            }
            w.flush();
        }
    }
}
