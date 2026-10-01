package com.family.expensemanager.common.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rejects profanity and junk placeholder values ("test", "xxx", "asdf"...) in free-text fields. Word lists
 * live in {@code text-filter/*.txt} on the classpath so they can grow without code changes. The frontend
 * mirrors these rules (src/utils/textQuality.js) for instant feedback — keep the two lists in sync.
 *
 * <p>Profanity is matched on whole words only, never on substrings, in two ways:
 * <ul>
 *   <li>{@code profanity-accented.txt}: compared WITH diacritics, because the unaccented form of these is an
 *       ordinary word ("lồn"/lon bia, "cặc"/các, "đụ"/du lịch, "đéo"/đeo kính);</li>
 *   <li>{@code profanity-plain.txt}: compared without diacritics — unambiguous abbreviations and English.</li>
 * </ul>
 * Common dodges are undone first: separators inside a word ("l.ồ.n", "đ-ụ"), letters spaced out ("l ồ n")
 * and stretched letters ("vcllll").
 *
 * <p>Junk is only junk when it is the WHOLE value: "test" is rejected, "Phí test COVID" is not.
 */
public final class TextQuality {

    public enum Problem { PROFANITY, JUNK }

    private static final Pattern IN_WORD_SEPARATORS = Pattern.compile("(?<=\\p{L})[._*~+\\-|/\\\\'`^]+(?=\\p{L})");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern REPEATED_CHAR = Pattern.compile("(.)\\1+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern ONLY_PUNCTUATION = Pattern.compile("[\\p{P}\\s]+");
    private static final Pattern ONE_CHAR_REPEATED = Pattern.compile("(.)\\1{2,}");

    private static final Set<String> ACCENTED = load("text-filter/profanity-accented.txt", false);
    private static final Set<String> PLAIN = load("text-filter/profanity-plain.txt", true);
    private static final Set<String> JUNK = load("text-filter/junk-values.txt", true);

    private TextQuality() {
    }

    /** Empty when the text is fine (null/blank included — required-ness is {@code @NotBlank}'s job). */
    public static Optional<Problem> check(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if (isProfane(value)) {
            return Optional.of(Problem.PROFANITY);
        }
        if (isJunk(value)) {
            return Optional.of(Problem.JUNK);
        }
        return Optional.empty();
    }

    /**
     * The text is tried both as typed and with stretched letters collapsed ("vcllll" → "vcl"); list entries are
     * NOT collapsed, or "dmm" would turn into "dm" and flag "2 dm vải" (đề-xi-mét).
     */
    static boolean isProfane(String value) {
        List<String> words = words(value);
        List<String> collapsed = words.stream().map(TextQuality::collapse).toList();
        return containsAny(ACCENTED, words) || containsAny(ACCENTED, collapsed)
                || containsAny(PLAIN, words.stream().map(TextQuality::stripMarks).toList())
                || containsAny(PLAIN, collapsed.stream().map(TextQuality::stripMarks).toList());
    }

    private static boolean containsAny(Set<String> entries, List<String> words) {
        String text = " " + String.join(" ", words) + " ";
        return entries.stream().anyMatch(entry -> text.contains(" " + entry + " "));
    }

    static boolean isJunk(String value) {
        String trimmed = value.strip();
        String compact = trimmed.replaceAll("\\s+", "");
        if (ONLY_PUNCTUATION.matcher(trimmed).matches() || ONE_CHAR_REPEATED.matcher(compact).matches()) {
            return true;
        }
        String normalized = String.join(" ", NON_WORD.split(stripMarks(lower(trimmed)))).strip();
        return JUNK.contains(normalized) || JUNK.contains(collapse(normalized)); // "testtt" = "test"
    }

    /** Lower-cased words with separator/spacing dodges undone ("l.ồ.n", "l ồ n"); letters NOT collapsed. */
    private static List<String> words(String value) {
        String joined = IN_WORD_SEPARATORS.matcher(lower(value)).replaceAll("");
        List<String> words = new ArrayList<>();
        StringBuilder spacedOut = new StringBuilder(); // "l ồ n" → "lồn"
        for (String token : NON_WORD.split(joined)) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.codePointCount(0, token.length()) == 1) {
                spacedOut.append(token);
                continue;
            }
            flushSpacedOut(spacedOut, words);
            words.add(token);
        }
        flushSpacedOut(spacedOut, words);
        return words;
    }

    private static void flushSpacedOut(StringBuilder spacedOut, List<String> words) {
        if (!spacedOut.isEmpty()) {
            words.add(spacedOut.toString());
            spacedOut.setLength(0);
        }
    }

    private static String lower(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static String collapse(String word) {
        return REPEATED_CHAR.matcher(word).replaceAll("$1");
    }

    private static String stripMarks(String value) {
        String decomposed = Normalizer.normalize(value.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("");
    }

    private static Set<String> load(String resource, boolean plain) {
        try (InputStream in = TextQuality.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing word list on the classpath: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        // Normalized exactly like the text it is compared with.
                        .map(line -> String.join(" ", words(plain ? stripMarks(line) : line)))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read word list " + resource, e);
        }
    }
}
