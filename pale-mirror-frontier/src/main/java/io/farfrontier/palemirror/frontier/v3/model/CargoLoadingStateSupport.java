package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure exact binding for a physical active-depot to cargo custody hand-off. */
public final class CargoLoadingStateSupport {
    private CargoLoadingStateSupport() { }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.CARGO_LOADING) return;
        SupplyContract contract = state.contracts().get(intent.causeSubjectId());
        if ((intent.status() != PhysicalIntentStatus.PREPARED && intent.status() != PhysicalIntentStatus.RUNNING)
                || contract == null || contract.status() != ContractStatus.ORDERED) {
            throw new IllegalArgumentException("cargo loading requires one ordered supply contract");
        }
        SubjectId itemId = intent.subjectIds().get(2);
        if (!intent.subjectIds().equals(List.of(contract.id(), contract.cargoId(), itemId))) {
            throw new IllegalArgumentException("cargo loading intent has foreign subjects");
        }
        ExactItemStack item = state.inventory().items().get(itemId);
        if (item == null || !item.economicOwnerId().equals(contract.settlementId()) || !item.itemKind().equals(contract.itemKind())
                || item.count() != contract.itemCount() || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierWorldState.depotId(contract.settlementId()))) {
            throw new IllegalArgumentException("cargo loading has no matching exact depot stack");
        }
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE || !intent.origin().equals(fixed(surface.position()))) {
            throw new IllegalArgumentException("cargo loading depot is not active at its canonical position");
        }
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, PhysicalEffectObservation evidence,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        if (!(evidence instanceof CargoLoadObservation receipt)) {
            throw new IllegalArgumentException("cargo loading requires exact depot removal evidence");
        }
        validateIntent(state, intent);
        SupplyContract contract = state.contracts().get(intent.causeSubjectId());
        ExactItemStack item = state.inventory().items().get(intent.subjectIds().get(2));
        if (contract == null || item == null || !intent.id().equals(receipt.intentId()) || !contract.id().equals(receipt.contractId())
                || !contract.cargoId().equals(receipt.cargoId()) || !item.id().equals(receipt.itemId()) || item.count() != receipt.itemCount()) {
            throw new IllegalArgumentException("cargo loading receipt does not match its exact contract stack");
        }
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents = new LinkedHashMap<>(intents);
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(receipt.id(), receipt);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(),
                state.productionJobs(), state.contracts(), state.operations(), nextIntents, observations, state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(),
                state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }

    static void validateReceipt(PhysicalIntent intent, CargoLoadObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.CARGO_LOADING || !intent.id().equals(receipt.intentId())
                || !intent.subjectIds().equals(List.of(receipt.contractId(), receipt.cargoId(), receipt.itemId()))) {
            throw new IllegalArgumentException("cargo-load receipt names a foreign intent or exact stack");
        }
    }

    static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        SupplyContract contract = state.contracts().get(intent.causeSubjectId());
        if (contract == null || !subject.equals(contract.settlementId())) {
            throw new IllegalArgumentException("cargo loading must be prepared by its contract settlement");
        }
        validateIntent(state, intent);
        return state.preparePhysicalIntent(intent);
    }

    static FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                                               PhysicalIntentTransition transition) {
        SupplyContract contract = state.contracts().get(intent.causeSubjectId());
        if (contract == null || !subject.equals(contract.settlementId())) {
            throw new IllegalArgumentException("cargo loading transition lacks contract settlement ownership");
        }
        return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
    }

    /** Pure loaded-world target; adapters must still inspect the exact tagged stack before removal. */
    public static Target target(FrontierWorldState state, PhysicalIntent intent) {
        validateIntent(state, intent);
        SupplyContract contract = state.contracts().get(intent.causeSubjectId());
        ExactItemStack item = state.inventory().items().get(intent.subjectIds().get(2));
        InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) item.custody();
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        return new Target(contract, item, slot, surface.position());
    }

    public record Target(SupplyContract contract, ExactItemStack item, InventoryCustody.ContainerSlot slot,
                         BlockPosition chestPosition) { }

    private static FixedPosition fixed(BlockPosition position) {
        return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z()));
    }
}
