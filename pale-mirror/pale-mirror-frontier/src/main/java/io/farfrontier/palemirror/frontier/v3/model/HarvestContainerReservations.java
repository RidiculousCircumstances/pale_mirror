package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Capacity and custody declarations belong to harvest work. Progress/time-only
 * updates cannot invalidate unchanged reservation facts in unrelated jobs. */
final class HarvestContainerReservations {
    private record Declaration(SubjectId job, SubjectId worker, SubjectId actorAccount, SubjectId depotAccount,
                               InventoryCustody.ContainerSlot output, boolean reservesOutput,
                               Optional<InventoryCustody.ContainerSlot> successor,
                               Optional<InventoryCustody.ContainerSlot> equipment,
                               Set<InventoryCustody.ContainerSlot> destinations) { }
    private static final int MAX_JOBS = ResourceSiteState.MAX_SITES * ResourceSiteLifecycle.MAX_HARVEST_WORKERS;
    private static final ImmutableRegistryView<SubjectId, ResourceSiteHarvestJob, Declaration> DECLARATIONS =
            new ImmutableRegistryView<>(MAX_JOBS, Objects::equals);
    private static final ImmutableInputView<List<ContainerSlotClaim>> CLAIMS = new ImmutableInputView<>();
    private static final ImmutableGroupView<SubjectId, Declaration, SubjectId> ACTOR_ACCOUNTS = new ImmutableGroupView<>();
    private static final ImmutableGroupView<SubjectId, Declaration, InventoryCustody.ContainerSlot> DESTINATIONS = new ImmutableGroupView<>();
    private static final ImmutableReferenceIndex.Source<Map<SubjectId, CustodyAccount>, SubjectId, CustodyAccount> ACCOUNTS =
            new ImmutableReferenceIndex.Source<>(values -> values);
    private static final ImmutableReferenceIndex<Map<SubjectId, CustodyAccount>, SubjectId, Declaration> ACCOUNT_CHECKS =
            new ImmutableReferenceIndex<>(MAX_JOBS, 2, List.of(ACCOUNTS),
                    (accounts, job) -> List.of(ACCOUNTS.at(job.actorAccount()), ACCOUNTS.at(job.depotAccount())),
                    (accounts, id, job) -> validateAccount(accounts, job));
    private static final ImmutableReferenceIndex.Source<Map<SubjectId, ContainerRecord>, SubjectId, ContainerRecord> CONTAINERS =
            new ImmutableReferenceIndex.Source<>(values -> values);
    private static final ImmutableReferenceIndex<Map<SubjectId, ContainerRecord>, SubjectId, Declaration> CONTAINER_CHECKS =
            new ImmutableReferenceIndex<>(MAX_JOBS, 2, List.of(CONTAINERS),
                    (containers, job) -> job.destinations().stream().map(slot -> CONTAINERS.at(slot.containerId())).toList(),
                    (containers, id, job) -> {
                        for (var slot : job.destinations()) {
                            var container = containers.get(slot.containerId());
                            if (container == null || slot.slot() >= container.slotCount())
                                throw new IllegalArgumentException("active harvest output names an undeclared container slot");
                        }
                    });
    private HarvestContainerReservations() { }
    private static Map<SubjectId, Declaration> declarations(Map<SubjectId, ResourceSiteLifecycle> sites) {
        return DECLARATIONS.project(HarvestContainerReservations.class, ResourceSiteState.harvestJobs(sites), (id, job) -> {
            var destinations = new HashSet<InventoryCustody.ContainerSlot>();
            if (job.reservesOutputCapacity()) destinations.add(job.outputSlot());
            job.batchSuccessorSlot().ifPresent(slot -> {
                if (!destinations.add(slot)) throw new IllegalArgumentException("active harvest output slot lacks exclusive container capacity");
            });
            return new Declaration(job.id(), job.workerId(), job.actorAccountId(), job.depotAccountId(), job.outputSlot(),
                    job.reservesOutputCapacity(), job.batchSuccessorSlot(), job.equipment().pending().map(step -> step.slot()), Set.copyOf(destinations));
        });
    }
    static void validateAccounts(Map<SubjectId, CustodyAccount> accounts, Map<SubjectId, ResourceSiteLifecycle> sites) {
        var jobs = declarations(sites);
        if (!ACTOR_ACCOUNTS.uniqueMemberships(HarvestContainerReservations.class, jobs, job -> Set.of(job.actorAccount())))
            throw new IllegalArgumentException("active harvest jobs share one declared actor resource account");
        ACCOUNT_CHECKS.validate(HarvestContainerReservations.class, jobs, accounts);
    }
    private static void validateAccount(Map<SubjectId, CustodyAccount> accounts, Declaration job) {
        var account = accounts.get(job.actorAccount());
        if (account != null && !account.custody().equals(new ResourceCustody.Actor(job.worker())))
            throw new IllegalArgumentException("active harvest actor account has foreign custody");
        var depot = accounts.get(job.depotAccount());
        if (depot != null && !depot.custody().equals(new ResourceCustody.Container(job.output().containerId())))
            throw new IllegalArgumentException("active harvest depot account has foreign custody");
    }
    static List<ContainerSlotClaim> claims(Map<SubjectId, ResourceSiteLifecycle> sites) {
        var jobs = declarations(sites);
        return CLAIMS.get(List.of(jobs), () -> {
            var result = new ArrayList<ContainerSlotClaim>();
            for (var job : jobs.values()) {
                var owner = new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.HARVEST, job.job());
                if (job.reservesOutput()) result.add(new ContainerSlotClaim(owner, job.output()));
                job.successor().ifPresent(slot -> result.add(new ContainerSlotClaim(owner, slot)));
                job.equipment().ifPresent(slot -> result.add(new ContainerSlotClaim(owner, slot)));
            }
            return List.copyOf(result);
        });
    }
    static void validate(Map<SubjectId, ContainerRecord> containers, Map<SubjectId, ResourceSiteLifecycle> sites) {
        var jobs = declarations(sites);
        if (!DESTINATIONS.uniqueMemberships(HarvestContainerReservations.class, jobs, Declaration::destinations))
            throw new IllegalArgumentException("active harvest output slot lacks exclusive container capacity");
        // Future capacity remains a hint, not exclusive authority over a player's chest.
        CONTAINER_CHECKS.validate(HarvestContainerReservations.class, jobs, containers);
    }
}
