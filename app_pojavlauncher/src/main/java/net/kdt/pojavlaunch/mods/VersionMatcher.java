package net.kdt.pojavlaunch.mods;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks Minecraft versions against Fabric predicates (">=1.21", "~1.20.1", "1.21.x") and
 * Forge maven ranges ("[1.20.1,1.21)"). Anything it does not understand counts as a match,
 * so a warning only shows up when the mismatch is certain.
 */
public final class VersionMatcher {
    private static final Pattern MAVEN_RANGE = Pattern.compile("([\\[(])([^\\])]*)([\\])])");

    private VersionMatcher() {}

    public static boolean matches(@Nullable String range, boolean forgeStyle, String version) {
        if(range == null || parse(version) == null) return true;
        try {
            return forgeStyle ? matchesMaven(range.trim(), version) : matchesFabric(range, version);
        }catch (RuntimeException e) {
            return true;
        }
    }

    private static boolean matchesFabric(String predicate, String version) {
        for(String alternative : predicate.split("\\|\\|")) {
            if(matchesAll(alternative.trim(), version)) return true;
        }
        return false;
    }

    private static boolean matchesAll(String predicate, String version) {
        if(predicate.isEmpty() || predicate.equals("*")) return true;
        for(String term : predicate.split("\\s+")) {
            if(!matchesTerm(term, version)) return false;
        }
        return true;
    }

    private static boolean matchesTerm(String term, String version) {
        String operator = "";
        for(String candidate : new String[]{">=", "<=", ">", "<", "=", "~", "^"}) {
            if(term.startsWith(candidate)) {
                operator = candidate;
                break;
            }
        }
        String value = term.substring(operator.length());
        if(value.contains("x") || value.contains("X") || value.contains("*")) {
            String prefix = value.replaceAll("[.]?[xX*].*$", "");
            int[] wanted = parse(prefix);
            int[] actual = parse(version);
            if(wanted == null || actual == null) return true;
            for(int i = 0; i < wanted.length; i++) {
                if(i >= actual.length ? wanted[i] != 0 : wanted[i] != actual[i]) return false;
            }
            return true;
        }
        int[] wanted = parse(value);
        int[] actual = parse(version);
        if(wanted == null || actual == null) return true;
        int compared = compare(actual, wanted);
        switch (operator) {
            case ">=": return compared >= 0;
            case "<=": return compared <= 0;
            case ">": return compared > 0;
            case "<": return compared < 0;
            case "~": return compared >= 0 && compare(actual, bump(wanted, 1)) < 0;
            case "^": return compared >= 0 && compare(actual, bump(wanted, 0)) < 0;
            default: return compared == 0;
        }
    }

    private static boolean matchesMaven(String range, String version) {
        if(range.isEmpty() || range.equals("*")) return true;
        Matcher matcher = MAVEN_RANGE.matcher(range);
        boolean found = false;
        int[] actual = parse(version);
        while(matcher.find()) {
            found = true;
            boolean lowerInclusive = matcher.group(1).equals("[");
            boolean upperInclusive = matcher.group(3).equals("]");
            String body = matcher.group(2);
            int comma = body.indexOf(',');
            String lower = comma < 0 ? body : body.substring(0, comma).trim();
            String upper = comma < 0 ? body : body.substring(comma + 1).trim();
            if(inRange(actual, lower, lowerInclusive, upper, upperInclusive)) return true;
        }
        // A bare version in maven means "recommended", not required
        return !found;
    }

    private static boolean inRange(int[] actual, String lower, boolean lowerInclusive, String upper, boolean upperInclusive) {
        if(!lower.isEmpty()) {
            int[] min = parse(lower);
            if(min == null) return true;
            int compared = compare(actual, min);
            if(compared < 0 || (compared == 0 && !lowerInclusive)) return false;
        }
        if(!upper.isEmpty()) {
            int[] max = parse(upper);
            if(max == null) return true;
            int compared = compare(actual, max);
            if(compared > 0 || (compared == 0 && !upperInclusive)) return false;
        }
        return true;
    }

    private static int[] bump(int[] version, int index) {
        int length = Math.max(version.length, index + 1);
        int[] result = new int[length];
        for(int i = 0; i <= index; i++) result[i] = i < version.length ? version[i] : 0;
        result[index]++;
        return result;
    }

    private static int compare(int[] a, int[] b) {
        int length = Math.max(a.length, b.length);
        for(int i = 0; i < length; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if(left != right) return left < right ? -1 : 1;
        }
        return 0;
    }

    /** "1.21.1" becomes [1, 21, 1]; pre-release suffixes are dropped; snapshots like 24w14a give null. */
    @Nullable
    static int[] parse(String version) {
        if(version == null) return null;
        String clean = version.trim();
        int dash = clean.indexOf('-');
        if(dash >= 0) clean = clean.substring(0, dash);
        int plus = clean.indexOf('+');
        if(plus >= 0) clean = clean.substring(0, plus);
        if(clean.isEmpty()) return null;
        String[] parts = clean.split("\\.");
        List<Integer> numbers = new ArrayList<>(parts.length);
        for(String part : parts) {
            if(part.isEmpty() || !part.matches("\\d+")) return null;
            numbers.add(Integer.parseInt(part));
        }
        int[] result = new int[numbers.size()];
        for(int i = 0; i < result.length; i++) result[i] = numbers.get(i);
        return result;
    }
}
