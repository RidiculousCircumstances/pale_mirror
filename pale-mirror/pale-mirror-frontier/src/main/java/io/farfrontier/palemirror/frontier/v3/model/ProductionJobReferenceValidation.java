package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Source;
import java.util.List;
import java.util.Map;

/** Production owns its declared title, employer, task, facility and worker reads.
 * The shared index selects checks; it never supplies a missing relationship. */
final class ProductionJobReferenceValidation {
    private record Context(FrontierBootstrap bootstrap, ExactInventory inventory, CompanyRegistry companies,
                           Map<SubjectId, ResidentProfile> residents, Map<SubjectId, StrategicTask> tasks) { }
    private static final Source<Context, SubjectId, EconomicAccount> ACCOUNTS = new Source<>(c -> c.inventory().economics().accounts());
    private static final Source<Context, SubjectId, Company> COMPANIES = new Source<>(c -> c.companies().companies());
    private static final Source<Context, SubjectId, ContainerRecord> CONTAINERS = new Source<>(c -> c.inventory().containers());
    private static final Source<Context, SubjectId, ResidentProfile> RESIDENTS = new Source<>(Context::residents);
    private static final Source<Context, SubjectId, StrategicTask> TASKS = new Source<>(Context::tasks);
    private static final ImmutableReferenceIndex<Context, SubjectId, ProductionJob> CHECKS =
            new ImmutableReferenceIndex<>(SimulationCapacity.MAX_RESIDENTS, 7,
                    List.of(ACCOUNTS, COMPANIES, CONTAINERS, RESIDENTS, TASKS), (c, job) -> List.of(
                        ACCOUNTS.at(job.rights().resourceOwner().id()), ACCOUNTS.at(job.rights().employer().id()),
                        COMPANIES.at(job.rights().resourceOwner().id()), COMPANIES.at(job.rights().employer().id()),
                        CONTAINERS.at(job.rights().destinationContainerId()), RESIDENTS.at(job.workerId()), TASKS.at(job.taskId())),
                    ProductionJobReferenceValidation::validate);

    static void validate(FrontierBootstrap bootstrap, ExactInventory inventory, CompanyRegistry companies,
                         Map<SubjectId, ResidentProfile> residents, Map<SubjectId, StrategicTask> tasks,
                         Map<SubjectId, ProductionJob> jobs) {
        var context = new Context(bootstrap, inventory, companies, residents, tasks);
        if (jobs.size() > SimulationCapacity.MAX_RESIDENTS) jobs.forEach((id, job) -> validate(context, id, job));
        else CHECKS.validate(bootstrap, jobs, context);
    }
    private static void validate(Context context, SubjectId id, ProductionJob job) {
        job.rights().validate(context.inventory(), context.companies(), job);
        if (!id.equals(job.id())) throw new IllegalArgumentException("production job map key must match job identity");
        var task = context.tasks().get(job.taskId());
        if (task == null || task.kind() != StrategicTaskKind.PRODUCE_AT_STATION || !task.ownerId().equals(job.settlementId())
                || task.status() != StrategicTaskStatus.ACTIVE && task.status() != StrategicTaskStatus.BLOCKED)
            throw new IllegalArgumentException("production job must retain its exact active or blocked strategic task: " + job.taskId().value());
        var settlement = FrontierWorldStateSupport.settlement(context.bootstrap(), job.settlementId());
        var facility = FrontierWorldStateSupport.structure(settlement, job.facilityId());
        if (job.stationWork().isEmpty() && facility.kind() != StructureKind.WORKSHOP)
            throw new IllegalArgumentException("legacy production job facility must be a workshop");
        var worker = context.residents().get(job.workerId());
        if (worker == null) throw new IllegalArgumentException("production job worker must be a canonical resident");
        if (!worker.settlementId().equals(settlement.id()))
            throw new IllegalArgumentException("bread production job worker must belong to its retained settlement");
    }
    private ProductionJobReferenceValidation() { }
}
