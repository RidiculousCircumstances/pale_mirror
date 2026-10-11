package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** The same pre/post protocol as a single container, with one all-member commit/retirement. */
final class FrontierV3CompositeClickExecutor {
    private FrontierV3CompositeClickExecutor() { }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, FrontierV3ContainerClickTransaction transaction) {
        var ledger = FrontierV3DepotClickLedger.get(level);
        var physical = new ArrayList<FrontierV3PhysicalContainer>();
        var layouts = new ArrayList<List<FungiblePhysicalObservation.Stack>>();
        for (var member : transaction.members()) {
            var provider = FrontierV3PhysicalContainer.loaded(level, state, member.containerId()).orElse(null);
            if (provider == null) return false;
            if (!FrontierV3ContainerClickExecutor.exactSlotsCurrent(state, member.containerId(), provider.inventory())
                    || FrontierV3ContainerSurfaceExecutor.hasForeignFungibleComponents(provider.inventory(), state, member.containerId()))
                return conflict(runtime, transaction, "composed member has foreign or changed exact components");
            physical.add(provider);
            layouts.add(FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(provider.inventory(), state, member.containerId()));
        }
        boolean witnessed = transaction.members().getFirst().after().isPresent();
        if (witnessed) {
            for (int i = 0; i < transaction.members().size(); i++)
                if (!transaction.members().get(i).after().orElseThrow().equals(layouts.get(i)))
                    return conflict(runtime, transaction, "composed member diverged from durable postimage");
            // A committed WAL may precede witness retirement; only the complete successor can retire it.
            if (committed(state, transaction, layouts)) return retire(level, runtime, ledger, transaction, physical);
        }
        boolean unchanged = true;
        for (int i = 0; i < layouts.size(); i++) unchanged &= layouts.get(i).equals(transaction.members().get(i).before());
        if (unchanged && !witnessed) { ledger.retire(transaction); ledger.persist(level); return true; }
        var observed = transaction;
        if (!witnessed) {
            Optional<Integer> remaining = Optional.empty();
            var returning = transaction.members().getFirst().returnSource();
            if (returning.isPresent()) {
                var player = level.getServer().getPlayerList().getPlayer(transaction.members().getFirst().playerId());
                if (player == null) return false;
                var source = returning.orElseThrow(); var actual = player.getInventory().getItem(source.playerSlot());
                if (!actual.isEmpty() && !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(source.itemKind()))
                    return conflict(runtime, transaction, "composed player source changed kind");
                remaining = Optional.of(actual.isEmpty() ? 0 : actual.getCount());
            }
            var members = new ArrayList<FrontierV3DepotClickWitness>();
            for (int i = 0; i < layouts.size(); i++) members.add(transaction.members().get(i).observed(layouts.get(i), remaining));
            observed = new FrontierV3ContainerClickTransaction(members);
            ledger.observe(transaction, observed); ledger.persist(level);
        }
        try {
            var first = observed.members().getFirst();
            var source = first.returnSource();
            var returning = source.map(s -> new ContainerPlayerEditObserved.PlayerReturn(s.bindingId(), s.accountId(),
                    s.epoch(), s.playerSlot(), s.itemKind(), s.quantity(), first.returnedRemaining().orElseThrow()));
            var members = observed.members().stream().map(m -> new ContainerPlayerEditObserved.Member(m.containerId(),
                    m.accountId(), m.ownerId(), m.authorityEpoch(), m.before(), m.after().orElseThrow())).toList();
            var payload = new ContainerPlayerEditObserved(first.playerId(), first.interactionId(), members, returning);
            var result = FrontierV3CommandSubmission.submit(runtime, "composed-container-player-edit", first.interactionId().toString(), payload);
            if (result instanceof CommandResult.Accepted) return retire(level, runtime, ledger, observed, physical);
            return conflict(runtime, observed, "composed canonical edit rejected: " + result);
        } catch (IllegalArgumentException invalid) { return conflict(runtime, observed, invalid.getMessage()); }
    }
    private static boolean committed(FrontierWorldState state, FrontierV3ContainerClickTransaction transaction,
            List<List<FungiblePhysicalObservation.Stack>> layouts) {
        var resources = state.inventory().fungibleResources();
        for (int i = 0; i < layouts.size(); i++) {
            var member = transaction.members().get(i); var account = resources.accounts().get(member.accountId());
            if (account == null) {
                if (!layouts.get(i).isEmpty() || resources.accounts().values().stream().anyMatch(a ->
                        a.custody().equals(new ResourceCustody.Container(member.containerId())))) return false;
            } else if (!FrontierV3ContainerClickExecutor.canonicalMatches(resources, member.accountId(), member.authorityEpoch(), layouts.get(i))) return false;
        }
        return true;
    }
    private static boolean retire(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3DepotClickLedger ledger, FrontierV3ContainerClickTransaction transaction,
            List<FrontierV3PhysicalContainer> physical) {
        for (int i = 0; i < physical.size(); i++)
            if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(runtime,
                    transaction.members().get(i).containerId(), physical.get(i))) return false;
        ledger.retire(transaction); ledger.persist(level); return true;
    }
    private static boolean conflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3ContainerClickTransaction transaction, String reason) {
        FrontierV3ContainerClickExecutor.conflict(runtime, transaction.members().getFirst(), reason); return false;
    }
}
