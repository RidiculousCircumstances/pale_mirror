package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.ActorDirective;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3SceneExecutor.*;

/** Durable HOT strike execution and recovery for registered combat scenes. */
final class FrontierV3SceneStrikeExecutor {
    private FrontierV3SceneStrikeExecutor() { }

    static void executeStrike(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease,
                              PhysicalIntentLifecycleOwner lifecycleOwner) {
        if (!FrontierSceneBehaviors.isSettlementAssault(lease)) throw new IllegalArgumentException("strike requires an assault scene");
        List<Body> bodies = lease.members().stream().map(member -> body(level, state, lease, member)).flatMap(Optional::stream).toList();
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(FrontierSceneBehaviors.settlementAssault(lease).assaultId());
        long strikeEpoch = assault == null ? 0L : SettlementAssaultCauseIdentity.hotEpoch(assault, state.physicalIntents().values());
        FrontierV3SettlementAssaultSceneExecutor.StrikePair pair = assault != null
                ? FrontierV3SettlementAssaultSceneExecutor.currentStrikePair(assault, strikeEpoch).orElse(null) : null;
        SubjectId sceneCause = FrontierV3SceneBehaviorRegistry.strikeCause(state, lease,
                pair == null ? null : pair.attackerId(), strikeEpoch);
        Optional<PhysicalIntent> pending = state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.lifecycleOwner() == lifecycleOwner && intent.status() != PhysicalIntentStatus.CONFIRMED)
                .filter(intent -> io.farfrontier.palemirror.frontier.v3.model.SceneStrikeStateSupport.boundTo(lease, intent))
                .filter(intent -> FrontierV3SettlementAssaultReceiptBinding.belongsToLease(state, lease, intent))
                .min(Comparator.comparing(PhysicalIntent::id));
        if (pending.filter(intent -> intent.status() == PhysicalIntentStatus.CONFLICTED).isPresent()) {
            if (lease.status() == SceneLeaseStatus.HOT) submit(runtime, "scene-strike-drain", lease.id().value(),
                    new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            return;
        }
        if (pending.filter(intent -> intent.status() != PhysicalIntentStatus.PREPARED
                && intent.status() != PhysicalIntentStatus.RUNNING
                && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).isPresent()) return;
        if (pending.isEmpty()) {
            if (sceneCause == null || lease.status() == SceneLeaseStatus.DRAINING) return;
            List<Body> attackers;
            List<Body> targets;
            {
                attackers = bodies.stream().filter(body -> body.member().actorId().equals(pair.attackerId())).toList();
                targets = bodies.stream().filter(body -> body.member().actorId().equals(pair.targetId())).toList();
            }
            if (attackers.isEmpty() || targets.isEmpty()) return;
            Body attacker = attackers.getFirst();
            Body target = targets.getFirst();
            if (attacker.entity().distanceToSqr(target.entity()) > 3.61D) return;

            PhysicalIntentId intentId = FrontierV3SettlementAssaultReceiptBinding.intentId(state, lease, sceneCause);
            String key = intentId.value().substring("intent:scene-strike-".length());
            PhysicalIntent intent = new PhysicalIntent(intentId, PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.PREPARED,
                    sceneCause, PhysicalIntentRoleBinding.assaultSceneStrike(attacker.member().actorId(), target.member().actorId(), lease.id(), lease.revision()), position(attacker.entity()), 0,
                    PhysicalPostcondition.SCENE_STRIKE_OBSERVED, lifecycleOwner);
            submit(runtime, "scene-strike-prepare", key, new PhysicalIntentPrepared(intent)); return;
        }
        PhysicalIntent intent = pending.orElseThrow();
        Body attacker = bodies.stream().filter(body -> body.member().actorId().equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ATTACKER))).findFirst().orElse(null);
        Body target = bodies.stream().filter(body -> body.member().actorId().equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.TARGET))).findFirst().orElse(null);
        if (target == null) {
            // A dead but still loaded exact body can supply evidence, never new work.
            var member = lease.members().stream().filter(value -> value.actorId().equals(intent.roles().require(
                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.TARGET))).findFirst().orElse(null);
            Entity entity = member == null ? null : level.getEntity(member.entityId());
            if (member != null && entity instanceof Mob mob && ownsDeclaration(entity, state, lease, member)) {
                target = new Body(member, mob, bioform(state, member.actorId()));
            }
        }
        if (target != null && target.entity().getPersistentData().contains(FrontierV3SceneStrikeReceipt.KEY)) {
            try {
                var saved = FrontierV3SceneStrikeReceipt.load(target.entity().getPersistentData().getCompound(FrontierV3SceneStrikeReceipt.KEY));
                var previous = state.physicalIntents().get(saved.observation().intentId());
                if (saved.world().equals(state.bootstrap().worldId()) && saved.targetEntity().equals(target.entity().getUUID())
                        && previous != null && previous.status() == PhysicalIntentStatus.CONFIRMED
                        && saved.observation().equals(state.physicalObservations().get(saved.observation().id()))) {
                    // WAL confirmation survived but marker removal did not. Clear only the
                    // already-acknowledged exact evidence; never restore its old health.
                    target.entity().getPersistentData().remove(FrontierV3SceneStrikeReceipt.KEY);
                    return;
                }
                if (saved.world().equals(state.bootstrap().worldId()) && saved.targetEntity().equals(target.entity().getUUID())
                        && saved.terminallyAbandoned(state)) {
                    target.entity().getPersistentData().remove(FrontierV3SceneStrikeReceipt.KEY);
                    return;
                }
                if (!saved.matches(state, lease, intent, target.entity().getUUID(), fixed(target.entity().getHealth()))) {
                    inspectUnresolvedStrike(level, runtime, state, intent, target.entity());
                    return;
                }
                io.farfrontier.palemirror.frontier.v3.model.SceneStrikeStateSupport.validateObservation(state, intent, saved.observation());
                submit(runtime, "scene-strike-confirm", intent.id().value(), new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(saved.observation())));
                target.entity().getPersistentData().remove(FrontierV3SceneStrikeReceipt.KEY);
            } catch (IllegalArgumentException invalid) {
                PaleMirrorMod.LOGGER.debug("Unresolved exact strike witness intent={}: {}", intent.id(), invalid.getMessage());
                inspectUnresolvedStrike(level, runtime, state, intent, target.entity());
            }
            return;
        }
        // No witness is not proof that damage did not happen. Never replay an unknown hit.
        if (intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            if (target != null) inspectUnresolvedStrike(level, runtime, state, intent, target.entity());
            else {
                var recorded = state.actorLocations().get(intent.roles().require(
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.TARGET));
                // Exact committed death is evidence independent of entity loading. It is
                // not evidence that this particular hit killed it; abandon, never confirm.
                if (recorded != null && recorded.condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) {
                    advanceUnresolvedStrike(runtime, state, intent);
                } else if (recorded != null && (lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING)) {
                    // Absence does not settle either the actor or the effect. Delegate to
                    // ordinary scene recovery, whose loaded-entity readiness gates inspection.
                    submit(runtime, "scene-strike-target-unobserved", lease.id().value(),
                            new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
                }
            }
            return;
        }
        if (lease.status() == SceneLeaseStatus.DRAINING && intent.status() == PhysicalIntentStatus.RUNNING) {
            submit(runtime, "scene-strike-drain-inspect", intent.id().value(),
                    new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
            return;
        }
        if (target != null && !target.entity().isAlive()) return;
        if (attacker == null || target == null || attacker.entity().distanceToSqr(target.entity()) > 3.61D) return;
        if (intent.status() == PhysicalIntentStatus.PREPARED) { submit(runtime, "scene-strike-running", intent.id().value(), new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty())); return; }

        var effect = state.fencedRecovery().current().get(
                io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport.bindingId(intent));
        if (effect == null || effect.asset() != io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset.EFFECT
                || effect.phase() != io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase.RUNNING
                || !effect.ownerId().equals(intent.causeSubjectId())) return;
        float before = target.entity().getHealth(); attacker.entity().swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        target.entity().hurt(level.damageSources().mobAttack(attacker.entity()), attacker.bioform() ? 2.0F : 1.5F);
        SceneStrikeObservation receipt = new SceneStrikeObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(),
                attacker.member().actorId(), target.member().actorId(), fixed(before), fixed(target.entity().getHealth()));
        var saved = new FrontierV3SceneStrikeReceipt(state.bootstrap().worldId(),
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalSceneBinding(lease.id(), lease.revision()),
                intent.lifecycleOwner(), effect.authorityEpoch(), target.entity().getUUID(), receipt);
        target.entity().getPersistentData().put(FrontierV3SceneStrikeReceipt.KEY, saved.save());
        submit(runtime, "scene-strike-confirm", intent.id().value(), new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
        target.entity().getPersistentData().remove(FrontierV3SceneStrikeReceipt.KEY);
    }

    /** Inspect only an actual owned loaded target; lack of chunk/entity readiness is not a failed inspection. */
    private static void inspectUnresolvedStrike(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, PhysicalIntent intent, net.minecraft.world.entity.LivingEntity target) {
        if (!level.areEntitiesLoaded(ChunkPos.asLong(target.blockPosition()))) return;
        advanceUnresolvedStrike(runtime, state, intent);
    }

    /** Caller supplies actual loaded inspection or exact committed target death, never inferred absence. */
    private static void advanceUnresolvedStrike(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                FrontierWorldState state, PhysicalIntent intent) {
        if (intent.status() == PhysicalIntentStatus.RUNNING) {
            submit(runtime, "scene-strike-witness-unresolved", intent.id().value(),
                    new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
            return;
        }
        if (intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return;
        var fence = state.fencedRecovery().current().get(
                io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport.bindingId(intent));
        if (fence == null || fence.phase() != io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase.AMBIGUOUS
                || fence.asset() != io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset.EFFECT
                || !fence.ownerId().equals(intent.causeSubjectId())) return;
        var next = fence.recoveryAttempts() < io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryBinding.MAX_RECOVERY_ATTEMPTS
                ? PhysicalIntentStatus.UNKNOWN_AFTER_RESTART : PhysicalIntentStatus.CONFLICTED;
        submit(runtime, "scene-strike-inspect", intent.id().value(), new PhysicalIntentTransition(intent.id(), next, Optional.empty()));
    }
}
