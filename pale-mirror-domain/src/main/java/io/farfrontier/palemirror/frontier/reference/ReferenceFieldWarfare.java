package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Source-owned temporary human infrastructure from Python {@code FieldWarfare}.
 *
 * <p>This initial field cut owns campaign, post, module and link identities,
 * construction admission and the route/support derivations. Operation arrival,
 * casualties and daily field execution remain with the not-yet-ported
 * {@code operations.py} owner; no synthetic execution path is introduced.</p>
 */
public final class ReferenceFieldWarfare {
    private final LinkedHashMap<Integer, ReferenceFieldPost> posts = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceFieldLink> links = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceFieldCampaign> campaigns = new LinkedHashMap<>();
    private int nextPostId = 1;
    private int nextLinkId = 1;
    private int nextCampaignId = 1;

    public Map<Integer, ReferenceFieldPost> posts() { return immutableOrdered(posts); }
    public Map<Integer, ReferenceFieldLink> links() { return immutableOrdered(links); }
    public Map<Integer, ReferenceFieldCampaign> campaigns() { return immutableOrdered(campaigns); }
    public int nextPostId() { return nextPostId; }
    public int nextLinkId() { return nextLinkId; }
    public int nextCampaignId() { return nextCampaignId; }

    public List<ReferenceFieldPost> activePosts() {
        return posts.values().stream().filter(post -> post.status() == ReferenceFieldPostStatus.BUILDING
                || post.status() == ReferenceFieldPostStatus.ACTIVE || post.status() == ReferenceFieldPostStatus.ISOLATED).toList();
    }

    public List<ReferenceFieldCampaign> activeCampaignsFor(int settlementId) {
        return campaigns.values().stream().filter(campaign -> campaign.phase() != ReferenceCampaignPhase.COMPLETE
                && campaign.phase() != ReferenceCampaignPhase.FAILED && campaign.contributors().contains(settlementId)).toList();
    }

    public ReferenceFieldPost postAt(double x, double y) { return postAt(x, y, ReferenceFieldRules.POST_INTERACTION_RADIUS); }

    public ReferenceFieldPost postAt(double x, double y, double radius) {
        ReferenceFieldPost result = null;
        double best = Double.POSITIVE_INFINITY;
        for (ReferenceFieldPost post : activePosts()) {
            double distance = Math.hypot(post.x() - x, post.y() - y);
            if (distance <= radius && distance < best) {
                result = post;
                best = distance;
            }
        }
        return result;
    }

    public double movementMultiplier(double x, double y, double targetX, double targetY) {
        for (ReferenceFieldLink link : links.values()) {
            if (link.kind() != ReferenceFieldLinkKind.SUPPLY_CORRIDOR || !link.status().equals("active")) continue;
            ReferenceFieldPost a = posts.get(link.aPostId());
            ReferenceFieldPost b = posts.get(link.bPostId());
            if (a == null || b == null) continue;
            if (segmentDistance(x, y, a.x(), a.y(), b.x(), b.y()) <= 1.0d
                    && segmentDistance(targetX, targetY, a.x(), a.y(), b.x(), b.y()) <= 1.5d) {
                return ReferenceFieldRules.supplyCorridorSpeedMultiplier();
            }
        }
        return 1.0d;
    }

