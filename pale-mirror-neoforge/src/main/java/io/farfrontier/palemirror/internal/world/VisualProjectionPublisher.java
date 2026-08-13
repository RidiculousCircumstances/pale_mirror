package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.api.JourneyProjection;
import io.farfrontier.palemirror.api.JourneyResidentLeaseView;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualStateProjection;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.PopulationDisposition;
import net.minecraft.server.MinecraftServer;

/** Read-only canonical projection. It grants no mutation authority to the visual provider. */
public final class VisualProjectionPublisher {
    private static final java.util.Map<MinecraftServer, java.util.Map<String, Long>> PUBLISHED =
            new java.util.IdentityHashMap<>();

    private VisualProjectionPublisher() { }

    public static void publish(MinecraftServer server, PaleMirrorSavedData data) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider == null) return;
        for (var region : data.worldState().livingRegions().stream()
                .sorted(java.util.Comparator.comparing(value -> value.id())).toList()) {
            var facility = data.worldState().facility(region.primaryFacilityId()).orElse(null);
            var community = data.worldState().community(region.communityId()).orElse(null);
            var place = data.worldState().place(region.placeId()).orElse(null);
            var economy = data.worldState().economy(region.communityId()).orElse(null);
            var development = data.worldState().settlementDevelopment(region.communityId()).orElse(null);
            var alternateDispatch = data.worldState().site(RegionBindings.fromRegionId(region.id())
                    .alternateDispatchSiteId()).orElse(null);
            if (facility == null || community == null || place == null || economy == null) continue;
            var iron = economy.require(ResourceKind.IRON);
            long revision = revision(facility.desiredRevision(), facility.status().name(),
                    place.structuralIntegrity().name(), iron.availability().name(), community.crisisState().name(),
                    development == null ? "NONE" : Integer.toString(development.prosperity()),
                    facility.threatTier().name(), alternateDispatch == null ? "UNKNOWN"
                            : alternateDispatch.operationalState().name());
            String projectionKey = "visual:" + facility.id().value();
            if (changed(server, projectionKey, revision)) provider.applyProjection(server.overworld(),
                    new VisualStateProjection(facility.id().value(), facility.desiredRevision(), revision,
                            facility.status().name(), place.structuralIntegrity().name(), iron.availability().name(),
                            community.crisisState().name(), development == null ? "NONE"
                            : Integer.toString(development.prosperity()), facility.threatTier().name(),
                            alternateDispatch == null ? "UNKNOWN" : alternateDispatch.operationalState().name()));
        }
        publishJourneys(server, data, provider);
    }

    private static void publishJourneys(MinecraftServer server, PaleMirrorSavedData data,
                                        io.farfrontier.palemirror.api.VisualProvider provider) {
        var seeds = provider.discoverAuthoredRegions(server.overworld()).stream()
                .collect(java.util.stream.Collectors.toMap(value -> value.planId(), value -> value));
        for (var journey : data.worldState().journeys().stream()
                .sorted(java.util.Comparator.comparing(value -> value.id())).toList()) {
            var group = data.worldState().populationGroup(journey.subjectGroupId()).orElse(null);
            var path = data.worldState().worldPath(journey.pathId()).orElse(null);
            var region = group == null ? null : data.worldState().livingRegions().stream()
                    .filter(value -> value.communityId().equals(group.communityId())).findFirst().orElse(null);
            var seed = region == null ? null : seeds.get(region.id());
            if (group == null || path == null || region == null || seed == null) continue;
            var livingRoster = livingRoster(seed, group, data.residentIdentities());
            int limit = PaleMirrorServerConfig.effectiveResidentLimit(livingRoster.size());
            boolean active = journey.id().equals(group.journeyId()) || group.disposition() == PopulationDisposition.RESETTLED
                    && journey.destinationSiteId().equals(group.hostSiteId());
            boolean restoreAtOrigin = group.disposition() == PopulationDisposition.RESIDENT;
            if (!active && data.residentJourneyLeases().releaseJourney(journey.id())) data.setDirty();
            var selected = livingRoster.stream().limit(limit);
            var leases = (active ? selected.map(resident -> {
                        boolean missing = data.residentJourneyLeases().lease(resident.residentId()) == null;
                        var lease = data.residentJourneyLeases().acquire(resident.residentId(), journey.id());
                        if (missing) data.setDirty();
                        return lease;
                    }) : selected.map(resident -> data.residentJourneyLeases().lease(resident.residentId()))
                            .filter(java.util.Objects::nonNull).filter(lease -> lease.journeyId().equals(journey.id())))
                    .map(lease ->
                    new JourneyResidentLeaseView(lease.residentId(), lease.phase().name(),
                            lease.checkpointIndex(), lease.revision())).toList();
            if (leases.isEmpty() && !active && !restoreAtOrigin) continue;
            var points = path.nodes().stream().map(node -> new VisualPoint(node.x(), node.y(), node.z())).toList();
            long revision = revision(journey.state().name(), Double.doubleToLongBits(journey.progress()),
                    journey.checkpointIndex(), limit, restoreAtOrigin, leases.stream()
                            .mapToLong(JourneyResidentLeaseView::revision).sum(), group.disposition().name());
            String projectionKey = "journey:" + journey.id();
            if (changed(server, projectionKey, revision)) provider.applyJourneyProjection(server.overworld(),
                    new JourneyProjection(journey.id(), region.id(), group.id(), revision, journey.state().name(),
                            journey.progress(), journey.checkpointIndex(), limit,
                            PaleMirrorServerConfig.JOURNEY_SPAWN_BUDGET.get(), restoreAtOrigin, points,
                            active ? seed.residents().stream().map(value -> value.residentId()).toList() : java.util.List.of(),
                            restoreAtOrigin ? livingRoster.stream().map(value -> value.residentId()).toList() : java.util.List.of(),
                            leases));
        }
    }

    public static void clear(MinecraftServer server) {
        synchronized (PUBLISHED) { PUBLISHED.remove(server); }
    }

    private static boolean changed(MinecraftServer server, String key, long revision) {
        synchronized (PUBLISHED) {
            return !java.util.Objects.equals(PUBLISHED.computeIfAbsent(server, ignored -> new java.util.HashMap<>())
                    .put(key, revision), revision);
        }
    }

    private static long revision(Object... values) {
        long hash = 0xcbf29ce484222325L;
        for (Object value : values) {
            String text = String.valueOf(value);
            for (int index = 0; index < text.length(); index++) {
                hash ^= text.charAt(index);
                hash *= 0x100000001b3L;
            }
            hash ^= 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static java.util.List<io.farfrontier.palemirror.api.ResidentSeed> livingRoster(
            io.farfrontier.palemirror.api.AuthoredRegionSeed seed,
            io.farfrontier.palemirror.domain.PopulationGroup group, ResidentIdentityLedger identities) {
        java.util.ArrayList<io.farfrontier.palemirror.api.ResidentSeed> result = new java.util.ArrayList<>();
        for (var cohort : io.farfrontier.palemirror.domain.SettlementCohort.values()) {
            int required = group.cohorts().getOrDefault(cohort, 0);
            seed.residents().stream().filter(value -> value.cohort().equals(cohort.name()))
                    .filter(value -> !identities.retired(value.residentId())).limit(required).forEach(result::add);
            long available = result.stream().filter(value -> value.cohort().equals(cohort.name())).count();
            if (available != required) throw new IllegalStateException("Canonical cohort " + cohort + " requires "
                    + required + " stable identities but authored roster provides " + available);
        }
        return java.util.List.copyOf(result);
    }
}
