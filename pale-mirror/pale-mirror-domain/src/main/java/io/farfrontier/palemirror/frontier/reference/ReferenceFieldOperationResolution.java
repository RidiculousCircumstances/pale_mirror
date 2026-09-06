package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Arrival-side field orders from Python {@code FieldWarfare.resolve_operation}. */
final class ReferenceFieldOperationResolution {
    private ReferenceFieldOperationResolution() { }

    static boolean resolve(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        switch (operation.kind()) {
            case BUILD_POST -> buildPost(field, world, operation);
            case RESUPPLY -> resupply(field, world, operation);
            case REINFORCE -> reinforce(field, world, operation);
            case BUILD_MODULE -> buildModule(field, world, operation);
            case BUILD_LINE -> buildLine(field, world, operation);
            case CLEANSE_PERIMETER -> cleanse(field, world, operation);
            default -> { return false; }
        }
        operation.status(ReferenceOperationStatus.RETURNING);
        operation.resolved(true);
        return true;
    }

    private static void buildPost(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        String kind = stringDetail(operation, "post_kind");
        Integer campaignId = ReferenceFieldExecution.detailInteger(operation, "campaign_id");
        if (kind == null || campaignId == null) { operation.outcome("invalid_post_order"); return; }
        Map<Integer, List<String>> residents = copyResidents(operation.residentIdsBySettlement());
        ReferenceFieldPost post = field.startPost(world, campaignId, postKind(kind), (int) Math.round(operation.x()),
                (int) Math.round(operation.y()), operation.owner().id(), operation.contributors().keySet().isEmpty()
                        ? Set.of(operation.owner().id()) : operation.contributors().keySet(), adoptGarrison(operation), operation.cargo(), residents);
        if (post != null) {
            for (Map.Entry<Integer, List<String>> entry : residents.entrySet()) {
                ReferenceSettlement settlement = world.settlements().get(entry.getKey());
                if (settlement != null && settlement.discretePeople()) settlement.assignPeopleToFieldPost(entry.getValue(), post.id());
            }
            operation.mutableResidentIdsBySettlement().clear();
        }
        operation.outcome(post == null ? "post_site_rejected" : "post_started");
    }

    private static void resupply(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        ReferenceFieldPost post = targetPost(field, operation);
        if (post == null || !(post.status() == ReferenceFieldPostStatus.BUILDING || active(post))) { operation.outcome("post_missing"); return; }
        Map<ReferenceResource, Double> delivered = post.receiveCargo(operation.cargo());
        operation.mutableDetails().put("cargo_delivered", deliveredDescription(delivered));
        post.lastSuppliedDay(world.day());
        post.isolationDays(0);
        operation.outcome(delivered.values().stream().mapToDouble(Double::doubleValue).sum() > 0.0d ? "post_resupplied" : "post_full");
    }

    private static void reinforce(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        ReferenceFieldPost post = targetPost(field, operation);
        if (post == null || !active(post)) { operation.outcome("post_missing"); return; }
        Map<Integer, List<String>> residents = copyResidents(operation.residentIdsBySettlement());
        for (Map.Entry<Integer, Double> entry : adoptGarrison(operation).entrySet()) {
            post.mutableGarrisonBySettlement().merge(entry.getKey(), entry.getValue(), Double::sum);
            List<String> ids = residents.getOrDefault(entry.getKey(), List.of());
            if (!ids.isEmpty()) {
                post.mutableResidentIdsBySettlement().put(entry.getKey(), ReferenceFieldExecution.merge(post.mutableResidentIdsBySettlement().get(entry.getKey()), ids));
                ReferenceSettlement settlement = world.settlements().get(entry.getKey());
                if (settlement != null && settlement.discretePeople()) settlement.assignPeopleToFieldPost(ids, post.id());
            }
        }
        operation.mutableResidentIdsBySettlement().clear();
        post.receiveCargo(operation.cargo());
        operation.outcome("post_reinforced");
    }

