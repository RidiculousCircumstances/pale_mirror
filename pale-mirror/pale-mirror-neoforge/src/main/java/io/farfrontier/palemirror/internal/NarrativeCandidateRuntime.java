package io.farfrontier.palemirror.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.DevelopmentOpportunityEligibility;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.NarrativeCandidate;
import io.farfrontier.palemirror.domain.NarrativeCandidateType;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.domain.ScenarioDefinitionRef;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.content.EncounterDefinitions;
import io.farfrontier.palemirror.internal.content.ScenarioDefinition;
import io.farfrontier.palemirror.internal.content.ScenarioDefinitions;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.PlayerAudienceEligibility;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** NeoForge-side candidate assembly. It supplies facts to the pure Narrator but never mutates world facts itself. */
final class NarrativeCandidateRuntime {
    private final MinecraftServer server;
    private final PaleMirrorSavedData data;
    private final DomainCommandExecutor commands;
    private final Function<ServerPlayer, StoryAudienceId> audiences;

    NarrativeCandidateRuntime(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands,
                              Function<ServerPlayer, StoryAudienceId> audiences) {
        this.server = server;
        this.data = data;
        this.commands = commands;
        this.audiences = audiences;
    }

    void handleDomainEvents(List<DomainEvent> events) {
        retireIneligibleDevelopmentOffers();
        Map<StoryAudienceId, List<NarrativeCandidate>> candidates = new LinkedHashMap<>();
        events.forEach(event -> {
            if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.MINE_INFECTED) {
                boolean regional = data.worldState().livingRegions().stream()
                        .anyMatch(region -> region.primaryFacilityId().equals(event.subject()));
                if (!regional && data.testMines().containsKey(event.subject())) append(candidates, event,
                        data.testMines().get(event.subject()).primaryAudience(), event.subject(), NarrativeCandidateType.THREAT,
                        ScenarioArchetype.INVESTIGATION_RECOVERY, 88, 72, 90, 80);
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED) {
                regionFor(event.subject()).ifPresent(region -> appendForKnownAudiences(candidates, event, region,
                        NarrativeCandidateType.SUPPLY_CRISIS, ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS,
                        96, 94, 100, 74));
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED) {
                regionFor(event.subject()).filter(region -> opportunityStillPending(event)
                        && DevelopmentOpportunityEligibility.isRecoveryOpportunity(data.worldState(), event))
                        .ifPresent(region -> appendForKnownAudiences(candidates, event, region,
                        NarrativeCandidateType.DEVELOPMENT_OPPORTUNITY, ScenarioArchetype.DEVELOPMENT_OPPORTUNITY,
                        50, 82, 88, 95));
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_RETURN_PLANNED) {
                regionFor(event.subject()).filter(region -> opportunityStillPending(event))
                        .ifPresent(region -> appendForKnownAudiences(candidates, event, region,
                        NarrativeCandidateType.RESETTLEMENT_OPPORTUNITY, ScenarioArchetype.RESETTLEMENT_OPPORTUNITY,
                        58, 86, 96, 90));
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.REGION_DISCOVERED
                    || event.type() == io.farfrontier.palemirror.domain.DomainEventType.REGIONAL_FEATURE_DISCOVERED) {
                appendKnownCrisis(candidates, event.subject());
            }
        });
        offer(candidates);
    }

    /** Re-derives delayed candidates until a cooldown is over or a decision is durable. */
    void offerPendingOpportunities() {
        retireIneligibleDevelopmentOffers();
        Map<StoryAudienceId, List<NarrativeCandidate>> candidates = new LinkedHashMap<>();
        data.worldState().history().stream()
                .filter(event -> event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED
                        || event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED
                        || event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_RETURN_PLANNED)
                .filter(event -> event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED
                        || opportunityStillPending(event))
                .forEach(event -> regionFor(event.subject()).ifPresent(region -> {
                    if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED) {
                        if (!crisisActive(region)) return;
                        appendForKnownAudiences(candidates, event, region, NarrativeCandidateType.SUPPLY_CRISIS,
                                ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS, 96, 94, 100, 74);
                    } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED) {
                        if (!DevelopmentOpportunityEligibility.isRecoveryOpportunity(data.worldState(), event)) return;
                        appendForKnownAudiences(candidates, event, region, NarrativeCandidateType.DEVELOPMENT_OPPORTUNITY,
                                ScenarioArchetype.DEVELOPMENT_OPPORTUNITY, 50, 82, 88, 95);
                    } else appendForKnownAudiences(candidates, event, region,
                            NarrativeCandidateType.RESETTLEMENT_OPPORTUNITY,
                            ScenarioArchetype.RESETTLEMENT_OPPORTUNITY, 58, 86, 96, 90);
                }));
        offer(candidates);
    }

    /** Performs startup reconciliation before autonomous development gets its first runtime turn. */
    void reconcileExistingOffers() {
        retireIneligibleDevelopmentOffers();
    }

    /**
     * Reconciles offers created by the former broad rule without touching the
     * valid settlement-owned DevelopmentIntent. Once the invalid presentation
     * is retired, ordinary prosperity continues through autonomous policy.
     */
    private void retireIneligibleDevelopmentOffers() {
        for (var scenario : data.worldState().scenarios().stream()
                .filter(value -> value.archetype() == ScenarioArchetype.DEVELOPMENT_OPPORTUNITY)
                .filter(value -> value.status() == io.farfrontier.palemirror.domain.ScenarioStatus.OFFERED)
                .toList()) {
            DomainEvent source = data.worldState().history().stream()
                    .filter(value -> value.eventId().equals(scenario.sourceEventId())).findFirst().orElse(null);
            if (source != null && DevelopmentOpportunityEligibility.isRecoveryOpportunity(
                    data.worldState(), source)) continue;
            if (!commands.execute(data.worldState(), new DomainCommand.CancelScenario(scenario.id(),
                    "Ordinary prosperity is autonomous; no recovered crisis qualifies this offer")).isEmpty()) {
                data.setDirty();
            }
        }
    }

    private boolean opportunityStillPending(DomainEvent event) {
        if (event.causationId() == null || event.causationId().isBlank()) return false;
        return data.worldState().developmentIntent(event.causationId())
                .map(intent -> intent.state() == io.farfrontier.palemirror.domain.DevelopmentIntentState.PLANNED)
                .orElse(false);
    }

    private void appendKnownCrisis(Map<StoryAudienceId, List<NarrativeCandidate>> candidates, WorldObjectId communityId) {
        data.worldState().history().stream()
                .filter(candidate -> candidate.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED)
                .filter(candidate -> candidate.subject().equals(communityId)).reduce((ignored, latest) -> latest)
                .ifPresent(event -> regionFor(communityId).ifPresent(region -> {
                    if (!crisisActive(region)) return;
                    appendForKnownAudiences(candidates, event, region, NarrativeCandidateType.SUPPLY_CRISIS,
                            ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS, 96, 94, 100, 74);
                }));
    }

    private void appendForKnownAudiences(Map<StoryAudienceId, List<NarrativeCandidate>> candidates,
                                         DomainEvent event, LivingRegionState region,
                                         NarrativeCandidateType type, ScenarioArchetype archetype,
                                         int urgency, int significance, int relevance, int novelty) {
        for (StoryAudienceId audience : data.worldState().audiencesKnowing(region.id(), KnownRegionalFeature.SETTLEMENT)) {
            append(candidates, event, audience, region.primaryFacilityId(), type, archetype,
                    urgency, significance, relevance, novelty);
        }
    }

    private boolean crisisActive(LivingRegionState region) {
        if (region.incidentResolvedAtStep() >= 0) return false;
        var iron = data.worldState().economy(region.communityId())
                .map(value -> value.require(io.farfrontier.palemirror.domain.ResourceKind.IRON)).orElse(null);
        if (iron == null || data.worldState().facility(region.primaryFacilityId())
                .map(value -> value.status() == io.farfrontier.palemirror.domain.FacilityStatus.OPERATIONAL)
                .orElse(false)) return false;
        boolean alternateSupplies = data.worldState().routeContract(region.alternateRouteId())
                .map(value -> value.transferableCapacity(data.worldState().simulationStep()) >= iron.effectiveConsumption())
                .orElse(false);
        boolean evacuated = data.worldState().populationGroups(region.communityId()).stream().allMatch(value ->
                value.disposition() == io.farfrontier.palemirror.domain.PopulationDisposition.DISPLACED
                        || value.disposition() == io.farfrontier.palemirror.domain.PopulationDisposition.RESETTLED);
        return !alternateSupplies && !evacuated;
    }

    private void append(Map<StoryAudienceId, List<NarrativeCandidate>> candidates, DomainEvent event,
                        StoryAudienceId audience, WorldObjectId sourceFacilityId, NarrativeCandidateType type,
                        ScenarioArchetype archetype, int urgency, int significance, int relevance, int novelty) {
        if (audience == null) return;
        InfectionSourceId source = data.worldState().facility(sourceFacilityId).map(FacilityState::infectionSource).orElse(null);
        if (source == null) return;
        ScenarioDefinition definition = ScenarioDefinitions.forSourceAndArchetype(source, archetype).stream().findFirst().orElse(null);
        if (definition == null) {
            if (!commands.execute(data.worldState(), new DomainCommand.NoScenario(event, audience,
                    "definition unavailable for " + archetype)).isEmpty()) data.setDirty();
            return;
        }
        if (!AdapterRegistry.supports(source, definition.capabilities())) {
            if (!commands.execute(data.worldState(), new DomainCommand.NoScenario(event, audience,
                    "required capability unavailable")).isEmpty()) data.setDirty();
            return;
        }
        candidates.computeIfAbsent(audience, ignored -> new ArrayList<>()).add(new NarrativeCandidate(event, audience, type,
                pinned(definition), urgency, significance, relevance, 100, novelty, distancePenalty(audience, sourceFacilityId)));
    }

    private void offer(Map<StoryAudienceId, List<NarrativeCandidate>> candidates) {
        candidates.forEach((audience, values) -> {
            if (!commands.execute(data.worldState(),
                    new DomainCommand.EvaluateNarrativeCandidates(audience, values)).isEmpty()) {
                data.setDirty();
            }
        });
    }

    private java.util.Optional<LivingRegionState> regionFor(WorldObjectId communityId) {
        return data.worldState().livingRegions().stream()
                .filter(region -> region.communityId().equals(communityId)).findFirst();
    }

    private ScenarioDefinitionRef pinned(ScenarioDefinition definition) {
        String profileId = definition.encounterProfileId();
        String profileVersion = profileId.isBlank() ? "" : EncounterDefinitions.current()
                .get(net.minecraft.resources.ResourceLocation.parse(profileId)) == null ? "unavailable"
                : Integer.toString(EncounterDefinitions.current().get(net.minecraft.resources.ResourceLocation.parse(profileId)).version());
        return new ScenarioDefinitionRef(definition.id().toString(), Integer.toString(definition.version()),
                definition.stages(), definition.capabilities().stream().map(Capability::name).sorted().toList(), definition.cooldownSteps(),
                profileId, profileVersion, definition.archetype());
    }

    private int distancePenalty(StoryAudienceId audience, WorldObjectId subject) {
        TargetLocation target = data.worldRegistry().find(subject)
                .map(value -> new TargetLocation(value.dimensionId(), value.anchor())).orElse(null);
        if (target == null) target = data.worldState().livingRegions().stream().filter(region -> region.communityId().equals(subject))
                .map(region -> data.campaignRegions().get(region.id())).filter(java.util.Objects::nonNull)
                .map(region -> new TargetLocation(region.dimensionId(), region.settlementAnchor())).findFirst().orElse(null);
        if (target == null) return 50;
        final TargetLocation location = target;
        int nearest = server.getPlayerList().getPlayers().stream().filter(PlayerAudienceEligibility::participates)
                .filter(player -> audiences.apply(player).equals(audience))
                .filter(player -> player.serverLevel().dimension().location().toString().equals(location.dimensionId()))
                .mapToInt(player -> (int) Math.min(100_000L, player.blockPosition().distManhattan(location.position())))
                .min().orElse(100_000);
        return nearest <= 256 ? 0 : nearest <= 1_024 ? 20 : nearest <= 4_096 ? 50 : 90;
    }

    private record TargetLocation(String dimensionId, net.minecraft.core.BlockPos position) { }
}
