package io.farfrontier.palemirror.frontier.v3.model.combat;

/** World-hashed tactical policy. Does not grant a weapon, target or physical movement authority. */
public record EngagementRules(int outerRadius, int arcDegrees, int flankAlignmentPermille, int rebaseDistance,
        int assemblyTicks, int releaseLeadTicks, int slotSpacingTicks, int slotWindowTicks, int maximumWaveSize,
        int participantLifetime, int targetDriftMicros, int quickTurnDegrees, int lungeTurnDegrees, int lungeRunPermille,
        int attackDistanceMicros, int spacingMicros, int arcWeightPermille, int confirmedFlankFailureTicks) {
    public EngagementRules {
        if (outerRadius < 4 || outerRadius > 8 || arcDegrees < 15 || arcDegrees > 60
                || flankAlignmentPermille < 500 || flankAlignmentPermille > 950 || rebaseDistance < outerRadius
                || assemblyTicks < 1 || releaseLeadTicks < 1 || slotSpacingTicks < 1 || slotWindowTicks < 1
                || maximumWaveSize < 1 || maximumWaveSize > 8 || participantLifetime < assemblyTicks
                || targetDriftMicros < 1 || quickTurnDegrees < 1 || quickTurnDegrees > 180
                || lungeTurnDegrees < 1 || lungeTurnDegrees > quickTurnDegrees || lungeRunPermille<1000 || lungeRunPermille>2000
                || attackDistanceMicros<1 || spacingMicros<1 || arcWeightPermille<1 || confirmedFlankFailureTicks<1 || confirmedFlankFailureTicks>participantLifetime)
            throw new IllegalArgumentException("invalid encounter tactics");
    }
    public static EngagementRules initial() { return new EngagementRules(5,45,700,12,8,2,12,8,3,80,2_000_000,90,55,1500,2_200_000,800_000,4000,12); }
    public String canonicalText() { return outerRadius+":"+arcDegrees+":"+flankAlignmentPermille+":"+rebaseDistance+":"+assemblyTicks
            +":"+releaseLeadTicks+":"+slotSpacingTicks+":"+slotWindowTicks+":"+maximumWaveSize+":"+participantLifetime
            +":"+targetDriftMicros+":"+quickTurnDegrees+":"+lungeTurnDegrees+":"+lungeRunPermille+":"+attackDistanceMicros+":"+spacingMicros+":"+arcWeightPermille+":"+confirmedFlankFailureTicks; }
}
