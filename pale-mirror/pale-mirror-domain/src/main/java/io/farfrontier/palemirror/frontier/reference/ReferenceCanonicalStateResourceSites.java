package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Source-shaped owner mapper for Python's {@code dict[int, ResourceSite]}. */
final class ReferenceCanonicalStateResourceSites {
    private ReferenceCanonicalStateResourceSites() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceResourceSite> entry : required.marketWorld().resourceSites().entrySet()) {
            pairs.add(pair(entry.getKey(), site(entry.getValue())));
        }
        return map(pairs);
    }

    private static Map<String, Object> site(ReferenceResourceSite value) {
        return typed("simulation.sites.ResourceSite", object(
                "id", value.id(), "kind", siteKind(value.kind()), "x", value.x(), "y", value.y(), "quality", value.quality(),
                "capacity", value.capacity(), "owner_id", value.ownerId(), "operator_company_id", value.operatorCompanyId(),
                "condition", value.condition(), "contamination", value.contamination(), "substrate", value.substrate(),
                "stock", inventory(value.stock()), "haul_capacity", value.haulCapacity(), "claimed_day", value.claimedDay()));
    }

    private static Map<String, Object> inventory(Map<ReferenceResource, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        for (ReferenceResource resource : ReferenceResource.values()) {
            Double value = values.get(resource);
            if (value == null) throw new IllegalStateException("resource site inventory lacks resource " + resource);
            pairs.add(pair(resource(resource), value));
        }
        return map(pairs);
    }

    private static Map<String, Object> siteKind(ReferenceSiteKind value) {
        return enumValue("simulation.sites.SiteKind", value.name().toLowerCase(Locale.ROOT));
    }

    private static Map<String, Object> resource(ReferenceResource value) {
        return enumValue("simulation.economy.Resource", value.name().toLowerCase(Locale.ROOT));
    }

    private static Map<String, Object> enumValue(String type, String value) { return object("$enum", type, "value", value); }
    private static Map<String, Object> typed(String type, Map<String, Object> fields) { return object("$type", type, "fields", fields); }

    private static Map<String, Object> map(List<List<Object>> pairs) {
        List<List<Object>> ordered = new ArrayList<>(pairs);
        ordered.sort(Comparator.comparing(pair -> ReferenceV2PublicSnapshot.canonicalJson(pair.getFirst())));
        return object("$map", List.copyOf(ordered));
    }

    private static List<Object> pair(Object key, Object value) {
        ArrayList<Object> result = new ArrayList<>(2);
        result.add(key);
        result.add(value);
        return Collections.unmodifiableList(result);
    }

    private static Map<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
