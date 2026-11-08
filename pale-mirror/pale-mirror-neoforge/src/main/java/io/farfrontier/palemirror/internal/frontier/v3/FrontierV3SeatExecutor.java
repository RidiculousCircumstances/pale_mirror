package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import java.util.*;

/** Only active seat reservations are visited. Nearby poses use the shared body/native-operation boundary. */
final class FrontierV3SeatExecutor {
    private static final String MARKER = "pm_v3_seat_operation";
    private static final Map<Mob, Long> LAST_ATTEMPT = new WeakHashMap<>();
    private static final Map<ServerLevel, Integer> CURSORS = new WeakHashMap<>();
    private static final ImmutableInputView<List<SeatUse>> USES = new ImmutableInputView<>();
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElseThrow();
        long tick = runtime.executionView().orElseThrow().instant().ticks();
        var source = state.humanPopulation().seats().uses();
        var uses = USES.get(List.of(source), () -> List.copyOf(source.values()));
        if (uses.isEmpty()) { CURSORS.remove(level); return; }
        int visited = Math.min(32, uses.size()), start = Math.floorMod(CURSORS.getOrDefault(level, 0), uses.size());
        CURSORS.put(level, (start + visited) % uses.size());
        for (int index = 0; index < visited; index++) {
            var use = uses.get((start + index) % uses.size());
            if (use.pendingBody().isPresent()) {
                if (use.pendingMode().equals(Optional.of(SeatUse.ExecutionMode.LOCAL))) execute(level, runtime, use);
                continue; // A composed activity owns its own exact effect and receipt.
            }
            var actorId = use.execution().actorId();
            if (ActorInventoryInteractionFences.pending(state, actorId)) continue;
            var nativeActor = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actorId));
            if (!(nativeActor instanceof Mob actor) || !actor.isAlive()) continue;
            var execution = state.actorExecutions().actors().get(actorId);
            if (execution == null || !execution.current().equals(Optional.of(use.execution()))) continue;
            var action = ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind()).seatAction(state, use.execution(), use, tick);
            if (action.isEmpty() || state.actorMovements().containsKey(actorId)) continue;
            if (action.orElseThrow() == SeatUse.Action.RESTORE && seated(state, use, actor)) continue;
            if (level.getGameTime() - LAST_ATTEMPT.getOrDefault(actor, Long.MIN_VALUE / 2) < 20) continue;
            if (action.orElseThrow() == SeatUse.Action.ENTER && !FrontierV3SurfaceObservation.at(actor, use.place().approach())) continue;
            if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, actor)) continue;
            state = runtime.decodedState().orElseThrow();
            if (!use.equals(state.humanPopulation().seats().uses().get(actorId))) continue;
            // The independent inspection may have changed the pose used by the earlier policy read.
            if (!action.equals(ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind())
                    .seatAction(state, use.execution(), use, tick))) continue;
            FrontierV3ActorActuation authority;
            try { authority = FrontierV3ActorActuation.capture(state, actor, use.execution(), runtime::decodedState); }
            catch (IllegalArgumentException stale) { continue; }
            LAST_ATTEMPT.put(actor, level.getGameTime());
            if (action.orElseThrow() == SeatUse.Action.ENTER) {
                var provider = FrontierV3SeatProvider.find(use.place().kind());
                var available = provider.flatMap(p -> p.available(level, use.place(), actor));
                if (available.isEmpty()) continue;
                if (!available.orElseThrow()) {
                    var policy = ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind());
                    for (var alternative : policy.seatAlternatives(state, use.execution(), use)) {
                        if (!FrontierV3SeatProvider.find(alternative.kind()).flatMap(p -> p.available(level, alternative, actor)).orElse(false)) continue;
                        var change = policy.seatReselection(state, authority.id(), use, alternative);
                        if (change.isPresent()) FrontierV3CommandSubmission.propose(runtime, "seat-reselection", actorId.value(), change.orElseThrow());
                        break;
                    }
                    continue;
                }
            }
            if (action.orElseThrow() != SeatUse.Action.EXIT && !furniturePresent(level, use.place())) continue;
            FrontierV3GoalNavigation.stop(actor, authority);
            FrontierV3CommandSubmission.propose(runtime, "seat-contact-prepare", actorId.value(),
                    new SeatEvent.Prepared(actorId, use.revision(), authority.id(), action.orElseThrow(), SeatUse.ExecutionMode.LOCAL));
        }
    }
    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SeatUse use) {
        var actorId = use.execution().actorId(); var authority = use.pendingBody().orElseThrow(); var action = use.pendingAction().orElseThrow();
        var before = new SeatEvent.Prepared(actorId, use.revision(), authority, action, SeatUse.ExecutionMode.LOCAL);
        var ledger = FrontierV3OperationLedger.get(level);
        var old = ledger.find(FrontierV3OperationReceipt.Protocol.SEAT_CONTACT, actorId, FrontierV3OperationConfirmation.fingerprint(before));
        if (old.isPresent() && ledger.ownerCommand(old.orElseThrow().ticket()).isPresent()) {
            FrontierV3OperationConfirmation.submitRequired(level, runtime, old.orElseThrow().ticket(), ledger.ownerCommand(old.orElseThrow().ticket()).orElseThrow().consequence());
            return;
        }
        var state = runtime.decodedState().orElseThrow();
        var nativeActor = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actorId));
        if (!(nativeActor instanceof Mob actor) || !actor.isAlive()) {
            if (old.isPresent() && FrontierV3MinecraftOperation.recoverOutcome(level, old.orElseThrow().ticket())) {
                var operation = ledger.require(old.orElseThrow().ticket());
                if (operation.applied().equals(operation.declaration().participants())) {
                    var observed = state.actorLocations().get(actorId).body();
                    FrontierV3OperationConfirmation.submitRequired(level, runtime, operation.ticket(),
                            new SeatEvent.Changed(actorId, use.revision(), Optional.of(authority), action, SeatUse.ExecutionMode.LOCAL, true, observed));
                    return;
                }
            }
            var unused = FrontierV3MinecraftOperation.cancelUnusedPreparation(level, FrontierV3OperationReceipt.Protocol.SEAT_CONTACT, actorId, before);
            if (unused.isEmpty()) return;
            var closed = new SeatEvent.Changed(actorId, use.revision(), Optional.of(authority), action, SeatUse.ExecutionMode.LOCAL, false, state.actorLocations().get(actorId).body());
            if (unused.orElseThrow().cancelledTicket().isPresent())
                FrontierV3OperationConfirmation.submitRequired(level, runtime, unused.orElseThrow().cancelledTicket().orElseThrow(), closed);
            else FrontierV3CommandSubmission.submit(runtime, "seat-contact-unattempted", actorId.value(), closed);
            return;
        }
        if (!FrontierV3LoadedWorld.ready(level, pos(use.place().chair())) || !FrontierV3LoadedWorld.ready(level, pos(use.place().egress().support()))) return;
        var ports = participants(level, use, actor, authority, action);
        if (ports.isEmpty()) return;
        var participants = ports.orElseThrow();
        // A completed operation settles from its exact saved application observation.
        // Missing furniture now cannot erase a previously applied pose or authorize replay.
        if (old.isPresent() && FrontierV3MinecraftOperation.recoverOutcome(level, old.orElseThrow().ticket())
                && ledger.require(old.orElseThrow().ticket()).applied().equals(old.orElseThrow().declaration().participants())) {
            var checkpoint = checkpoint(level, runtime, use, actor, old.orElseThrow().ticket(), old.orElseThrow().declaration());
            if (checkpoint.isPresent()) FrontierV3OperationConfirmation.submitRequired(level, runtime, old.orElseThrow().ticket(),
                    new SeatEvent.Changed(actorId, use.revision(), Optional.of(authority), action,
                            SeatUse.ExecutionMode.LOCAL, true, checkpoint.orElseThrow()));
            return;
        }
        var contact = contact(level, state, use, actor, authority, action);
        if (contact.isEmpty()) {
            // Furniture can disappear between the durable preparation and its native
            // attempt. Only the common journal's positive unused proof may unpin it.
            var unused = FrontierV3MinecraftOperation.cancelUnusedPreparation(level, FrontierV3OperationReceipt.Protocol.SEAT_CONTACT, actorId, before);
            if (unused.isEmpty()) return;
            var cancelled = new SeatEvent.Changed(actorId, use.revision(), Optional.of(authority), action,
                    SeatUse.ExecutionMode.LOCAL, false, state.actorLocations().get(actorId).body());
            if (unused.orElseThrow().cancelledTicket().isPresent())
                FrontierV3OperationConfirmation.submitRequired(level, runtime, unused.orElseThrow().cancelledTicket().orElseThrow(), cancelled);
            else FrontierV3CommandSubmission.submit(runtime, "seat-contact-unattempted", actorId.value(), cancelled);
            return;
        }
        var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.SEAT_CONTACT, actorId, before, participants);
        var operation = FrontierV3MinecraftOperation.applyDeferred(level, declaration, participants,
                () -> permitted(level, runtime, use, actor), new FrontierV3NativeEffect(() -> FrontierV3NativeEffect.Admission.READY,
                        () -> changePose(actor, use, authority, contact.orElseThrow())), () -> witness(actor, authority, use));
        if (operation.pending()) return;
        if (!operation.complete() && operation.status() != FrontierV3MinecraftOperation.Status.NOT_APPLIED
                && operation.status() != FrontierV3MinecraftOperation.Status.SOURCE_CHANGED) return;
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, actor)) return;
        var observed = runtime.decodedState().orElseThrow().actorLocations().get(actorId).body();
        if (operation.complete()) {
            var checkpoint = checkpoint(level, runtime, use, actor, operation.ticket(), declaration);
            if (checkpoint.isEmpty()) return;
            observed = checkpoint.orElseThrow();
        }
        FrontierV3OperationConfirmation.submitRequired(level, runtime, operation.ticket(),
                new SeatEvent.Changed(actorId, use.revision(), Optional.of(authority), action, SeatUse.ExecutionMode.LOCAL, operation.complete(), observed));
    }
    static Optional<BodyPosition> checkpoint(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            SeatUse expected, Mob actor, UUID ticket, FrontierV3OperationReceipt.Declaration declaration) {
        var operation = FrontierV3OperationLedger.get(level).require(ticket);
        if (operation.cancelled() || !operation.declaration().equals(declaration)
                || !declaration.owner().equals(expected.execution().actorId())
                || !operation.applied().equals(operation.declaration().participants())) return Optional.empty();
        var state = runtime.decodedState().orElseThrow();
        var use = state.humanPopulation().seats().uses().get(expected.execution().actorId());
        if (use == null || !use.execution().equals(expected.execution()) || use.revision() != expected.revision()
                || !use.pendingBody().equals(expected.pendingBody()) || !use.pendingAction().equals(expected.pendingAction())
                || !use.pendingMode().equals(expected.pendingMode())) return Optional.empty();
        if (use.pendingObservation().isPresent()) return use.pendingObservation();
        if (!actor.isAlive()) return Optional.empty();
        // The actor participant's saved native receipt fences this captured application fact.
        // It remains history when subsequent player/physics movement changes the current body.
        var observed = applicationObservation(actor, use.pendingBody().orElseThrow(), use);
        if (observed.isEmpty()) return Optional.empty();
        if (!(FrontierV3CommandSubmission.submit(runtime, "seat-application-checkpoint", use.execution().actorId().value(),
                new SeatEvent.ApplicationCheckpointed(use.execution().actorId(), use.revision(), use.pendingBody().orElseThrow(),
                        use.pendingAction().orElseThrow(), use.pendingMode().orElseThrow(), ticket, observed.orElseThrow())) instanceof CommandResult.Accepted))
            return Optional.empty();
        return runtime.decodedState().orElseThrow().humanPopulation().seats().uses().get(use.execution().actorId()).pendingObservation();
    }
    /** Both entity columns participate when a local pose crosses their boundary. Column receipts survive helper removal. */
    static Optional<List<FrontierV3MinecraftOperation.Participant>> participants(ServerLevel level, SeatUse use, Mob actor,
            ActorActuationId authority, SeatUse.Action action) {
        var result = new ArrayList<FrontierV3MinecraftOperation.Participant>();
        result.add(FrontierV3OperationEntityParticipant.current(actor, authority.body().physicalEpoch()));
        var columns = new LinkedHashSet<net.minecraft.world.level.ChunkPos>();
        columns.add(actor.chunkPosition()); columns.add(new net.minecraft.world.level.ChunkPos(pos(use.place().chair())));
        // The complete bounded local footprint stays identical before/after seating and during fatality recovery.
        for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2})
            columns.add(new net.minecraft.world.level.ChunkPos(pos(use.place().chair()).offset(dx, 0, dz)));
        if (action == SeatUse.Action.EXIT) columns.add(new net.minecraft.world.level.ChunkPos(pos(use.place().egress().support())));
        for (var column : columns) {
            var port = FrontierV3OperationEntityColumns.current(level, column);
            if (port.isEmpty()) return Optional.empty();
            result.add(port.orElseThrow());
        }
        return Optional.of(List.copyOf(result));
    }
    static Optional<FrontierV3SeatProvider.Contact> contact(ServerLevel level, FrontierWorldState state, SeatUse use,
            Mob actor, ActorActuationId authority, SeatUse.Action action) {
        return FrontierV3SeatProvider.find(use.place().kind()).flatMap(provider -> provider.contact(level, state, use, actor, authority, action));
    }
    static boolean seated(FrontierWorldState state, SeatUse use, Mob actor) {
        return FrontierV3SeatProvider.find(use.place().kind()).filter(provider -> provider.seated(state, use, actor)).isPresent();
    }
    static boolean furniturePresent(ServerLevel level, SeatPlace place) {
        return FrontierV3SeatProvider.find(place.kind()).filter(provider -> provider.furniturePresent(level, place)).isPresent();
    }
    private static boolean permitted(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SeatUse use, Mob actor) {
        var state = runtime.decodedState().orElseThrow();
        if (!use.equals(state.humanPopulation().seats().uses().get(use.execution().actorId())) || !actor.isAlive()) return false;
        try { if (!FrontierV3ActorActuation.capture(state, actor, use.execution(), runtime::decodedState).id().equals(use.pendingBody().orElseThrow())) return false; }
        catch (IllegalArgumentException stale) { return false; }
        return contact(level, state, use, actor, use.pendingBody().orElseThrow(), use.pendingAction().orElseThrow())
                .filter(value -> value.ready().getAsBoolean()).isPresent();
    }
    static FrontierV3NativeEffect.Outcome changePose(Mob actor, SeatUse use, ActorActuationId authority,
            FrontierV3SeatProvider.Contact contact) {
        if (witness(actor, authority, use)) return FrontierV3NativeEffect.Outcome.APPLIED;
        var result = contact.change().get();
        if (result != FrontierV3NativeEffect.Outcome.APPLIED) return result;
        var observed = FrontierV3SupportedBodyCapture.observe((ServerLevel) actor.level(), actor);
        if (observed.isEmpty()) return FrontierV3NativeEffect.Outcome.UNKNOWN;
        var marker = new CompoundTag(); marker.putLong("revision", use.revision());
        marker.putString("actor", authority.body().actorId().value());
        marker.putInt("activityKind", FrontierWireTags.tag(authority.execution().activityKind()));
        marker.putInt("action", use.pendingAction().orElseThrow().wireTag());
        marker.putInt("mode", use.pendingMode().orElseThrow().wireTag());
        marker.putString("owner", authority.execution().activityOwnerId().value()); marker.putLong("generation", authority.execution().generation());
        marker.putLong("physicalEpoch", authority.body().physicalEpoch());
        marker.putInt("observedX", observed.orElseThrow().x()); marker.putInt("observedY", observed.orElseThrow().y());
        marker.putInt("observedZ", observed.orElseThrow().z()); actor.getPersistentData().put(MARKER, marker);
        return FrontierV3NativeEffect.Outcome.APPLIED;
    }
    static boolean witness(Mob actor, ActorActuationId authority, SeatUse use) {
        return applicationObservation(actor, authority, use).isPresent();
    }
    static Optional<BodyPosition> applicationObservation(Mob actor, ActorActuationId authority, SeatUse use) {
        var marker = actor.getPersistentData().getCompound(MARKER);
        if (marker.getLong("revision") != use.revision()
                || !marker.getString("actor").equals(authority.body().actorId().value())
                || marker.getInt("activityKind") != FrontierWireTags.tag(authority.execution().activityKind())
                || !marker.getString("owner").equals(authority.execution().activityOwnerId().value())
                || marker.getLong("generation") != authority.execution().generation()
                || marker.getLong("physicalEpoch") != authority.body().physicalEpoch()
                || use.pendingAction().isEmpty() || marker.getInt("action") != use.pendingAction().orElseThrow().wireTag()
                || use.pendingMode().isEmpty() || marker.getInt("mode") != use.pendingMode().orElseThrow().wireTag()
                || !marker.contains("observedX", 3) || !marker.contains("observedY", 3) || !marker.contains("observedZ", 3))
            return Optional.empty();
        return Optional.of(new BodyPosition(marker.getInt("observedX"), marker.getInt("observedY"), marker.getInt("observedZ")));
    }
    private static BlockPos pos(BlockPosition value) { return new BlockPos(value.x(), value.y(), value.z()); }
    private FrontierV3SeatExecutor() { }
}
