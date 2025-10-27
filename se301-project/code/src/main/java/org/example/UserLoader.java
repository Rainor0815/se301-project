package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public final class UserLoader {
    private final Path file;

    public UserLoader(Path file) {
        this.file = file;
    }

    public Map<String, User> loadUsers() throws IOException {
        return Files.lines(file)
                .map(line -> line.split(",", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0].trim(),
                        parts -> new User(parts[0].trim(), parts[1].trim())
                ));
    }
}
