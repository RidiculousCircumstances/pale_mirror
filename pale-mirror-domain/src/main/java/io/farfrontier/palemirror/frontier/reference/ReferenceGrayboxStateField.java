package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict all-or-nothing hydration of FieldWarfare's source-owned graph. */
final class ReferenceGrayboxStateField {
    private ReferenceGrayboxStateField() { }

    static State read(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.field.FieldWarfare", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "field warfare", "posts", "links", "campaigns", "engagements", "completed_campaigns",
                "completed_engagements", "next_post_id", "next_link_id", "next_campaign_id", "next_engagement_id");
        LinkedHashMap<Integer, ReferenceFieldCampaign> campaigns = campaigns(fields.get("campaigns"), "field campaigns");
        List<ReferenceFieldCampaign> completedCampaigns = completedCampaigns(fields.get("completed_campaigns"), campaigns);
        LinkedHashMap<Integer, ReferenceFieldPost> posts = posts(fields.get("posts"), campaigns);
        LinkedHashMap<Integer, ReferenceFieldLink> links = links(fields.get("links"), posts, campaigns);
        LinkedHashMap<Integer, ReferenceFieldEngagement> engagements = engagements(fields.get("engagements"), campaigns, posts);
        List<ReferenceFieldEngagement> completedEngagements = engagementList(fields.get("completed_engagements"), "completed field engagements");
        int nextPostId = ReferenceGrayboxStateReader.integer(fields.get("next_post_id"), "next field post id");
        int nextLinkId = ReferenceGrayboxStateReader.integer(fields.get("next_link_id"), "next field link id");
        int nextCampaignId = ReferenceGrayboxStateReader.integer(fields.get("next_campaign_id"), "next field campaign id");
        int nextEngagementId = ReferenceGrayboxStateReader.integer(fields.get("next_engagement_id"), "next field engagement id");
        checkSequence(posts.keySet(), nextPostId, "field post"); checkSequence(links.keySet(), nextLinkId, "field link");
        checkSequence(campaigns.keySet(), nextCampaignId, "field campaign");
        checkSequence(ids(engagements, completedEngagements), nextEngagementId, "field engagement");
        validateGraph(posts, campaigns, engagements, completedEngagements);
        return new State(posts, links, campaigns, engagements, completedCampaigns, completedEngagements, nextPostId, nextLinkId, nextCampaignId, nextEngagementId);
    }

    private static LinkedHashMap<Integer, ReferenceFieldPost> posts(Object encoded, Map<Integer, ReferenceFieldCampaign> campaigns) {
        LinkedHashMap<Integer, ReferenceFieldPost> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "field posts")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "field post key");
            ReferenceFieldPost post = post(entry.value());
            if (id != post.id() || !campaigns.containsKey(post.campaignId()) || result.putIfAbsent(id, post) != null) throw new IllegalArgumentException("field post is invalid");
        }
        return result;
    }

    private static ReferenceFieldPost post(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.field.FieldPost", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "field post", "id", "kind", "x", "y", "campaign_id", "leader_id", "contributors", "integrity",
                "build_days_remaining", "status", "modules", "module_projects", "stock", "garrison_by_settlement", "wounded_by_settlement",
                "resident_ids_by_settlement", "wounded_resident_ids_by_settlement", "cargo_scale", "isolation_days", "last_supplied_day", "created_day", "destroyed_day");
        ReferenceFieldPost post = new ReferenceFieldPost(ReferenceGrayboxStateReader.integer(fields.get("id"), "field post id"),
                postKind(fields.get("kind"), "field post kind"), ReferenceGrayboxStateReader.integer(fields.get("x"), "field post x"),
                ReferenceGrayboxStateReader.integer(fields.get("y"), "field post y"), ReferenceGrayboxStateReader.integer(fields.get("campaign_id"), "field post campaign"),
                ReferenceGrayboxStateReader.integer(fields.get("leader_id"), "field post leader"), integerSet(fields.get("contributors"), "field post contributors"),
                doubles(fields.get("garrison_by_settlement"), "field post garrison"), residents(fields.get("resident_ids_by_settlement"), "field post residents"),
                number(fields.get("cargo_scale"), "field post cargo scale"), ReferenceGrayboxStateReader.integer(fields.get("last_supplied_day"), "field post supplied day"),
                ReferenceGrayboxStateReader.integer(fields.get("created_day"), "field post created day"));
        if (post.id() < 1) throw new IllegalArgumentException("field post id is invalid");
        post.integrity(number(fields.get("integrity"), "field post integrity")); post.buildDaysRemaining(ReferenceGrayboxStateReader.integer(fields.get("build_days_remaining"), "field post build days"));
        post.status(postStatus(fields.get("status"), "field post status")); post.mutableModules().addAll(modules(fields.get("modules"), "field post modules"));
        post.mutableModuleProjects().putAll(moduleProjects(fields.get("module_projects"), "field module projects"));
        EnumMap<ReferenceResource, Double> stock = resources(fields.get("stock"), "field post stock", true);
        for (ReferenceResource resource : ReferenceResource.values()) post.stock(resource, stock.get(resource));
        post.mutableWoundedBySettlement().putAll(doubles(fields.get("wounded_by_settlement"), "field post wounded"));
        post.mutableWoundedResidentIdsBySettlement().putAll(residents(fields.get("wounded_resident_ids_by_settlement"), "field post wounded residents"));
        post.isolationDays(ReferenceGrayboxStateReader.integer(fields.get("isolation_days"), "field post isolation"));
        post.destroyedDay(ReferenceGrayboxStateReader.nullableInteger(fields.get("destroyed_day"), "field post destruction"));
        return post;
    }

    private static LinkedHashMap<Integer, ReferenceFieldLink> links(Object encoded, Map<Integer, ReferenceFieldPost> posts,
                                                                      Map<Integer, ReferenceFieldCampaign> campaigns) {
        LinkedHashMap<Integer, ReferenceFieldLink> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "field links")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "field link key");
            Map<String, Object> fields = ReferenceGrayboxStateReader.typed(entry.value(), "simulation.field.FieldLink", "fields");
            ReferenceGrayboxStateReader.exactKeys(fields, "field link", "id", "kind", "a_post_id", "b_post_id", "integrity", "campaign_id", "status", "build_days_remaining");
            int a = ReferenceGrayboxStateReader.integer(fields.get("a_post_id"), "field link a"), b = ReferenceGrayboxStateReader.integer(fields.get("b_post_id"), "field link b");
            int campaign = ReferenceGrayboxStateReader.integer(fields.get("campaign_id"), "field link campaign");
            if (a == b || !posts.containsKey(a) || !posts.containsKey(b) || !campaigns.containsKey(campaign)) throw new IllegalArgumentException("field link owner is unknown");
            ReferenceFieldLink link = new ReferenceFieldLink(ReferenceGrayboxStateReader.integer(fields.get("id"), "field link id"),
                    linkKind(fields.get("kind"), "field link kind"), a, b, campaign);
            double integrity = number(fields.get("integrity"), "field link integrity");
            if (id != link.id() || id < 1 || integrity < 0.0d || result.putIfAbsent(id, link) != null) {
                throw new IllegalArgumentException("field link is invalid");
            }
            link.integrity(integrity);
            link.status(ReferenceGrayboxStateReader.string(fields.get("status"), "field link status"));
            link.buildDaysRemaining(ReferenceGrayboxStateReader.integer(fields.get("build_days_remaining"), "field link build days"));
        }
        return result;
    }

    private static LinkedHashMap<Integer, ReferenceFieldCampaign> campaigns(Object encoded, String label) {
        LinkedHashMap<Integer, ReferenceFieldCampaign> result = new LinkedHashMap<>();
        for (Object item : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceGrayboxStateReader.Entry entry = (ReferenceGrayboxStateReader.Entry) item;
            int id = ReferenceGrayboxStateReader.integer(entry.key(), label + " key"); ReferenceFieldCampaign campaign = campaign(entry.value());
            if (id != campaign.id() || id < 1 || result.putIfAbsent(id, campaign) != null) throw new IllegalArgumentException(label + " has invalid identity");
        }
        return result;
    }

    private static List<ReferenceFieldCampaign> completedCampaigns(
            Object encoded,
            Map<Integer, ReferenceFieldCampaign> campaigns
    ) {
        List<ReferenceFieldCampaign> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "completed field campaigns")) {
            ReferenceFieldCampaign snapshot = campaign(item);
            ReferenceFieldCampaign canonical = campaigns.get(snapshot.id());
            if (canonical == null || !sameCampaign(canonical, snapshot) || result.contains(canonical)) {
                throw new IllegalArgumentException("completed field campaign is not a canonical campaign");
            }
            result.add(canonical);
        }
        return List.copyOf(result);
    }

    private static ReferenceFieldCampaign campaign(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.field.Campaign", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "field campaign", "id", "kind", "leader_id", "contributors", "target_kind", "target_id", "target_x",
                "target_y", "created_day", "phase", "post_ids", "engagement_ids", "reason", "assessment_summary", "readiness", "expected_personnel",
                "expected_power", "expected_defence", "risk", "cooldown_until", "finished_day");
        ReferenceFieldCampaign campaign = new ReferenceFieldCampaign(ReferenceGrayboxStateReader.integer(fields.get("id"), "field campaign id"),
                campaignKind(fields.get("kind"), "field campaign kind"), ReferenceGrayboxStateReader.integer(fields.get("leader_id"), "field campaign leader"),
                integerSet(fields.get("contributors"), "field campaign contributors"), ReferenceGrayboxStateReader.string(fields.get("target_kind"), "field campaign target kind"),
                ReferenceGrayboxStateReader.nullableInteger(fields.get("target_id"), "field campaign target"), ReferenceGrayboxStateReader.integer(fields.get("target_x"), "field campaign x"),
                ReferenceGrayboxStateReader.integer(fields.get("target_y"), "field campaign y"), ReferenceGrayboxStateReader.integer(fields.get("created_day"), "field campaign created day"),
                ReferenceGrayboxStateReader.string(fields.get("reason"), "field campaign reason"));
        campaign.phase(campaignPhase(fields.get("phase"), "field campaign phase")); campaign.mutablePostIds().addAll(integerSet(fields.get("post_ids"), "field campaign posts"));
        campaign.mutableEngagementIds().addAll(integerSet(fields.get("engagement_ids"), "field campaign engagements"));
        campaign.assessmentSummary(ReferenceGrayboxStateReader.string(fields.get("assessment_summary"), "field assessment")); campaign.readiness(number(fields.get("readiness"), "field readiness"));
        campaign.expectedPersonnel(number(fields.get("expected_personnel"), "field expected personnel")); campaign.expectedPower(number(fields.get("expected_power"), "field expected power"));
        campaign.expectedDefence(number(fields.get("expected_defence"), "field expected defence")); campaign.risk(number(fields.get("risk"), "field risk"));
        campaign.cooldownUntil(ReferenceGrayboxStateReader.integer(fields.get("cooldown_until"), "field campaign cooldown")); campaign.finishedDay(ReferenceGrayboxStateReader.nullableInteger(fields.get("finished_day"), "field campaign finish"));
        return campaign;
    }

    private static LinkedHashMap<Integer, ReferenceFieldEngagement> engagements(Object encoded, Map<Integer, ReferenceFieldCampaign> campaigns,
                                                                                  Map<Integer, ReferenceFieldPost> posts) {
        LinkedHashMap<Integer, ReferenceFieldEngagement> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "field engagements")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "field engagement key"); ReferenceFieldEngagement engagement = engagement(entry.value());
            if (id != engagement.id() || id < 1 || result.putIfAbsent(id, engagement) != null
                    || (engagement.campaignId() != null && !campaigns.containsKey(engagement.campaignId()))
                    || (engagement.postId() != null && !posts.containsKey(engagement.postId()))) throw new IllegalArgumentException("field engagement is invalid");
        }
        return result;
    }

    private static List<ReferenceFieldEngagement> engagementList(Object encoded, String label) {
        List<ReferenceFieldEngagement> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", label)) result.add(engagement(item));
        return List.copyOf(result);
    }

    private static ReferenceFieldEngagement engagement(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.field.Engagement", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "field engagement", "id", "kind", "x", "y", "created_day", "campaign_id", "post_id", "operation_id",
                "nest_id", "swarm_id", "status", "days", "attacker_power", "defender_power", "killed", "wounded");
        ReferenceFieldEngagement engagement = new ReferenceFieldEngagement(ReferenceGrayboxStateReader.integer(fields.get("id"), "field engagement id"),
                engagementKind(fields.get("kind"), "field engagement kind"), number(fields.get("x"), "field engagement x"), number(fields.get("y"), "field engagement y"),
                ReferenceGrayboxStateReader.integer(fields.get("created_day"), "field engagement created day"), ReferenceGrayboxStateReader.nullableInteger(fields.get("campaign_id"), "field engagement campaign"),
                ReferenceGrayboxStateReader.nullableInteger(fields.get("post_id"), "field engagement post"), ReferenceGrayboxStateReader.nullableInteger(fields.get("operation_id"), "field engagement operation"),
                ReferenceGrayboxStateReader.nullableInteger(fields.get("nest_id"), "field engagement nest"), ReferenceGrayboxStateReader.nullableInteger(fields.get("swarm_id"), "field engagement swarm"));
        engagement.status(engagementStatus(fields.get("status"), "field engagement status")); engagement.days(ReferenceGrayboxStateReader.integer(fields.get("days"), "field engagement days"));
        engagement.attackerPower(number(fields.get("attacker_power"), "field attacker power")); engagement.defenderPower(number(fields.get("defender_power"), "field defender power"));
        engagement.killed(number(fields.get("killed"), "field killed")); engagement.wounded(number(fields.get("wounded"), "field wounded"));
        return engagement;
    }

    private static EnumMap<ReferenceResource, Double> resources(Object encoded, String label, boolean complete) {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceResource resource = resource(entry.key(), label + " resource");
            if (result.putIfAbsent(resource, number(entry.value(), label + " amount")) != null) throw new IllegalArgumentException(label + " has duplicate resource");
        }
        if (complete && result.size() != ReferenceResource.values().length) throw new IllegalArgumentException(label + " lacks a resource");
        return result;
    }

    private static LinkedHashMap<ReferenceFieldModuleKind, Integer> moduleProjects(Object encoded, String label) {
        LinkedHashMap<ReferenceFieldModuleKind, Integer> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceFieldModuleKind kind = module(entry.key(), label + " kind");
            if (result.putIfAbsent(kind, ReferenceGrayboxStateReader.integer(entry.value(), label + " days")) != null) throw new IllegalArgumentException(label + " has duplicate module");
        }
        return result;
    }

    private static Set<ReferenceFieldModuleKind> modules(Object encoded, String label) {
        LinkedHashSet<ReferenceFieldModuleKind> result = new LinkedHashSet<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "set", label)) if (!result.add(module(item, label + " module"))) throw new IllegalArgumentException(label + " has duplicate module");
        return result;
    }

    private static LinkedHashMap<Integer, Double> doubles(Object encoded, String label) {
        LinkedHashMap<Integer, Double> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement");
            if (result.putIfAbsent(id, number(entry.value(), label + " value")) != null) throw new IllegalArgumentException(label + " has duplicate settlement");
        }
        return result;
    }

    private static LinkedHashMap<Integer, List<String>> residents(Object encoded, String label) {
        LinkedHashMap<Integer, List<String>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement"); List<String> values = new ArrayList<>();
            for (Object value : ReferenceGrayboxStateReader.sequence(entry.value(), "tuple", label + " ids")) values.add(ReferenceGrayboxStateReader.string(value, label + " resident"));
            if (values.stream().distinct().count() != values.size() || result.putIfAbsent(id, List.copyOf(values)) != null) throw new IllegalArgumentException(label + " has duplicate identity");
        }
        return result;
    }

    private static Set<Integer> integerSet(Object encoded, String label) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, "set", label)) if (!result.add(ReferenceGrayboxStateReader.integer(value, label + " value"))) throw new IllegalArgumentException(label + " has duplicate value");
        return result;
    }

    private static Set<Integer> ids(Map<Integer, ?> active, List<? extends Object> completed) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>(active.keySet());
        for (Object value : completed) {
            int id = value instanceof ReferenceFieldCampaign campaign ? campaign.id() : ((ReferenceFieldEngagement) value).id();
            if (!result.add(id)) throw new IllegalArgumentException("field terminal identity duplicates an active identity");
        }
        return result;
    }

    private static void checkSequence(Set<Integer> ids, int next, String label) {
        if (next < 1 || ids.stream().anyMatch(id -> id < 1 || id >= next)) throw new IllegalArgumentException(label + " identity sequence is invalid");
    }

    private static void validateGraph(
            Map<Integer, ReferenceFieldPost> posts,
            Map<Integer, ReferenceFieldCampaign> campaigns,
            Map<Integer, ReferenceFieldEngagement> engagements,
            List<ReferenceFieldEngagement> completedEngagements
    ) {
        for (ReferenceFieldCampaign campaign : campaigns.values()) {
            Set<Integer> postIds = new LinkedHashSet<>();
            posts.values().stream().filter(post -> post.campaignId() == campaign.id()).map(ReferenceFieldPost::id).forEach(postIds::add);
            if (!campaign.postIds().equals(postIds)) throw new IllegalArgumentException("campaign posts do not match field posts");
            Set<Integer> engagementIds = new LinkedHashSet<>();
            addCampaignEngagements(engagementIds, engagements.values(), campaign.id());
            addCampaignEngagements(engagementIds, completedEngagements, campaign.id());
            if (!campaign.engagementIds().equals(engagementIds)) throw new IllegalArgumentException("campaign engagements do not match field engagements");
        }
    }

    private static void addCampaignEngagements(
            Set<Integer> ids,
            Iterable<ReferenceFieldEngagement> engagements,
            int campaignId
    ) {
        for (ReferenceFieldEngagement engagement : engagements) {
            if (engagement.campaignId() != null && engagement.campaignId() == campaignId) ids.add(engagement.id());
        }
    }

    private static boolean sameCampaign(ReferenceFieldCampaign left, ReferenceFieldCampaign right) {
        return left.id() == right.id() && left.kind() == right.kind() && left.leaderId() == right.leaderId()
                && left.contributors().equals(right.contributors()) && left.targetKind().equals(right.targetKind())
                && java.util.Objects.equals(left.targetId(), right.targetId()) && left.targetX() == right.targetX()
                && left.targetY() == right.targetY() && left.createdDay() == right.createdDay() && left.phase() == right.phase()
                && left.postIds().equals(right.postIds()) && left.engagementIds().equals(right.engagementIds())
                && left.reason().equals(right.reason()) && left.assessmentSummary().equals(right.assessmentSummary())
                && sameNumber(left.readiness(), right.readiness()) && sameNumber(left.expectedPersonnel(), right.expectedPersonnel())
                && sameNumber(left.expectedPower(), right.expectedPower()) && sameNumber(left.expectedDefence(), right.expectedDefence())
                && sameNumber(left.risk(), right.risk()) && left.cooldownUntil() == right.cooldownUntil()
                && java.util.Objects.equals(left.finishedDay(), right.finishedDay());
    }

    private static boolean sameNumber(double left, double right) {
        return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
    }

    private static double number(Object value, String label) { return ReferenceGrayboxStateReader.number(value, label); }
    private static ReferenceResource resource(Object value, String label) { return byName(ReferenceGrayboxStateReader.enumValue(value, "simulation.economy.Resource", label), ReferenceResource.class, label); }
    private static ReferenceFieldPostKind postKind(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.field.FieldPostKind", label), ReferenceFieldPostKind.values(), label); }
    private static ReferenceFieldLinkKind linkKind(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.field.FieldLinkKind", label), ReferenceFieldLinkKind.values(), label); }
    private static ReferenceFieldModuleKind module(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.field.FieldModuleKind", label), ReferenceFieldModuleKind.values(), label); }
    private static ReferenceFieldPostStatus postStatus(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.field.FieldPostStatus", label), ReferenceFieldPostStatus.values(), label); }
    private static ReferenceCampaignKind campaignKind(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.field.CampaignKind", label), ReferenceCampaignKind.values(), label); }
    private static ReferenceCampaignPhase campaignPhase(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.field.CampaignPhase", label), ReferenceCampaignPhase.values(), label); }
    private static ReferenceEngagementKind engagementKind(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.field.EngagementKind", label), ReferenceEngagementKind.values(), label); }
    private static ReferenceEngagementStatus engagementStatus(Object value, String label) { return byId(ReferenceGrayboxStateReader.enumValue(value, "simulation.field.EngagementStatus", label), ReferenceEngagementStatus.values(), label); }

    private static ReferenceResource byName(String value, Class<ReferenceResource> type, String label) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException(label + " is unsupported", error); }
    }

    private static <T extends Enum<T>> T byId(String value, T[] values, String label) {
        for (T candidate : values) if (id(candidate).equals(value)) return candidate;
        throw new IllegalArgumentException(label + " is unsupported");
    }

    private static String id(Enum<?> value) {
        return switch (value) {
            case ReferenceFieldPostKind typed -> typed.id(); case ReferenceFieldLinkKind typed -> typed.id(); case ReferenceFieldModuleKind typed -> typed.id();
            case ReferenceFieldPostStatus typed -> typed.id(); case ReferenceCampaignKind typed -> typed.id(); case ReferenceCampaignPhase typed -> typed.id();
            case ReferenceEngagementKind typed -> typed.id(); case ReferenceEngagementStatus typed -> typed.id();
            default -> throw new IllegalArgumentException("unsupported field enum");
        };
    }

    record State(Map<Integer, ReferenceFieldPost> posts, Map<Integer, ReferenceFieldLink> links, Map<Integer, ReferenceFieldCampaign> campaigns,
                 Map<Integer, ReferenceFieldEngagement> engagements, List<ReferenceFieldCampaign> completedCampaigns,
                 List<ReferenceFieldEngagement> completedEngagements, int nextPostId, int nextLinkId, int nextCampaignId, int nextEngagementId) {
        State {
            posts = Collections.unmodifiableMap(new LinkedHashMap<>(posts)); links = Collections.unmodifiableMap(new LinkedHashMap<>(links));
            campaigns = Collections.unmodifiableMap(new LinkedHashMap<>(campaigns)); engagements = Collections.unmodifiableMap(new LinkedHashMap<>(engagements));
            completedCampaigns = List.copyOf(completedCampaigns); completedEngagements = List.copyOf(completedEngagements);
        }

        void applyTo(ReferenceFieldWarfare field) {
            field.mutablePosts().clear(); field.mutablePosts().putAll(posts); field.mutableLinks().clear(); field.mutableLinks().putAll(links);
            field.mutableCampaigns().clear(); field.mutableCampaigns().putAll(campaigns); field.mutableEngagements().clear(); field.mutableEngagements().putAll(engagements);
            field.mutableCompletedCampaigns().clear(); field.mutableCompletedCampaigns().addAll(completedCampaigns);
            field.mutableCompletedEngagements().clear(); field.mutableCompletedEngagements().addAll(completedEngagements);
            field.nextPostId = nextPostId; field.nextLinkId = nextLinkId; field.nextCampaignId = nextCampaignId; field.nextEngagementId = nextEngagementId;
        }
    }
}
