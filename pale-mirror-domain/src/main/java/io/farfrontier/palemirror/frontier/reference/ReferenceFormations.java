package io.farfrontier.palemirror.frontier.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure source port of formation totals, display and discrete role allocation. */
public final class ReferenceFormations {
    private ReferenceFormations() { }

    public static double totalUnits(Map<?, Double> composition) {
        return required(composition).values().stream().mapToDouble(amount -> Math.max(0.0d, amount)).sum();
    }

    public static String compactComposition(Map<?, Double> composition) {
        List<Map.Entry<?, Double>> entries = new ArrayList<>(required(composition).entrySet());
        entries.sort(Comparator.comparing(entry -> roleId(entry.getKey())));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<?, Double> entry : entries) {
            if (entry.getValue() > 0.01d) parts.add(roleId(entry.getKey()) + ":" + formatZeroDecimals(entry.getValue()));
        }
        return parts.isEmpty() ? "—" : String.join(", ", parts);
    }

    public static double compositionRatio(Map<?, Double> composition, Object kind) {
        double total = totalUnits(composition);
        if (total <= 0.0d) return 0.0d;
        String expected = roleId(Objects.requireNonNull(kind, "kind"));
        for (Map.Entry<?, Double> entry : required(composition).entrySet()) {
            if (roleId(entry.getKey()).equals(expected)) return Math.max(0.0d, entry.getValue()) / total;
        }
        return 0.0d;
    }

    /** Deterministic Python largest-remainder allocation for literal graybox roles. */
    public static Map<String, Integer> integerComposition(int total, Map<?, Double> weights) {
        if (total < 0) throw new IllegalArgumentException("formation size must be non-negative");
        LinkedHashMap<String, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, Double> entry : required(weights).entrySet()) {
            normalized.put(roleId(entry.getKey()), Math.max(0.0d, entry.getValue()));
        }
        if (normalized.isEmpty()) {
            if (total != 0) throw new IllegalArgumentException("cannot allocate a non-empty formation without role weights");
            return Map.of();
        }
        double weightTotal = normalized.values().stream().mapToDouble(Double::doubleValue).sum();
        if (weightTotal <= 0.0d) throw new IllegalArgumentException("formation role weights must contain a positive value");
        LinkedHashMap<String, Double> raw = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : normalized.entrySet()) {
            double value = total * entry.getValue() / weightTotal;
            raw.put(entry.getKey(), value);
            result.put(entry.getKey(), (int) Math.floor(value));
        }
        int remainder = total - result.values().stream().mapToInt(Integer::intValue).sum();
        List<String> order = new ArrayList<>(normalized.keySet());
        order.sort(Comparator.<String>comparingDouble(role -> raw.get(role) - result.get(role)).reversed()
                .thenComparing(Comparator.<String>comparingDouble(normalized::get).reversed())
                .thenComparing(Comparator.naturalOrder()));
        for (int index = 0; index < remainder; index++) result.merge(order.get(index), 1, Integer::sum);
        result.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static String formatZeroDecimals(double value) {
        return new BigDecimal(value).setScale(0, RoundingMode.HALF_EVEN).toPlainString();
    }

    private static String roleId(Object role) {
        return switch (Objects.requireNonNull(role, "formation role")) {
            case String value -> value;
            case ReferenceHumanUnitKind value -> value.id();
            case ReferenceBioformKind value -> value.id();
            default -> throw new IllegalArgumentException("formation role must be a source role enum or string: " + role);
        };
    }

    private static <K, V> Map<K, V> required(Map<K, V> value) { return Objects.requireNonNull(value, "composition"); }
}
