package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Source;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Read;
import java.util.*;

/** Station fulfillment owns its complete material reads. The common index only
 * selects affected checks; the existing business validator remains authoritative. */
final class StationWorkReferenceValidation {
    private record Context(FrontierBootstrap bootstrap, ExactInventory inventory) { }
    private static final Source<Context, SubjectId, ContainerRecord> CONTAINERS = new Source<>(c -> c.inventory().containers());
    private static final Source<Context, SubjectId, ContainerRecord> STATIONS = new Source<>(c -> ProductionStationDirectory.declarations(c.inventory()));
    private static final Source<Context, SubjectId, ExactItemStack> ITEMS = new Source<>(c -> c.inventory().items());
    private static final Source<Context, SubjectId, CustodyAccount> ACCOUNTS = new Source<>(c -> c.inventory().fungibleResources().accounts());
    private static final Source<Context, SubjectId, ClaimAllocation> CLAIMS = new Source<>(c -> c.inventory().fungibleResources().claims());
    private static final Source<Context, SubjectId, ResourceLot> LOTS = new Source<>(c -> c.inventory().fungibleResources().lots());
    // At most64 future/loaded legs (two reads each),64 current input lots,
    // output and fixed source/station/cargo/maintenance/claim addresses.
    private static final ImmutableReferenceIndex<Context, SubjectId, ProductionJob> CHECKS =
            new ImmutableReferenceIndex<>(SimulationCapacity.MAX_RESIDENTS, 16 + 64 * 3,
                    List.of(CONTAINERS, STATIONS, ITEMS, ACCOUNTS, CLAIMS, LOTS), StationWorkReferenceValidation::reads,
                    (context, id, job) -> validate(context, job));

    static void validate(FrontierBootstrap bootstrap, ExactInventory inventory, Map<SubjectId, ProductionJob> jobs) {
        var context = new Context(bootstrap, inventory);
        // This is a derived-index budget, never a new job admission limit.
        if (jobs.size() > SimulationCapacity.MAX_RESIDENTS) jobs.values().forEach(job -> validate(context, job));
        else CHECKS.validate(bootstrap, jobs, context);
    }
    private static void validate(Context context, ProductionJob job) {
        if (job.stationWork().isPresent()) StationWorkValidation.validate(context.inventory(), job,
                FrontierWorldStateSupport.settlement(context.bootstrap(), job.settlementId()), context.bootstrap().ruleset().production());
    }
    private static List<Read<Context>> reads(Context context, ProductionJob job) {
        if (job.stationWork().isEmpty()) return List.of(); // Other production protocols retain their existing checks.
        var work = job.stationWork().orElseThrow(); var reads = new ArrayList<Read<Context>>();
        reads.add(CONTAINERS.at(work.sourceContainerId())); reads.add(STATIONS.at(work.stationId()));
        reads.add(ITEMS.at(job.consumedItemId())); reads.add(ITEMS.at(job.outputItemId()));
        work.maintenanceTarget().ifPresent(target -> reads.add(ITEMS.at(target.itemId())));
        reads.add(ACCOUNTS.at(work.sourceAccountId())); reads.add(ACCOUNTS.at(work.actorAccountId()));
        reads.add(ACCOUNTS.at(work.stationAccountId())); reads.add(ACCOUNTS.at(work.destinationAccountId()));
        var station = ProductionStationDirectory.require(context.inventory(), work.stationId()).productionStation().orElseThrow();
        reads.add(ACCOUNTS.at(ContainerResourceAccounts.id(station.custody(ActorContainerItemOrder.StationPort.OUTPUT))));
        reads.add(LOTS.at(job.outputItemId())); job.inputQuantities().keySet().forEach(id -> reads.add(LOTS.at(id)));
        if (job.inputHold() instanceof ProductionInputHold.FungibleCold hold) reads.add(CLAIMS.at(hold.claimId()));
        else if (job.inputHold() instanceof ProductionInputHold.FungibleBound hold) reads.add(CLAIMS.at(hold.claimId()));
        work.materials().queued().forEach(input -> {
            reads.add(ACCOUNTS.at(input.sourceAccount())); reads.add(CLAIMS.at(input.claimId()));
        });
        work.materials().loaded().forEach(input -> reads.add(CLAIMS.at(input.claimId())));
        return List.copyOf(reads);
    }
    private StationWorkReferenceValidation() { }
}
