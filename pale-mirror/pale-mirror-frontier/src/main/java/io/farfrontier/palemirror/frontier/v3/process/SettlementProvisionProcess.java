package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact daily food provisioning.  One living resident needs one ordinary bread item per cycle;
 * there is no population multiplier or aggregate reserve.  An active depot is changed only by
 * the matching physical executor, while an unmaterialized depot is a legitimate COLD custody.
 */
public final class SettlementProvisionProcess {
    public static final String BREAD = "minecraft:bread";
    private SettlementProvisionProcess() { }

    public static ScheduledAction review(SubjectId settlementId, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:settlement-provision-review-" + suffix(settlementId) + "-" + ordinal),
                new SimInstant(dueAt), 0, settlementId, "frontier.settlement.provision.review", 1);
    }

    public static List<ProposedEvent> planReview(FrontierWorldState state, ScheduledAction action) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), action.subject());
        SettlementProvision current = state.humanPopulation().provision(settlement.id()); int nextOrdinal = nextReviewOrdinal(action);
        List<ProposedEvent> events = new ArrayList<>();
        events.add(schedule(review(settlement.id(), nextOrdinal,
                Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().provisionReviewInterval()))));
        if (current.status() == SettlementProvisionStatus.IN_PROGRESS) return List.copyOf(events);
        List<SubjectId> recipients = recipients(state, settlement.id(), nextOrdinal);
        SettlementProvision provision = SettlementProvision.started(settlement.id(), nextOrdinal, action.dueAt().ticks(), recipients.size(), recipients,
                allocations(state, settlement.id(), recipients));
        events.add(new ProposedEvent(settlement.id(), new SettlementProvisionStarted(provision)));
        if (provision.status() == SettlementProvisionStatus.IN_PROGRESS) events.add(schedule(progress(provision, action.dueAt().ticks() + 1L)));
        return List.copyOf(events);
    }

    /** Derived, bounded food reserve; inventory remains the sole stock ledger. */
    public static int reserveRequirement(FrontierWorldState state, SubjectId settlementId) {
        int living = Math.toIntExact(state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE).count());
        SettlementProvision provision = state.humanPopulation().provision(settlementId);
        int pending = provision.status() == SettlementProvisionStatus.IN_PROGRESS ? provision.requiredRations() - provision.fulfilledRations() : 0;
        return Math.addExact(Math.multiplyExact(living, 2), pending);
    }

    public static int availableFood(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        return state.inventory().items().values().stream().filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
    }

    public static java.util.Optional<ExactItemStack> exportableBread(FrontierWorldState state, SubjectId settlementId) {
        int reserve = reserveRequirement(state, settlementId); int available = availableFood(state, settlementId); SubjectId depot = FrontierWorldState.depotId(settlementId);
        return state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id)).filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot))
                .filter(item -> item.count() == 64 && available - item.count() >= reserve).findFirst();
    }

    public static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        SettlementProvision provision = state.humanPopulation().provision(action.subject());
        if (provision.status() != SettlementProvisionStatus.IN_PROGRESS || provision.activeIntentId().isPresent()
                || !action.id().equals(progress(provision, action.dueAt().ticks()).id())) return List.of();
        SettlementRationAllocation allocation = provision.currentAllocation(); ExactItemStack item = state.inventory().items().get(allocation.itemId());
        SubjectId depot = FrontierWorldState.depotId(provision.settlementId());
        if (item == null || !BREAD.equals(item.itemKind()) || item.count() < allocation.count()
                || !(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(depot)) {
            return List.of(new ProposedEvent(provision.settlementId(), new SettlementProvisionResolved(provision.settlementId(), SettlementProvisionStatus.CONFLICT)));
        }
        ContainerSurface surface = state.inventory().surfaces().get(depot);
        // The converted depot's visible surface is only a locator.  Its exact current
        // replica lease, rather than ACTIVE history, is the sole physical-consumption
        // authority.  During a released re-emission boundary COLD must retain the same
        // canonical path instead of preparing an unfenced physical consumption intent.
        boolean referenceDepot = ReferenceContainerCustody.isReferenceContainer(state, depot);
        boolean physicalCustody = referenceDepot && ReferenceContainerCustody.hasLiveCustody(state, depot);
        if (surface.status() == ContainerSurfaceStatus.UNMATERIALIZED || (referenceDepot && !physicalCustody)) {
            List<ProposedEvent> events = new ArrayList<>();
            events.add(new ProposedEvent(provision.settlementId(), new SettlementProvisionConsumed(provision.settlementId(), item.id(), allocation.count())));
            if (provision.nextAllocation() + 1 < provision.allocations().size()) {
                events.add(schedule(progressAfter(provision, action.dueAt().ticks() + 1L)));
            }
            return List.copyOf(events);
        }
        if (surface.status() != ContainerSurfaceStatus.ACTIVE) {
            return List.of(new ProposedEvent(provision.settlementId(), new SettlementProvisionResolved(provision.settlementId(), SettlementProvisionStatus.CONFLICT)));
        }
        PhysicalIntent intent = intent(state.bootstrap(), provision, allocation);
        return List.of(new ProposedEvent(provision.settlementId(), new PhysicalIntentPrepared(intent)));
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        SettlementProvision provision = provisionForIntent(state, intent);
        List<ProposedEvent> events = new ArrayList<>(); events.add(new ProposedEvent(provision.settlementId(), transition));
        if (transition.status() == PhysicalIntentStatus.CONFIRMED && provision.nextAllocation() + 1 < provision.allocations().size()) {
            events.add(schedule(progressAfter(provision, now + 1L)));
        }
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            events.add(new ProposedEvent(provision.settlementId(), new SettlementProvisionResolved(provision.settlementId(), SettlementProvisionStatus.CONFLICT)));
        }
        return List.copyOf(events);
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, SettlementProvisionStarted started) {
        SettlementProvision provision = started.provision();
        if (!subject.equals(provision.settlementId()) || state.humanPopulation().provision(subject).status() == SettlementProvisionStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("settlement provision start lacks an idle owner");
        }
        HumanPopulation population = state.humanPopulation().withProvision(provision);
        if (provision.status() == SettlementProvisionStatus.SHORTAGE) population = resolveUnserved(population, provision, 0);
        return state.withHumanPopulation(population);
    }

    /** Upgrades the retained count-only v57 start event before it reaches the current recipient-aware owner. */
    public static FrontierWorldState reduceLegacyStarted(FrontierWorldState state, SubjectId subject, LegacySettlementProvisionStarted legacy) {
        List<SubjectId> recipients = recipients(state, legacy.settlementId(), legacy.cycleOrdinal());
        if (!subject.equals(legacy.settlementId()) || legacy.requiredRations() != recipients.size() || legacy.fulfilledRations() != 0
                || legacy.nextAllocation() != 0 || legacy.status() == SettlementProvisionStatus.CONFLICT) {
            throw new IllegalArgumentException("legacy provision event cannot be losslessly upgraded");
        }
        List<SettlementRationAllocation> allocations = new ArrayList<>(); int nextRecipient = 0;
        for (LegacySettlementProvisionStarted.LegacyAllocation allocation : legacy.allocations()) {
            int end = Math.addExact(nextRecipient, allocation.count());
            if (end > recipients.size()) throw new IllegalArgumentException("legacy provision allocation exceeds current recipients");
            allocations.add(new SettlementRationAllocation(allocation.itemId(), recipients.subList(nextRecipient, end))); nextRecipient = end;
        }
        SettlementProvision upgraded = SettlementProvision.started(legacy.settlementId(), legacy.cycleOrdinal(), legacy.startedAtTick(), legacy.requiredRations(),
                recipients, allocations);
        if (upgraded.status() != legacy.status()) throw new IllegalArgumentException("legacy provision terminal status cannot be losslessly upgraded");
        return reduceStarted(state, subject, new SettlementProvisionStarted(upgraded));
    }

    public static FrontierWorldState reduceConsumed(FrontierWorldState state, SubjectId subject, SettlementProvisionConsumed consumed) {
        SettlementProvision provision = state.humanPopulation().provision(consumed.settlementId());
        if (!subject.equals(consumed.settlementId())) throw new IllegalArgumentException("settlement provision consumption has a foreign owner");
        return consume(state, provision, consumed.itemId(), consumed.count(), false);
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        SettlementProvision provision = provisionForPreparedIntent(state, intent);
        if (!subject.equals(provision.settlementId())) throw new IllegalArgumentException("settlement provision intent has a foreign owner");
        return state.withHumanPopulation(state.humanPopulation().withProvision(provision.beginPhysical(intent.id()))).preparePhysicalIntent(intent);
    }

    public static FrontierWorldState reducePhysicalConsumptionAfterInventory(FrontierWorldState state, PhysicalIntent intent, ExactItemConsumedObservation consumed) {
        SettlementProvision provision = provisionForIntent(state, intent);
        SettlementRationAllocation allocation = provision.currentOrActiveAllocation();
        if (!allocation.itemId().equals(consumed.itemId()) || allocation.count() != consumed.consumedCount()) {
            throw new IllegalArgumentException("physical settlement provision receipt does not match its allocation");
        }
        HumanPopulation population = feedCurrentAllocation(state.humanPopulation(), provision);
        if (provision.nextAllocation() + 1 == provision.allocations().size()) {
            population = resolveUnserved(population, provision, provision.nextAllocation() + 1);
        }
        return state.withHumanPopulation(population.withProvision(provision.consumeCurrent(consumed.itemId(), consumed.consumedCount())));
    }

    public static FrontierWorldState reduceResolved(FrontierWorldState state, SubjectId subject, SettlementProvisionResolved resolved) {
        SettlementProvision provision = state.humanPopulation().provision(resolved.settlementId());
        if (!subject.equals(resolved.settlementId()) || provision.status() != SettlementProvisionStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("settlement provision resolution lacks an active owner");
        }
        SettlementProvision next = resolved.status() == SettlementProvisionStatus.CONFLICT ? provision.conflict() : provision.shortage();
        HumanPopulation population = state.humanPopulation().withProvision(next);
        return state.withHumanPopulation(resolved.status() == SettlementProvisionStatus.CONFLICT ? population
                : resolveUnserved(population, provision, provision.nextAllocation()));
    }

    private static FrontierWorldState consume(FrontierWorldState state, SettlementProvision provision, SubjectId itemId, int count, boolean physical) {
        SettlementRationAllocation allocation = provision.currentOrActiveAllocation();
        ExactItemStack item = state.inventory().items().get(itemId); SubjectId depot = FrontierWorldState.depotId(provision.settlementId());
        if (!allocation.itemId().equals(itemId) || allocation.count() != count || item == null || !BREAD.equals(item.itemKind()) || item.count() < count
                || !(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(depot)) {
            throw new IllegalArgumentException("settlement provision consumption does not match its exact food allocation");
        }
        ContainerSurfaceStatus surface = state.inventory().surfaces().get(depot).status();
        boolean physicalBoundary = ReferenceContainerCustody.isReferenceContainer(state, depot)
                ? ReferenceContainerCustody.hasLiveCustody(state, depot)
                : surface == ContainerSurfaceStatus.ACTIVE;
        if (physical != physicalBoundary) throw new IllegalArgumentException("settlement provision crossed the wrong physical custody boundary");
        HumanPopulation population = feedCurrentAllocation(state.humanPopulation(), provision);
        if (provision.nextAllocation() + 1 == provision.allocations().size()) population = resolveUnserved(population, provision, provision.nextAllocation() + 1);
        return state.withInventory(state.inventory().consume(itemId, count))
                .withHumanPopulation(population.withProvision(provision.consumeCurrent(itemId, count)));
    }

    private static SettlementProvision provisionForIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("not a settlement provision intent");
        SettlementProvision provision = state.humanPopulation().provision(intent.causeSubjectId());
        if (provision.activeIntentId().filter(intent.id()::equals).isEmpty()) throw new IllegalArgumentException("exact consumption has no active settlement provision");
        SettlementRationAllocation allocation = provision.currentOrActiveAllocation();
        if (!intent.subjectIds().equals(List.of(provision.settlementId(), allocation.itemId()))) throw new IllegalArgumentException("settlement provision intent names a foreign allocation");
        return provision;
    }

    private static SettlementProvision provisionForPreparedIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("not a settlement provision intent");
        SettlementProvision provision = state.humanPopulation().provision(intent.causeSubjectId());
        if (provision.status() != SettlementProvisionStatus.IN_PROGRESS || provision.activeIntentId().isPresent()) {
            throw new IllegalArgumentException("settlement provision is not ready to prepare an exact consumption");
        }
        SettlementRationAllocation allocation = provision.currentAllocation();
        if (!intent.id().equals(intent(state.bootstrap(), provision, allocation).id())
                || !intent.subjectIds().equals(List.of(provision.settlementId(), allocation.itemId()))) {
            throw new IllegalArgumentException("settlement provision prepared intent names a foreign allocation");
        }
        return provision;
    }

    private static PhysicalIntent intent(FrontierBootstrap bootstrap, SettlementProvision provision, SettlementRationAllocation allocation) {
        String suffix = suffix(provision.settlementId()) + "-" + provision.cycleOrdinal() + "-" + provision.nextAllocation();
        Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, provision.settlementId());
        return new PhysicalIntent(new PhysicalIntentId("intent:settlement-provision-" + suffix), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
                PhysicalIntentStatus.PREPARED, provision.settlementId(), List.of(provision.settlementId(), allocation.itemId()),
                new FixedPosition(FixedScalar.whole(settlement.anchor().x()), FixedScalar.whole(settlement.anchor().y()), FixedScalar.whole(settlement.anchor().z())),
                0, PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED);
    }

    private static List<SettlementRationAllocation> allocations(FrontierWorldState state, SubjectId settlementId, List<SubjectId> recipients) {
        int nextRecipient = 0; List<SettlementRationAllocation> allocations = new ArrayList<>(); SubjectId depot = FrontierWorldState.depotId(settlementId);
        for (ExactItemStack item : state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id)).toList()) {
            if (nextRecipient == recipients.size()) break;
            if (!BREAD.equals(item.itemKind()) || !(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(depot)) continue;
            int count = Math.min(recipients.size() - nextRecipient, item.count());
            allocations.add(new SettlementRationAllocation(item.id(), recipients.subList(nextRecipient, nextRecipient + count))); nextRecipient += count;
        }
        return List.copyOf(allocations);
    }

    /** Rotating exact order keeps a persistent shortage from always selecting lexicographically first residents. */
    private static List<SubjectId> recipients(FrontierWorldState state, SubjectId settlementId, int cycleOrdinal) {
        List<SubjectId> values = state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .map(ResidentProfile::id).sorted().collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (values.isEmpty()) return List.of();
        java.util.Collections.rotate(values, -Math.floorMod(cycleOrdinal - 1, values.size()));
        return List.copyOf(values);
    }

    private static HumanPopulation feedCurrentAllocation(HumanPopulation population, SettlementProvision provision) {
        for (SubjectId residentId : provision.currentOrActiveAllocation().recipientIds()) {
            population = population.resolveNutrition(residentId, provision.cycleOrdinal(), true);
        }
        return population;
    }

    /** Only allocations already confirmed by this cycle count as food actually eaten. */
    private static HumanPopulation resolveUnserved(HumanPopulation population, SettlementProvision provision, int confirmedAllocationCount) {
        if (confirmedAllocationCount < 0 || confirmedAllocationCount > provision.allocations().size()) {
            throw new IllegalArgumentException("invalid confirmed provision allocation count");
        }
        java.util.Set<SubjectId> served = provision.allocations().subList(0, confirmedAllocationCount).stream()
                .flatMap(allocation -> allocation.recipientIds().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (SubjectId residentId : provision.recipientIds()) if (!served.contains(residentId)) population = population.resolveNutrition(residentId, provision.cycleOrdinal(), false);
        return population;
    }

    private static ScheduledAction progress(SettlementProvision provision, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:settlement-provision-progress-" + suffix(provision.settlementId()) + "-" + provision.cycleOrdinal() + "-" + provision.nextAllocation()),
                new SimInstant(dueAt), 0, provision.settlementId(), "frontier.settlement.provision.progress", 1);
    }

    private static ScheduledAction progressAfter(SettlementProvision provision, long dueAt) {
        SettlementProvision next = new SettlementProvision(provision.settlementId(), provision.cycleOrdinal(), provision.startedAtTick(), provision.requiredRations(),
                provision.fulfilledRations(), provision.recipientIds(), provision.allocations(), provision.nextAllocation() + 1, SettlementProvisionStatus.IN_PROGRESS,
                java.util.Optional.empty());
        return progress(next, dueAt);
    }

    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static int nextReviewOrdinal(ScheduledAction action) {
        String prefix = "schedule:settlement-provision-review-" + suffix(action.subject()) + "-";
        if (!action.id().value().startsWith(prefix)) throw new IllegalStateException("settlement provision review has invalid stable identity");
        return Math.addExact(Integer.parseInt(action.id().value().substring(prefix.length())), 1);
    }
    private static String suffix(SubjectId settlementId) { return settlementId.value().substring("settlement:".length()); }
}
