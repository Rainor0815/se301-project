package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Loads the targets file which should contain lines: username,sha256hex
 * Builds:
 *  - userToHash (Map username -> hash)
 *  - hashToUsernames (Map hash -> List<username>)
 *  - targetHashSet (Set of hashes for fast lookup)
 */
public class TargetHashManager {
    private final Map<String, String> userToHash = new ConcurrentHashMap<>();
    private final Map<String, List<String>> hashToUsernames = new ConcurrentHashMap<>();
    private final Set<String> targetHashSet = ConcurrentHashMap.newKeySet();

    private TargetHashManager() {}

    public static TargetHashManager loadFrom(Path targetsFile) throws IOException {
        TargetHashManager mgr = new TargetHashManager();

        try (Stream<String> lines = Files.lines(targetsFile, StandardCharsets.UTF_8)) {
            lines.parallel()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .forEach(line -> {
                        String[] parts = line.split(",", 2);
                        if (parts.length < 2) {
                            System.err.println("WARN: malformed target line (skipping): `" + line + "`");
                            return;
                        }

                        String username = parts[0].trim();
                        String hash = parts[1].trim().toLowerCase(Locale.ROOT);
                        if (username.isEmpty() || hash.isEmpty()) {
                            System.err.println("WARN: empty username or hash (skipping): `" + line + "`");
                            return;
                        }

                        // Concurrent-safe operations
                        mgr.userToHash.put(username, hash);
                        mgr.targetHashSet.add(hash);
                        mgr.hashToUsernames
                                .computeIfAbsent(hash, k -> Collections.synchronizedList(new ArrayList<>()))
                                .add(username);
                    });
        }

        return mgr;
    }

    public Map<String, String> getUserToHash() {
        return Collections.unmodifiableMap(userToHash);
    }

    public Map<String, List<String>> getHashToUsernames() {
        return Collections.unmodifiableMap(hashToUsernames);
    }

    public Set<String> getTargetHashSet() {
        return targetHashSet;
    }
}
