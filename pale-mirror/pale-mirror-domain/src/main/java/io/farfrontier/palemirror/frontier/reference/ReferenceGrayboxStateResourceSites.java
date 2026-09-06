package io.farfrontier.palemirror.frontier.reference;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Strict all-or-nothing hydration of physical resource-site owners. */
final class ReferenceGrayboxStateResourceSites {
    private ReferenceGrayboxStateResourceSites() { }

    static LinkedHashMap<Integer, ReferenceResourceSite> restore(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        Map<Integer, ReferenceSettlement> knownSettlements = Map.copyOf(settlements);
        LinkedHashMap<Integer, ReferenceResourceSite> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "resource sites")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "resource-site map key");
            ReferenceResourceSite site = site(entry.value(), knownSettlements);
            if (id != site.id()) throw new IllegalArgumentException("resource-site map key does not match its id");
            if (result.putIfAbsent(id, site) != null) throw new IllegalArgumentException("duplicate resource site " + id);
        }
        return result;
    }

    private static ReferenceResourceSite site(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.sites.ResourceSite", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "resource-site fields", "id", "kind", "x", "y", "quality", "capacity",
                "owner_id", "operator_company_id", "condition", "contamination", "substrate", "stock", "haul_capacity", "claimed_day");
        Integer ownerId = ReferenceGrayboxStateReader.nullableInteger(fields.get("owner_id"), "resource-site owner");
        if (ownerId != null && !settlements.containsKey(ownerId)) throw new IllegalArgumentException("resource site owner is unknown");
        ReferenceResourceSite result = new ReferenceResourceSite(
                ReferenceGrayboxStateReader.integer(fields.get("id"), "resource-site id"),
                ReferenceSiteKind.valueOf(ReferenceGrayboxStateReader.enumValue(fields.get("kind"), "simulation.sites.SiteKind", "resource-site kind")
                        .toUpperCase(Locale.ROOT)),
                ReferenceGrayboxStateReader.integer(fields.get("x"), "resource-site x"),
                ReferenceGrayboxStateReader.integer(fields.get("y"), "resource-site y"),
                ReferenceGrayboxStateReader.number(fields.get("quality"), "resource-site quality"),
                ReferenceGrayboxStateReader.number(fields.get("capacity"), "resource-site capacity"), ownerId);
        result.operatorCompanyId(ReferenceGrayboxStateReader.nullableInteger(fields.get("operator_company_id"), "resource-site operator"));
        result.condition(ReferenceGrayboxStateReader.number(fields.get("condition"), "resource-site condition"));
        result.contamination(ReferenceGrayboxStateReader.number(fields.get("contamination"), "resource-site contamination"));
        result.substrate(ReferenceGrayboxStateReader.number(fields.get("substrate"), "resource-site substrate"));
        result.haulCapacity(ReferenceGrayboxStateReader.number(fields.get("haul_capacity"), "resource-site haul capacity"));
        result.claimedDay(ReferenceGrayboxStateReader.nullableInteger(fields.get("claimed_day"), "resource-site claimed day"));
        resources(fields.get("stock"), "resource-site stock").forEach(result::add);
        return result;
    }

    private static EnumMap<ReferenceResource, Double> resources(Object encoded, String label) {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceResource resource = ReferenceResource.valueOf(ReferenceGrayboxStateReader.enumValue(entry.key(),
                    "simulation.economy.Resource", label + " resource").toUpperCase(Locale.ROOT));
            double amount = ReferenceGrayboxStateReader.number(entry.value(), label + " amount");
            if (amount < 0.0d) throw new IllegalArgumentException(label + " cannot be negative");
            if (result.putIfAbsent(resource, amount) != null) throw new IllegalArgumentException(label + " has duplicate " + resource);
        }
        if (result.size() != ReferenceResource.values().length) throw new IllegalArgumentException(label + " lacks a resource");
        return result;
    }
}
