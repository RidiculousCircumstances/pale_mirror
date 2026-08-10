package io.farfrontier.palemirror.domain;

import java.util.Objects;

/**
 * The only inputs that may make durable changes to a {@link WorldState}.
 * Adapters and Minecraft observers supply facts; the NeoForge bridge translates
 * those facts into these commands before the domain model is touched.
 */
public sealed interface DomainCommand permits DomainCommand.AdvanceSimulation,
        DomainCommand.OfferScenario, DomainCommand.AcceptScenario,
        DomainCommand.PlayerEnteredFacility, DomainCommand.ThreatControllerDestroyed,
        DomainCommand.MaterializationObserved, DomainCommand.NoScenario,
        DomainCommand.SetScenarioBlocked, DomainCommand.ActivateGate,
        DomainCommand.BypassGate, DomainCommand.GatePartDestroyed {

    record AdvanceSimulation(int steps) implements DomainCommand { }

    record OfferScenario(DomainEvent sourceEvent, StoryAudienceId audience, ScenarioDefinitionRef definition) implements DomainCommand {
        public OfferScenario {
            Objects.requireNonNull(sourceEvent, "sourceEvent");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(definition, "definition");
        }
    }

    record AcceptScenario(String scenarioId) implements DomainCommand {
        public AcceptScenario { Objects.requireNonNull(scenarioId, "scenarioId"); }
    }

    record PlayerEnteredFacility(StoryAudienceId audience, WorldObjectId facilityId) implements DomainCommand {
        public PlayerEnteredFacility {
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(facilityId, "facilityId");
        }
    }

    record ThreatControllerDestroyed(WorldObjectId facilityId, String causationId) implements DomainCommand {
        public ThreatControllerDestroyed {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record MaterializationObserved(WorldObjectId facilityId, long desiredRevision, String causationId) implements DomainCommand {
        public MaterializationObserved {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record NoScenario(DomainEvent sourceEvent, StoryAudienceId audience, String reason) implements DomainCommand {
        public NoScenario {
            Objects.requireNonNull(sourceEvent, "sourceEvent");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record SetScenarioBlocked(String scenarioId, boolean blocked, String reason) implements DomainCommand {
        public SetScenarioBlocked {
            Objects.requireNonNull(scenarioId, "scenarioId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record ActivateGate(WorldObjectId facilityId, GatePlanRef plan, String causationId) implements DomainCommand {
        public ActivateGate {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record BypassGate(WorldObjectId facilityId, String causationId) implements DomainCommand {
        public BypassGate {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }

    record GatePartDestroyed(WorldObjectId facilityId, String slotId, String causationId) implements DomainCommand {
        public GatePartDestroyed {
            Objects.requireNonNull(facilityId, "facilityId");
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(causationId, "causationId");
        }
    }
}
