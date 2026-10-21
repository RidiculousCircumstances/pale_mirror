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

    static void commit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyDied consequence, Runnable nativeDeath, BooleanSupplier died) {
        var participant = FrontierV3OperationEntityParticipant.current(body, consequence.body().physicalEpoch());
        var world = runtime.decodedState().orElseThrow().bootstrap().worldId();
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_FATALITY,
                consequence.body().actorId(), world.value() + "/epoch:" + consequence.body().physicalEpoch()
                        + "/residence:" + body.getPersistentData().getLong(FrontierV3ActorBodyController.RESIDENCE_KEY),
                FrontierV3OperationConfirmation.fingerprint(consequence), Set.of(participant.identity()));
        var ledger = FrontierV3OperationLedger.get(level);
        if (!ledger.canPrepare(declaration)) throw new IllegalStateException("body fatality cannot lose its durable intent under backpressure");
        var operation = ledger.prepare(declaration);
        ledger.retainOwnerIntent(operation.ticket(), new FrontierV3OperationLedger.OwnerCommand(
                new CommandId("physical-operation:" + operation.ticket()), consequence));
        ledger.persist(level);
        var result = FrontierV3MinecraftOperation.apply(level, declaration, List.of(participant),
                () -> !died.getAsBoolean() && !body.isRemoved() && level.getEntity(body.getUUID()) == body,
                () -> { nativeDeath.run(); return true; }, died);
        if (!result.complete()) throw new IllegalStateException("body fatality lacks an exact native outcome: "
                + operation.ticket() + " " + result.status());
        if (!FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(), consequence))
            throw new IllegalStateException("native body fatality awaits canonical confirmation: " + operation.ticket());
    }

    /** Recovered intent is never another invocation of die or death loot. */
    static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE || runtime.decodedState().isEmpty()) return;
        var ledger = FrontierV3OperationLedger.get(level);
        for (var operation : ledger.probe(FrontierV3OperationReceipt.Protocol.BODY_FATALITY, 8)) {
            var retained = ledger.ownerCommand(operation.ticket()).orElseThrow(
                    () -> new IllegalStateException("body fatality lost its exact pre-effect consequence"));
            if (!(retained.consequence() instanceof ActorBodyDied consequence)
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
            FrontierV3ActorDeathResourceComposition.AfterFatality resources = () -> { };
            if (location != null && location.condition().status() == ActorLifeStatus.ALIVE && entity instanceof Mob body) {
                if (body.getPersistentData().getLong(FrontierV3NativeOperationMarkers.LIFETIME) != participant.incarnation()) continue;
                resources = FrontierV3ActorDeathResourceComposition.prepare(level, runtime, body, consequence.body());
            }
            if (!FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(), consequence)) continue;
            state = runtime.decodedState().orElseThrow();
            var carriers = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            FrontierV3BodyOperationIntegration.recover(level, state, carriers, consequence.body().actorId());
            carriers.retireDeadActor(state, consequence.body().actorId()); carriers.persist(level, state.bootstrap().worldId());
            resources.settle();
        }
    }
}
