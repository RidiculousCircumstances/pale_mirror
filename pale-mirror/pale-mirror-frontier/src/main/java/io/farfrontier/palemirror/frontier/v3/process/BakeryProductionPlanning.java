package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.*;

/** Bread policy selects stock and workers; common production owns execution. */
final class BakeryProductionPlanning {
    static List<ProposedEvent> planStart(FrontierWorldState state, StrategicTask task, ScheduledAction action, long now) {
        Settlement settlement = settlement(state, task.ownerId());
        SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) return blocked(task, settlement, workshop, workshop.id(), ProductionDiagnosticProducer.FACILITY_UNAVAILABLE);
        if (!SettlementCommitmentComposition.ADMISSION.facilityAvailable(state, workshop.id()))
            return List.of(reschedule(action, start(task, Math.addExact(now,
                    state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
        Optional<ResidentProfile> availableBaker = FrontierWorldStateSupport.availableWorkResident(
                state, settlement.id(), ResidentWorkKind.PRODUCTION, HumanCapability.INDUSTRY);
        if (availableBaker.isEmpty()) {
            return blocked(task, settlement, workshop, workshop.id(), ProductionDiagnosticProducer.WORKER_UNAVAILABLE);
        }
        List<ResidentProfile> eligible = ResidentWorkComposition.SELECTION.eligible(state, settlement.id(),
                ResidentWorkKind.PRODUCTION, HumanCapability.INDUSTRY, now);
        if (eligible.isEmpty())
            return List.of(reschedule(action, start(task, Math.addExact(now,
                    state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
        var availableInput = BakeryBatchSelection.available(state, settlement.id());
        if (availableInput.isEmpty()) {
            var stored = BakeryBatchSelection.storedInputContainers(state, settlement.id());
            if (stored.stream().anyMatch(id -> !ReferenceContainerCustody.blocksCanonicalUse(state, id)))
                return List.of(reschedule(action, start(task, Math.addExact(now, 20L))));
            return blocked(task, settlement, workshop, stored.isEmpty() ? workshop.id() : stored.getFirst(), ProductionDiagnosticProducer.INPUT_UNAVAILABLE);
        }
        SubjectId depot = availableInput.orElseThrow().sourceContainer(state);
        boolean physicalCustody = ReferenceContainerCustody.hasLiveCustody(state, depot);
        var batch = BakeryBatchSelection.admissible(state, settlement.id(), now);
        if (batch.isEmpty())
            return List.of(reschedule(action, start(task, Math.addExact(now,
                    state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
        Optional<ExactItemStack> input = batch.orElseThrow().exactInput();
        Optional<FungibleResourceCustodySupport.LotSelection> fungible = batch.orElseThrow().fungibleInput();
        if (ReferenceContainerCustody.hasLiveCustody(state, batch.orElseThrow().sourceContainer(state))
                    && !ReferenceContainerCustody.hasOperationalCustody(state, batch.orElseThrow().sourceContainer(state))
                || fungible.isPresent() && !ProductionResourceCustody.canStart(state, fungible.orElseThrow(), batch.orElseThrow().quantity())) {
            return List.of(reschedule(action, start(task, Math.addExact(now, 20L))));
        }
        // Exact and fungible stock remain distinct representations. A resource job admitted
        // under physical custody reserves the current bound epoch, never a COLD mirror.
        Optional<ProductionJob> selectedJob = ResidentWorkComposition.SELECTION.offers(state, settlement.id(), now,
                BakeryJobAdmission.provider(task, settlement, workshop, input, fungible)).stream()
                .map(ResidentWorkOffer::execution)
                .filter(job -> ProductionCommercialProcess.canReserve(state, job)).findFirst();
        // Finance is a start precondition.  A blocked task must not leave a durable job
        // occupying its workshop: otherwise a later objective review could create a
        // second job for the same facility and quarantine the canonical engine.
        if (selectedJob.isEmpty()) {
            // This branch offers public work: municipal production has no invoice.
            // Company-owned or buyer-owned work retains its separate commercial admission.
            return List.of(reschedule(action, start(task, Math.addExact(now,
                    state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
        }
        ProductionJob job = selectedJob.orElseThrow();
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(settlement.id(),
                new ProductionStarted(job, job.consumedItemId(), Optional.of(StationWorkAdmissionNavigation.propose(state, job)))),
                schedule(complete(job, now + 100L)));
    }

    private BakeryProductionPlanning() { }
}