    private static void buildModule(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        ReferenceFieldPost post = targetPost(field, operation);
        String module = stringDetail(operation, "module");
        operation.outcome(post == null || module == null ? "module_target_missing"
                : field.startModule(world, post.id(), moduleKind(module)) ? "module_started" : "module_rejected");
    }

    private static void buildLine(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        Integer a = ReferenceFieldExecution.detailInteger(operation, "a_post_id");
        Integer b = ReferenceFieldExecution.detailInteger(operation, "b_post_id");
        Integer campaignId = ReferenceFieldExecution.detailInteger(operation, "campaign_id");
        String kind = stringDetail(operation, "link_kind");
        if (a == null || b == null || campaignId == null || kind == null) operation.outcome("invalid_line_order");
        else operation.outcome(field.startLink(world, campaignId, linkKind(kind), a, b) == null ? "line_rejected" : "line_started");
    }

    private static void cleanse(ReferenceFieldWarfare field, ReferenceWorld world, ReferenceOperation operation) {
        ReferenceFieldPost post = targetPost(field, operation);
        if (post == null || !post.hasModule(ReferenceFieldModuleKind.DECONTAMINATION)) { operation.outcome("decontamination_unavailable"); return; }
        double removed = world.infection().suppressArea(post.x(), post.y(), ReferenceFieldRules.DECONTAMINATION_RADIUS,
                ReferenceFieldRules.DECONTAMINATION_STRENGTH);
        world.infection().recordDamage("cleanse", removed);
        operation.mutableDetails().put("infection_removed", removed);
        operation.outcome("perimeter_cleansed");
    }

    private static Map<Integer, Double> adoptGarrison(ReferenceOperation operation) {
        Map<Integer, Double> result = new LinkedHashMap<>(operation.personnelBySettlement());
        operation.personnel(0.0d);
        operation.committedPersonnel(0.0d);
        operation.mutablePersonnelBySettlement().clear();
        return result;
    }
    private static ReferenceFieldPost targetPost(ReferenceFieldWarfare field, ReferenceOperation operation) {
        return operation.target().id() == null ? null : field.mutablePosts().get(operation.target().id());
    }
    private static boolean active(ReferenceFieldPost post) {
        return post.status() == ReferenceFieldPostStatus.ACTIVE || post.status() == ReferenceFieldPostStatus.ISOLATED;
    }
    private static String stringDetail(ReferenceOperation operation, String key) {
        Object value = operation.details().get(key);
        return value instanceof String string ? string : null;
    }
    private static String deliveredDescription(Map<ReferenceResource, Double> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey().name().toLowerCase(java.util.Locale.ROOT)
                + ":" + String.format(java.util.Locale.ROOT, "%.1f", entry.getValue())).reduce((a, b) -> a + ", " + b).orElse("none (post full)");
    }
    private static ReferenceFieldPostKind postKind(String id) {
        for (ReferenceFieldPostKind kind : ReferenceFieldPostKind.values()) if (kind.id().equals(id)) return kind;
        throw new IllegalArgumentException("unknown field post kind " + id);
    }
    private static ReferenceFieldModuleKind moduleKind(String id) {
        for (ReferenceFieldModuleKind kind : ReferenceFieldModuleKind.values()) if (kind.id().equals(id)) return kind;
        throw new IllegalArgumentException("unknown field module kind " + id);
    }
    private static ReferenceFieldLinkKind linkKind(String id) {
        for (ReferenceFieldLinkKind kind : ReferenceFieldLinkKind.values()) if (kind.id().equals(id)) return kind;
        throw new IllegalArgumentException("unknown field link kind " + id);
    }
    private static Map<Integer, List<String>> copyResidents(Map<Integer, List<String>> source) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        source.forEach((id, values) -> result.put(id, List.copyOf(values)));
        return result;
    }
}
