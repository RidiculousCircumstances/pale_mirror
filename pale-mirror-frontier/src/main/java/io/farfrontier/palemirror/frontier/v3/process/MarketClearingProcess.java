package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded deterministic clearing for production demand; it has no global inventory or money pool. */
public final class MarketClearingProcess {
    private static final String BREAD = "minecraft:bread";
    private MarketClearingProcess() { }

    public static MarketDemand foodDemand(FrontierWorldState state, StrategicTask task, long openedAt) {
        if (task.kind() != StrategicTaskKind.PRODUCE_BREAD) throw new IllegalArgumentException("only bread work creates the current food demand");
        String suffix = task.id().value().substring("task:".length());
        return new MarketDemand(new SubjectId("demand:" + suffix), task.ownerId(), task.id(), BREAD, 64,
                state.bootstrap().ruleset().rates().worksJobPrice(), openedAt,
                Math.addExact(openedAt, state.bootstrap().ruleset().cadence().marketDemandLifetime()), MarketDemandStatus.OPEN);
    }

    public static ScheduledAction clear(MarketDemand demand, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:market-clear-" + demand.id().value().substring("demand:".length()) + "-" + ordinal),
                new SimInstant(dueAt), 0, demand.id(), "frontier.market.clear", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        MarketDemand demand = state.companies().market().demands().get(action.subject());
        if (demand == null || demand.status() != MarketDemandStatus.OPEN) return List.of();
        long now = action.dueAt().ticks();
        if (now > demand.expiresAtTick()) return List.of(new ProposedEvent(demand.buyerId(), new MarketDemandExpired(demand.id())),
                taskTransition(state, demand, StrategicTaskStatus.PENDING, StrategicTaskStatus.BLOCKED));
        StrategicTask task = state.strategicPlans().tasks().get(demand.reasonId());
        if (task == null || task.status() != StrategicTaskStatus.PENDING || !task.ownerId().equals(demand.buyerId()) || task.kind() != StrategicTaskKind.PRODUCE_BREAD) {
            return List.of(new ProposedEvent(demand.buyerId(), new MarketDemandCancelled(demand.id(), MarketDemandCancellationReason.TASK_NO_LONGER_PENDING)));
        }
        List<ProposedEvent> start = ProductionProcess.planStart(state, ProductionProcess.start(task, now));
        ProductionStarted started = start.stream().map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElse(null);
        if (started == null) {
            if (start.stream().map(ProposedEvent::payload).noneMatch(ProductionBlocked.class::isInstance)) {
                throw new IllegalStateException("market clearing failed without a terminal production decision");
            }
            List<ProposedEvent> cancelled = new ArrayList<>();
            cancelled.add(new ProposedEvent(demand.buyerId(), new MarketDemandCancelled(demand.id(), MarketDemandCancellationReason.PRODUCTION_BLOCKED)));
            cancelled.addAll(start);
            return List.copyOf(cancelled);
        }
        ProductionJob job = started.job();
        EmploymentContract jobContract = CompanyWorkPaymentProcess.contractFor(state, job).orElse(null);
        // A quote is commercial evidence for one exact prospective job, not an offer from an
        // arbitrarily selected company founder.  Production chooses the available worker first;
        // only that worker's active contract can price and reserve the work.  If an old quote
        // survived while availability or terms changed, retain the open demand and retry rather
        // than admitting a job whose financial cause cannot be proven.
        if (jobContract == null) return retry(state, demand, action, now);
        Company company = state.companies().companies().get(jobContract.companyId());
        if (company == null || company.status() != CompanyStatus.ACTIVE) return retry(state, demand, action, now);
        Optional<CompanyQuote> existing = state.companies().market().bestCurrentQuote(demand.id(), now);
        if (existing.isPresent() && (!jobContract.companyId().equals(existing.orElseThrow().sellerId())
                || !jobContract.invoicePerCompletedJob().equals(existing.orElseThrow().totalPrice()))) {
            return retry(state, demand, action, now);
        }
        List<ProposedEvent> planned = new ArrayList<>();
        CompanyQuote quote = existing.orElseGet(() -> new CompanyQuote(new SubjectId("quote:"
                + demand.id().value().substring("demand:".length()) + "-" + jobContract.companyId().value().substring("company:".length())),
                demand.id(), company.id(), demand.itemCount(), jobContract.invoicePerCompletedJob(), now, demand.expiresAtTick()));
        FinancialReservation reservation = CompanyWorkPaymentProcess.reservation(job, jobContract);
        MarketWorkOrder order = new MarketWorkOrder(new SubjectId("order:" + job.id().value().substring("job:".length())), demand.id(), quote.id(), quote.sellerId(),
                task.id(), job.id(), reservation.id(), quote.totalPrice(), MarketWorkOrderStatus.ACCEPTED);
        if (existing.isEmpty()) planned.add(new ProposedEvent(company.id(), new MarketQuotePublished(quote)));
        planned.add(new ProposedEvent(demand.buyerId(), new MarketWorkOrderAccepted(order))); planned.addAll(start);
        return List.copyOf(planned);
    }

