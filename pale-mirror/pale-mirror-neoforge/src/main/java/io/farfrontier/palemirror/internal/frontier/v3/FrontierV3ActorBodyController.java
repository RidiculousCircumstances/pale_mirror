package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * One construction/placement/admission owner for every exact actor body.
 * Resource and presentation owners initialize only a not-yet-admitted body;
 * they cannot select the admission protocol or insert it into Minecraft.
 * Existing-body recognition, observation, departure and death also enter this
 * owner; activity and presentation changes never transfer or replace a body.
 */
final class FrontierV3ActorBodyController {
    /** Explanatory only; weak physical-object keys cannot retain a departed NPC or confer authority. */
    private static final java.util.Map<Mob, String> INSPECTION_WAITS = new java.util.WeakHashMap<>();
    static final String RESIDENCE_KEY = "pm_v3_body_residence_generation";
    /** Source firewall and the Forge join event may observe the same object twice. */
    private static final java.util.Map<Mob, Long> JOINED_RESIDENCES = new java.util.WeakHashMap<>();
    enum Result { APPLIED, DEFERRED, CONFLICT }
    enum Admission { FIRST, RECONSTRUCTION, DEFERRED, CONFLICT }

    @FunctionalInterface
    interface NewBodyProjection {
        boolean initialize(Mob body);
    }

    record BirthRequest(FrontierV3ActorCarrierComposition.InventoryEntry requester,
                        FrontierV3ActorOwnerBinding binding, List<SurfaceAnchor> surfaces,
                        FrontierV3NavigationScope scope,
                        BiFunction<ServerLevel, BlockPos, BlockPos> standing,
                        NewBodyProjection projection) {
        BirthRequest {
            FrontierV3ActorCarrierComposition.requireRole(requester, FrontierV3ActorCarrierComposition.Role.ADOPTER);
            Objects.requireNonNull(binding); Objects.requireNonNull(scope);
            Objects.requireNonNull(standing); Objects.requireNonNull(projection);
            surfaces = List.copyOf(surfaces);
            if (surfaces.isEmpty()) throw new IllegalArgumentException("body birth requires an explicit placement zone");
            if (binding.declaration().representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY)
                throw new IllegalArgumentException("body birth requires a declared living representation");
        }
    }

    private FrontierV3ActorBodyController() { }

