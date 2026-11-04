package io.farfrontier.palemirror.frontier.v3.model.combat;

/** World-hashed tactical policy. Does not grant a weapon, target or physical movement authority. */
public record EngagementRules(int outerRadius, int arcDegrees, int flankAlignmentPermille, int rebaseDistance,
        int assemblyTicks, int releaseLeadTicks, int slotSpacingTicks, int slotWindowTicks, int maximumWaveSize,
        int participantLifetime, int targetDriftMicros, int quickTurnDegrees, int lungeTurnDegrees, int lungeRunPermille,
        int attackDistanceMicros, int spacingMicros, int arcWeightPermille, int confirmedFlankFailureTicks, int aimTrackPermille, int aimTurnDegreesPerTick, int lungeCorrectionBlocks,
        int roleCommitmentTicks,int flankRetryTicks,int pairedAngleDegrees,int frameCommitmentTicks,int frameAngleDegrees,
        int flankProgressTicks,int flankProgressPermille,int contactCorrectionBlocks,int contactRunPermille,
        int approachPacePermille,int flankPacePermille) {
    public EngagementRules {
        if (frameCommitmentTicks<1 || frameCommitmentTicks>100 || frameAngleDegrees<15 || frameAngleDegrees>90
                || flankProgressTicks<roleCommitmentTicks || flankProgressTicks>400 || flankProgressPermille<1 || flankProgressPermille>500
                || contactCorrectionBlocks<1 || contactCorrectionBlocks>2 || contactRunPermille<1000 || contactRunPermille>2000
                || approachPacePermille<1000 || approachPacePermille>2500 || flankPacePermille<1000 || flankPacePermille>2500
                || roleCommitmentTicks<confirmedFlankFailureTicks || roleCommitmentTicks>participantLifetime || flankRetryTicks<roleCommitmentTicks || flankRetryTicks>400
                || pairedAngleDegrees<30 || pairedAngleDegrees>150 || aimTrackPermille<1 || aimTrackPermille>900 || aimTurnDegreesPerTick<1 || aimTurnDegreesPerTick>30
                || lungeCorrectionBlocks<1 || lungeCorrectionBlocks>3 || outerRadius < 4 || outerRadius > 8 || arcDegrees < 15 || arcDegrees > 60
                || flankAlignmentPermille < 500 || flankAlignmentPermille > 950 || rebaseDistance < outerRadius
                || assemblyTicks < 1 || releaseLeadTicks < 1 || slotSpacingTicks < 1 || slotWindowTicks < 1
                || maximumWaveSize < 1 || maximumWaveSize > 8 || participantLifetime < assemblyTicks
                || targetDriftMicros < 1 || quickTurnDegrees < 1 || quickTurnDegrees > 180
                || lungeTurnDegrees < 1 || lungeTurnDegrees > quickTurnDegrees || lungeRunPermille<1000 || lungeRunPermille>2000
                || attackDistanceMicros<1 || spacingMicros<1 || arcWeightPermille<1 || confirmedFlankFailureTicks<1 || confirmedFlankFailureTicks>participantLifetime)
            throw new IllegalArgumentException("invalid encounter tactics");
    }
    public static EngagementRules initial() { return new EngagementRules(5,45,700,12,4,1,2,8,2,80,2_000_000,60,40,1500,2_200_000,800_000,4000,20,650,12,2,40,60,60,20,45,60,100,1,1600,2100,2400); }
    public String canonicalText() { return outerRadius+":"+arcDegrees+":"+flankAlignmentPermille+":"+rebaseDistance+":"+assemblyTicks
            +":"+releaseLeadTicks+":"+slotSpacingTicks+":"+slotWindowTicks+":"+maximumWaveSize+":"+participantLifetime
            +":"+targetDriftMicros+":"+quickTurnDegrees+":"+lungeTurnDegrees+":"+lungeRunPermille+":"+attackDistanceMicros+":"+spacingMicros+":"+arcWeightPermille+":"+confirmedFlankFailureTicks
            +":"+aimTrackPermille+":"+aimTurnDegreesPerTick+":"+lungeCorrectionBlocks+":"+roleCommitmentTicks+":"+flankRetryTicks+":"+pairedAngleDegrees+":"+frameCommitmentTicks+":"+frameAngleDegrees+":"+flankProgressTicks+":"+flankProgressPermille
            +":"+contactCorrectionBlocks+":"+contactRunPermille+":"+approachPacePermille+":"+flankPacePermille; }
}
