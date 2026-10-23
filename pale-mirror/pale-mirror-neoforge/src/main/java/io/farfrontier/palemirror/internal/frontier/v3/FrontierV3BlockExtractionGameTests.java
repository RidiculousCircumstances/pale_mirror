package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Isolated real-block/loot adapter checks, not an end-to-end farmer acceptance claim. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3BlockExtractionGameTests {
    private FrontierV3BlockExtractionGameTests() { }

    @GameTest(batch = "pm-frontier-v3-block-extraction", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void retainedLootUsesOneAdapterForGrainAndStoneWithoutIssuingInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:extraction-native"), 71L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution)
                .commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var worker = EntityType.VILLAGER.create(level);
        if (worker == null) throw new IllegalStateException("native extraction needs a real worker");
        worker.setUUID(ActorBodyId.entityId(state.bootstrap().worldId(), actor));
        FrontierV3ActorCarrierComposition.stamp(worker, FrontierV3ActorCarrierComposition.fromCanonical(
                state, actor, ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, worker.getUUID(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, body.physicalEpoch()));
        var spawn = helper.absolutePos(new BlockPos(1, 1, 1));
        worker.setPos(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
        worker.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(worker), "the fixture must admit its actual declared worker body");
        var source = new java.util.concurrent.atomic.AtomicReference<>(Optional.of(state));
        var port = new FrontierV3MinecraftBlockExtraction(level, worker,
                FrontierV3ActorActuation.capture(state, worker, execution, source::get), state.bootstrap().ruleset().tools());
        BlockPos soil = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos crop = soil.above();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 6), 3);
        var target = new BlockPosition(crop.getX(), crop.getY(), crop.getZ());
        helper.assertTrue(rejects(() -> port.prepare("native:immature", execution, target, FieldHarvestExtraction.DEFINITION)),
                "an immature physical crop cannot mint mature grain");
        level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        var grain = port.prepare("native:grain", execution, target, FieldHarvestExtraction.DEFINITION);
        helper.assertTrue(level.getBlockState(crop).getValue(CropBlock.AGE) == 7 && grain.quantity("minecraft:wheat") == 1,
                "actual standard loot-table evaluation prepares one grain without mutating the world");
        var retained = FrontierV3BlockExtractionCodec.read(FrontierV3BlockExtractionCodec.write(grain));
        helper.assertTrue(port.apply(retained) == BlockExtractionPort.Result.APPLIED && level.getBlockState(crop).isAir()
                        && port.apply(retained) == BlockExtractionPort.Result.POSTCONDITION_PRESENT
                        && worker.getOffhandItem().isEmpty(),
                "a restored effect removes the real block once, does not reroll or issue resources, and leaves replant to the field");
        checkTerritorialLoot(helper, port, execution, crop);

        var stone = new BlockExtraction.Definition("pale_mirror:test_stone_v1",
                new BlockExtraction.Block("minecraft:stone", Map.of()),
                new BlockExtraction.Block("minecraft:air", Map.of()),
                new io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement(
                        io.farfrontier.palemirror.frontier.v3.model.tooling.ToolAbilities.MINE, 0, 1), "minecraft:blocks/stone",
                List.of(new BlockExtraction.Output("minecraft:cobblestone", 1)));
        level.setBlock(crop, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(rejects(() -> port.prepare("native:no-tool", execution, target, stone)),
                "stone cannot borrow bare-hand permission");
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        var rock = port.prepare("native:stone", execution, target, stone);
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        helper.assertTrue(port.apply(rock) == BlockExtractionPort.Result.TOOL_CHANGED && level.getBlockState(crop).is(Blocks.STONE),
                "a changed tool cannot authorize an already prepared destructive write");
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        level.setBlock(crop, Blocks.DIRT.defaultBlockState(), 3);
        helper.assertTrue(port.apply(rock) == BlockExtractionPort.Result.SOURCE_CHANGED && level.getBlockState(crop).is(Blocks.DIRT),
                "foreign source replacement is not repaired or converted into output");
        level.setBlock(crop, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(rock.quantity("minecraft:cobblestone") == 1 && port.apply(rock) == BlockExtractionPort.Result.APPLIED,
                "the same adapter resolves Minecraft's actual stone loot with a correct tool");
        var cover = ExtractionRules.terracedGraybox().surfaceCut().orElseThrow().cover();
        level.setBlock(crop, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SHOVEL));
        var cap = port.prepare("native:surface-cap", execution, target, cover);
        helper.assertTrue(cap.quantity("minecraft:dirt") == 1 && cap.quantity("minecraft:cobblestone") == 0
                        && port.apply(cap) == BlockExtractionPort.Result.APPLIED && level.getBlockState(crop).isAir(),
                "ordinary surface preparation uses native grass loot, not free air projection or invented stone");
        var iron = FrontierRulesets.mineralDepositsGraybox().extraction().definitionFor(
                new BlockExtraction.Block("minecraft:iron_ore", Map.of())).orElseThrow();
        level.setBlock(crop, Blocks.IRON_ORE.defaultBlockState(), 3);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        var ore = port.prepare("native:iron", execution, target, iron);
        var savedOre = FrontierV3BlockExtractionCodec.read(FrontierV3BlockExtractionCodec.write(ore));
        helper.assertTrue(savedOre.output().equals(iron.coldOutput()) && savedOre.quantity("minecraft:raw_iron") == 1
                        && port.apply(savedOre) == BlockExtractionPort.Result.APPLIED && level.getBlockState(crop).isAir()
                        && port.apply(savedOre) == BlockExtractionPort.Result.POSTCONDITION_PRESENT,
                "the existing stone pick and native iron loot agree with COLD and survive receipt recovery without duplicate extraction");
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        level.setBlock(crop, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(crop);
        if (chest == null) throw new IllegalStateException("native extraction requires the actual chest entity");
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        var chestDefinition = new BlockExtraction.Definition("pale_mirror:test_chest_v1",
                FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(crop)),
                new BlockExtraction.Block("minecraft:air", Map.of()),
                new io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement(
                        io.farfrontier.palemirror.frontier.v3.model.tooling.ToolAbilities.MINE, 0, 1), "minecraft:blocks/chest",
                List.of(new BlockExtraction.Output("minecraft:chest", 1)));
        helper.assertTrue(rejects(() -> port.prepare("native:chest", execution, target, chestDefinition))
                        && rejects(() -> port.apply(BlockExtraction.prepareKnown("native:chest", execution, target,
                            chestDefinition, chestDefinition.before())))
                        && level.getBlockEntity(crop) == chest && chest.getItem(0).getCount() == 3,
                "an unsupported block entity cannot lose its unretained contents through preparation or known-state bypass");
        level.setBlock(crop, Blocks.STONE.defaultBlockState(), 3);
        source.set(Optional.empty());
        helper.assertTrue(port.apply(rock) == BlockExtractionPort.Result.UNAVAILABLE && level.getBlockState(crop).is(Blocks.STONE),
                "loss of captured authority cannot remove a block");
        worker.discard();
        helper.succeed();
    }

    private static boolean rejects(Runnable operation) {
        try { operation.run(); return false; }
        catch (IllegalArgumentException expected) { return true; }
    }

    /** Synchronous fixture-only biome change, restored before any other test/server tick. */
    private static void checkTerritorialLoot(GameTestHelper helper, FrontierV3MinecraftBlockExtraction port,
            ActorExecutionId execution, BlockPos crop) {
        var level = helper.getLevel();
        var chunk = level.getChunkSource().getChunkNow(Math.floorDiv(crop.getX(), 16), Math.floorDiv(crop.getZ(), 16));
        if (chunk == null) throw new IllegalStateException("native loot fixture requires its already loaded template chunk");
        var section = chunk.getSection(level.getSectionIndex(crop.getY()));
        var prior = section.getBiomes();
        int x = Math.floorDiv(crop.getX(), 4), y = Math.floorDiv(crop.getY(), 4), z = Math.floorDiv(crop.getZ(), 4);
        var sampler = level.getChunkSource().randomState().sampler();
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var rules = io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture.rules();
        try {
            for (var biome : rules.biomes().keySet().stream().sorted().toList()) {
                var holder = registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.parse(biome)));
                section.fillBiomesFromNoise((qx, qy, qz, ignored) -> qx == x && qy == y && qz == z
                        ? holder : prior.get(qx & 3, qy & 3, qz & 3), sampler, x & ~3, y & ~3, z & ~3);
                level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
                int expected = rules.yieldInBiome("minecraft:wheat", biome);
                int ordinary = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(crop), level, crop, null)
                        .stream().filter(stack -> stack.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum();
                var prepared = port.prepare("native:territorial-" + expected, execution,
                        new BlockPosition(crop.getX(), crop.getY(), crop.getZ()), FieldHarvestExtraction.definition(expected));
                helper.assertTrue(ordinary == expected && prepared.quantity("minecraft:wheat") == expected,
                        "ordinary block drops and worker extraction must agree with canonical yield in " + biome);
            }
        } finally {
            section.fillBiomesFromNoise((qx, qy, qz, ignored) -> prior.get(qx & 3, qy & 3, qz & 3),
                    sampler, x & ~3, y & ~3, z & ~3);
        }
    }
}