    /** Physical fatality is recognized by common provenance, never by presentation membership. */
    static boolean observeDeath(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.Entity source) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !(entity instanceof Mob body) || body.getHealth() > 0.0F
                || body.level() != level || !recognizes(state, body)) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
        if (level.getEntity(declaration.entityId()) != body) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration))
                || !hasKnownResidence(ledger, body, declaration.actorId())
                || !ledger.currentBodyResidence(declaration.actorId(), body.getPersistentData().getLong(RESIDENCE_KEY))) return false;
        // A positively identified indexed fatality also resolves contradictory living unload
        // receipts. Those receipts still forbid movement; they must not hide the actual death.
        // Resource owners prepare their own settlement. That may commit a newer state;
        // the body event must capture the resulting baseline rather than the entry snapshot.
        var resources = FrontierV3ActorDeathResourceComposition.prepare(level, runtime, body,
                ActorBodyAuthority.current(state, declaration.actorId()));
        state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || !recognizes(state, body)) return false;
        var actor = state.actorLocations().get(declaration.actorId());
        var id = ActorBodyAuthority.current(state, declaration.actorId());
        var observed = FrontierV3BodyObservation.capture(body).supportedBody();
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        var payload = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(id, actor.body(),
                actor.condition().health(), observed, FrontierV3ActorBodyDeparture.execution(state, declaration.actorId()), cause);
        var result = FrontierV3CommandSubmission.submit(runtime, "actor-body-death", declaration.actorId().value(), payload);
        if (!(result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        ledger.retireDeadActor(runtime.decodedState().orElseThrow(), declaration.actorId());
        FrontierV3GoalNavigation.retireIncarnation(body, id);
        resources.settle();
        return true;
    }

    /** Read-only eligibility for host deferral; the departure transition keeps all exact fences. */
    static boolean departurePending(ServerLevel level, FrontierWorldState state,
                                    io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        return ledger.bodyDeparture(actor).filter(receipt -> departurePending(state, receipt)).isPresent();
    }

    static boolean departurePending(FrontierWorldState state, FrontierV3ActorBodyDeparture receipt) {
        // Saved historical receipts may outlive their incarnation. As in progressDeparture,
        // establish exact current evidence before requesting its retained physical authority.
        if (!receipt.current(state)) return false;
        var body = ActorBodyAuthority.current(state, receipt.identity().actorId());
        var phase = ActorBodyAuthority.require(state, body).phase();
        return phase == FencedRecoveryPhase.RUNNING || phase == FencedRecoveryPhase.AMBIGUOUS;
    }

    /** Called by bounded actor probes, including actors with no remaining projection scope. */
    static void progressDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var indexed = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actor));
        if (indexed instanceof Mob living && living.isAlive() && recognizesRecordedBody(level, state, living)
                && (ActorBodyAuthority.require(state, ActorBodyAuthority.current(state, actor)).phase() == FencedRecoveryPhase.RUNNING
                    || ActorBodyAuthority.require(state, ActorBodyAuthority.current(state, actor)).phase() == FencedRecoveryPhase.AMBIGUOUS)
                && FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).currentBodyResidence(actor,
                    living.getPersistentData().getLong(RESIDENCE_KEY))
                && FrontierV3NativeBodyResidence.reconcile(level, living)) {
            // Native visibility may close immediately; durable absence does not.
            // Do not fabricate a receipt or skip the existing save/sync/read fence.
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_BODY_RESIDENCY_DRAIN actor={} epoch={} column={} reason=NATIVE_INACTIVE_ENTITY_COLUMN",
                    actor.value(), ActorBodyAuthority.current(state, actor).physicalEpoch(), living.chunkPosition());
            return;
        }
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var receipt = ledger.bodyDeparture(actor).orElse(null);
        if (receipt == null || !receipt.current(state) || !ledger.savedBodyDeparture(receipt)
                || !ledger.currentBodyResidence(actor, receipt.residenceGeneration())
                || departureReadPending(level, receipt)
                || level.getEntity(receipt.identity().entityId()) != null
                || FrontierV3AmbientPendingAdmissions.get(runtime, receipt.identity().entityId()) != null) return;
        var body = ActorBodyAuthority.current(state, actor);
        var phase = ActorBodyAuthority.require(state, body).phase();
        if (phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.AMBIGUOUS) return;
        // A process must first settle its own hand/effect/observation obligations.
        // These are explicit projection-scope constraints, never a body owner lookup.
        var ambient = state.ambientLeases().get(actor);
        if (ambient != null && ambient.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED
                || state.sceneLeases().values().stream().anyMatch(scene -> scene.retainsMemberCustody(actor))) return;
        var declaration = receipt.identity();
        if (!ledger.fence(declaration, declaration.epoch(), 0L)) return;
        ledger.persist(level, state.bootstrap().worldId());
        var location = state.actorLocations().get(actor);
        var payload = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded(body,
                location.body(), location.condition().health(), receipt.observed().body(), receipt.observed().health(),
                FrontierV3ActorBodyDeparture.execution(state, actor));
        FrontierV3CommandSubmission.submit(runtime, "actor-body-unloaded", actor.value(), payload);
    }

    /** Final terrain witness for all common bodies, including already-hidden entity sections. */
    static void observeTerrainDeparture(ServerLevel level, FrontierWorldState state,
                                        net.minecraft.world.level.chunk.LevelChunk chunk) {
        var manager = ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ServerEntityManagerAccessor) level)
                .frontierV3$getEntityManager();
        var sections = ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3EntityPermanentStorageAccessor) manager)
                .frontierV3$getSectionStorage();
        sections.getExistingSectionsInChunk(chunk.getPos().toLong()).flatMap(section -> section.getEntities())
                .filter(entity -> entity instanceof Mob && recognizesRecordedBody(level, state, entity))
                .forEach(entity -> FrontierV3BodyObservation.observeTerrainDeparture(entity, chunk));
    }

    /** The source callback records physical facts, never discovers a job/scene owner. */
    static boolean observeLeave(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof Mob body) || body.getHealth() <= 0.0F
                || entity.getRemovalReason() != net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK) return false;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var receipt = capture(level, state, body, true).orElse(null);
        var declaration = FrontierV3ActorCarrierComposition.declaredByUnloading(body).orElse(null);
        if (receipt == null) {
            if (declaration != null) io.farfrontier.palemirror.PaleMirrorMod.LOGGER.error(
                    "PMV3_BODY_DEPARTURE_REJECTED actor={} entity={} reason=CAPTURE_UNAVAILABLE epoch={} residence={} position={} indexed={}",
                    declaration.actorId(), body.getUUID(), declaration.epoch(),
                    body.getPersistentData().getLong(RESIDENCE_KEY), body.position(), level.getEntity(body.getUUID()) != null);
            return false;
        }
        boolean recorded = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).recordBodyDeparture(receipt);
        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                "PMV3_BODY_DEPARTURE_OBSERVED actor={} entity={} epoch={} residence={} position={} recorded={}",
                receipt.identity().actorId(), body.getUUID(), receipt.identity().epoch(),
                receipt.residenceGeneration(), receipt.observed().body(), recorded);
        return recorded;
    }

    static void observeJoin(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                            net.minecraft.world.entity.Entity entity) {
        FrontierV3BodyObservation.forgetDeparture(entity);
        if (!(entity instanceof Mob body) || !body.isAlive()) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        if (declaration == null || body.level() != level || !recognizesDeclaration(state, declaration)) return;
        var indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != body) return;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration))
                || !hasKnownResidence(ledger, body, declaration.actorId())) return;
        ensureOrdinaryPhysics(body);
        long residence = body.getPersistentData().getLong(RESIDENCE_KEY);
        if (java.util.Objects.equals(JOINED_RESIDENCES.get(body), residence)) return;
        var receipt = ledger.bodyDeparture(declaration.actorId()).orElse(null);
        if (receipt != null) {
            var actual = captureReturnedBody(level, state, body).orElse(null);
            String rejection = actual == null ? "RETURN_CAPTURE_UNAVAILABLE"
                    : !receipt.current(state) ? "DEPARTURE_NOT_CURRENT"
                    : receipt.residenceGeneration() != actual.residenceGeneration() ? "RESIDENCE_MISMATCH"
                    : !receipt.identity().equals(actual.identity()) ? "IDENTITY_MISMATCH"
                    : !receipt.observed().equals(actual.observed()) ? "OBSERVATION_MISMATCH"
                    : !receipt.offhand().equals(actual.offhand()) || !receipt.mainhand().equals(actual.mainhand()) ? "HAND_MISMATCH"
                    : !ledger.resumeBodyDeparture(receipt) ? "LEDGER_RETURN_CONFLICT" : null;
            if (rejection != null) {
                io.farfrontier.palemirror.PaleMirrorMod.LOGGER.error(
                        "PMV3_BODY_RETURN_REJECTED actor={} entity={} reason={} expected={} actual={}",
                        declaration.actorId(), body.getUUID(), rejection, receipt, actual);
                return;
            }
        }
        // Private first/reconstruction insertion already reserved this residency.
        if (ledger.pendingAdoption(declaration.actorId()).isPresent()
                || ledger.firstAdmission(declaration.actorId()).filter(value ->
                    value.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING).isPresent()) {
            if (!ledger.currentBodyResidence(declaration.actorId(), residence)) return;
            JOINED_RESIDENCES.put(body, residence); return;
        }
        if (!recognizesRecordedBody(level, state, body)) return;
        long next = ledger.beginBodyResidence(FrontierV3ActorOwnerBinding.from(body).orElseThrow().declaration());
        body.getPersistentData().putLong(RESIDENCE_KEY, next);
        ledger.persist(level, state.bootstrap().worldId());
        JOINED_RESIDENCES.put(body, next);
    }

    private static boolean hasKnownResidence(FrontierV3AmbientCarrierLedger ledger, net.minecraft.world.entity.Entity body,
                                             io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        return body.getPersistentData().contains(RESIDENCE_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && ledger.knownBodyResidence(actor, body.getPersistentData().getLong(RESIDENCE_KEY));
    }

    /** Returning a saved body proves lifetime/pose, never grounded work or goal arrival. */
    static java.util.Optional<FrontierV3ActorBodyDeparture> captureReturnedBody(
            ServerLevel level, FrontierWorldState state, Mob body) {
        return capture(level, state, body, false);
    }

    private static java.util.Optional<FrontierV3ActorBodyDeparture> capture(ServerLevel level, FrontierWorldState state,
                                                                          Mob body, boolean departing) {
        var declaration = (departing ? FrontierV3ActorCarrierComposition.declaredByUnloading(body)
                : FrontierV3ActorCarrierComposition.declaredBy(body)).orElse(null);
        if (body.level() != level || declaration == null || !recognizesDeclaration(state, declaration))
            return java.util.Optional.empty();
        var indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != body) return java.util.Optional.empty();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration))) return java.util.Optional.empty();
        var metadata = body.getPersistentData();
        if (!metadata.contains(RESIDENCE_KEY, net.minecraft.nbt.Tag.TAG_LONG)) return java.util.Optional.empty();
        long residence = metadata.getLong(RESIDENCE_KEY);
        if (!(departing ? ledger.currentBodyResidence(declaration.actorId(), residence)
                : ledger.knownBodyResidence(declaration.actorId(), residence))) return java.util.Optional.empty();
        // Unload certifies physical custody/pose, not a grounded work checkpoint.
        // Minecraft can stop ticking a jumping body before its final storage callback.
        // Keep the actual classified pose (also retained in vanilla's save witness),
        // rather than waiting for an impossible landing or inventing an old support.
        // Live arrivals/inspection still require actual supported contact. COLD work
        // must establish its normal known route/station, independently of this receipt.
        // The same spatial observation is admissible on both sides of storage.
        // Requiring support here cancels a saved airborne body before Minecraft
        // can index it and apply gravity. Grounded execution/arrival remains
        // independently fenced by inspectCurrent and semantic movement.
        var position = departing ? FrontierV3BodyObservation.captureForDeparture(body).position()
                : FrontierV3BodyObservationSave.returnedPosition(body);
        var off = body.getOffhandItem(); var main = body.getMainHandItem();
        if (!plainHand(off) || !plainHand(main)) return java.util.Optional.empty();
        var actor = state.actorLocations().get(declaration.actorId());
        return java.util.Optional.of(new FrontierV3ActorBodyDeparture(declaration.inactiveCarrier(), residence,
                new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(declaration.actorId(), position,
                    new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth()
                        * (double) io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE))),
                actor.body(), actor.condition().health(), FrontierV3ActorBodyDeparture.execution(state, declaration.actorId()),
                hand(off), hand(main), FrontierV3StoredAttachedStorage.capture(state, declaration.actorId(), body)));
    }
    private static boolean plainHand(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() || net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack,
                new net.minecraft.world.item.ItemStack(stack.getItem(), stack.getCount()));
    }
    private static java.util.Optional<FrontierV3ActorBodyDeparture.HandStack> hand(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(new FrontierV3ActorBodyDeparture.HandStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
    }

    /** Body provenance is independent of the currently selected activity and its scene. */
    static boolean recognizes(FrontierWorldState state, net.minecraft.world.entity.Entity entity) {
        if (entity == null || entity.isRemoved()) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(entity).orElse(null);
        return declaration != null && recognizesDeclaration(state, declaration);
    }

    static boolean recognizesDeclaration(FrontierWorldState state, FrontierV3ActorCarrierComposition.Declaration declaration) {
        if (declaration.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY)
            return false;
        var actor = state.actorLocations().get(declaration.actorId());
        if (actor == null || actor.kind() != declaration.kind()
                || actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE
                || !SceneLease.deterministicEntityId(state.bootstrap().worldId(), declaration.actorId()).equals(declaration.entityId()))
            return false;
        try { return ActorBodyAuthority.current(state, declaration.actorId()).physicalEpoch() == declaration.epoch(); }
        catch (IllegalArgumentException absentOrStale) { return false; }
    }

    /** A joining private admission can precede the UUID index, never replace another object. */
    static boolean retainsRecordedBody(ServerLevel level, FrontierWorldState state, net.minecraft.world.entity.Entity entity) {
        if (entity == null || entity.level() != level || !recognizes(state, entity)) return false;
        var binding = FrontierV3ActorOwnerBinding.from(entity).orElseThrow();
        var declaration = binding.declaration();
        var indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != entity) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!hasKnownResidence(ledger, entity, declaration.actorId())) return false;
        if (!ledger.currentBodyResidence(declaration.actorId(), entity.getPersistentData().getLong(RESIDENCE_KEY))) return false;
        if (ledger.hasCarrier(declaration.actorId()) || ledger.hasDepartureConflict(declaration.actorId())) return false;
        return ledger.pendingAdoption(declaration.actorId()).filter(value -> value.matches(binding)).isPresent()
                || ledger.firstAdmission(declaration.actorId())
                    .filter(value -> value.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING
                        && value.attempt().orElseThrow().equals(binding)).isPresent();
    }

    /** Lifetime recognition requires physical history, never an active scene or ambient scope. */
    static boolean recognizesRecordedBody(ServerLevel level, FrontierWorldState state, net.minecraft.world.entity.Entity entity) {
        if (entity == null || entity.level() != level || !recognizes(state, entity)) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(entity).orElseThrow();
        var indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != entity) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!hasKnownResidence(ledger, entity, declaration.actorId())) return false;
        if (ledger.hasCarrier(declaration.actorId()) || ledger.hasBodyDeparture(declaration.actorId())
                || ledger.hasDepartureConflict(declaration.actorId())
                || !ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration))) return false;
        var phase = ActorBodyAuthority.require(state, ActorBodyAuthority.current(state, declaration.actorId())).phase();
        return phase == FencedRecoveryPhase.RUNNING || phase == FencedRecoveryPhase.AMBIGUOUS
                // A native save can settle the insertion ledger before supported admission
                // is observed. The same saved PREPARED body must still be indexable so the
                // common observer can confirm it. Completed insertion is not a new birth
                // permission; exact canonical epoch, recorded owner, absence of competing
                // custody and current residence were/are all required here.
                || phase == FencedRecoveryPhase.PREPARED && ledger.currentBodyResidence(declaration.actorId(),
                    entity.getPersistentData().getLong(RESIDENCE_KEY));
    }

    /**
     * Quarantine stops execution, never the native retention of an exact saved body.
     * Read-only departure matching does not consume its fence or fabricate an arrival.
     */
    static boolean recognizesPassiveBody(ServerLevel level, FrontierWorldState state,
                                         net.minecraft.world.entity.Entity entity) {
        if (retainsRecordedBody(level, state, entity) || recognizesRecordedBody(level, state, entity)) return true;
        if (!(entity instanceof Mob body) || !body.isAlive() || body.level() != level || !recognizes(state, body)) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
        var indexed = level.getEntity(body.getUUID());
        if (indexed != null && indexed != body) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (ledger.hasDepartureConflict(declaration.actorId())) return false;
        var receipt = ledger.bodyDeparture(declaration.actorId()).orElse(null);
        if (receipt == null || !receipt.current(state)) return false;
        var actual = captureReturnedBody(level, state, body).orElse(null);
        return actual != null && receipt.residenceGeneration() == actual.residenceGeneration()
                && receipt.identity().equals(actual.identity()) && receipt.observed().equals(actual.observed())
                && receipt.offhand().equals(actual.offhand()) && receipt.mainhand().equals(actual.mainhand())
                && receipt.attachedStorage().equals(actual.attachedStorage());
    }

    /** Called only after the join firewall has accepted this exact physical object. */
    static void confirmPresent(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                               net.minecraft.world.entity.Entity entity) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || !(entity instanceof Mob body) || !body.isAlive()
                || level.getEntity(body.getUUID()) != body
                || !recognizesRecordedBody(level, state, entity)) return;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
        if (!FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).currentBodyResidence(
                declaration.actorId(), body.getPersistentData().getLong(RESIDENCE_KEY))) return;
        ensureOrdinaryPhysics(body);
        var id = ActorBodyAuthority.current(state, declaration.actorId());
        if (ActorBodyAuthority.require(state, id).phase() == FencedRecoveryPhase.RUNNING) return;
        var observed = FrontierV3SupportedBodyCapture.observe(level, body);
        if (observed.isEmpty()) return;
        var location = state.actorLocations().get(declaration.actorId());
        var health = new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth()
                * (double) io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE));
        FrontierV3CommandSubmission.submit(runtime, "actor-body-present", declaration.actorId().value(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent(id,
                        location.body(), location.condition().health(), observed.orElseThrow(), health));
    }

    /** A physical insertion is not execution readiness until the common observation is durable. */
    static boolean readyForExecution(ServerLevel level, FrontierWorldState state,
                                     java.util.Collection<ActorBodyId> expectedBodies) {
        for (ActorBodyId expected : expectedBodies) {
            if (!expected.equals(ActorBodyAuthority.current(state, expected.actorId()))
                    || ActorBodyAuthority.require(state, expected).phase() != FencedRecoveryPhase.RUNNING)
                return false;
            var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), expected.actorId()));
            if (!(entity instanceof Mob body) || !body.isAlive()
                    || !recognizesRecordedBody(level, state, body)) return false;
            if (!FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).currentBodyResidence(
                    expected.actorId(), body.getPersistentData().getLong(RESIDENCE_KEY))) return false;
        }
        return true;
    }

    /** Common inspection of one indexed resident object; family receipts cannot confer body permission. */
    static boolean inspectCurrent(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || !body.isAlive()) return inspectionDeferred(body, "RUNTIME_OR_LIFE");
        if (!recognizesRecordedBody(level, state, body)) return inspectionDeferred(body, "RECORDED_BODY_IDENTITY");
        if (level.getEntity(body.getUUID()) != body) return inspectionDeferred(body, "INDEXED_OBJECT");
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.currentBodyResidence(declaration.actorId(), body.getPersistentData().getLong(RESIDENCE_KEY)))
            return inspectionDeferred(body, "RESIDENCE_GENERATION");
        ensureOrdinaryPhysics(body);
        var id = ActorBodyAuthority.current(state, declaration.actorId());
        var phase = ActorBodyAuthority.require(state, id).phase();
        if (phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.AMBIGUOUS)
            return inspectionDeferred(body, "BODY_PHASE:" + phase);
        var observed = FrontierV3SupportedBodyCapture.observe(level, body);
        if (observed.isEmpty()) return inspectionDeferred(body, "SUPPORTED_CONTACT:chunk=" + level.hasChunkAt(body.getOnPos()));
        var actor = state.actorLocations().get(declaration.actorId());
        var health = new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth()
                * (double) io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE));
        if (phase == FencedRecoveryPhase.RUNNING && actor.body().equals(observed.orElseThrow())
                && actor.condition().health().equals(health)) { INSPECTION_WAITS.remove(body); return true; }
        var receipt = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(id,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING,
                actor.body(), actor.condition().health(), observed.orElseThrow(), health,
                FrontierV3ActorBodyDeparture.execution(state, declaration.actorId()));
        var result = FrontierV3CommandSubmission.submitResult(runtime, "actor-body-inspected", declaration.actorId().value(), receipt);
        if (result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted) {
            INSPECTION_WAITS.remove(body); return true;
        }
        return inspectionDeferred(body, "OBSERVATION_REJECTED:" + result);
    }

    /** Physics belongs to the physical incarnation, never its ambient/work presentation.
     * Native navigation supplies gravity while steering; its stop leaves this same bridge
     * alive, without double-integrating an active native path or awarding goal arrival.
     */
    private static void ensureOrdinaryPhysics(Mob body) {
        if (!FrontierV3ControlledMobMotion.ordinaryPhysicsRegistered(body))
            FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(body);
    }

    private static boolean inspectionDeferred(Mob body, String reason) {
        if (!reason.equals(INSPECTION_WAITS.put(body, reason)))
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                    "PMV3_BODY_INSPECTION_WAIT entity={} reason={} physical={},{},{} onGround={}",
                    body.getUUID(), reason, body.getX(), body.getY(), body.getZ(), body.onGround());
        return false;
    }

    /** Record a saved physical checkpoint before its process settles hands/effects and closes.
     * The exact unload/save/sync/read fence belongs here, never to a scene's route policy.
     * This retains physical custody and grants no loaded actuator or semantic arrival.
     */
    static boolean checkpointSavedDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var physical = ledger.bodyDeparture(actorId).orElse(null);
        if (physical == null || !physical.current(state) || !ledger.savedBodyDeparture(physical)
                || !ledger.currentBodyResidence(actorId, physical.residenceGeneration())
                || departureReadPending(level, physical)
                || level.getEntity(physical.identity().entityId()) != null
                || FrontierV3AmbientPendingAdmissions.get(runtime, physical.identity().entityId()) != null) return false;
        var id = ActorBodyAuthority.current(state, actorId);
        var phase = ActorBodyAuthority.require(state, id).phase();
        if (phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.AMBIGUOUS) return false;
        var actor = state.actorLocations().get(actorId);
        if (!actor.body().equals(physical.observed().body()) || !actor.condition().health().equals(physical.observed().health())) {
            var receipt = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(id,
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.SAVED_DEPARTURE,
                    actor.body(), actor.condition().health(), physical.observed().body(), physical.observed().health(),
                    FrontierV3ActorBodyDeparture.execution(state, actorId));
            if (!(FrontierV3CommandSubmission.submitResult(runtime, "actor-body-saved-checkpoint", actorId.value(), receipt)
                    instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        }
        if (!ledger.fence(physical.identity(), id.physicalEpoch(), 0L)) return false;
        ledger.persist(level, state.bootstrap().worldId());
        return true;
    }

    /** Protect the reservation interval before the durable returned-body fence is published. */
    static boolean departureReadPending(ServerLevel level, FrontierV3ActorBodyDeparture receipt) {
        return FrontierV3DepartureReturnReadFence.readPending(level, receipt.observed().body());
    }

    /** Read-only conversion of explicit disk body identity; no scene/activity supplies an epoch or pose. */
    static java.util.Optional<FrontierV3ActorBodyDeparture> storedDeparture(FrontierWorldState state,
            ActorBodyId body, FrontierV3SceneDeparturePersistence.SavedBody saved) {
        try {
            var actor = state.actorLocations().get(body.actorId());
            var phase = ActorBodyAuthority.require(state, body).phase();
            if (actor == null || actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE
                    || phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.AMBIGUOUS) return java.util.Optional.empty();
            var kind = io.farfrontier.palemirror.frontier.v3.model.ActorKind.valueOf(saved.kind());
            var inactive = FrontierV3ActorCarrierComposition.fromCanonical(state, body.actorId(), kind,
                    FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, ActorBodyId.entityId(state.bootstrap().worldId(), body.actorId()),
                    FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L, body.physicalEpoch());
            var receipt = new FrontierV3ActorBodyDeparture(inactive, saved.residenceGeneration(),
                    new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(body.actorId(), saved.body(), saved.health()),
                    actor.body(), actor.condition().health(), FrontierV3ActorBodyDeparture.execution(state, body.actorId()),
                    saved.offhand(), saved.mainhand(), saved.attachedStorage());
            return saved.matches(receipt) ? java.util.Optional.of(receipt) : java.util.Optional.empty();
        } catch (IllegalArgumentException staleOrForeign) { return java.util.Optional.empty(); }
    }

    /** Positive no-load disk proof supplies a missing unload callback, independently of process scopes. */
    static boolean confirmStoredDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            ActorBodyId body, io.farfrontier.palemirror.frontier.v3.model.ActorLocation expected,
            FrontierV3StoredEntityCensus.Result census, net.minecraft.world.level.ChunkPos column,
            FrontierV3StoredEntityInspection.StampedSnapshot snapshot) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        var entityId = ActorBodyId.entityId(state.bootstrap().worldId(), body.actorId());
        if (!expected.equals(state.actorLocations().get(body.actorId()))
                || !census.stillCurrent(level) || !census.uniqueAt(entityId, column) || !snapshot.stillCurrent(level, column)
                || level.getEntity(entityId) != null || FrontierV3AmbientPendingAdmissions.get(runtime, entityId) != null) return false;
        var saved = snapshot.snapshot().actors().get(entityId);
        if (saved == null) return false;
        var receipt = storedDeparture(state, body, saved).orElse(null);
        if (receipt == null) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.currentBodyResidence(body.actorId(), receipt.residenceGeneration())
                || !ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(
                    receipt.identity().liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, body.physicalEpoch())))
                || !ledger.recordBodyDeparture(receipt) || !ledger.confirmSavedBodyDeparture(receipt)) return false;
        ledger.persist(level, state.bootstrap().worldId());
        return true;
    }

    /** Bounded actor probes supply candidates; historical scene membership never authorizes removal. */
    static void cleanRetired(ServerLevel level, FrontierWorldState state,
                             io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
        var entity = level.getEntity(SceneLease.deterministicEntityId(state.bootstrap().worldId(), actorId));
        if (entity instanceof Mob body) discardRetired(level, state, body);
    }

    /** Read-only evidence for already-applied resource settlement; never actuation or revival. */
    static boolean recognizesRetiredDeadBody(ServerLevel level, FrontierWorldState state, net.minecraft.world.entity.Entity body) {
        if (body == null || body.level() != level || body.isRemoved()) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        if (declaration == null || declaration.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                || !body.getUUID().equals(declaration.entityId())
                || !SceneLease.deterministicEntityId(state.bootstrap().worldId(), declaration.actorId()).equals(declaration.entityId())) return false;
        var actor = state.actorLocations().get(declaration.actorId());
        var retired = state.fencedRecovery().tombstones().get(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(declaration.actorId()));
        return actor != null && actor.kind() == declaration.kind()
                && actor.condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD
                && retired != null && retired.retiredEpoch() == declaration.epoch();
    }

    static boolean discardRetired(ServerLevel level, FrontierWorldState state, Mob body) {
        if (body.level() != level || body.isRemoved()) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        if (declaration == null) return false;
        var actor = state.actorLocations().get(declaration.actorId());
        if (actor == null || actor.kind() != declaration.kind()
                || !SceneLease.deterministicEntityId(state.bootstrap().worldId(), declaration.actorId()).equals(declaration.entityId()))
            return false;
        var id = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(declaration.actorId(), declaration.epoch());
        boolean dead = recognizesRetiredDeadBody(level, state, body);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!dead && !ledger.fencesBody(declaration)) return false;
        if (!FrontierV3GoalNavigation.retireIncarnation(body, id)) return false;
        body.discard();
        return true;
    }

    static Result materialize(ServerLevel level, FrontierWorldState state, BirthRequest request) {
        var declaration = request.binding().declaration();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var ticket = FrontierV3BodyInsertionJournal.get(level, state.bootstrap().worldId(), declaration.actorId());
        if (ticket != null && (!ticket.binding().equals(request.binding()) || !ticket.current(ledger))) return Result.CONFLICT;
        if (ticket != null && !ticket.ready()) return Result.DEFERRED;
        Admission admission = ticket == null ? admission(state, request.binding(), ledger, level.getEntity(declaration.entityId()) != null)
                : ticket.admission();
        if (ticket != null && (!recognizesDeclaration(state, declaration)
                || ActorBodyAuthority.require(state, ActorBodyAuthority.current(state, declaration.actorId())).phase() != FencedRecoveryPhase.PREPARED
                || level.getEntity(declaration.entityId()) != null)) return Result.CONFLICT;
        if (admission == Admission.CONFLICT) return Result.CONFLICT;
        if (admission == Admission.DEFERRED
                || !FrontierV3NativeBodyResidence.admissionReady(level, request.surfaces().getFirst())) return Result.DEFERRED;
        // Only the controller has the producer capability. Families supply exact
        // demand and owned projections, never their own creation permission.
        Mob body = FrontierV3ActorCarrierFactory.create(
                FrontierV3ActorCarrierComposition.InventoryEntry.ACTOR_BODY, level, declaration,
                state.actorLocations().get(declaration.actorId()).condition());
        var placement = FrontierV3BodyPlacement.select(level, body, request.surfaces(), request.scope(), request.standing());
        if (placement.isEmpty()) return Result.DEFERRED;
        var point = FrontierV3SemanticMovement.point(level, placement.orElseThrow());
        body.setPos(point.x, point.y, point.z);
        FrontierV3BodyObservation.refreshGroundContact(level, body);
        body.setPersistenceRequired();
        body.setNoAi(true);
        request.binding().stamp(body);
        if (!request.projection().initialize(body)) return Result.CONFLICT;
        // Inventory admission belongs to the body owner, not an optional scene/ambient callback.
        if (!FrontierV3ActorCarryProjection.prepareNew(state, declaration.actorId(), body)) return Result.CONFLICT;
        // A resource projection is not allowed to change the complete physical
        // declaration, and must not have admitted or removed its private body.
        if (body.isRemoved() || !body.isAlive() || !body.position().equals(point) || level.getEntity(declaration.entityId()) != null
                || !FrontierV3ActorOwnerBinding.from(body).filter(request.binding()::equals).isPresent())
            return Result.CONFLICT;
        if (!FrontierV3BodyPlacement.available(level, body, body.getBoundingBox())) return Result.DEFERRED;
        if (ticket == null) {
            ticket = FrontierV3BodyInsertionJournal.prepare(level, state, ledger, request.binding(), admission);
            return Result.DEFERRED; // No server-thread wait, even if the disk completed quickly.
        }
        body.getPersistentData().putLong(RESIDENCE_KEY, ticket.residence());
        if (!FrontierV3BodyInsertionJournal.consume(level, ticket)) throw new IllegalStateException("body insertion lost its durable ticket");
        boolean added = level.addFreshEntity(body);
        if (!added) {
            if (!ticket.undo().reject(ledger)) throw new IllegalStateException("rejected uncreated body lost its predecessor");
            ledger.persist(level, state.bootstrap().worldId());
        }
        // Both admission boundaries restore the exact unused permission on a synchronous
        // false, or throw if that absence is not provable. A canceled, uncreated insertion
        // is retryable, not a family conflict. Unknown outcomes remain durable and fenced.
        return added ? Result.APPLIED : Result.DEFERRED;
    }

    /** Exact physical incarnation plus durable history, never UUID absence alone. */
    static Admission admission(FrontierWorldState state, FrontierV3ActorOwnerBinding binding,
                               FrontierV3AmbientCarrierLedger ledger, boolean bodyPresent) {
        var declaration = binding.declaration();
        var actor = state.actorLocations().get(declaration.actorId());
        if (actor == null || actor.kind() != declaration.kind()
                || declaration.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                || !SceneLease.deterministicEntityId(state.bootstrap().worldId(), declaration.actorId()).equals(declaration.entityId()))
            throw new IllegalArgumentException("birth declaration does not identify its canonical actor body");
        var bodyId = ActorBodyAuthority.current(state, declaration.actorId());
        if (bodyId.physicalEpoch() != declaration.epoch())
            throw new IllegalArgumentException("birth declaration has a stale physical incarnation");
        if (actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE
                || ActorBodyAuthority.require(state, bodyId).phase() != FencedRecoveryPhase.PREPARED
                || bodyPresent) return Admission.CONFLICT;
        if (ledger.hasDepartureConflict(declaration.actorId())) return Admission.CONFLICT;
        var pending = ledger.pendingAdoption(declaration.actorId()).orElse(null);
        if (pending != null) return pending.matches(binding) ? Admission.DEFERRED : Admission.CONFLICT;
        var first = ledger.firstAdmission(declaration.actorId()).orElse(null);
        if (first != null && first.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING)
            return binding.equals(first.attempt().orElseThrow()) ? Admission.DEFERRED : Admission.CONFLICT;
        var reconciliation = ledger.reconciliation(declaration, bodyPresent);
        if (reconciliation == FrontierV3AmbientCarrierLedger.Reconciliation.READY) {
            return first != null && first.identity().matches(declaration)
                    && first.phase() == FrontierV3ActorFirstAdmission.Phase.ESTABLISHED
                    ? Admission.RECONSTRUCTION : Admission.CONFLICT;
        }
        return reconciliation == FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER
                && hasUnusedFirstAdmission(ledger, declaration) ? Admission.FIRST : Admission.CONFLICT;
    }

    static boolean hasUnusedFirstAdmission(FrontierV3AmbientCarrierLedger ledger,
                                           FrontierV3ActorCarrierComposition.Declaration declaration) {
        return ledger.firstAdmission(declaration.actorId()).filter(value ->
                value.identity().matches(declaration) && (value.phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                    || value.phase() == FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT
                        && value.attempt().orElseThrow().declaration().equals(declaration))).isPresent();
    }

}
