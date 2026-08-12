package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Canonical travel state; physical entities are leased projections of it. */
public final class WorldJourney {
    private final String id;
    private final String subjectGroupId;
    private final String pathId;
    private final WorldObjectId originSiteId;
    private final WorldObjectId destinationSiteId;
    private final JourneyMode mode;
    private final JourneyRiskPolicy riskPolicy;
    private final long riskSeed;
    private final long startedAtStep;
    private final long expectedDurationSteps;
    private JourneyState state;
    private long elapsedSteps;
    private int checkpointIndex;
    private int confirmedLosses;
    private String diagnostic;
    private long revision;

    public WorldJourney(String id, String subjectGroupId, String pathId, WorldObjectId originSiteId,
                        WorldObjectId destinationSiteId, JourneyMode mode, JourneyRiskPolicy riskPolicy,
                        long riskSeed, long startedAtStep, long expectedDurationSteps, JourneyState state,
                        long elapsedSteps, int checkpointIndex, int confirmedLosses, String diagnostic, long revision) {
        this.id = required(id, "journey id");
        this.subjectGroupId = required(subjectGroupId, "subject group id");
        this.pathId = required(pathId, "path id");
        this.originSiteId = Objects.requireNonNull(originSiteId, "originSiteId");
        this.destinationSiteId = Objects.requireNonNull(destinationSiteId, "destinationSiteId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.riskPolicy = Objects.requireNonNull(riskPolicy, "riskPolicy");
        if (startedAtStep < 0 || expectedDurationSteps < 1 || elapsedSteps < 0 || checkpointIndex < 0
                || confirmedLosses < 0 || revision < 0) throw new IllegalArgumentException("Invalid journey state");
        this.riskSeed = riskSeed;
        this.startedAtStep = startedAtStep;
        this.expectedDurationSteps = expectedDurationSteps;
        this.state = Objects.requireNonNull(state, "state");
        this.elapsedSteps = elapsedSteps;
        this.checkpointIndex = checkpointIndex;
        this.confirmedLosses = confirmedLosses;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        this.revision = revision;
    }

    public static WorldJourney evacuation(String id, String groupId, String pathId, WorldObjectId origin,
                                           WorldObjectId destination, long step, long duration, long riskSeed) {
        return new WorldJourney(id, groupId, pathId, origin, destination, JourneyMode.LAND_FOOT,
                JourneyRiskPolicy.evacuationDefault(), riskSeed, step, duration, JourneyState.IN_TRANSIT,
                0, 0, 0, "", 0);
    }

    public static WorldJourney communityReturn(String id, String groupId, String pathId, WorldObjectId origin,
                                               WorldObjectId destination, long step, long duration, long riskSeed) {
        return new WorldJourney(id, groupId, pathId, origin, destination, JourneyMode.LAND_FOOT,
                JourneyRiskPolicy.communityReturn(), riskSeed, step, duration, JourneyState.IN_TRANSIT,
                0, 0, 0, "", 0);
    }

    public String id() { return id; }
    public String subjectGroupId() { return subjectGroupId; }
    public String pathId() { return pathId; }
    public WorldObjectId originSiteId() { return originSiteId; }
    public WorldObjectId destinationSiteId() { return destinationSiteId; }
    public JourneyMode mode() { return mode; }
    public JourneyRiskPolicy riskPolicy() { return riskPolicy; }
    public long riskSeed() { return riskSeed; }
    public long startedAtStep() { return startedAtStep; }
    public long expectedDurationSteps() { return expectedDurationSteps; }
    public JourneyState state() { return state; }
    public long elapsedSteps() { return elapsedSteps; }
    public int checkpointIndex() { return checkpointIndex; }
    public int confirmedLosses() { return confirmedLosses; }
    public String diagnostic() { return diagnostic; }
    public long revision() { return revision; }
    public double progress() { return Math.min(1.0D, (double) elapsedSteps / expectedDurationSteps); }
    public boolean terminal() { return state == JourneyState.ARRIVED || state == JourneyState.LOST || state == JourneyState.CANCELLED; }

    public boolean advanceAbstractStep() {
        if (terminal() || state == JourneyState.BLOCKED) return false;
        elapsedSteps++;
        state = JourneyState.IN_TRANSIT;
        revision++;
        if (elapsedSteps >= expectedDurationSteps) state = JourneyState.ARRIVED;
        return true;
    }

    public boolean observeCheckpoint(int index) {
        if (terminal() || index <= checkpointIndex) return false;
        checkpointIndex = index;
        state = JourneyState.PHYSICALLY_OBSERVED;
        revision++;
        return true;
    }

    public void recordLosses(int amount) {
        if (amount < 1 || terminal()) return;
        confirmedLosses = Math.addExact(confirmedLosses, amount);
        revision++;
    }

    public void lose(String reason) {
        if (terminal()) return;
        state = JourneyState.LOST;
        diagnostic = required(reason, "loss reason");
        revision++;
    }

    public void block(String reason) {
        if (terminal()) return;
        state = JourneyState.BLOCKED;
        diagnostic = required(reason, "block reason");
        revision++;
    }

    public void resume() {
        if (state != JourneyState.BLOCKED) return;
        state = JourneyState.IN_TRANSIT;
        diagnostic = "";
        revision++;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