    public static FrontierWorldState reduceOpened(FrontierWorldState state, SubjectId subject, MarketDemandOpened opened) {
        MarketDemand demand = opened.demand(); StrategicTask task = state.strategicPlans().tasks().get(demand.reasonId());
        if (!subject.equals(demand.buyerId()) || task == null || task.kind() != StrategicTaskKind.PRODUCE_BREAD || task.status() != StrategicTaskStatus.PENDING
                || !task.ownerId().equals(demand.buyerId()) || !foodDemand(state, task, demand.openedAtTick()).equals(demand)) {
            throw new IllegalArgumentException("market food demand must be emitted for its exact pending production task");
        }
        MarketOrderBook compacted = state.companies().market().compactTerminal(protectedReferences(state));
        return state.withCompanies(state.companies().withMarket(compacted.open(demand)));
    }

    public static FrontierWorldState reduceQuote(FrontierWorldState state, SubjectId subject, long now, MarketQuotePublished published) {
        CompanyQuote quote = published.quote(); Company company = state.companies().companies().get(quote.sellerId());
        if (company == null || !subject.equals(company.id()) || company.status() != CompanyStatus.ACTIVE) {
            throw new IllegalArgumentException("market quote must be published by its active company");
        }
        return state.withCompanies(state.companies().withMarket(state.companies().market().publish(quote, now)));
    }

    public static FrontierWorldState reduceAccepted(FrontierWorldState state, SubjectId subject, long now, MarketWorkOrderAccepted accepted) {
        MarketWorkOrder order = accepted.order(); MarketDemand demand = state.companies().market().demands().get(order.demandId());
        if (demand == null || !subject.equals(demand.buyerId()) || !order.taskId().equals(demand.reasonId())) {
            throw new IllegalArgumentException("market work order must be accepted by its exact buyer and task");
        }
        return state.withCompanies(state.companies().withMarket(state.companies().market().accept(order, now)));
    }

