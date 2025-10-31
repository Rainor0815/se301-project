package org.example;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Main launcher (legacy-style console output).
 */
public class DictionaryAttack {
    public static void main(String[] args) throws Exception {
        Path targetsPath = args.length > 0 ? Paths.get(args[0]) : Paths.get("root/datasets/small/in.txt");
        Path dictPath = args.length > 1 ? Paths.get(args[1]) : Paths.get("root/datasets/small/dictionary.txt");
        Path outPath = args.length > 2 ? Paths.get(args[2]) : Paths.get("root/datasets/small/out.txt");

        // System.out.println("Targets: " + targetsPath.toAbsolutePath());
        // System.out.println("Dictionary: " + dictPath.toAbsolutePath());
        // System.out.println("Output: " + outPath.toAbsolutePath());

        TargetHashManager thm = TargetHashManager.loadFrom(targetsPath);
        Set<String> targetHashSet = thm.getTargetHashSet();
        Map<String, List<String>> hashToUsers = thm.getHashToUsernames();
        List<String> dictionary = DictionaryLoader.loadDictionary(dictPath);

        // Print Corretto/provider status (helps verify native acceleration)
        System.out.println("[INFO] Corretto available: " + HashUtil.isCorrettoAvailable());
        try {
            String prov = java.security.MessageDigest.getInstance("SHA-256").getProvider().getName();
            System.out.println("[INFO] SHA-256 provider: " + prov);
        } catch (Throwable t) {
            System.out.println("[WARN] Could not determine SHA-256 provider: " + t.getMessage());
        }

        int threads = Runtime.getRuntime().availableProcessors();
        CrackingEngine engine = new CrackingEngine(targetHashSet, hashToUsers, dictionary, threads);

        StatusReporter reporter = new StatusReporter(
                engine.getHashesComputedAtomic(),
                engine.getPasswordsFoundAtomic(),
                dictionary.size(),
                Executors.newSingleThreadScheduledExecutor()
        );
        reporter.start();

        long start = System.currentTimeMillis();
        engine.runAndWait();
        long elapsed = System.currentTimeMillis() - start;

        reporter.stop();

        System.out.println("Total passwords found: " + engine.getPasswordsFound());
        System.out.println("Total hashes computed: " + engine.getHashesComputed());
        System.out.println("Total time spent (milliseconds): " + elapsed);

        engine.writeResults(outPath);
    }
}
