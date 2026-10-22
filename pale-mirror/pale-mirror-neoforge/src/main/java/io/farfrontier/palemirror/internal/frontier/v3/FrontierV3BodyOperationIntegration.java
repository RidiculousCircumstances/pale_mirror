package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.List;

/** Body producer's integration, not a second admission or physical-save owner. */
final class FrontierV3BodyOperationIntegration {
    private FrontierV3BodyOperationIntegration() { }
    static FrontierV3MinecraftOperation.Result insert(ServerLevel level, Mob body,
            FrontierV3AmbientCarrierLedger carriers, FrontierV3BodyInsertionJournal.Ticket ticket) {
        var link = carriers.bodyOperation(ticket.binding().declaration().actorId()).orElseThrow();
        if (!link.world().equals(ticket.world()) || !link.binding().equals(ticket.binding()) || link.residence() != ticket.residence() || link.rejected())
            throw new IllegalStateException("insertion lost exact operation binding");
        if (body.getPersistentData().getLong(FrontierV3NativeOperationMarkers.LIFETIME) != link.participant().incarnation())
            throw new IllegalStateException("body projection changed its native lifetime");
        var port = FrontierV3OperationEntityParticipant.birth(body);
        return FrontierV3MinecraftOperation.applyDeferred(level, new FrontierV3MinecraftOperation.Request(
                link.operation().declaration(), List.of(port), () -> ticket.current(carriers) && !body.isRemoved()
                        && level.getEntity(body.getUUID()) == null,
                () -> {
                    if (!FrontierV3BodyInsertionJournal.consume(level, ticket)) throw new IllegalStateException("body insertion lost durable ticket");
                    return level.addFreshEntity(body);
                }, () -> level.getEntity(body.getUUID()) == body && !body.isRemoved(),
                // Native addFreshEntity(false) is a positive refusal in this callback,
                // never a timeout, recovered UUID absence or inferred cancellation.
                () -> level.getEntity(body.getUUID()) == null && !body.isAddedToLevel()));
    }
    static void reject(ServerLevel level, WorldId world, FrontierV3AmbientCarrierLedger carriers,
            FrontierV3BodyAdmissionOperation link) {
        if (!link.world().equals(world)) throw new IllegalArgumentException("foreign body operation world");
        var operations = FrontierV3OperationLedger.get(level);
        var pending = operations.find(link.operation().ticket());
        if (pending.isPresent() && !pending.orElseThrow().cancelled()
                || pending.isEmpty() && (!link.rejected() || !retired(operations, link)))
            throw new IllegalStateException("body rollback lacks a sealed refusal");
        operations.persist(level); // The refusal must survive before its owner regrants the unused permission.
        carriers.rejectBodyOperation(link);
        carriers.persist(level, world); // Exact undo and rejected outbox share one actor row.
        if (pending.isPresent()) {
            operations.closeOwner(link.operation().ticket()); operations.retire(link.operation().ticket()); operations.persist(level);
        }
        carriers.forgetBodyOperation(link.rejection()); carriers.persist(level, world);
    }
    /** Reconcile only exact retained operations. Unknown prepare never regrants birth. */
    static void recover(ServerLevel level, FrontierWorldState state, FrontierV3AmbientCarrierLedger carriers,
            SubjectId actor) {
        var link = carriers.bodyOperation(actor).orElse(null);
        if (link == null) return;
        if (!link.world().equals(state.bootstrap().worldId())) throw new IllegalArgumentException("foreign recovered body world");
        var operations = FrontierV3OperationLedger.get(level); var pending = operations.find(link.operation().ticket());
        if (link.rejected() || pending.filter(FrontierV3OperationReceipt::cancelled).isPresent()) {
            reject(level, state.bootstrap().worldId(), carriers, link); return;
        }
        if (retired(operations, link) || pending.filter(FrontierV3OperationReceipt::ownerClosed).isPresent()) {
            carriers.forgetBodyOperation(link); carriers.persist(level, state.bootstrap().worldId()); return;
        }
        if (pending.isEmpty()) {
            FrontierV3OperationDiagnostics.result(level, link.operation(), FrontierV3MinecraftOperation.Status.AMBIGUOUS);
            return; // Incomplete independent publication grants neither replay nor invented native evidence.
        }
        // Canonical fatality supersedes the birth presentation, but never its native
        // save obligation. This also covers a crash after ActorBodyDied was committed.
        var location = state.actorLocations().get(actor);
        if (location != null && location.condition().status() == ActorLifeStatus.DEAD && pending.isPresent()
                && FrontierV3MinecraftOperation.recoverOutcome(level, link.operation().ticket())) {
            operations.closeOwner(link.operation().ticket());
            if (operations.require(link.operation().ticket()).retirable()) operations.retire(link.operation().ticket());
            operations.persist(level);
            carriers.forgetBodyOperation(link); carriers.persist(level, state.bootstrap().worldId());
        }
    }
    static void recover(ServerLevel level, FrontierWorldState state, FrontierV3AmbientCarrierLedger carriers) {
        for (var link : carriers.bodyOperations()) recover(level, state, carriers, link.actor());
    }
    static boolean confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, Mob body, SubjectId actor, FrontierPayload consequence) {
        var carriers = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var link = carriers.bodyOperation(actor).orElse(null);
        if (link == null) return false; // Returning saved bodies have their own residence observation, not another birth.
        if (!link.world().equals(state.bootstrap().worldId())) throw new IllegalArgumentException("foreign body confirmation world");
        if (link.rejected() || body.getPersistentData().getLong(FrontierV3NativeOperationMarkers.LIFETIME) != link.participant().incarnation()
                || !FrontierV3NativeOperationMarkers.contains(body.getPersistentData(), link.operation().nativeReceipt(link.participant()))) return true;
        var operations = FrontierV3OperationLedger.get(level);
        if (retired(operations, link)) {
            carriers.forgetBodyOperation(link); carriers.persist(level, state.bootstrap().worldId()); return false;
        }
        if (!operations.durableOperation(link.operation().ticket())) return true;
        if (FrontierV3OperationConfirmation.submitRecovery(level, runtime, link.operation().ticket(), consequence, java.util.Optional.empty())) {
            closeAdmissionOwner(level, carriers, link);
        } else operations.ownerCommand(link.operation().ticket()).ifPresent(command ->
                runtime.afterCommandReceipt(command.id(), () -> closeAdmissionOwner(level, carriers, link)));
        return true;
    }
    private static void closeAdmissionOwner(ServerLevel level, FrontierV3AmbientCarrierLedger carriers,
            FrontierV3BodyAdmissionOperation link) {
        // ActorBodyPresent may become durable between turns. Once RUNNING, ordinary
        // inspection no longer calls confirm, so exact owner cleanup must follow that receipt.
        if (carriers.bodyOperation(link.actor()).filter(link::equals).isEmpty()) return;
        carriers.forgetBodyOperation(link); carriers.persist(level, link.world());
    }
    private static boolean retired(FrontierV3OperationLedger operations, FrontierV3BodyAdmissionOperation link) {
        return operations.prunable(link.operation().nativeReceipt(link.participant()));
    }
}
