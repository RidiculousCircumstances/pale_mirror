package io.farfrontier.palemirror.frontier.reference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict test-only reader for the Python-generated yearly calibration envelope. */
final class ReferenceGrayboxCalibrationFixture {
    static final String FILENAME = "frontier-reference-graybox-calibration.json";
    private static final int SCHEMA = 2;
    private static final Set<String> ROOT_KEYS = Set.of(
            "schema", "source", "config", "measurements", "summary", "acceptance", "comparison");
    private static final Set<String> CONFIG_KEYS = Set.of(
            "width", "height", "settlements", "infection_seeds", "v2", "profile", "days", "seeds");
    private static final Set<String> COMPARISON_KEYS = Set.of("rules", "metric_rules");

    private ReferenceGrayboxCalibrationFixture() { }

    static Fixture load() {
        return load(sourceFixture(FILENAME));
    }

    static Fixture load(Path fixture) {
        if (!Files.isRegularFile(fixture)) throw new IllegalStateException("graybox calibration fixture is missing: " + fixture);
        try {
            return parse(Files.readString(fixture, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read graybox calibration fixture: " + fixture, exception);
        }
    }

    static Fixture parse(String json) {
        Map<String, Object> root = object(new JsonReader(json).read(), "root");
        exactKeys(root, ROOT_KEYS, "root");
        if (integer(root.get("schema"), "schema") != SCHEMA) {
            throw new IllegalStateException("unsupported graybox calibration fixture schema");
        }
        Map<String, Object> source = object(root.get("source"), "source");
        String sourceTree = string(required(source, "tree_sha256", "source"), "source.tree_sha256");
        if (!sourceTree.matches("[0-9a-f]{64}")) throw new IllegalStateException("invalid source tree fingerprint in graybox calibration fixture");

        Map<String, Object> config = object(root.get("config"), "config");
        exactKeys(config, CONFIG_KEYS, "config");
        Config expectedConfig = new Config(
                integer(config.get("width"), "config.width"), integer(config.get("height"), "config.height"),
                integer(config.get("settlements"), "config.settlements"), integer(config.get("infection_seeds"), "config.infection_seeds"),
                bool(config.get("v2"), "config.v2"), string(config.get("profile"), "config.profile"),
                integer(config.get("days"), "config.days"), longs(list(config.get("seeds"), "config.seeds"), "config.seeds"));
        validateConfig(expectedConfig);

        Map<String, Object> comparison = object(root.get("comparison"), "comparison");
        exactKeys(comparison, COMPARISON_KEYS, "comparison");
        Map<String, Rule> rules = rules(object(comparison.get("rules"), "comparison.rules"));
        Map<String, String> metricRules = metricRules(object(comparison.get("metric_rules"), "comparison.metric_rules"), rules);
        List<ExpectedRun> measurements = measurements(list(root.get("measurements"), "measurements"), expectedConfig, metricRules.keySet());
        Map<String, Acceptance> acceptance = acceptance(object(root.get("acceptance"), "acceptance"));
        Map<String, Double> summary = numbers(object(root.get("summary"), "summary"), "summary");
        if (!summary.keySet().equals(acceptance.keySet())) {
            throw new IllegalStateException("graybox calibration fixture summary and acceptance keys differ");
        }
        return new Fixture(sourceTree, expectedConfig, measurements, rules, metricRules, summary, acceptance);
    }

    private static Map<String, Rule> rules(Map<String, Object> values) {
        Map<String, Rule> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Map<String, Object> fields = object(entry.getValue(), "comparison rule " + entry.getKey());
            Double absolute = optionalNumber(fields, "absolute_tolerance", "comparison rule " + entry.getKey());
            Double relative = optionalNumber(fields, "relative_tolerance", "comparison rule " + entry.getKey());
            Double zeroMaximum = optionalNumber(fields, "zero_maximum", "comparison rule " + entry.getKey());
            Double minimum = optionalNumber(fields, "minimum", "comparison rule " + entry.getKey());
            Double maximum = optionalNumber(fields, "maximum", "comparison rule " + entry.getKey());
            if (absolute == null && relative == null) throw new IllegalStateException("comparison rule has no tolerance: " + entry.getKey());
            if (absolute != null && absolute < 0.0d || relative != null && relative < 0.0d || zeroMaximum != null && zeroMaximum < 0.0d
                    || minimum != null && maximum != null && minimum > maximum) {
                throw new IllegalStateException("comparison rule has a negative tolerance: " + entry.getKey());
            }
            result.put(entry.getKey(), new Rule(absolute, relative, zeroMaximum, minimum, maximum));
        }
        if (result.isEmpty()) throw new IllegalStateException("graybox calibration fixture has no comparison rules");
        return Map.copyOf(result);
    }

    private static Map<String, String> metricRules(Map<String, Object> values, Map<String, Rule> rules) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String rule = string(entry.getValue(), "metric rule " + entry.getKey());
            if (!rules.containsKey(rule)) throw new IllegalStateException("metric uses unknown comparison rule: " + entry.getKey());
            if (result.put(entry.getKey(), rule) != null) throw new IllegalStateException("duplicate calibration metric rule: " + entry.getKey());
        }
        if (result.isEmpty()) throw new IllegalStateException("graybox calibration fixture has no metric rules");
        return Map.copyOf(result);
    }

    private static List<ExpectedRun> measurements(List<Object> values, Config config, Set<String> metricNames) {
        List<ExpectedRun> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Object value : values) {
            Map<String, Object> fields = object(value, "measurement");
            Set<String> expectedKeys = new LinkedHashSet<>(metricNames);
            expectedKeys.add("seed");
            expectedKeys.add("days_simulated");
            exactKeys(fields, expectedKeys, "measurement");
            long seed = longValue(fields.get("seed"), "measurement.seed");
            int days = integer(fields.get("days_simulated"), "measurement.days_simulated");
            if (!seen.add(seed)) throw new IllegalStateException("duplicate graybox calibration measurement seed: " + seed);
            result.add(new ExpectedRun(seed, days, numbers(excluding(fields, "seed", "days_simulated"), "measurement")));
        }
        if (!result.stream().map(ExpectedRun::seed).toList().equals(config.seeds())) {
            throw new IllegalStateException("graybox calibration measurement seeds differ from config");
        }
        if (result.stream().anyMatch(item -> item.daysSimulated() != config.days())) {
            throw new IllegalStateException("graybox calibration measurement horizon differs from config");
        }
        return List.copyOf(result);
    }

    private static Map<String, Acceptance> acceptance(Map<String, Object> values) {
        Map<String, Acceptance> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof Number) {
                result.put(entry.getKey(), new Acceptance(number(entry.getValue(), "acceptance." + entry.getKey()), null));
                continue;
            }
            Map<String, Object> bounds = object(entry.getValue(), "acceptance." + entry.getKey());
            exactKeys(bounds, Set.of("minimum", "maximum"), "acceptance." + entry.getKey());
            double minimum = number(bounds.get("minimum"), "acceptance minimum " + entry.getKey());
            double maximum = number(bounds.get("maximum"), "acceptance maximum " + entry.getKey());
            if (minimum > maximum) throw new IllegalStateException("reversed acceptance range: " + entry.getKey());
            result.put(entry.getKey(), new Acceptance(minimum, maximum));
        }
        if (result.isEmpty()) throw new IllegalStateException("graybox calibration fixture has no acceptance values");
        return Map.copyOf(result);
    }

    private static Map<String, Double> numbers(Map<String, Object> values, String subject) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) result.put(entry.getKey(), number(entry.getValue(), subject + "." + entry.getKey()));
        return Map.copyOf(result);
    }

    private static Map<String, Object> excluding(Map<String, Object> values, String... excluded) {
        Set<String> unwanted = Set.of(excluded);
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> { if (!unwanted.contains(key)) result.put(key, value); });
        return result;
    }

    private static void validateConfig(Config config) {
        if (config.width() != 64 || config.height() != 44 || config.settlements() != 12 || config.infectionSeeds() != 2
                || !config.v2() || !config.profile().equals("graybox_1_40") || config.days() != 365
                || !config.seeds().equals(List.of(7L, 17L, 41L, 73L))) {
            throw new IllegalStateException("graybox calibration fixture has an unexpected source profile");
        }
    }

    private static Path sourceFixture(String filename) {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth++, current = current.getParent()) {
            Path candidate = current.resolve("docs").resolve(filename);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("graybox calibration fixture is missing from the Pale Mirror checkout: " + filename);
    }

    private static void exactKeys(Map<String, Object> actual, Set<String> expected, String subject) {
        if (!actual.keySet().equals(expected)) throw new IllegalStateException("unexpected keys in " + subject + ": " + actual.keySet());
    }

    private static Object required(Map<String, Object> values, String key, String subject) {
        if (!values.containsKey(key)) throw new IllegalStateException("missing " + subject + "." + key);
        return values.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String subject) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalStateException(subject + " must be a JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || result.put(key, entry.getValue()) != null) {
                throw new IllegalStateException(subject + " has invalid keys");
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value, String subject) {
        if (!(value instanceof List<?> values)) throw new IllegalStateException(subject + " must be a JSON array");
        return (List<Object>) values;
    }

    private static String string(Object value, String subject) {
        if (!(value instanceof String result)) throw new IllegalStateException(subject + " must be a string");
        return result;
    }

    private static boolean bool(Object value, String subject) {
        if (!(value instanceof Boolean result)) throw new IllegalStateException(subject + " must be a boolean");
        return result;
    }

    private static double number(Object value, String subject) {
        if (!(value instanceof Number number)) throw new IllegalStateException(subject + " must be a number");
        double result = number.doubleValue();
        if (!Double.isFinite(result)) throw new IllegalStateException(subject + " must be finite");
        return result;
    }

    private static int integer(Object value, String subject) {
        double number = number(value, subject);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalStateException(subject + " must be an integer");
        }
        return (int) number;
    }

    private static long longValue(Object value, String subject) {
        double number = number(value, subject);
        if (number != Math.rint(number) || number < Long.MIN_VALUE || number > Long.MAX_VALUE) {
            throw new IllegalStateException(subject + " must be an integer");
        }
        return (long) number;
    }

    private static List<Long> longs(List<Object> values, String subject) {
        List<Long> result = new ArrayList<>();
        for (Object value : values) result.add(longValue(value, subject));
        return List.copyOf(result);
    }

    private static Double optionalNumber(Map<String, Object> values, String key, String subject) {
        return values.containsKey(key) ? number(values.get(key), subject + "." + key) : null;
    }

    record Fixture(
            String sourceTree,
            Config config,
            List<ExpectedRun> measurements,
            Map<String, Rule> rules,
            Map<String, String> metricRules,
            Map<String, Double> summary,
            Map<String, Acceptance> acceptance
    ) {
        Rule ruleFor(String metric) {
            String rule = metricRules.get(metric);
            if (rule == null) throw new IllegalArgumentException("fixture has no rule for metric: " + metric);
            return rules.get(rule);
        }

        boolean within(String metric, double expected, double actual) {
            Rule rule = ruleFor(metric);
            if (rule.minimum() != null && actual < rule.minimum() || rule.maximum() != null && actual > rule.maximum()) return false;
            if (expected == 0.0d) return Math.abs(actual) <= (rule.zeroMaximum() == null ? 0.0d : rule.zeroMaximum());
            double tolerance = 0.0d;
            if (rule.absoluteTolerance() != null) tolerance = Math.max(tolerance, rule.absoluteTolerance());
            if (rule.relativeTolerance() != null) tolerance = Math.max(tolerance, Math.abs(expected) * rule.relativeTolerance());
            return Math.abs(actual - expected) <= tolerance;
        }

        String expectedRange(String metric, double expected) {
            Rule rule = ruleFor(metric);
            if (expected == 0.0d) return range(rule, -(rule.zeroMaximum() == null ? 0.0d : rule.zeroMaximum()),
                    rule.zeroMaximum() == null ? 0.0d : rule.zeroMaximum());
            double tolerance = 0.0d;
            if (rule.absoluteTolerance() != null) tolerance = Math.max(tolerance, rule.absoluteTolerance());
            if (rule.relativeTolerance() != null) tolerance = Math.max(tolerance, Math.abs(expected) * rule.relativeTolerance());
            return range(rule, expected - tolerance, expected + tolerance);
        }

        private static String range(Rule rule, double minimum, double maximum) {
            if (rule.minimum() != null) minimum = Math.max(minimum, rule.minimum());
            if (rule.maximum() != null) maximum = Math.min(maximum, rule.maximum());
            return "[" + minimum + ", " + maximum + "]";
        }
    }

    record Config(int width, int height, int settlements, int infectionSeeds, boolean v2, String profile, int days, List<Long> seeds) { }
    record ExpectedRun(long seed, int daysSimulated, Map<String, Double> metrics) { }
    record Rule(Double absoluteTolerance, Double relativeTolerance, Double zeroMaximum, Double minimum, Double maximum) { }
    record Acceptance(double minimum, Double maximum) { }

    /** Small strict parser so tests do not add a production JSON dependency to the pure domain. */
    private static final class JsonReader {
        private final String input;
        private int index;

        JsonReader(String input) { this.input = input == null ? "" : input; }

        Object read() {
            Object result = value();
            whitespace();
            if (index != input.length()) error("trailing JSON input");
            return result;
        }

        private Object value() {
            whitespace();
            if (index >= input.length()) error("unexpected end of JSON input");
            return switch (input.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return result;
            while (true) {
                whitespace();
                if (index >= input.length() || input.charAt(index) != '"') error("object key must be a string");
                String key = string();
                whitespace();
                expect(':');
                if (result.containsKey(key)) error("duplicate object key: " + key);
                result.put(key, value());
                whitespace();
                if (take('}')) return result;
                expect(',');
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (take(']')) return result;
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char value = input.charAt(index++);
                if (value == '"') return result.toString();
                if (value < 0x20) error("control character in JSON string");
                if (value != '\\') {
                    result.append(value);
                    continue;
                }
                if (index >= input.length()) error("unfinished JSON escape");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append((char) hex());
                    default -> error("invalid JSON escape");
                }
            }
            error("unterminated JSON string");
            return "";
        }

        private Object literal(String literal, Object value) {
            if (!input.startsWith(literal, index)) error("invalid JSON literal");
            index += literal.length();
            return value;
        }

        private Double number() {
            int start = index;
            if (take('-')) { }
            digits();
            if (take('.')) digits();
            if (take('e') || take('E')) {
                if (take('+') || take('-')) { }
                digits();
            }
            try {
                double result = Double.parseDouble(input.substring(start, index));
                if (!Double.isFinite(result)) error("non-finite JSON number");
                return result;
            } catch (NumberFormatException exception) {
                error("invalid JSON number");
                return 0.0d;
            }
        }

        private int hex() {
            if (index + 4 > input.length()) error("short unicode JSON escape");
            int result = 0;
            for (int offset = 0; offset < 4; offset++) {
                int digit = Character.digit(input.charAt(index++), 16);
                if (digit < 0) error("invalid unicode JSON escape");
                result = result * 16 + digit;
            }
            return result;
        }

        private void digits() {
            int start = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
            if (start == index) error("expected JSON digit");
        }

        private boolean take(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            whitespace();
            if (!take(expected)) error("expected '" + expected + "'");
        }

        private void whitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private void error(String message) {
            throw new IllegalStateException(message + " at JSON offset " + index);
        }
    }
}
