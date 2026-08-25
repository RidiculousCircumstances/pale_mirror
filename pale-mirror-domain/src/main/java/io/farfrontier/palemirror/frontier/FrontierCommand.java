package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** The sole mutation port for Frontier state. */
public sealed interface FrontierCommand permits FrontierCommand.AdvanceDays, FrontierCommand.ApplyObservation,
        FrontierCommand.StartMorphogenesis, FrontierCommand.LaunchHarvester, FrontierCommand.LaunchPropagationRun {
    record AdvanceDays(int days, String causationId) implements FrontierCommand {
        public AdvanceDays {
            if (days < 0) throw new IllegalArgumentException("days must not be negative");
            Objects.requireNonNull(causationId, "causationId");
        }
    }
    record ApplyObservation(FrontierPhysicalObservation observation) implements FrontierCommand {
        public ApplyObservation { Objects.requireNonNull(observation, "observation"); }
    }
    /** Explicit domain command used by the hive director now and future operator/UI intents later. */
    record StartMorphogenesis(String hiveId, String sourceOrganId, FrontierHiveOrganKind kind,
                              FrontierPoint position, String causationId) implements FrontierCommand {
        public StartMorphogenesis {
            if (hiveId == null || hiveId.isBlank() || sourceOrganId == null || sourceOrganId.isBlank()
                    || causationId == null || causationId.isBlank()) {
                throw new IllegalArgumentException("morphogenesis identity is required");
            }
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(position, "position");
        }
    }
    /** Explicit deterministic launch port used by the autonomous hive director and conformance tests. */
    record LaunchHarvester(String hiveId, String sourceOrganId, String bioformId,
                           FrontierPoint foragePosition, String causationId) implements FrontierCommand {
        public LaunchHarvester {
            if (hiveId == null || hiveId.isBlank() || sourceOrganId == null || sourceOrganId.isBlank()
                    || bioformId == null || bioformId.isBlank() || causationId == null || causationId.isBlank()) {
                throw new IllegalArgumentException("harvester identity is required");
            }
            Objects.requireNonNull(foragePosition, "foragePosition");
        }
    }
    /** Explicit launch port for a non-combat carrier; target selection remains canonical. */
    record LaunchPropagationRun(String hiveId, String sourceOrganId, String bioformId,
                          FrontierPoint target, String causationId) implements FrontierCommand {
        public LaunchPropagationRun {
            if (hiveId == null || hiveId.isBlank() || sourceOrganId == null || sourceOrganId.isBlank()
                    || bioformId == null || bioformId.isBlank() || causationId == null || causationId.isBlank()) {
                throw new IllegalArgumentException("propagation identity is required");
            }
            Objects.requireNonNull(target, "target");
        }
    }
}
