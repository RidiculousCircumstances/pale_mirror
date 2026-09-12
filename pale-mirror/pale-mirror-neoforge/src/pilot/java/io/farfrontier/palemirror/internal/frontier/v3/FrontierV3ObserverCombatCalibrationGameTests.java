package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStrike;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.process.FrontierObserverNeutralityContract;
import io.farfrontier.palemirror.frontier.v3.process.HiveSettlementAssaultProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fixed-seed observer calibration where COLD planning and HOT Minecraft damage are independent.
 *
 * <p>The fixture never carries a COLD damage value into the HOT executor.  It admits the normal
 * canonical candidate, lets the registered three-turn physical strike path create its receipt,
 * and measures the receipt's actual health transition and elapsed server turns.  The COLD sample
 * is separately planned and reduced from the same seed.  Thus role/identity/effect/recovery
 * invariants remain exact while the declared calibrated outcome distribution may absorb vanilla
 * damage-model differences.</p>
 */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ObserverCombatCalibrationGameTests {
    private static final int SAMPLES = 16;

    private FrontierV3ObserverCombatCalibrationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-observer-combat-calibration", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void fixedSeedSettlementAssaultUsesIndependentColdPlansAndHotReceipts(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 8, 2));
        List<Sample> cold = new ArrayList<>(SAMPLES), hot = new ArrayList<>(SAMPLES);
        List<Session> sessions = new ArrayList<>(java.util.Collections.nCopies(SAMPLES, null));
        for (int index = 0; index < SAMPLES; index++) cold.add(coldSample(seed(index)));
        for (int index = 0; index < SAMPLES; index++) {
            int sample = index;
            long start = 1L + sample * 5L;
            helper.runAtTickTime(start, () -> sessions.set(sample, startHotSample(helper, origin, seed(sample))));
            helper.runAtTickTime(start + 1L, () -> prepareHotStrike(helper, sessions.get(sample)));
            helper.runAtTickTime(start + 2L, () -> runHotStrike(helper, sessions.get(sample)));
            helper.runAtTickTime(start + 3L, () -> hot.add(confirmAndRelease(helper, sessions.get(sample))));
            helper.runAtTickTime(start + 4L, () -> close(sessions.get(sample)));
        }
        helper.runAtTickTime(1L + SAMPLES * 5L, () -> assertComparable(helper, cold, hot));
    }

    private static long seed(int sample) { return 201L + sample; }

    private static Sample coldSample(long seed) {
        var configuration = FrontierV3FixtureCatalog.settlementAssaultConfiguration(world(seed), seed);
        FrontierWorldState before = configuration.initialState();
        SettlementAssault assault = onlyAssault(before);
        SettlementAssaultStrike strike = HiveSettlementAssaultProcess.planCombat(before,
                        HiveSettlementAssaultProcess.combat(assault, configuration.initialInstant().ticks())).stream()
                .map(event -> event.payload()).filter(SettlementAssaultStrike.class::isInstance)
                .map(SettlementAssaultStrike.class::cast).findFirst().orElseThrow();
        FrontierWorldState after = HiveSettlementAssaultProcess.reduceStrike(before, assault.hiveId(), strike);
        FixedScalar beforeHealth = before.actorLocations().get(strike.targetId()).condition().health();
        FixedScalar afterHealth = after.actorLocations().get(strike.targetId()).condition().health();
        return new Sample(seed, after, assault.id(), strike.attackerId(), strike.targetId(),
                SettlementAssaultCauseIdentity.strike(assault.id(), strike.attackerId(), strike.epoch()),
                beforeHealth.minus(afterHealth), afterHealth.compareTo(FixedScalar.ZERO) > 0,
                afterHealth.equals(FixedScalar.ZERO), configuration.initialState().bootstrap().ruleset().cadence()
                        .hiveSettlementAssaultCombatInterval());
    }

    private static Session startHotSample(GameTestHelper helper, BlockPos origin, long seed) {
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.settlementAssaultConfiguration(world(seed), seed),
                        new EphemeralStore(), 20_000);
        FrontierWorldState state = state(runtime);
        SettlementAssaultSceneCandidate candidate = state.coldSettlementAssaultSceneCandidates().getFirst();
        SceneLease lease = lease(runtime, state, candidate, new SceneLeaseId("lease:observer-combat-" + seed));
        FrontierV3CommandSubmission.submit(runtime, "observer-combat-prepare", lease.id().value(),
                new SettlementAssaultSceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "observer-combat-hot", lease.id().value(),
                new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        List<Entity> bodies = new ArrayList<>();
        for (int index = 0; index < lease.members().size(); index++) {
            BlockPos position = origin.offset(index % 3, 0, index / 3);
            prepareFloor(helper.getLevel(), position);
            Entity body = addOwnedBody(helper, helper.getLevel(), state(runtime), lease, lease.members().get(index), position);
            body.setPos(origin.getX() + .25D + (index % 3) * .4D, origin.getY(), origin.getZ() + .25D + (index / 3) * .4D);
            bodies.add(body);
        }
        return new Session(seed, runtime, lease, bodies);
    }

    private static void prepareHotStrike(GameTestHelper helper, Session session) {
        FrontierV3SceneExecutor.executeStrike(helper.getLevel(), session.runtime(), state(session.runtime()), session.lease());
        PhysicalIntent intent = onlyStrike(state(session.runtime()));
        helper.assertTrue(intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED,
                "the independent HOT sample must first retain its canonical prepared strike");
        session.preparedAt = helper.getLevel().getGameTime();
    }

    private static void runHotStrike(GameTestHelper helper, Session session) {
        FrontierV3SceneExecutor.executeStrike(helper.getLevel(), session.runtime(), state(session.runtime()), session.lease());
        helper.assertTrue(onlyStrike(state(session.runtime())).status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING,
                "the physical HOT sample must durably enter RUNNING before vanilla damage");
    }

    private static Sample confirmAndRelease(GameTestHelper helper, Session session) {
        FrontierV3SceneExecutor.executeStrike(helper.getLevel(), session.runtime(), state(session.runtime()), session.lease());
        FrontierWorldState confirmedState = state(session.runtime());
        PhysicalIntent intent = onlyStrike(confirmedState);
        SceneStrikeObservation receipt = (SceneStrikeObservation) confirmedState.physicalObservations()
                .get(intent.postconditionObservationId().orElseThrow());
        helper.assertTrue(receipt.targetHealthAfter().compareTo(receipt.targetHealthBefore()) < 0,
                "the HOT sample must measure a real vanilla wound, never a COLD-supplied expected damage value");
        FrontierV3CommandSubmission.submit(session.runtime(), "observer-combat-drain", session.lease().id().value(),
                new SceneLeaseTransition(session.lease().id(), SceneLeaseStatus.DRAINING));
        FrontierWorldState draining = state(session.runtime());
        List<SceneMemberPosition> released = session.lease().members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                session.lease().memberPosition(member.actorId()), draining.actorLocations().get(member.actorId()).condition().health())).toList();
        FrontierV3CommandSubmission.submit(session.runtime(), "observer-combat-release", session.lease().id().value(),
                new SceneLeaseReleased(session.lease().id(), released));
        FrontierWorldState after = state(session.runtime());
        return new Sample(session.seed(), after, ((io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause) session.lease().cause()).assaultId(),
                receipt.attackerId(), receipt.targetId(), intent.causeSubjectId(), receipt.targetHealthBefore().minus(receipt.targetHealthAfter()),
                receipt.targetHealthAfter().compareTo(FixedScalar.ZERO) > 0, receipt.targetHealthAfter().equals(FixedScalar.ZERO),
                helper.getLevel().getGameTime() - session.preparedAt);
    }

    private static void assertComparable(GameTestHelper helper, List<Sample> cold, List<Sample> hot) {
        helper.assertTrue(cold.size() == SAMPLES && hot.size() == SAMPLES, "the declared calibration requires all sixteen fixed-seed samples");
        for (int index = 0; index < SAMPLES; index++) {
            Sample expected = cold.get(index), observed = hot.get(index);
            helper.assertTrue(expected.seed() == observed.seed() && expected.attacker().equals(observed.attacker())
                            && expected.target().equals(observed.target()) && expected.cause().equals(observed.cause()),
                    "HOT must independently select the same exact seed-bound participants and cause as COLD");
        }
        FrontierObserverNeutralityContract.Declaration declaration = FrontierObserverNeutralityContract.declaration(
                FrontierDurationProcessDriverRegistry.Family.SETTLEMENT_ASSAULT);
        FrontierObserverNeutralityContract.Run coldRun = run(declaration, cold);
        FrontierObserverNeutralityContract.Run hotRun = run(declaration, hot);
        FrontierObserverNeutralityContract.requireComparable(coldRun, hotRun);
        PaleMirrorMod.LOGGER.info("PMV3_OBSERVER_CALIBRATION version={} seeds=201..216 cold={} hot={} tolerances=successes:3,casualties:3,duration:1200",
                declaration.version(), coldRun.calibration(), hotRun.calibration());
        helper.assertTrue(java.util.stream.IntStream.range(0, SAMPLES).anyMatch(index -> !cold.get(index).damage().equals(hot.get(index).damage())),
                "calibration must accept measured physical damage distributions without requiring injected identical hit values");
        boolean foreignRejected = false;
        try {
            FrontierObserverNeutralityContract.requireComparable(coldRun, new FrontierObserverNeutralityContract.Run(declaration,
                    hotRun.actorIds(), hotRun.objectIds(), hotRun.claims(), hotRun.custody(), hotRun.completedStages(), hotRun.retainedWork(),
                    hotRun.legalTopology(), java.util.Set.of("cause:foreign"), hotRun.recoveryDiscriminators(), hotRun.randomOpportunityKeys(), hotRun.calibration()));
        } catch (IllegalArgumentException expected) { foreignRejected = true; }
        helper.assertTrue(foreignRejected, "combat tolerance must not admit a foreign confirmed effect");
        helper.succeed();
    }

    private static FrontierObserverNeutralityContract.Run run(FrontierObserverNeutralityContract.Declaration declaration, List<Sample> samples) {
        LinkedHashSet<String> actors = new LinkedHashSet<>(), objects = new LinkedHashSet<>(), stages = new LinkedHashSet<>();
        LinkedHashSet<String> topology = new LinkedHashSet<>(), effects = new LinkedHashSet<>(), opportunities = new LinkedHashSet<>();
        LinkedHashMap<String, Long> claims = new LinkedHashMap<>(), custody = new LinkedHashMap<>(), work = new LinkedHashMap<>();
        LinkedHashMap<String, String> recovery = new LinkedHashMap<>();
        int successes = 0, casualties = 0; long duration = 0L;
        for (Sample sample : samples) {
            SettlementAssault assault = sample.state().strategicPlans().settlementAssaults().get(sample.assaultId());
            String prefix = "seed:" + sample.seed() + ":";
            assault.attackerIds().forEach(id -> actors.add(prefix + id.value()));
            assault.defenderIds().forEach(id -> actors.add(prefix + id.value()));
            objects.add(prefix + assault.id().value()); claims.put(prefix + "epoch", (long) assault.nextStrikeEpoch());
            custody.put(prefix + "open-scene-leases", sample.state().sceneLeases().values().stream()
                    .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED).count());
            stages.add(prefix + assault.status());
            work.put(prefix + "strike-epoch", (long) assault.nextStrikeEpoch()); topology.add(prefix + assault.tacticalPlan().policy());
            effects.add(prefix + sample.cause().value()); recovery.put(prefix + "status", assault.status().name());
            opportunities.add(prefix + sample.attacker().value() + ":" + sample.target().value());
            if (sample.success()) successes++; if (sample.casualty()) casualties++; duration += sample.duration();
        }
        return new FrontierObserverNeutralityContract.Run(declaration, actors, objects, claims, custody, stages, work, topology, effects,
                recovery, opportunities, new FrontierObserverNeutralityContract.CalibrationSample(samples.size(), successes, casualties, duration));
    }

    private static SettlementAssault onlyAssault(FrontierWorldState state) {
        return state.strategicPlans().settlementAssaults().values().stream().findFirst().orElseThrow();
    }

    private static WorldId world(long seed) { return new WorldId("frontier:observer-combat-" + seed); }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                    SettlementAssaultSceneCandidate candidate, SceneLeaseId id) {
        List<SceneMember> members = candidate.memberPositions().keySet().stream().sorted()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(runtime.checkpointImage().orElseThrow().worldId(), actor))).toList();
        return SceneLease.forCause(id, runtime.checkpointImage().orElseThrow().worldId(),
                new io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause(candidate.assaultId(), candidate.settlementId()),
                candidate.handoffPosition(), runtime.checkpointImage().orElseThrow().instant(), runtime.checkpointImage().orElseThrow().revision().value(),
                SceneLeaseStatus.PREPARED, members, SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), java.util.Set.of(), Optional.empty());
    }

    private static Entity addOwnedBody(GameTestHelper helper, ServerLevel level, FrontierWorldState state, SceneLease lease,
                                       SceneMember member, BlockPos position) {
        boolean bioform = member.actorId().value().startsWith("bioform:");
        net.minecraft.world.entity.Mob body = bioform ? net.minecraft.world.entity.EntityType.ZOMBIE.create(level)
                : net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        helper.assertTrue(body != null, "the exact HOT calibration body must be constructible");
        body.setUUID(member.entityId()); body.setPos(position.getX() + .5D, position.getY(), position.getZ() + .5D); body.setNoAi(true);
        if (body instanceof net.minecraft.world.entity.monster.Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                FrontierV3AmbientActorExecutor.bioformProfile(state, member.actorId()));
        body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, lease.id().value());
        body.getPersistentData().putString(FrontierV3SceneExecutor.ACTOR_KEY, member.actorId().value());
        body.getPersistentData().putLong(FrontierV3SceneExecutor.REVISION_KEY, lease.revision());
        helper.assertTrue(level.addFreshEntity(body), "the exact HOT calibration body must enter the physical level");
        return body;
    }

    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }

    private static PhysicalIntent onlyStrike(FrontierWorldState state) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE)
                .reduce((left, right) -> right).orElseThrow(() -> new IllegalStateException("HOT sample did not retain a strike intent"));
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }

    private static void close(Session session) {
        session.bodies().forEach(Entity::discard);
        session.runtime().shutdown();
    }

    private record Sample(long seed, FrontierWorldState state, SubjectId assaultId, SubjectId attacker, SubjectId target,
                          SubjectId cause, FixedScalar damage, boolean success, boolean casualty, long duration) { }

    private static final class Session {
        private final long seed; private final FrontierV3ServerRuntime<FrontierWorldState, ?> runtime;
        private final SceneLease lease; private final List<Entity> bodies; private long preparedAt;

        private Session(long seed, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, List<Entity> bodies) {
            this.seed = seed; this.runtime = runtime; this.lease = lease; this.bodies = bodies;
        }
        private long seed() { return seed; }
        private FrontierV3ServerRuntime<FrontierWorldState, ?> runtime() { return runtime; }
        private SceneLease lease() { return lease; }
        private List<Entity> bodies() { return bodies; }
    }

    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }
}
