package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Source-shaped owner mapper for Python's {@code FieldWarfare}. */
final class ReferenceCanonicalStateField {
    private ReferenceCanonicalStateField() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        ReferenceFieldWarfare field = required.field();
        return typed("simulation.field.FieldWarfare", "fields", object(
                "posts", postMap(field.posts()), "links", linkMap(field.links()), "campaigns", campaignMap(field.campaigns()),
                "engagements", engagementMap(field.engagements()),
                "completed_campaigns", sequence("list", field.completedCampaigns().stream().map(ReferenceCanonicalStateField::campaign).toList()),
                "completed_engagements", sequence("list", field.completedEngagements().stream().map(ReferenceCanonicalStateField::engagement).toList()),
                "next_post_id", field.nextPostId(), "next_link_id", field.nextLinkId(), "next_campaign_id", field.nextCampaignId(),
                "next_engagement_id", field.nextEngagementId()));
    }

    private static Map<String, Object> postMap(Map<Integer, ReferenceFieldPost> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((id, value) -> pairs.add(pair(id, post(value))));
        return map(pairs);
    }

    private static Map<String, Object> linkMap(Map<Integer, ReferenceFieldLink> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((id, value) -> pairs.add(pair(id, link(value))));
        return map(pairs);
    }

    private static Map<String, Object> campaignMap(Map<Integer, ReferenceFieldCampaign> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((id, value) -> pairs.add(pair(id, campaign(value))));
        return map(pairs);
    }

    private static Map<String, Object> engagementMap(Map<Integer, ReferenceFieldEngagement> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((id, value) -> pairs.add(pair(id, engagement(value))));
        return map(pairs);
    }

    private static Map<String, Object> post(ReferenceFieldPost value) {
        return typed("simulation.field.FieldPost", "fields", object(
                "id", value.id(), "kind", enumValue("simulation.field.FieldPostKind", value.kind().id()), "x", value.x(), "y", value.y(),
                "campaign_id", value.campaignId(), "leader_id", value.leaderId(), "contributors", intSet(value.contributors()),
                "integrity", value.integrity(), "build_days_remaining", value.buildDaysRemaining(),
                "status", enumValue("simulation.field.FieldPostStatus", value.status().id()), "modules", moduleSet(value.modules()),
                "module_projects", moduleProjects(value.moduleProjects()), "stock", resourceMap(value.stock()),
                "garrison_by_settlement", doubleMap(value.garrisonBySettlement()), "wounded_by_settlement", doubleMap(value.woundedBySettlement()),
                "resident_ids_by_settlement", residentMap(value.residentIdsBySettlement()),
                "wounded_resident_ids_by_settlement", residentMap(value.woundedResidentIdsBySettlement()), "cargo_scale", value.cargoScale(),
                "isolation_days", value.isolationDays(), "last_supplied_day", value.lastSuppliedDay(), "created_day", value.createdDay(),
                "destroyed_day", value.destroyedDay()));
    }

    private static Map<String, Object> link(ReferenceFieldLink value) {
        return typed("simulation.field.FieldLink", "fields", object(
                "id", value.id(), "kind", enumValue("simulation.field.FieldLinkKind", value.kind().id()), "a_post_id", value.aPostId(),
                "b_post_id", value.bPostId(), "integrity", value.integrity(), "campaign_id", value.campaignId(), "status", value.status(),
                "build_days_remaining", value.buildDaysRemaining()));
    }

    private static Map<String, Object> campaign(ReferenceFieldCampaign value) {
        return typed("simulation.field.Campaign", "fields", object(
                "id", value.id(), "kind", enumValue("simulation.field.CampaignKind", value.kind().id()), "leader_id", value.leaderId(),
                "contributors", intSet(value.contributors()), "target_kind", value.targetKind(), "target_id", value.targetId(),
                "target_x", value.targetX(), "target_y", value.targetY(), "created_day", value.createdDay(),
                "phase", enumValue("simulation.field.CampaignPhase", value.phase().id()), "post_ids", intSet(value.postIds()),
                "engagement_ids", intSet(value.engagementIds()), "reason", value.reason(), "assessment_summary", value.assessmentSummary(),
                "readiness", value.readiness(), "expected_personnel", value.expectedPersonnel(), "expected_power", value.expectedPower(),
                "expected_defence", value.expectedDefence(), "risk", value.risk(), "cooldown_until", value.cooldownUntil(),
                "finished_day", value.finishedDay()));
    }

    private static Map<String, Object> engagement(ReferenceFieldEngagement value) {
        return typed("simulation.field.Engagement", "fields", object(
                "id", value.id(), "kind", enumValue("simulation.field.EngagementKind", value.kind().id()), "x", value.x(), "y", value.y(),
                "created_day", value.createdDay(), "campaign_id", value.campaignId(), "post_id", value.postId(),
                "operation_id", value.operationId(), "nest_id", value.nestId(), "swarm_id", value.swarmId(),
                "status", enumValue("simulation.field.EngagementStatus", value.status().id()), "days", value.days(),
                "attacker_power", value.attackerPower(), "defender_power", value.defenderPower(), "killed", value.killed(), "wounded", value.wounded()));
    }

    private static Map<String, Object> resourceMap(Map<ReferenceResource, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((resource, amount) -> pairs.add(pair(resource(resource), amount)));
        return map(pairs);
    }

    private static Map<String, Object> moduleProjects(Map<ReferenceFieldModuleKind, Integer> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((module, days) -> pairs.add(pair(module(module), days)));
        return map(pairs);
    }

    private static Map<String, Object> doubleMap(Map<Integer, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((key, value) -> pairs.add(pair(key, value)));
        return map(pairs);
    }

    private static Map<String, Object> residentMap(Map<Integer, List<String>> values) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((settlementId, residentIds) -> pairs.add(pair(settlementId, sequence("tuple", residentIds))));
        return map(pairs);
    }

    private static Map<String, Object> intSet(Set<Integer> values) { return sequence("set", values.stream().sorted().toList()); }
    private static Map<String, Object> moduleSet(Set<ReferenceFieldModuleKind> values) {
        return sequence("set", values.stream().map(ReferenceCanonicalStateField::module)
                .sorted(Comparator.comparing(ReferenceV2PublicSnapshot::canonicalJson)).toList());
    }
    private static Map<String, Object> resource(ReferenceResource value) { return enumValue("simulation.economy.Resource", value.name().toLowerCase(Locale.ROOT)); }
    private static Map<String, Object> module(ReferenceFieldModuleKind value) { return enumValue("simulation.field.FieldModuleKind", value.id()); }
    private static Map<String, Object> enumValue(String type, String value) { return object("$enum", type, "value", value); }
    private static Map<String, Object> typed(String type, String fieldName, Map<String, Object> values) { return object("$type", type, fieldName, values); }
    private static Map<String, Object> sequence(String kind, List<?> items) { return object("$sequence", kind, "items", List.copyOf(items)); }

    private static Map<String, Object> map(List<List<Object>> pairs) {
        pairs.sort(Comparator.comparing(pair -> ReferenceV2PublicSnapshot.canonicalJson(pair.getFirst())));
        return object("$map", List.copyOf(pairs));
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
