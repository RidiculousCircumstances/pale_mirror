package io.farfrontier.palemirror.visuals.gametest;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.genesis.FrontierClimate;
import io.farfrontier.palemirror.visuals.genesis.AuthoredModuleCompiler;
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
        CompoundTag legacyShape = AuthoredRegionSeedNbt.write(seed);
        legacyShape.getCompound("settlementSite").remove("buildings");
        try {
            AuthoredRegionSeedNbt.read(legacyShape);
            throw new AssertionError("a pre-v40 manifest shape must fail closed");
        } catch (IllegalArgumentException expected) {
            helper.assertTrue(expected.getMessage().contains("requires functional buildings"),
                    "manifest rejection must identify the missing typed building contract");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void authoredMineBlueprintIsSanitizedAndReceivesBoundedKinetics(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(918273L, 1, new VisualPoint(8000, 72, -4000),
                FrontierClimate.TEMPERATE);
        var module = seed.alternateMineSite().stagedModules().stream()
                .filter(value -> value.stage().equals("machinery"))
                .findFirst().orElseThrow().module();
        var snapshot = AuthoredModuleCompiler.compile(module);
        helper.assertTrue(!snapshot.blocks().isEmpty(), "staged industrial building must compile physical cells");
        boolean forbidden = snapshot.blocks().stream().map(value -> net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(value.state().getBlock()))
                .anyMatch(id -> id.getPath().contains("spawner") || id.getPath().equals("tnt")
                        || id.getPath().contains("chest") || id.getPath().equals("barrel")
                        || id.getPath().contains("ore") || id.getPath().startsWith("raw_"));
        helper.assertTrue(!forbidden, "imported blueprint must not retain loot, hazards, or canonical-looking ore");
        var allMineModules = java.util.stream.Stream.concat(
                seed.primaryMineSite().initialModules().stream(),
                java.util.stream.Stream.concat(seed.alternateMineSite().initialModules().stream(),
                        seed.alternateMineSite().stagedModules().stream().map(value -> value.module()))).toList();
        helper.assertTrue(allMineModules.stream().flatMap(value -> AuthoredModuleCompiler.compile(value).blocks().stream())
                        .noneMatch(value -> value.state().hasBlockEntity()),
                "sanitized imported MineSite cells must never create pending block entities");
        var portal = seed.primaryMineSite().surfaceBuildings().stream()
                .filter(value -> value.buildingId().equals("portal")).findFirst().orElseThrow()
                .modules().getFirst();
        boolean rawTerrainShell = AuthoredModuleCompiler.compile(portal).blocks().stream()
                .map(value -> value.state())
                .anyMatch(state -> state.is(net.minecraft.world.level.block.Blocks.STONE)
                        || state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)
                        || state.is(net.minecraft.world.level.block.Blocks.TUFF)
                        || state.is(net.minecraft.world.level.block.Blocks.DIRT)
                        || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK));
        helper.assertTrue(!rawTerrainShell,
                "surface mine buildings must not retain copied terrain shells that read as cubes");
        var surfaceById = seed.primaryMineSite().surfaceBuildings().stream().collect(
                java.util.stream.Collectors.toMap(value -> value.buildingId(),
                        value -> AuthoredModuleCompiler.compile(value.modules().getFirst())));
        var processingStates = surfaceById.get("processing").blocks().stream().map(value -> value.state()).toList();
        helper.assertTrue(processingStates.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.BRICKS))
                        && processingStates.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.IRON_BARS))
                        && processingStates.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.DEEPSLATE_TILE_SLAB)),
                "processing hall must compile articulated masonry bays, glazing and a roofline");
        var powerStates = surfaceById.get("power").blocks().stream().map(value -> value.state()).toList();
        helper.assertTrue(powerStates.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.BRICKS))
                        && powerStates.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES)),
                "power house must compile as a masonry building with an integrated stack");
        var loadingModule = seed.primaryMineSite().surfaceBuildings().stream()
                .filter(value -> value.buildingId().equals("loading")).findFirst().orElseThrow()
                .modules().getFirst();
        var loadingSnapshot = surfaceById.get("loading");
        int loadingVolume = (loadingModule.footprint().max().x() - loadingModule.footprint().min().x() + 1)
                * (loadingModule.footprint().max().y() - loadingModule.footprint().min().y() + 1)
                * (loadingModule.footprint().max().z() - loadingModule.footprint().min().z() + 1);
        helper.assertTrue(loadingSnapshot.blocks().size() < loadingVolume / 2,
                "loading facility must remain an open freight canopy rather than a solid shell");
        var generatedSurface = seed.primaryMineSite().surfaceBuildings().stream()
                .filter(value -> value.modules().getFirst().templateId().contains("/mine/"))
                .map(value -> AuthoredModuleCompiler.compile(value.modules().getFirst())).toList();
        helper.assertTrue(generatedSurface.stream().flatMap(value -> value.blocks().stream())
                        .filter(value -> value.state().getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
                        .noneMatch(value -> value.state().getValue(
                                net.minecraft.world.level.block.SlabBlock.TYPE)
                                == net.minecraft.world.level.block.state.properties.SlabType.TOP),
                "authored mine roofs must not stack unsupported top-slab courses");
        for (var snapshotWithChains : generatedSurface) {
            var byPosition = snapshotWithChains.blocks().stream().collect(java.util.stream.Collectors.toMap(
                    value -> new BlockPos(value.position().x(), value.position().y(), value.position().z()),
                    value -> value.state(), (first, second) -> second));
            for (var block : snapshotWithChains.blocks()) {
                BlockPos position = new BlockPos(block.position().x(), block.position().y(), block.position().z());
                if (!block.state().is(net.minecraft.world.level.block.Blocks.CHAIN)
                        || byPosition.getOrDefault(position.above(), net.minecraft.world.level.block.Blocks.AIR
                                .defaultBlockState()).is(net.minecraft.world.level.block.Blocks.CHAIN)) continue;
                helper.assertTrue(!byPosition.getOrDefault(position.above(),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()).isAir(),
                        "mine canopy chain must terminate against a real roof cell at " + position);
            }
        }
        var catalog = new FrontierGenesisCompiler().compile(java.util.List.of(seed));
        boolean kinetic = catalog.chunks().values().stream().flatMap(value -> value.blocks().values().stream())
                .map(value -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(value.getBlock()))
                .anyMatch(id -> id.getNamespace().equals("create") && (id.getPath().equals("shaft")
                        || id.getPath().equals("creative_motor") || id.getPath().equals("encased_fan")));
        helper.assertTrue(kinetic, "mine grammar must add a bounded Create visual network");
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> compiledBlocks = catalog.chunks()
                .values().stream().flatMap(value -> value.blocks().entrySet().stream()).collect(
                        java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,
                                java.util.Map.Entry::getValue));
        for (var underground : seed.primaryMineSite().undergroundModules()) {
            BlockPos position = new BlockPos(underground.origin().x(), underground.origin().y(),
                    underground.origin().z());
            boolean connected = false;
            for (int dx = -3; dx <= 3 && !connected; dx++) for (int dz = -3; dz <= 3 && !connected; dz++) {
                for (int dy = -1; dy <= 2 && !connected; dy++) {
                    BlockPos candidate = position.offset(dx, dy, dz);
                    connected = compiledBlocks.getOrDefault(candidate,
                                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()).isAir()
                            && compiledBlocks.getOrDefault(candidate.above(),
                                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()).isAir();
                }
            }
            helper.assertTrue(connected,
                    "final tunnel carve must connect the authored underground room at " + position);
        }
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
        int cleanupOnlyColumns = 0;
        int surfaceDecorations = 0;
        for (var entry : first.chunks().entrySet()) {
            var slice = entry.getValue();
            helper.assertValueEqual(slice.chunkKey(), entry.getKey(), "slice must retain its owning chunk");
            for (var column : slice.terrain()) helper.assertValueEqual(
                    net.minecraft.world.level.ChunkPos.asLong(column.x() >> 4, column.z() >> 4), entry.getKey(),
                    "terrain write escaped its chunk-local slice");
            for (var column : slice.vegetation()) {
                helper.assertValueEqual(net.minecraft.world.level.ChunkPos.asLong(column.x() >> 4, column.z() >> 4),
                        entry.getKey(), "vegetation cleanup escaped its chunk-local slice");
                VisualPoint surface = new VisualPoint(column.x(), seed.anchor().y(), column.z());
                if (!seed.settlementBounds().contains(surface)) cleanupOnlyColumns++;
            }
            for (var rail : slice.rails()) {
                helper.assertValueEqual(new net.minecraft.world.level.ChunkPos(rail.rail()).toLong(), entry.getKey(),
                        "rail write escaped its chunk-local slice");
                rails++;
            }
            for (var decoration : slice.decorations()) {
                helper.assertValueEqual(new net.minecraft.world.level.ChunkPos(
                                decoration.position()).toLong(), entry.getKey(),
                        "surface decoration escaped its chunk-local slice");
                surfaceDecorations++;
            }
            for (var position : slice.blocks().keySet()) helper.assertValueEqual(
                    new net.minecraft.world.level.ChunkPos(position).toLong(), entry.getKey(),
                    "template write escaped its chunk-local slice");
        }
        helper.assertValueEqual(rails, seed.baselineRailNodes().size(), "every authored rail node must compile once");
        var railColumns = first.chunks().values().stream().flatMap(value -> value.rails().stream()).toList();
        helper.assertTrue(railColumns.stream().allMatch(value -> value.railState().is(
                        net.minecraft.world.level.block.Blocks.RAIL)
                        || value.railState().is(net.minecraft.world.level.block.Blocks.POWERED_RAIL)),
                "baseline freight must never compile activator or detector rails");
        helper.assertTrue(railColumns.stream().allMatch(value -> value.support().is(
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK)
                        || value.support().is(net.minecraft.world.level.block.Blocks.STONE_BRICKS)),
                "baseline freight supports must be powered or non-falling masonry");
        helper.assertTrue(railColumns.stream().filter(value -> value.railState().is(
                        net.minecraft.world.level.block.Blocks.POWERED_RAIL))
                        .allMatch(value -> value.support().is(net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK)),
                "every powered rail must receive an actual redstone power source");
        helper.assertTrue(cleanupOnlyColumns > 0,
                "baseline rail compilation must retain a narrow cleanup-only safety envelope");
        helper.assertTrue(surfaceDecorations > 0,
                "settlement compilation must provide terrain-following fences and lamps");
        var decorations = first.chunks().values().stream()
                .flatMap(value -> value.decorations().stream()).map(value -> value.state()).toList();
        var gates = decorations.stream().filter(value -> value.getBlock()
                instanceof net.minecraft.world.level.block.FenceGateBlock).toList();
        helper.assertValueEqual(gates.size(), 8,
                "one five-block freight gate and three pedestrian gates must compile exactly once");
        helper.assertTrue(gates.stream().allMatch(value -> value.getValue(
                        net.minecraft.world.level.block.FenceGateBlock.OPEN)),
                "fresh-world settlement gates must begin open and remain manually closable");
        helper.assertTrue(decorations.stream().noneMatch(net.minecraft.world.level.block.state.BlockState::hasBlockEntity),
                "terrain-following public and industrial furniture must remain block-entity-free");
        helper.assertTrue(decorations.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.GRAVEL))
                        && decorations.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE))
                        && decorations.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.LANTERN)),
                "primary mine campus must compile a working yard, ore-sort furniture and safety lighting");
        helper.assertTrue(decorations.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.FARMLAND))
                        && decorations.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.FLOWERING_AZALEA)),
                "township public realm must include productive gardens and maintained planting");
        helper.assertTrue(first.chunks().values().stream().flatMap(value -> value.terrain().stream())
                        .noneMatch(value -> value.surface().is(net.minecraft.world.level.block.Blocks.DIRT_PATH)),
                "authored circulation must be paved rather than emitted as dirt paths");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 40)
    public static void poweredRailScheduleSkipsAuthoredCorners(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(54_185_464_310_597_810L, 0,
                new VisualPoint(8000, 72, -4000), FrontierClimate.TEMPERATE, (x, z) -> 72);
        java.util.List<VisualPoint> rail = new java.util.ArrayList<>();
        for (int x = 0; x <= 12; x++) rail.add(new VisualPoint(8200 + x, 73, -4000));
        for (int z = 1; z <= 13; z++) rail.add(new VisualPoint(8212, 73, -4000 + z));
        var turning = withRail(seed, rail);
        var catalog = new FrontierGenesisCompiler().compile(java.util.List.of(turning));
        var compiledRails = catalog.chunks().values().stream().flatMap(slice -> slice.rails().stream()).toList();
        long powered = compiledRails.stream()
                .filter(value -> value.railState().is(net.minecraft.world.level.block.Blocks.POWERED_RAIL)).count();
        helper.assertTrue(powered > 0, "valid straight segments must retain powered rails");
        var corner = compiledRails.stream().filter(value -> value.rail().getX() == 8212
                && value.rail().getZ() == -4000).findFirst().orElseThrow();
        helper.assertTrue(corner.railState().is(net.minecraft.world.level.block.Blocks.RAIL),
                "a scheduled powered segment must fall back to ordinary rail at a corner");
        helper.assertValueEqual(corner.railState().getValue(net.minecraft.world.level.block.RailBlock.SHAPE),
                net.minecraft.world.level.block.state.properties.RailShape.SOUTH_WEST,
                "east-to-south travel must connect the west and south neighbouring rails");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void authoredStateMasksAreBoundedAndReversible(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(7719L, 0, new VisualPoint(900, 72, 900),
                FrontierClimate.TEMPERATE, (x, z) -> 72);
        var module = seed.modules().stream().filter(value -> value.templateId().endsWith("/civic_hall"))
                .findFirst().orElseThrow();
        var intact = AuthoredModuleCompiler.compile(module);
        var damaged = AuthoredModuleCompiler.compileState(module, "DAMAGED");
        var ruined = AuthoredModuleCompiler.compileState(module, "RUINED");
        helper.assertTrue(!damaged.blocks().isEmpty() && damaged.blocks().size() <= 24,
                "damaged overlay must be authored and bounded");
        helper.assertTrue(ruined.blocks().size() >= damaged.blocks().size() && ruined.blocks().size() <= 48,
                "ruined overlay must include the damaged semantic mask within its larger budget");
        helper.assertTrue(intact.blocks().size() > ruined.blocks().size(),
                "state overlay must not duplicate the full authored building");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void residentCommissioningRejectsAnOccupiedHomeCell(GameTestHelper helper) {
        net.minecraft.core.BlockPos intended = helper.absolutePos(new net.minecraft.core.BlockPos(5, 2, 5));
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            helper.getLevel().setBlock(intended.offset(dx, -1, dz),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        }
        helper.getLevel().setBlock(intended, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(intended.above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        var spawn = io.farfrontier.palemirror.visuals.resident.ResidentMaterializer.safeSpawn(
                helper.getLevel(), intended);
        helper.assertTrue(spawn != null, "a nearby safe commissioned-resident cell must be found");
        helper.assertTrue(!net.minecraft.core.BlockPos.containing(spawn).equals(intended),
                "commissioned resident must not be placed inside the authored wall");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void goldMasterMatrixCompilesEveryClimateRotationAndState(GameTestHelper helper) {
        var matrix = io.farfrontier.palemirror.visuals.genesis.VisualShowcasePlan.matrix(
                new VisualPoint(20_000, 80, 20_000));
        for (var entry : matrix) {
            var snapshot = AuthoredModuleCompiler.compileState(entry.module(), entry.state());
            helper.assertTrue(!snapshot.blocks().isEmpty(), "gold master cell is empty: " + entry.key());
        }
        helper.succeed();
    }

    private static AuthoredRegionSeed withRail(AuthoredRegionSeed seed, java.util.List<VisualPoint> rail) {
        return new AuthoredRegionSeed(seed.planId(), seed.archetypeId(), seed.definitionVersion(), seed.contentHash(),
                seed.dimensionId(), seed.climate(), seed.palette(), seed.anchor(), seed.settlementSite(),
                seed.primaryMineSite(), seed.alternateMineSite(), rail, seed.residents());
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void authoredWorldgenFeatureIsInstalledInTargetBiomeWithSable(GameTestHelper helper) {
        helper.runAfterDelay(5, () -> {
            var placedFeatureKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.PLACED_FEATURE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            PaleMirrorVisualsMod.MOD_ID, "authored_region"));
            var biome = helper.getLevel().getBiome(helper.absolutePos(BlockPos.ZERO));
            boolean featureInstalled = biome.value().getGenerationSettings().features().stream()
                    .flatMap(net.minecraft.core.HolderSet::stream)
                    .anyMatch(holder -> holder.is(placedFeatureKey));
            helper.assertTrue(featureInstalled,
                    "authored_region placed feature must be installed in the target overworld biome");
            helper.succeed();
        });
    }
}
