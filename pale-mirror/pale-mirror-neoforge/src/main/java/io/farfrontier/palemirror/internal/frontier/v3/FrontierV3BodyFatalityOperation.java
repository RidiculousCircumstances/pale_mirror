package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Body-owned fatality consequence. Resource settlement remains with its owners. */
final class FrontierV3BodyFatalityOperation {
    private FrontierV3BodyFatalityOperation() { }

    static java.util.UUID commit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyDied consequence, List<FrontierV3OperationAftermath> aftermath, Runnable nativeDeath, BooleanSupplier died) {
        var participant = FrontierV3OperationEntityParticipant.current(body, consequence.body().physicalEpoch());
        var world = runtime.decodedState().orElseThrow().bootstrap().worldId();
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_FATALITY,
                consequence.body().actorId(), world.value() + "/epoch:" + consequence.body().physicalEpoch()
                        + "/residence:" + body.getPersistentData().getLong(FrontierV3ActorBodyController.RESIDENCE_KEY),
                FrontierV3OperationConfirmation.fingerprint(consequence), Set.of(participant.identity()));
        var ledger = FrontierV3OperationLedger.get(level);
        if (!ledger.canPrepare(declaration)) throw new IllegalStateException("body fatality cannot lose its durable intent under backpressure");
        var operation = ledger.prepare(declaration);
        var snapshot = FrontierV3BodyResourceSnapshot.capture(level, runtime.decodedState().orElseThrow(), body, aftermath);
        ledger.retainOwnerIntent(operation.ticket(), new FrontierV3OperationLedger.OwnerCommand(
                new CommandId("physical-operation:" + operation.ticket()), consequence,
                FrontierV3OperationLedger.OwnerCommand.Closure.EXPLICIT_OWNER,
                snapshot.encode()));
        snapshot.retain(ledger, operation.ticket());
        ledger.persist(level);
        var result = FrontierV3MinecraftOperation.apply(level, declaration, List.of(participant),
                () -> !died.getAsBoolean() && !body.isRemoved() && level.getEntity(body.getUUID()) == body, FrontierV3NativeEffect.observed(() -> { nativeDeath.run(); return true; }), died);
        if (!result.complete()) throw new IllegalStateException("body fatality lacks an exact native outcome: "
                + operation.ticket() + " " + result.status());
        // The native death is already witnessed. Retained explicit-owner recovery owns
        // settlement even if this host turn cannot yet publish its canonical consequence.
        FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(), consequence,
                () -> snapshot.settle(level, runtime, operation.ticket()));
        return operation.ticket();
    }

    static boolean finish(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            java.util.UUID ticket, io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId id) {
        var owner = FrontierV3OperationLedger.get(level).ownerCommand(ticket).orElseThrow();
        if (!(owner.consequence() instanceof ActorBodyDied death) || !death.body().equals(id))
            throw new IllegalArgumentException("fatality completion names a foreign body/operation");
        var pending = FrontierV3ActorDeathResourceComposition.pending(runtime.decodedState().orElseThrow(), id);
        if (!pending.isEmpty()) {
            FrontierV3OperationDiagnostics.waiting(level, FrontierV3OperationLedger.get(level).require(ticket).declaration(),
                    FrontierV3OperationDiagnostics.Backpressure.OWNER_AFTERMATH, pending.toString());
            return false;
        }
        var operation = FrontierV3OperationLedger.get(level).require(ticket);
        boolean complete = FrontierV3OperationConfirmation.completeOwner(level, runtime, ticket);
        if (complete) FrontierV3OperationDiagnostics.result(level, operation, FrontierV3MinecraftOperation.Status.APPLIED);
        return complete;
    }

    static boolean holdsBody(ServerLevel level, FrontierWorldState state, net.minecraft.world.entity.Entity body) {
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        if (declaration == null || !declaration.entityId().equals(body.getUUID())) return false;
        String key = state.bootstrap().worldId().value() + "/epoch:" + declaration.epoch() + "/residence:"
                + body.getPersistentData().getLong(FrontierV3ActorBodyController.RESIDENCE_KEY);
        return FrontierV3OperationLedger.get(level).find(FrontierV3OperationReceipt.Protocol.BODY_FATALITY, declaration.actorId(), key)
                .filter(operation -> !operation.ownerClosed()).isPresent();
    }

    /** Recovered intent is never another invocation of die or death loot. */
    static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE || runtime.decodedState().isEmpty()) return;
        var ledger = FrontierV3OperationLedger.get(level);
        for (var operation : ledger.probe(FrontierV3OperationReceipt.Protocol.BODY_FATALITY, 8)) {
            var retained = ledger.ownerCommand(operation.ticket()).orElseThrow(
                    () -> new IllegalStateException("body fatality lost its exact pre-effect consequence"));
            if (!(retained.consequence() instanceof ActorBodyDied consequence)
                    || retained.closure() != FrontierV3OperationLedger.OwnerCommand.Closure.EXPLICIT_OWNER
                    || !operation.declaration().owner().equals(consequence.body().actorId())
                    || !operation.declaration().fingerprint().equals(retained.fingerprint()) || operation.cancelled()
                    || operation.declaration().participants().size() != 1)
                throw new IllegalStateException("body fatality has a foreign or cancelled consequence");
            var state = runtime.decodedState().orElseThrow();
            var participant = operation.declaration().participants().iterator().next();
            if (participant.medium() != FrontierV3OperationReceipt.Medium.ENTITIES
                    || !participant.identity().equals(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(
                            state.bootstrap().worldId(), consequence.body().actorId()).toString()))
                throw new IllegalStateException("body fatality has a foreign native identity");
            if (!FrontierV3MinecraftOperation.recoverOutcome(level, operation.ticket())) continue;
            var location = state.actorLocations().get(consequence.body().actorId());
            if (location != null && location.condition().status() == ActorLifeStatus.ALIVE
                    && !io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, consequence.body().actorId())
                        .equals(consequence.body())) continue;
            var entity = level.getEntity(java.util.UUID.fromString(participant.identity()));
            if (entity != null && entity.getPersistentData().getLong(FrontierV3NativeOperationMarkers.LIFETIME) != participant.incarnation()) continue;
            if (!FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(), consequence)) continue;
            state = runtime.decodedState().orElseThrow();
            var carriers = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            FrontierV3BodyOperationIntegration.recover(level, state, carriers, consequence.body().actorId());
            carriers.retireDeadActor(state, consequence.body().actorId()); carriers.persist(level, state.bootstrap().worldId());
            var snapshot = FrontierV3BodyResourceSnapshot.decode(retained.context());
            snapshot.settle(level, runtime, operation.ticket());
            if (entity instanceof Mob body && FrontierV3ActorBodyController.recognizesRetiredDeadBody(level, state, body))
                FrontierV3ActorDeathResourceComposition.recover(level, runtime, body, consequence.body(),
                        snapshot);
            finish(level, runtime, operation.ticket(), consequence.body());
        }
    }
}