    public ReferenceFieldCampaign createCampaign(ReferenceWorld world, ReferenceCampaignKind kind, int leaderId,
                                                  Set<Integer> contributors, String targetKind, Integer targetId,
                                                  int targetX, int targetY, String reason) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceSettlement leader = required.settlements().get(leaderId);
        if (leader == null || !leader.alive()) return null;
        for (ReferenceFieldCampaign campaign : campaigns.values()) {
            if (campaign.targetKind().equals(targetKind) && Objects.equals(campaign.targetId(), targetId)
                    && campaign.finishedDay() != null && required.day() - campaign.finishedDay() < ReferenceFieldRules.CAMPAIGN_RETRY_DAYS) {
                return null;
            }
        }
        long activeWorld = campaigns.values().stream().filter(campaign -> campaign.phase() != ReferenceCampaignPhase.COMPLETE
                && campaign.phase() != ReferenceCampaignPhase.FAILED).count();
        if (activeWorld >= ReferenceFieldRules.MAX_ACTIVE_CAMPAIGNS_WORLD
                || activeCampaignsFor(leaderId).size() >= ReferenceFieldRules.MAX_CAMPAIGNS_PER_SETTLEMENT) return null;
        Set<Integer> coalition = new LinkedHashSet<>(Objects.requireNonNull(contributors, "contributors"));
        coalition.add(leaderId);
        ReferenceFieldCampaign campaign = new ReferenceFieldCampaign(nextCampaignId++, Objects.requireNonNull(kind, "kind"), leaderId,
                coalition, Objects.requireNonNull(targetKind, "targetKind"), targetId, targetX, targetY, required.day(),
                Objects.requireNonNull(reason, "reason"));
        campaigns.put(campaign.id(), campaign);
        required.marketWorld().event("D" + required.day() + ": " + leader.name() + " opened " + kind.id()
                + " campaign " + campaign.id() + ": " + reason);
        return campaign;
    }

    public boolean positionIsBuildable(ReferenceWorld world, int x, int y, ReferenceFieldPostKind kind) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        Objects.requireNonNull(kind, "kind");
        if (x < 1 || x >= required.config().width() - 1 || y < 1 || y >= required.config().height() - 1) return false;
        if (required.infection().infectionAt(x, y) > ReferenceFieldRules.POST_MAXIMUM_INFECTION) return false;
        return activePosts().stream().noneMatch(post -> Math.hypot(post.x() - x, post.y() - y) < ReferenceFieldRules.MINIMUM_POST_SPACING);
    }

    public ReferenceFieldPost startPost(ReferenceWorld world, int campaignId, ReferenceFieldPostKind kind, int x, int y,
                                        int leaderId, Set<Integer> contributors, Map<Integer, Double> garrisonBySettlement,
                                        Map<ReferenceResource, Double> cargo, Map<Integer, List<String>> residentIdsBySettlement) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!positionIsBuildable(required, x, y, kind)) return null;
        ReferenceFieldCampaign campaign = campaigns.get(campaignId);
        if (campaign == null) return null;
        ReferenceFieldPost post = new ReferenceFieldPost(nextPostId++, kind, x, y, campaignId, leaderId,
                Objects.requireNonNull(contributors, "contributors"), Objects.requireNonNull(garrisonBySettlement, "garrisonBySettlement"),
                Objects.requireNonNull(residentIdsBySettlement, "residentIdsBySettlement"), required.profile().personScale(),
                required.day(), required.day());
        Map<ReferenceResource, Double> stores = new LinkedHashMap<>();
        for (Map.Entry<ReferenceResource, Double> item : Objects.requireNonNull(cargo, "cargo").entrySet()) {
            if (item.getKey() != ReferenceResource.TIMBER && item.getKey() != ReferenceResource.ORE && item.getKey() != ReferenceResource.TOOLS) {
                stores.put(item.getKey(), item.getValue());
            }
        }
        post.receiveCargo(stores);
        posts.put(post.id(), post);
        campaign.mutablePostIds().add(post.id());
        campaign.phase(ReferenceCampaignPhase.ESTABLISH);
        required.marketWorld().event("D" + required.day() + ": campaign " + campaign.id() + " began building "
                + kind.id() + " post " + post.id() + " at " + x + "," + y);
        return post;
    }

    public boolean startModule(ReferenceWorld world, int postId, ReferenceFieldModuleKind module) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceFieldPost post = posts.get(postId);
        if (post == null || (post.status() != ReferenceFieldPostStatus.ACTIVE && post.status() != ReferenceFieldPostStatus.ISOLATED)) return false;
        if (post.hasModule(module) || post.mutableModuleProjects().containsKey(module)) return false;
        if (post.modules().size() + post.moduleProjects().size() >= ReferenceFieldRules.moduleSlots(post.kind())) return false;
        post.mutableModuleProjects().put(Objects.requireNonNull(module, "module"), ReferenceFieldRules.moduleBuildDays(module));
        required.marketWorld().event("D" + required.day() + ": field post " + post.id() + " began " + module.id() + " module");
        return true;
    }

    public ReferenceFieldLink startLink(ReferenceWorld world, int campaignId, ReferenceFieldLinkKind kind, int aPostId, int bPostId) {
        Objects.requireNonNull(world, "world");
        if (aPostId == bPostId || !posts.containsKey(aPostId) || !posts.containsKey(bPostId)) return null;
        for (ReferenceFieldLink link : links.values()) {
            if (link.aPostId() == aPostId && link.bPostId() == bPostId && link.kind() == kind) return null;
        }
        ReferenceFieldPost a = posts.get(aPostId);
        ReferenceFieldPost b = posts.get(bPostId);
        if (Math.hypot(a.x() - b.x(), a.y() - b.y()) > ReferenceFieldRules.linkMaximumLength(kind)) return null;
        ReferenceFieldLink link = new ReferenceFieldLink(nextLinkId++, Objects.requireNonNull(kind, "kind"), aPostId, bPostId, campaignId);
        links.put(link.id(), link);
        return link;
    }

    public double supportFor(int settlementId, double x, double y) {
        double support = 0.0d;
        for (ReferenceFieldPost post : activePosts()) {
            if ((post.kind() == ReferenceFieldPostKind.CHECKPOINT || post.kind() == ReferenceFieldPostKind.STRONGPOINT
                    || post.kind() == ReferenceFieldPostKind.FORWARD_BASE) && post.contributors().contains(settlementId)
                    && Math.hypot(post.x() - x, post.y() - y) <= ReferenceFieldRules.SETTLEMENT_SUPPORT_RADIUS) {
                support += postPower(post);
            }
        }
        return support;
    }

    public double[] routeControl(ReferenceWorld world, int aId, int bId) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceSettlement a = required.settlements().get(aId);
        ReferenceSettlement b = required.settlements().get(bId);
        if (a == null || b == null) throw new IllegalArgumentException("route endpoint is missing");
        for (ReferenceFieldPost post : activePosts()) {
            if (post.kind() == ReferenceFieldPostKind.CHECKPOINT && post.status() == ReferenceFieldPostStatus.ACTIVE
                    && segmentDistance(post.x(), post.y(), a.x(), a.y(), b.x(), b.y()) <= ReferenceFieldRules.CHECKPOINT_ROUTE_RADIUS) {
                return new double[] {ReferenceFieldRules.CHECKPOINT_INFECTION_MULTIPLIER, ReferenceFieldRules.CHECKPOINT_CAPACITY_MULTIPLIER};
            }
        }
        return new double[] {1.0d, 1.0d};
    }

    public boolean screensRefugees(ReferenceWorld world, int originId, int destinationId) {
        return routeControl(world, originId, destinationId)[0] < 1.0d;
    }

    double postPower(ReferenceFieldPost post) {
        double stockFactor = Math.min(1.0d, post.stock(ReferenceResource.AMMO)
                / Math.max(1.0d, post.garrison() * ReferenceFieldRules.AMMO_PER_GARRISON));
        double fortification = post.hasModule(ReferenceFieldModuleKind.FORTIFICATION)
                ? ReferenceFieldRules.fortificationPower() / post.cargoScale() : 0.0d;
        double isolation = Math.max(0.2d, 1.0d - post.isolationDays() * ReferenceFieldRules.ISOLATION_POWER_LOSS);
        return (post.garrison() * ReferenceFieldRules.garrisonPower(post.kind()) * stockFactor + fortification) * isolation;
    }

    private static double segmentDistance(double x, double y, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq <= 1.0e-9d) return Math.hypot(x - ax, y - ay);
        double ratio = Math.max(0.0d, Math.min(1.0d, ((x - ax) * dx + (y - ay) * dy) / lengthSq));
        return Math.hypot(x - (ax + dx * ratio), y - (ay + dy * ratio));
    }

    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
