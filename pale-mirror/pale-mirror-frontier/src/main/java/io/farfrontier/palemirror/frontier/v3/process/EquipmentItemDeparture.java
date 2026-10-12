package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Exact equipment commitments cancel before source departure; begun effects retain inspection. */
final class EquipmentItemDeparture {
    private EquipmentItemDeparture() { }

    static boolean committed(FrontierWorldState state, SubjectId item, PhysicalIntentLifecycleOwner owner) {
        return !commitments(state, item, owner).isEmpty();
    }

    private static List<PhysicalIntent> commitments(FrontierWorldState state, SubjectId item, PhysicalIntentLifecycleOwner owner) {
        return state.physicalIntents().values().stream().filter(intent -> intent.lifecycleOwner() == owner
                && (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                && intent.status() != PhysicalIntentStatus.CONFIRMED
                && intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT).equals(item))
                .peek(EquipmentServiceBoundary::capability).sorted(Comparator.comparing(PhysicalIntent::id)).toList();
    }

    static List<ProposedEvent> plan(FrontierWorldState state, FrontierCommand command, SubjectId item,
            ProposedEvent observation, PhysicalIntentLifecycleOwner owner) {
        var events = new ArrayList<ProposedEvent>();
        for (var intent : commitments(state, item, owner)) {
            if (intent.status() == PhysicalIntentStatus.CONFLICTED) continue;
            var binding = state.fencedRecovery().current().get(FencedRecoveryPhysicalIntentSupport.bindingId(intent));
            if (intent.status() != PhysicalIntentStatus.PREPARED || binding == null || binding.phase() != FencedRecoveryPhase.PREPARED
                    || ReferenceContainerCustody.blocksCanonicalUse(state, EquipmentPhysicalAuthority.container(intent))
                    || ContainerPhysicalAuthorityComposition.pending(state, EquipmentPhysicalAuthority.container(intent)))
                throw new IllegalArgumentException("equipment source departure requires inspection of its begun or unresolved exact transfer");
            var transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFLICTED, Optional.empty());
            var plan = FrontierWorldProcessCatalog.physicalLifecycles().planTransition(state, command, intent, transition);
            if (!(plan instanceof io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted accepted))
                throw new IllegalArgumentException("equipment source departure cannot retire its exact prepared transfer: " + plan);
            events.addAll(accepted.events());
        }
        events.add(observation);
        return List.copyOf(events);
    }

    static FrontierWorldState observe(FrontierWorldState state, SubjectId item, InventoryCustody source,
            ExactInventory inventory, PhysicalIntentLifecycleOwner owner) {
        for (var intent : commitments(state, item, owner)) {
            if (intent.status() != PhysicalIntentStatus.CONFLICTED)
                throw new IllegalArgumentException("equipment source departure lacks its preceding exact transfer cancellation");
            state = FrontierWorldProcessCatalog.physicalLifecycles().compactCancelledBeforeEffect(state, intent);
        }
        return state.withInventory(inventory);
    }

    /** The exact pre-effect tombstone is produced by the common authority, never guessed from job state. */
    static boolean mayCompact(FrontierWorldState state, PhysicalIntent intent) {
        if (PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery().mayCompact(state, intent)) return true;
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE && intent.kind() != PhysicalIntentKind.EQUIPMENT_RETURN
                || intent.status() != PhysicalIntentStatus.CONFLICTED || intent.postconditionObservationId().isPresent()) return false;
        EquipmentServiceBoundary.capability(intent);
        return PhysicalIntentResolvedRetentionPolicy.cancelledBeforeEffect().mayCompact(state, intent);
    }
}
