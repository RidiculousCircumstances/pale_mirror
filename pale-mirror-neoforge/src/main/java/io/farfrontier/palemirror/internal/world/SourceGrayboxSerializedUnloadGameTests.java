package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Proves the exact body hand-off when Minecraft serializes a player-free HOT chunk. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxSerializedUnloadGameTests {
    private SourceGrayboxSerializedUnloadGameTests() { }

    // This proof uses an exact source-world actor coordinate and temporarily
    // narrows the level-wide border. It must not run concurrently with other
    // materializer fixtures that can hold the same deterministic actor UUID.
    @GameTest(batch = "pm-source-graybox-serialized-unload", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void serializedUnloadResumesOneExactBodyWhenHotDemandReturns(GameTestHelper helper) {
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(54_161L);
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(helper.getLevel());
        var initial = data.actorExecution().actors().stream()
                .filter(actor -> actor.kind() == ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT)
                .filter(actor -> zone.safeToDrain(position(actor)))
                .findFirst().orElseThrow(() -> new IllegalStateException("GameTest fixture has no resident outside the drain safety radius"));
        String residentId = initial.id();
        BlockPos position = position(initial);
        helper.getLevel().getChunkAt(position);
        TestBorder border = TestBorder.openAround(helper.getLevel().getWorldBorder(), position);
        try {
            prepareFloor(helper, position);
            SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
            Map<String, Entity> admitted = new LinkedHashMap<>();
            long gameTick = data.actorExecutionGameTime(helper.getLevel().getGameTime());
            helper.assertTrue(data.prepareActor(residentId, SourceGrayboxActorExecutionRuntime.HOLDER, gameTick),
                    "the fixture needs one physical lease before testing the chunk-unload hand-off");
            String lease = data.actorExecution().actor(residentId).orElseThrow().leaseId();
            materializer.apply(helper.getLevel(), data.snapshot(), data.actorExecution(), admitted);
            helper.assertTrue(data.activateActor(residentId, lease, SourceGrayboxActorExecutionRuntime.HOLDER, gameTick + 1L),
                    "the physical body must become HOT before chunk serialization");
            Villager body = resident(helper, position, residentId);
            body.setPos(position.getX() + 2.5d, ReferenceGrayboxLayout.GROUND_Y + 1, position.getZ() + 1.5d);
            String key = SourceGrayboxMaterializer.entityKey(residentId, "RESIDENT");
            var hot = data.actorExecution().actor(residentId).orElseThrow();

            helper.assertTrue(SourceGrayboxActorExecutionRuntime.beginUnloadedHotDrain(data, hot, body, gameTick + 2L),
                    "a normal chunk unload must retain the exact live lease as DRAINING");
            var draining = data.actorExecution().actor(residentId).orElseThrow();
            helper.assertValueEqual(draining.mode(), ReferenceGrayboxActorExecutionState.Mode.DRAINING,
                    "an unloaded HOT body may not be declared dead or COLD just because its chunk left memory");
            helper.assertValueEqual(draining.leaseId(), lease, "the serialized body must retain its original lease");
            helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed(key),
                    "the duplicate-prevention reservation must survive until the serialized body is inspected");

            helper.assertTrue(SourceGrayboxActorExecutionRuntime.resumeDemandedDrainingBody(data, draining, body, true, gameTick + 3L),
                    "a returning player demand must re-adopt the exact reloaded body, not create another one");
            var resumed = data.actorExecution().actor(residentId).orElseThrow();
            helper.assertValueEqual(resumed.mode(), ReferenceGrayboxActorExecutionState.Mode.HOT,
                    "the exact serialized body must resume HOT ownership");
            helper.assertValueEqual(resumed.leaseId(), lease, "resume must preserve the old lease rather than mint a replacement");
            helper.assertValueEqual(residents(helper, position, residentId), 1,
                    "unload followed by return must leave exactly the original body in the loaded scene");
            helper.assertValueEqual(body.getX(), position.getX() + 2.5d,
                    "resume must retain the observed Minecraft position instead of snapping to a source marker");
        } finally {
            border.restore();
        }
        helper.succeed();
    }

    private static void prepareFloor(GameTestHelper helper, BlockPos position) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            helper.getLevel().setBlock(position.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(position.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(position.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static Villager resident(GameTestHelper helper, BlockPos position, String residentId) {
        return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(position).inflate(32), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).stream().findFirst().orElseThrow();
    }

    private static int residents(GameTestHelper helper, BlockPos position, String residentId) {
        return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(position).inflate(32), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).size();
    }

    private static BlockPos position(ReferenceGrayboxActorExecutionState.ActorState actor) {
        return new BlockPos(Math.floorDiv(actor.actualXSixteenths(), ReferenceGrayboxActorExecutionState.POSITION_SCALE),
                ReferenceGrayboxLayout.GROUND_Y + 1,
                Math.floorDiv(actor.actualZSixteenths(), ReferenceGrayboxActorExecutionState.POSITION_SCALE));
    }

    private record TestBorder(WorldBorder border, double centerX, double centerZ, double size) {
        static TestBorder openAround(WorldBorder border, BlockPos position) {
            TestBorder prior = new TestBorder(border, border.getCenterX(), border.getCenterZ(), border.getSize());
            border.setCenter(position.getX() + .5d, position.getZ() + .5d);
            border.setSize(64.0d);
            return prior;
        }

        void restore() {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
    }
}
