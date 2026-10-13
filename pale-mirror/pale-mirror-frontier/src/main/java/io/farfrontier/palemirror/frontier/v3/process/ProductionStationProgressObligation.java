package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

/** Read-only obligation of the installed machine; a resident is not its lifetime identity. */
public record ProductionStationProgressObligation(String fingerprint, long budgetTicks,
        Disposition disposition, Next next) {
    public enum Disposition { ELIGIBLE, HIGHER_PRIORITY_ACTIVITY, SERVICE_ACCESS, RECOVERY_UNKNOWN, TERMINAL }
    public enum Next { PHYSICAL_RECEIPT, PROCESS, COLLECT_OUTPUT, CLOSED }

    public static ProductionStationProgressObligation describe(FrontierWorldState state, ProductionStations.Machine machine) {
        var container = ProductionStationEnergy.record(state, machine.stationId());
        var operation = machine.operation();
        var clock = operation.flatMap(ProductionOperation::progress);
        boolean pending = machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent();
        // Retired equipment is unavailable by design, not an unknown recovery.
        // Outstanding effects/operations still prevent declaring its story closed.
        boolean closedRetirement = state.inventory().surfaces().get(container.id()).status() == ContainerSurfaceStatus.RETIRED
                && operation.isEmpty() && !pending;
        var disposition = closedRetirement ? Disposition.TERMINAL
                : ReferenceContainerCustody.blocksCanonicalUse(state, container.id()) ? Disposition.RECOVERY_UNKNOWN
                : pending ? Disposition.ELIGIBLE
                : operation.isEmpty() ? Disposition.TERMINAL
                : operation.orElseThrow().transformed() ? Disposition.SERVICE_ACCESS
                : clock.filter(value -> value.running() || value.complete()).isPresent() ? Disposition.ELIGIBLE
                : Disposition.HIGHER_PRIORITY_ACTIVITY;
        var next = pending ? Next.PHYSICAL_RECEIPT : operation.isEmpty() ? Next.CLOSED
                : operation.orElseThrow().transformed() ? Next.COLLECT_OUTPUT : Next.PROCESS;
        // Reuse the movement safety ceiling for the receipt boundary. Processing adds
        // its pinned work requirement; no wall clock or independently tuned process timer.
        long budget = Math.addExact(ActorMovementProcess.maximumJourneyTicks(),
                clock.map(value -> (value.requiredMilliWork() + 999) / 1_000).orElse(0L));
        String fingerprint = machine.storedHeat() + ":" + pending + ":" + operation.map(value ->
                value.id().value().value() + ":" + value.transformed() + ":"
                        + value.progress().map(WorkProgress::completedMilliWork).orElse(0L)).orElse("IDLE");
        return new ProductionStationProgressObligation(fingerprint, budget, disposition, next);
    }
}
