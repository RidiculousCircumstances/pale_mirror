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
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.InfectionSourceId;
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
        Map<StoryAudienceId, List<NarrativeCandidate>> candidates = new LinkedHashMap<>();
        events.forEach(event -> {
            if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.MINE_INFECTED) {
                boolean regional = data.worldState().livingRegions().stream()
                        .anyMatch(region -> region.primaryFacilityId().equals(event.subject()));
                if (!regional && data.testMines().containsKey(event.subject())) append(candidates, event,
                        data.testMines().get(event.subject()).primaryAudience(), event.subject(), NarrativeCandidateType.THREAT,
                        ScenarioArchetype.INVESTIGATION_RECOVERY, 88, 72, 90, 80);
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED) {
                regionFor(event.subject()).ifPresent(region -> append(candidates, event, region.primaryAudience(),
                        region.primaryFacilityId(), NarrativeCandidateType.SUPPLY_CRISIS,
                        ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS, 96, 94, 100, 74));
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED) {
                regionFor(event.subject()).filter(region -> opportunityStillPending(event)).ifPresent(region -> append(candidates, event, region.primaryAudience(),
                        region.primaryFacilityId(), NarrativeCandidateType.DEVELOPMENT_OPPORTUNITY,
                        ScenarioArchetype.DEVELOPMENT_OPPORTUNITY, 50, 82, 88, 95));
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_RETURN_PLANNED) {
                regionFor(event.subject()).filter(region -> opportunityStillPending(event)).ifPresent(region -> append(candidates, event, region.primaryAudience(),
                        region.primaryFacilityId(), NarrativeCandidateType.RESETTLEMENT_OPPORTUNITY,
                        ScenarioArchetype.RESETTLEMENT_OPPORTUNITY, 58, 86, 96, 90));
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.REGION_DISCOVERED) {
                appendKnownCrisis(candidates, event.subject());
            }
        });
        offer(candidates);
    }

    /** Re-derives delayed candidates until a cooldown is over or a decision is durable. */
    void offerPendingOpportunities() {
        Map<StoryAudienceId, List<NarrativeCandidate>> candidates = new LinkedHashMap<>();
        data.worldState().history().stream()
                .filter(event -> event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED
                        || event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED
                        || event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_RETURN_PLANNED)
                .filter(event -> event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED
                        || opportunityStillPending(event))
                .forEach(event -> regionFor(event.subject()).ifPresent(region -> {
                    if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED) {
                        append(candidates, event, region.primaryAudience(), region.primaryFacilityId(),
                                NarrativeCandidateType.SUPPLY_CRISIS,
                                ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS, 96, 94, 100, 74);
                    } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED) {
                        append(candidates, event, region.primaryAudience(), region.primaryFacilityId(),
                                NarrativeCandidateType.DEVELOPMENT_OPPORTUNITY,
                                ScenarioArchetype.DEVELOPMENT_OPPORTUNITY, 50, 82, 88, 95);
                    } else append(candidates, event, region.primaryAudience(), region.primaryFacilityId(),
                            NarrativeCandidateType.RESETTLEMENT_OPPORTUNITY,
                            ScenarioArchetype.RESETTLEMENT_OPPORTUNITY, 58, 86, 96, 90);
                }));
        offer(candidates);
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
                .ifPresent(event -> regionFor(communityId).ifPresent(region -> append(candidates, event, region.primaryAudience(),
                        region.primaryFacilityId(), NarrativeCandidateType.SUPPLY_CRISIS,
                        ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS, 96, 94, 100, 74)));
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
            commands.execute(data.worldState(), new DomainCommand.EvaluateNarrativeCandidates(audience, values));
        });
    }

    private java.util.Optional<io.farfrontier.palemirror.domain.LivingRegionState> regionFor(WorldObjectId communityId) {
        return data.worldState().livingRegions().stream()
                .filter(region -> region.communityId().equals(communityId) && region.primaryAudience() != null).findFirst();
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
        int nearest = server.getPlayerList().getPlayers().stream().filter(player -> audiences.apply(player).equals(audience))
                .filter(player -> player.serverLevel().dimension().location().toString().equals(location.dimensionId()))
                .mapToInt(player -> (int) Math.min(100_000L, player.blockPosition().distManhattan(location.position())))
                .min().orElse(100_000);
        return nearest <= 256 ? 0 : nearest <= 1_024 ? 20 : nearest <= 4_096 ? 50 : 90;
    }

    private record TargetLocation(String dimensionId, net.minecraft.core.BlockPos position) { }
}