    public static FrontierWorldState reduceWorkOrderCancelled(FrontierWorldState state, SubjectId subject, MarketWorkOrderCancelled cancelled) {
        MarketWorkOrder order = state.companies().market().workOrders().get(cancelled.orderId());
        ProductionJob job = state.productionJobs().get(cancelled.jobId());
        if (order == null || job == null || order.status() != MarketWorkOrderStatus.ACCEPTED || !order.jobId().equals(job.id())) {
            throw new IllegalArgumentException("market cancellation must name one accepted active work order");
        }
        MarketDemand demand = state.companies().market().demands().get(order.demandId());
        StrategicTask task = demand == null ? null : state.strategicPlans().tasks().get(demand.reasonId());
        if (demand == null || !subject.equals(demand.buyerId()) || task == null || task.status() != StrategicTaskStatus.ACTIVE
                || !order.taskId().equals(task.id()) || !job.settlementId().equals(subject)
                || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id())
                && (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.PRODUCTION_TRANSFORMATION
                || intent.status() != PhysicalIntentStatus.PREPARED))
                || (cancelled.reason() != ProductionBlockReason.WORKER_UNAVAILABLE && state.physicalIntents().values().stream()
                .anyMatch(intent -> intent.causeSubjectId().equals(job.id())))) {
            throw new IllegalArgumentException("market cancellation may only release work before any physical production intent");
        }
        boolean allowed = switch (cancelled.reason()) {
            case FACILITY_UNAVAILABLE -> state.structureConditions().get(job.facilityId()) != StructureCondition.INTACT;
            case INPUT_UNAVAILABLE -> job.inputHold() instanceof ProductionInputHold.Materialized
                    && !materializedInputMatches(state, job);
            case WORKER_UNAVAILABLE -> state.actorLocations().get(job.workerId()).condition().status() != ActorLifeStatus.ALIVE
                    || CompanyWorkPaymentProcess.contractFor(state, job).isEmpty();
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("market cancellation reason no longer holds");
        FinancialReservation reservation = state.inventory().economics().reservations().get(order.reservationId());
        if (reservation == null || !reservation.reasonId().equals(job.id()) || !reservation.payerId().equals(job.settlementId())
                || !reservation.payeeId().equals(order.sellerId()) || !reservation.amount().equals(order.acceptedTotalPrice())) {
            throw new IllegalArgumentException("market cancellation reservation no longer matches its order");
        }
        FrontierWorldState restored = state.cancelProductionJob(job.id());
        ExactInventory inventory = restored.inventory().withEconomics(restored.inventory().economics().release(order.reservationId()));
        StrategicPlanState plans = restored.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.BLOCKED);
        return restored.withInventory(inventory).withCompanies(restored.companies().withMarket(restored.companies().market().cancel(order.id(), MarketWorkOrderStatus.CANCELLED)))
                .withStrategicPlans(plans);
    }

    public static FrontierWorldState reduceExpired(FrontierWorldState state, SubjectId subject, long now, MarketDemandExpired expired) {
        MarketDemand demand = state.companies().market().demands().get(expired.demandId());
        if (demand == null || !subject.equals(demand.buyerId()) || demand.status() != MarketDemandStatus.OPEN || now <= demand.expiresAtTick()) {
            throw new IllegalArgumentException("market expiry must name an overdue open demand");
        }
        return state.withCompanies(state.companies().withMarket(state.companies().market().expireOpen(now)));
    }

    public static FrontierWorldState reduceCancelled(FrontierWorldState state, SubjectId subject, MarketDemandCancelled cancelled) {
        MarketDemand demand = state.companies().market().demands().get(cancelled.demandId());
        StrategicTask task = demand == null ? null : state.strategicPlans().tasks().get(demand.reasonId());
        if (demand == null || !subject.equals(demand.buyerId()) || task == null || !task.ownerId().equals(subject)
                || task.kind() != StrategicTaskKind.PRODUCE_BREAD || task.status() != StrategicTaskStatus.PENDING) {
            throw new IllegalArgumentException("market cancellation must close its exact pending food task demand");
        }
        return state.withCompanies(state.companies().withMarket(state.companies().market().cancelOpen(demand.id())));
    }


    private static boolean materializedInputMatches(FrontierWorldState state, ProductionJob job) {
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        return input != null && input.economicOwnerId().equals(job.settlementId()) && "minecraft:wheat".equals(input.itemKind())
                && input.count() == job.outputCount() && input.custody() instanceof InventoryCustody.ContainerSlot slot
                && slot.containerId().equals(FrontierWorldState.depotId(job.settlementId()));
    }
    private static List<ProposedEvent> retry(FrontierWorldState state, MarketDemand demand, ScheduledAction action, long now) {
        return List.of(new ProposedEvent(demand.id(), new ScheduleEffect.Created(clear(demand, nextOrdinal(action), Math.addExact(now,
                state.bootstrap().ruleset().cadence().marketRetryInterval())))));
    }
    private static int nextOrdinal(ScheduledAction action) {
        String suffix = action.id().value().substring(action.id().value().lastIndexOf('-') + 1);
        return Math.addExact(Integer.parseInt(suffix), 1);
    }
    private static Set<SubjectId> protectedReferences(FrontierWorldState state) {
        Set<SubjectId> protectedIds = new LinkedHashSet<>();
        protectedIds.addAll(state.productionJobs().keySet()); protectedIds.addAll(state.inventory().economics().reservations().keySet());
        // A confirmed intent has its own immutable observation receipt.  It no
        // longer needs the market's operational record for recovery, whereas a
        // nonterminal intent still does: a restart must be able to inspect its
        // exact live cause and subjects before doing anything in Minecraft.
        state.physicalIntents().values().stream().filter(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED)
                .forEach(intent -> { protectedIds.add(intent.causeSubjectId()); protectedIds.addAll(intent.subjectIds()); });
        state.inventory().cargo().values().forEach(cargo -> { protectedIds.add(cargo.id()); protectedIds.addAll(cargo.itemIds()); });
        return Set.copyOf(protectedIds);
    }
    private static ProposedEvent taskTransition(FrontierWorldState state, MarketDemand demand, StrategicTaskStatus expected, StrategicTaskStatus next) {
        StrategicTask task = state.strategicPlans().tasks().get(demand.reasonId());
        if (task == null || task.status() != expected || !task.ownerId().equals(demand.buyerId())) {
            throw new IllegalStateException("market demand no longer has its expected strategic task");
        }
        return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), next));
    }
}
