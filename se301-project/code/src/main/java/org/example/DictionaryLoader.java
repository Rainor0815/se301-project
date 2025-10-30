package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads dictionary file (one password per line), trims and skips empties.
 */
public class DictionaryLoader {
    public static List<String> loadDictionary(Path dictFile) throws IOException {
        try (Stream<String> lines = Files.lines(dictFile, StandardCharsets.UTF_8)) {
            return lines.parallel()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(s -> s.replace("\r", ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }
}
