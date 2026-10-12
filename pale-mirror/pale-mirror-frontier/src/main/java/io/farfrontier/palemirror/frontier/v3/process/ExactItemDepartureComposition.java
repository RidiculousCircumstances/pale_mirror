package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactInventory;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Registered exact-source commitments supply disruption policy; the observation owner knows no jobs. */
final class ExactItemDepartureComposition {
    private static final ExactItemDepartureComposition CURRENT = new ExactItemDepartureComposition(List.of(
            new Policy(PhysicalIntentLifecycleOwner.PRODUCTION_WORK, ProductionProcess::hasMaterializedInput,
                    (state, command, item, observation) -> ProductionProcess.planMaterializedInputDeparture(state, item, observation),
                    ProductionProcess::observeMaterializedInputDeparture),
            new Policy(PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT, MedicalTreatmentProcess::hasSupplyCommitment,
                    MedicalTreatmentProcess::planSupplyDeparture, MedicalTreatmentProcess::observeSupplyDeparture),
            equipmentPolicy(PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE),
            new Policy(PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, DefenderEquipmentSourceDeparture::committed,
                    DefenderEquipmentSourceDeparture::plan, DefenderEquipmentSourceDeparture::observe)));
    private final List<Policy> policies;

    ExactItemDepartureComposition(List<Policy> policies) {
        var supplied = EnumSet.noneOf(PhysicalIntentLifecycleOwner.class);
        for (var policy : policies) if (!supplied.add(policy.owner()))
            throw new IllegalArgumentException("duplicate exact-source departure owner");
        if (!supplied.equals(EnumSet.of(PhysicalIntentLifecycleOwner.PRODUCTION_WORK, PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT,
                PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE, PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT)))
            throw new IllegalArgumentException("missing or foreign exact-source departure owner");
        this.policies = List.copyOf(policies);
    }

    private static Policy equipmentPolicy(PhysicalIntentLifecycleOwner owner) {
        return new Policy(owner, (state, item) -> EquipmentItemDeparture.committed(state, item, owner),
                (state, command, item, observation) -> EquipmentItemDeparture.plan(state, command, item, observation, owner),
                (state, item, source, inventory) -> EquipmentItemDeparture.observe(state, item, source, inventory, owner));
    }

    static List<ProposedEvent> plan(FrontierWorldState state, FrontierCommand command, SubjectId item, ProposedEvent observation) {
        var policy = CURRENT.owner(state, item);
        if (policy == null) return List.of(observation);
        var events = List.copyOf(policy.planner().plan(state, command, item, observation));
        if (events.stream().filter(observation::equals).count() != 1)
            throw new IllegalArgumentException("exact-source departure must retain its observation once");
        return events;
    }

    static FrontierWorldState observe(FrontierWorldState state, SubjectId item, InventoryCustody source, ExactInventory inventory) {
        var policy = CURRENT.owner(state, item);
        return policy == null ? state.withInventory(inventory) : policy.reducer().observe(state, item, source, inventory);
    }

    private Policy owner(FrontierWorldState state, SubjectId item) {
        Policy selected = null;
        for (var policy : policies) if (policy.committed().test(state, item)) {
            if (selected != null) throw new IllegalArgumentException("exact stock has competing declared source commitments");
            selected = policy;
        }
        return selected;
    }

    @FunctionalInterface interface Planner {
        List<ProposedEvent> plan(FrontierWorldState state, FrontierCommand command, SubjectId item, ProposedEvent observation);
    }
    @FunctionalInterface interface Observer {
        FrontierWorldState observe(FrontierWorldState state, SubjectId item, InventoryCustody source, ExactInventory inventory);
    }
    record Policy(PhysicalIntentLifecycleOwner owner, BiPredicate<FrontierWorldState, SubjectId> committed,
                  Planner planner, Observer reducer) {
        Policy {
            Objects.requireNonNull(owner, "declared departure owner"); Objects.requireNonNull(committed, "source commitment");
            Objects.requireNonNull(planner, "departure planner"); Objects.requireNonNull(reducer, "departure observer");
        }
    }
}
