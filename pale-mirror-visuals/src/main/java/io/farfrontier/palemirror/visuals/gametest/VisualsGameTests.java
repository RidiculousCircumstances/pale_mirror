package io.farfrontier.palemirror.visuals.gametest;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.genesis.FrontierClimate;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import io.farfrontier.palemirror.visuals.genesis.FrontierGenesisCompiler;
import io.farfrontier.palemirror.visuals.runtime.AuthoredVisualProvider;
import io.farfrontier.palemirror.visuals.runtime.FrontierGenesisRuntime;
import io.farfrontier.palemirror.visuals.runtime.AuthoredRegionSeedNbt;
import io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity;
import io.farfrontier.palemirror.visuals.threat.VisualEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PaleMirrorVisualsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VisualsGameTests {
    private VisualsGameTests() { }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void threatHeartPersistsCanonicalVisualIdentity(GameTestHelper helper) {
        ThreatHeartEntity heart = VisualEntityTypes.THREAT_HEART.get().create(helper.getLevel());
        helper.assertTrue(heart != null, "Threat Heart must have a registered entity factory");
        heart.moveTo(helper.absolutePos(new BlockPos(0, 4, 0)), 0, 0);
        heart.facilityId("pale_mirror:test_facility"); heart.setStage(4);
        helper.getLevel().addFreshEntity(heart);
        CompoundTag saved = new CompoundTag(); heart.saveWithoutId(saved);
        ThreatHeartEntity restored = VisualEntityTypes.THREAT_HEART.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "Threat Heart must reload from its exact entity type");
        restored.load(saved);
        helper.assertValueEqual(restored.facilityId(), "pale_mirror:test_facility",
                "visual carrier must persist its canonical facility identity");
        helper.assertValueEqual(restored.stage(), 4, "visual carrier must persist the projected threat stage");
        heart.discard(); helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void authoredManifestRoundTripsExactly(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(918273L, 1, new VisualPoint(8000, 72, -4000),
                FrontierClimate.COLD_TAIGA);
        helper.assertValueEqual(AuthoredRegionSeedNbt.read(AuthoredRegionSeedNbt.write(seed)), seed,
                "persisted authored manifest must preserve every identity and geometry fact");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void genesisCatalogIsDeterministicAndChunkLocal(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(913771L, 0, new VisualPoint(512, 72, 512),
                FrontierClimate.TEMPERATE, (x, z) -> 72);
        FrontierGenesisCompiler compiler = new FrontierGenesisCompiler();
        var first = compiler.compile(java.util.List.of(seed));
        var second = compiler.compile(java.util.List.of(seed));
        helper.assertValueEqual(first.hash(), second.hash(), "compiled catalog hash must be deterministic");
        helper.assertValueEqual(first.chunks().keySet(), second.chunks().keySet(),
                "compiled chunk address space must be deterministic");
        int rails = 0;
        for (var entry : first.chunks().entrySet()) {
            var slice = entry.getValue();
            helper.assertValueEqual(slice.chunkKey(), entry.getKey(), "slice must retain its owning chunk");
            for (var column : slice.terrain()) helper.assertValueEqual(
                    net.minecraft.world.level.ChunkPos.asLong(column.x() >> 4, column.z() >> 4), entry.getKey(),
                    "terrain write escaped its chunk-local slice");
            for (var rail : slice.rails()) {
                helper.assertValueEqual(new net.minecraft.world.level.ChunkPos(rail.rail()).toLong(), entry.getKey(),
                        "rail write escaped its chunk-local slice");
                rails++;
            }
            for (var position : slice.blocks().keySet()) helper.assertValueEqual(
                    new net.minecraft.world.level.ChunkPos(position).toLong(), entry.getKey(),
                    "template write escaped its chunk-local slice");
        }
        helper.assertValueEqual(rails, seed.baselineRailNodes().size(), "every authored rail node must compile once");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void authoredWorldgenFeatureIsInstalledInTargetBiomeWithSable(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(FrontierGenesisRuntime.readiness().ready(), "genesis catalog must be ready before use");
            var seed = AuthoredVisualProvider.INSTANCE.discoverAuthoredRegions(helper.getLevel()).stream()
                    .findFirst().orElseThrow();
            var target = new net.minecraft.world.level.ChunkPos(seed.anchor().x() >> 4, seed.anchor().z() >> 4);
            var expected = FrontierGenesisRuntime.compiledChunk(target.toLong());
            helper.assertTrue(expected != null, "authored settlement chunk must have one compiled slice");
            var placedFeatureKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.PLACED_FEATURE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            PaleMirrorVisualsMod.MOD_ID, "authored_region"));
            var generator = helper.getLevel().getChunkSource().getGenerator();
            var biome = generator.getBiomeSource().getNoiseBiome(
                    net.minecraft.core.QuartPos.fromBlock(seed.anchor().x()),
                    net.minecraft.core.QuartPos.fromBlock(seed.anchor().y()),
                    net.minecraft.core.QuartPos.fromBlock(seed.anchor().z()),
                    helper.getLevel().getChunkSource().randomState().sampler());
            boolean featureInstalled = biome.value().getGenerationSettings().features().stream()
                    .flatMap(net.minecraft.core.HolderSet::stream)
                    .anyMatch(holder -> holder.is(placedFeatureKey));
            helper.assertTrue(featureInstalled,
                    "authored_region placed feature must be installed in the target overworld biome");
            helper.succeed();
        });
    }
}
