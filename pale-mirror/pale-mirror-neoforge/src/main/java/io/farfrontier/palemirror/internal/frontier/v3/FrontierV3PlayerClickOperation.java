package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.*;

/** Adapter for an admitted vanilla callback. The common protocol never interprets
 * a slot, exact item or click kind; player accounting remains the domain owner's job. */
final class FrontierV3PlayerClickOperation {
    private FrontierV3PlayerClickOperation() { }
    static void perform(ServerPlayer player, AbstractContainerMenu menu,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Runnable nativeClick) {
        var level = player.serverLevel(); var state = runtime.decodedState().orElseThrow();
        var targets = FrontierV3ContainerMenuTarget.resolveAll(player, menu, state);
        if (targets.isEmpty()) throw new IllegalStateException("admitted player click lost its containers");
        var owner = FrontierV3DepotClickLedger.get(level); var transaction = owner.transaction(targets.getFirst().record().id());
        if (transaction == null || !transaction.members().getFirst().playerId().equals(player.getUUID()))
            throw new IllegalStateException("native click lacks its exact admitted player witness");
        var ports = ports(level, state, transaction);
        if (ports.isEmpty()) { owner.retire(transaction); owner.persist(level); return; } // No native callback occurred.
        var first = transaction.members().getFirst();
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.PLAYER_CONTAINER_CLICK,
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR, first.interactionId().toString(),
                FrontierV3OperationFingerprint.of(transaction.save()),
                ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        var result = FrontierV3MinecraftOperation.apply(level, declaration, ports, () -> true,
                () -> {
                    nativeClick.run();
                    owner.capture(transaction, FrontierV3PlayerClickObservation.capture(player, state, transaction)); owner.persist(level);
                    return true;
                }, () -> owner.transaction(transaction.id()).outcome().isPresent());
        if (result.complete()) {
            owner.bind(owner.transaction(transaction.id()), FrontierV3OperationLedger.get(level).require(result.ticket())); owner.persist(level);
        } else if (result.status() == FrontierV3MinecraftOperation.Status.WAITING_DURABILITY
                || result.status() == FrontierV3MinecraftOperation.Status.NOT_APPLIED) {
            owner.retire(transaction); owner.persist(level);
        }
    }
    static FrontierV3ContainerClickTransaction ready(ServerLevel level, FrontierWorldState state,
            FrontierV3ContainerClickTransaction transaction) {
        if (confirmed(level, transaction)) return transaction;
        var operation = operation(level, transaction).orElse(null);
        if (operation == null) return null; // Missing evidence cannot be replaced by a matching layout.
        if (!operation.applied().equals(operation.declaration().participants())) {
            var ports = ports(level, state, transaction);
            if (!ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet())
                    .equals(operation.declaration().participants())) return null;
            var observed = FrontierV3MinecraftOperation.observe(level, operation.declaration(), ports, () -> true).orElseThrow();
            if (!observed.complete()) return null;
            operation = FrontierV3OperationLedger.get(level).require(operation.ticket());
        }
        var owner = FrontierV3DepotClickLedger.get(level); var linked = owner.bind(transaction, operation); owner.persist(level); return linked;
    }
    static Optional<FrontierV3OperationReceipt> operation(ServerLevel level, FrontierV3ContainerClickTransaction transaction) {
        var ledger = FrontierV3OperationLedger.get(level);
        return transaction.operation().isPresent() ? ledger.find(transaction.operation().orElseThrow().ticket())
                : ledger.find(FrontierV3OperationReceipt.Protocol.PLAYER_CONTAINER_CLICK, FrontierExecutionSubjects.PHYSICAL_EXECUTOR,
                    transaction.members().getFirst().interactionId().toString());
    }
    static boolean confirmed(ServerLevel level, FrontierV3ContainerClickTransaction transaction) {
        var current = operation(level, transaction);
        if (current.isPresent()) return current.orElseThrow().ownerClosed();
        return transaction.operation().map(proof -> FrontierV3OperationLedger.get(level).prunable(
                proof.nativeReceipt(proof.declaration().participants().iterator().next()))).orElse(false);
    }
    static boolean confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3ContainerClickTransaction transaction, ContainerPlayerEditObserved consequence) {
        return confirmed(level, transaction) || FrontierV3OperationConfirmation.submit(level, runtime,
                operation(level, transaction).orElseThrow().ticket(), consequence);
    }
    private static List<FrontierV3MinecraftOperation.Participant> ports(ServerLevel level, FrontierWorldState state,
            FrontierV3ContainerClickTransaction transaction) {
        var player = level.getServer().getPlayerList().getPlayer(transaction.members().getFirst().playerId());
        if (player == null) return List.of();
        var ports = new ArrayList<FrontierV3MinecraftOperation.Participant>();
        for (var member : transaction.members()) {
            var physical = FrontierV3PhysicalContainer.loaded(level, state, member.containerId()).orElse(null);
            if (physical == null) return List.of();
            ports.add(physical.persistence().get());
        }
        ports.add(FrontierV3OperationPlayers.current(player)); return List.copyOf(ports);
    }
}
