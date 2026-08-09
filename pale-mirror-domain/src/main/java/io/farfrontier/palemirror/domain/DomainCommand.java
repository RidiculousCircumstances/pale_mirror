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
        DomainCommand.MaterializationObserved {

    record AdvanceSimulation(int steps) implements DomainCommand { }

    record OfferScenario(DomainEvent sourceEvent, StoryAudienceId audience) implements DomainCommand {
        public OfferScenario {
            Objects.requireNonNull(sourceEvent, "sourceEvent");
            Objects.requireNonNull(audience, "audience");
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
}
