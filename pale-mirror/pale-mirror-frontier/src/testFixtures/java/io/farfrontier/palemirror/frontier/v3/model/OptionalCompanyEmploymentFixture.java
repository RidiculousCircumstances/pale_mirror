package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import java.util.ArrayList;
import java.util.List;

/** Explicit optional-company setup for commercial invoice/recovery tests, never production bootstrap. */
public final class OptionalCompanyEmploymentFixture {
    private OptionalCompanyEmploymentFixture() { }
    public static List<ProposedEvent> foundation(FrontierWorldState state, SubjectId home, long tick) {
        var events = new ArrayList<ProposedEvent>();
        var worker = SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.BAKING)
                .stream().sorted().findFirst().orElseThrow();
        var company = new Company(CompanyFoundationProcess.companyId(home), home, worker, CompanyPurpose.WORKS, CompanyStatus.ACTIVE, tick);
        events.addAll(CompanyFoundationProcess.planRegistration(state, company, tick));
        for (var baker : SettlementWorkPolicy.permissions(state, home).workers(ResidentWorkKind.BAKING).stream().sorted().toList())
            events.add(new ProposedEvent(home, new EmploymentContractOpened(
                    SettlementEmploymentProcess.agreement(state, WorkEmployer.company(company), baker, tick))));
        return List.copyOf(events);
    }
    public static ProductionRights publicCompanyService(FrontierWorldState state, SubjectId home) {
        var company = state.companies().companies().get(CompanyFoundationProcess.companyId(home));
        return new ProductionRights(ProductionRights.Mode.BUYER_OWNED_SERVICE,
                GoodsParticipantDeclarations.publicParty(home), FrontierWorldState.depotId(home), WorkEmployer.company(company));
    }
    /** Receipt fixture on the small layout; real due work, not large-field throughput or injected results. */
    public static io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection>
            completedColdService(WorldId world, long seed) {
        var bootstrap = FrontierResourceSiteHarvestFixture.smallFieldBootstrap(world, seed);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(coldServiceConfiguration(bootstrap));
        var budget = new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(256, 1_024);
        engine.advanceTo(new SimInstant(200), budget);
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var state = codec.decode(engine.checkpoint().canonicalState());
        for (int admission = 0; state.productionJobs().isEmpty() && admission < 16; admission++) {
            var next = engine.checkpoint().schedules().stream().filter(action -> action.kind().equals("frontier.market.clear"))
                    .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction::dueAt)
                    .min(java.util.Comparator.naturalOrder()).orElseThrow();
            var target = next.compareTo(engine.checkpoint().instant()) > 0 ? next : engine.checkpoint().instant();
            engine.advanceTo(target, budget);
            state = codec.decode(engine.checkpoint().canonicalState());
        }
        int remaining = state.productionJobs().values().stream().mapToInt(job ->
                job.bakeryWork().isPresent() ? ProductionWorkProgress.REQUIRED_PROCESSING_TICKS + 512
                        : ProductionWorkProgress.REQUIRED_PROCESSING_TICKS + job.workTraversal().linearCorridorSurfaces().size() + 4).sum();
        for (int step = 0; !state.productionJobs().isEmpty() && step < remaining; step++) {
            var jobs = state.productionJobs().keySet();
            var next = engine.checkpoint().schedules().stream().filter(action -> jobs.contains(action.subject()))
                    .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction::dueAt)
                    .min(java.util.Comparator.naturalOrder()).orElseThrow();
            var target = next.compareTo(engine.checkpoint().instant()) > 0 ? next : engine.checkpoint().instant();
            var result = engine.advanceTo(target, budget);
            if (result.status().kind() != EngineStatus.Kind.ACTIVE)
                throw new IllegalStateException(result.status().failureDetail().orElse("commercial fixture stopped"));
            state = codec.decode(engine.checkpoint().canonicalState());
        }
        if (!state.productionJobs().isEmpty()
                || state.companies().market().workOrders().values().stream().noneMatch(order -> order.terminalReceipt().isPresent()))
            throw new IllegalStateException("commercial fixture did not reach its terminal work receipt: tick="
                    + engine.checkpoint().instant() + ", jobs=" + state.productionJobs().keySet()
                    + ", orders=" + state.companies().market().workOrders().values());
        return engine;
    }
    /** Actual optional commercial service queue, distinct from ordinary direct settlement production. */
    public static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>
            coldServiceConfiguration(FrontierBootstrap bootstrap) {
        var state = FrontierWorldState.initial(bootstrap); var home = bootstrap.settlements().getFirst().id();
        for (var event : foundation(state, home, 0)) {
            if (event.payload() instanceof CompanyRegistered registered) state = CompanyFoundationProcess.reduce(state, home, registered);
            if (event.payload() instanceof EmploymentContractOpened opened) state = SettlementEmploymentProcess.reduceEmployment(state, home, opened);
        }
        var objective = new StrategicObjective(new SubjectId("objective:optional-company-service"), home,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, java.util.Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        var task = new StrategicTask(new SubjectId("task:optional-company-service"), objective.id(), home,
                StrategicTaskKind.PRODUCE_BREAD, java.util.Optional.empty(),
                List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT),
                List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));
        var demand = MarketClearingProcess.foodDemand(state, task, 100);
        state = MarketClearingProcess.reduceOpened(state, home, new MarketDemandOpened(demand));
        var base = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(bootstrap);
        return new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(bootstrap.worldId(), state, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(MarketClearingProcess.clear(demand, 1, 200)), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }
}
