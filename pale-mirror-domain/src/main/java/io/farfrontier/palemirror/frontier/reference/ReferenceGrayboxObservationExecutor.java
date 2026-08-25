package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Applies graybox facts through the one owner that currently holds a named resident. */
final class ReferenceGrayboxObservationExecutor {
    private static final int FRONT_CAMPAIGN_OPERATION_OFFSET = 1_000_000;

    private ReferenceGrayboxObservationExecutor() { }

    static ReferenceGrayboxObservationOutcome apply(
            ReferenceWorld world,
            ReferenceGrayboxResidentObservation observation
    ) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceGrayboxResidentObservation event = Objects.requireNonNull(observation, "observation");
        ReferenceGrayboxLayout.requireSupported(required);
        String before = ReferenceGrayboxProjection.from(required).stateRevision();
        if (!before.equals(event.observedStateRevision())) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE,
                "observation was made against an older graybox state", before);

        ResidentOwner owner = findResident(required, event.residentId());
        if (owner == null) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_UNKNOWN,
                "resident no longer exists in canonical state", before);
        boolean applied = switch (owner.resident().location()) {
            case SETTLEMENT -> applySettlement(owner, event.kind());
            case OPERATION -> applyOperation(required, owner, event.kind());
            case FIELD_POST -> applyFieldPost(required, owner, event.kind());
        };
        if (!applied) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                "resident custody no longer accepts this observation", before);

        required.marketWorld().event("D" + required.day() + ": physical resident " + event.kind().name().toLowerCase()
                + " " + event.residentId() + " (" + event.eventId() + ")");
        required.assertProfileInvariants();
        String after = ReferenceGrayboxProjection.from(required).stateRevision();
        return outcome(event, ReferenceGrayboxObservationOutcome.Status.APPLIED, "canonical resident custody updated", after);
    }

    static ReferenceGrayboxObservationOutcome apply(
            ReferenceWorld world,
            ReferenceGrayboxBioformObservation observation
    ) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceGrayboxBioformObservation event = Objects.requireNonNull(observation, "observation");
        ReferenceGrayboxLayout.requireSupported(required);
        String before = ReferenceGrayboxProjection.from(required).stateRevision();
        if (!before.equals(event.observedStateRevision())) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE,
                "observation was made against an older graybox state", before);
        boolean known = required.infection().swarms().stream().anyMatch(swarm -> swarm.hasExactBioform(event.bioformId()));
        if (!known) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_UNKNOWN,
                "bioform no longer exists in canonical state", before);
        if (!ReferenceBioformObservationMutation.killExact(required.infection(), event.bioformId())) return outcome(event, ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                "bioform custody no longer accepts this observation", before);

        required.operations().syncInfectionSwarms(required);
        required.marketWorld().event("D" + required.day() + ": physical bioform killed " + event.bioformId() + " (" + event.eventId() + ")");
        required.assertProfileInvariants();
        String after = ReferenceGrayboxProjection.from(required).stateRevision();
        return outcome(event, ReferenceGrayboxObservationOutcome.Status.APPLIED, "canonical bioform custody updated", after);
    }

    private static boolean applySettlement(ResidentOwner owner, ReferenceGrayboxResidentObservation.Kind kind) {
        return ReferenceResidentObservationMutation.apply(owner.settlement(), owner.resident().id(), kind);
    }

    private static boolean applyOperation(ReferenceWorld world, ResidentOwner owner, ReferenceGrayboxResidentObservation.Kind kind) {
        Integer reference = owner.resident().locationRef();
        if (reference == null) return false;
        if (reference >= FRONT_CAMPAIGN_OPERATION_OFFSET) {
            ReferenceFrontCampaign campaign = world.v2().frontCampaigns().get(reference - FRONT_CAMPAIGN_OPERATION_OFFSET);
            return campaign != null && ReferenceV2Frontier.applyExactResidentObservation(campaign, owner.settlement(),
                    owner.resident().id(), kind);
        }
        ReferenceOperation operation = world.operations().active().stream().filter(item -> item.id() == reference).findFirst().orElse(null);
        return operation != null && ReferenceOperationExecution.applyExactResidentObservation(world, operation, owner.settlement(),
                owner.resident().id(), kind);
    }

    private static boolean applyFieldPost(ReferenceWorld world, ResidentOwner owner, ReferenceGrayboxResidentObservation.Kind kind) {
        Integer reference = owner.resident().locationRef();
        ReferenceFieldPost post = reference == null ? null : world.field().posts().get(reference);
        return post != null && ReferenceFieldExecution.applyExactResidentObservation(post, owner.settlement(), owner.resident().id(), kind);
    }

    private static ResidentOwner findResident(ReferenceWorld world, String residentId) {
        ResidentOwner result = null;
        for (ReferenceSettlement settlement : world.settlements().values()) {
            ReferenceResident resident = settlement.residents() == null ? null : settlement.residents().resident(residentId);
            if (resident == null) continue;
            if (result != null) throw new IllegalStateException("resident has duplicate canonical ownership: " + residentId);
            result = new ResidentOwner(settlement, resident);
        }
        return result;
    }

    private static ReferenceGrayboxObservationOutcome outcome(
            ReferenceGrayboxResidentObservation event,
            ReferenceGrayboxObservationOutcome.Status status,
            String reason,
            String revision
    ) {
        return new ReferenceGrayboxObservationOutcome(event.eventId(), status, reason, revision);
    }

    private static ReferenceGrayboxObservationOutcome outcome(
            ReferenceGrayboxBioformObservation event,
            ReferenceGrayboxObservationOutcome.Status status,
            String reason,
            String revision
    ) {
        return new ReferenceGrayboxObservationOutcome(event.eventId(), status, reason, revision);
    }

    private record ResidentOwner(ReferenceSettlement settlement, ReferenceResident resident) { }
}
