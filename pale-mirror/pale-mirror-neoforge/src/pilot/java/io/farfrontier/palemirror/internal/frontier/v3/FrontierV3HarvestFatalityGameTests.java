package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Connected native work/fatality component; not process-crash or ordinary player-visit acceptance. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3HarvestFatalityGameTests {
    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 100)
    public static void zeroHealthFarmerSettlesTerrainWorkAndRetiresWithoutWaitingForAnotherTick(GameTestHelper helper) {
        var level = helper.getLevel(); var outcome = ResourceFieldCycle.WorkOutcome.TILLED;
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:field-fatality"), 125);
        var origin = HarvestPendingWorkFixture.create(bootstrap, outcome);
        var old = origin.state().resourceSites().cycle(origin.site()).layout().requireCell(origin.job().target().cellId()).crop();
        var target = helper.absolutePos(new BlockPos(12, 10, 12));
        var fixture = HarvestPendingWorkFixture.create(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(bootstrap,
                target.getX() - old.x(), target.getY() - old.y(), target.getZ() - old.z()), outcome);
        var state = fixture.state(); var job = fixture.job(); var lease = fixture.lease();
        var cycle = state.resourceSites().cycle(job.siteId()); var cell = cycle.layout().requireCell(job.target().cellId());
        helper.assertValueEqual(cell.crop(), new BlockPosition(target.getX(), target.getY(), target.getZ()),
                "fixture work remains inside the declared native template");
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, fixture.due().dueAt(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(fixture.due()), base.transactionCommitter());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        var body = EntityType.VILLAGER.create(level);
        var id = ActorBodyAuthority.current(state, job.workerId());
        body.setUUID(ActorBodyId.entityId(state.bootstrap().worldId(), job.workerId()));
        body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                FrontierV3ExactItemPresentation.materializedStack(job.equipment().lastUse().orElseThrow().before()));
        body.setPos(target.getX() + .5, target.getY(), target.getZ() + .5); body.setNoAi(true);
        FrontierV3ActorCarrierComposition.stamp(body, FrontierV3ActorCarrierComposition.fromCanonical(state, job.workerId(),
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, body.getUUID(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0, id.physicalEpoch()));
        helper.assertTrue(level.addFreshEntity(body), "declared native worker is indexed");
        try {
            level.setBlock(target.below(2), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(target.below(), Blocks.DIRT.defaultBlockState(), 2);
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 2);
            var ledger = FrontierV3ResourceSiteLedger.get(level);
            var ground = new LinkedHashMap<ResourceFieldLayout.CellId, ResourceFieldCycle.Soil>();
            cycle.cellStates().forEach((key, value) -> ground.put(key, value.soil()));
            var site = FrontierResourceSitePlan.compile(state.bootstrap()).get(job.siteId());
            ledger.adoptSurveyedField(new ResourceSiteDeclaration(site.id(), site.settlementId(), site.facilityId(), site.kind(),
                    new FieldLandOrigin(FieldLandOrigin.Kind.SURVEYED, ground)), cycle, job.intentId());
            var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) ledger.fieldClaim(job.siteId());
            var witness = FrontierV3ResourceFieldWitness.claimed(job.siteId(), cycle.epoch(), ResourceFieldPhysicalSurface.fromCycle(cycle))
                    .begin(cycle.physicalWorkTransition(cell.id()).orElseThrow(), FrontierV3ResourceFieldWorkExecutor.cause(job, cycle, cell.id()));
            ledger.replaceFieldClaim(owner, owner.withWitness(witness)); ledger.persist(level);
            // Tilling changes terrain and the retained hoe condition in one operation.
            var operation = FrontierV3FieldWorkOperation.apply(level, state, lease, job, body, cell,
                    witness.cell(cell.id()).pending().orElseThrow(), null);
            if (operation.status() == FrontierV3MinecraftOperation.Status.WAITING_DURABILITY) {
                helper.assertTrue(level.getBlockState(target.below()).is(Blocks.DIRT), "pending permission cannot till early");
                FrontierV3OperationLedger.get(level).persist(level);
                operation = FrontierV3FieldWorkOperation.apply(level, state, lease, job, body, cell,
                        witness.cell(cell.id()).pending().orElseThrow(), null);
            }
            helper.assertTrue(operation.complete() && level.getBlockState(target.below()).is(Blocks.FARMLAND),
                    "actual tilling precedes the native death, without canonical completion yet");
            body.setHealth(0);
            var resources = FrontierV3ActorDeathResourceComposition.prepare(level, runtime, body, id);
            helper.assertValueEqual(resources.effects().size(), 1, "pre-loot zero-health observation retains the exact terrain effect");
            var identity = new ActorActuationId(id, HarvestDeathResources.execution(state, job.workerId()).orElseThrow());
            var declaration = resources.effects().getFirst().child().declaration();
            helper.assertTrue(declaration.participants().stream().anyMatch(port -> port.medium() == FrontierV3OperationReceipt.Medium.ENTITIES),
                    "tool wear includes its actual body participant in the durable operation");
            var location = state.actorLocations().get(job.workerId());
            var death = new ActorBodyDied(id, location.body(), location.condition().health(), Optional.empty(),
                    Optional.of(identity.execution()), "fixture:native-field-fatality");
            var fatality = FrontierV3BodyFatalityOperation.commit(level, runtime, body, death, resources.effects(),
                    () -> body.die(level.damageSources().generic()), () -> body.getPose() == Pose.DYING);
            resources.settle();
            var next = runtime.decodedState().orElseThrow();
            helper.assertTrue(next.resourceSites().site(job.siteId()).harvestJob(job.id()).isEmpty()
                    && HarvestDeathResources.execution(next, job.workerId()).isEmpty(), "job and execution retire in this same causal turn");
            helper.assertTrue(next.resourceSites().cycle(job.siteId()).cell(cell.id()).soil() == ResourceFieldCycle.Soil.FARMLAND
                    && body.getOffhandItem().isEmpty(), "real work remains accounted without inventing yield");
            helper.assertTrue(FrontierV3BodyFatalityOperation.finish(level, runtime, fatality, id), "all registered aftermath owners release fatality");
            helper.assertTrue(!FrontierV3OperationLedger.get(level).require(fatality).retirable(),
                    "canonical closure cannot manufacture a native corpse-file save");
        } finally { body.discard(); runtime.shutdown(); }
        helper.succeed();
    }
}
