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
    public static void authoredDepotSeparatesFunctionalCoreFromRailhead(GameTestHelper helper) {
        for (FrontierClimate climate : FrontierClimate.values()) {
            for (int ordinal = 0; ordinal < 4; ordinal++) {
                AuthoredRegionSeed seed = new FrontierRegionPlanner().plan(918273L, ordinal,
                        new VisualPoint(8000 + ordinal * 1000, 72, -4000), climate);
                var settlement = seed.settlementSite();
                BlockPos core = block(settlement.depotFunctionalCore());
                BlockPos railhead = block(settlement.receivingRailhead());
                helper.assertTrue(!core.equals(railhead),
                        "depot functional core must not alias the railway hand-off");
                helper.assertValueEqual(railhead, block(seed.baselineRailNodes().getFirst()),
                        "canonical railway must begin at the exact railhead");
                helper.assertValueEqual(core, block(settlement.building("receiving_depot")
                                .modules().getFirst().origin()),
                        "functional core must retain the authored depot shell origin");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void authoredMineBlueprintPreservesCuratedMachineryAndRejectsUnsafeControllers(GameTestHelper helper) {
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
                        || id.getPath().equals("creative_motor") || id.getPath().equals("redstone_link")
                        || id.getPath().equals("display_link") || id.getPath().equals("linked_controller"));
        helper.assertTrue(!forbidden, "imported blueprint must reject explosives, free power and unscoped links");
        var allMineModules = java.util.stream.Stream.concat(
                seed.primaryMineSite().initialModules().stream(),
                java.util.stream.Stream.concat(seed.alternateMineSite().initialModules().stream(),
                        seed.alternateMineSite().stagedModules().stream().map(value -> value.module()))).toList();
        var machineCells = allMineModules.stream()
                .flatMap(value -> AuthoredModuleCompiler.compile(value).blocks().stream())
                .filter(value -> value.state().hasBlockEntity()).toList();
        helper.assertTrue(!machineCells.isEmpty(), "curated MineSite must preserve functional block entities");
        helper.assertTrue(machineCells.stream().anyMatch(value -> value.blockEntityData().isPresent()),
                "curated machine state must retain explicit one-shot payloads where the NBT defines them");
        helper.assertTrue(machineCells.stream().filter(value -> value.blockEntityData().isPresent())
                        .allMatch(value -> value.blockEntityData().orElseThrow().contains("id")),
                "every retained machine payload must remain typed");
        helper.assertTrue(machineCells.stream().filter(value -> value.blockEntityData().isPresent())
                        .noneMatch(value -> containsUnsafeRuntimeReference(value.blockEntityData().orElseThrow())),
                "retained machinery must not import source-world links or active contraptions");
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
        helper.assertTrue(rawTerrainShell,
                "curated architectural stone must not be stripped merely because it resembles terrain");
        var surfaceById = seed.primaryMineSite().surfaceBuildings().stream().collect(
                java.util.stream.Collectors.toMap(value -> value.buildingId(),
                        value -> AuthoredModuleCompiler.compile(value.modules().getFirst())));
        helper.assertValueEqual(6, surfaceById.size(), "complete Mine17 must retain six semantic surface buildings");
        surfaceById.forEach((id, compiled) -> {
            long physical = compiled.blocks().stream().filter(value -> !value.state().isAir()).count();
            int volume = (compiled.footprint().max().x() - compiled.footprint().min().x() + 1)
                    * (compiled.footprint().max().y() - compiled.footprint().min().y() + 1)
                    * (compiled.footprint().max().z() - compiled.footprint().min().z() + 1);
            long palette = compiled.blocks().stream().filter(value -> !value.state().isAir())
                    .map(value -> value.state().getBlock()).distinct().count();
            helper.assertTrue(physical > volume / 20,
                    id + " must retain a substantial curated architectural shell");
            helper.assertTrue(physical < volume * 3L / 4L,
                    id + " must remain articulated rather than compiling as a solid cuboid");
            helper.assertTrue(palette >= 4, id + " must retain an architectural material palette");
        });
        var loadingModule = seed.primaryMineSite().surfaceBuildings().stream()
                .filter(value -> value.buildingId().equals("loading")).findFirst().orElseThrow()
                .modules().getFirst();
        var loadingSnapshot = surfaceById.get("loading");
        int loadingVolume = (loadingModule.footprint().max().x() - loadingModule.footprint().min().x() + 1)
                * (loadingModule.footprint().max().y() - loadingModule.footprint().min().y() + 1)
                * (loadingModule.footprint().max().z() - loadingModule.footprint().min().z() + 1);
        helper.assertTrue(loadingSnapshot.blocks().stream().filter(value -> !value.state().isAir()).count()
                        < loadingVolume * 3L / 4L,
                "loading facility must remain an open freight building rather than a solid shell");
        var generatedSurface = seed.primaryMineSite().surfaceBuildings().stream()
                .filter(value -> value.modules().getFirst().templateId().contains("/mine/"))
                .map(value -> AuthoredModuleCompiler.compile(value.modules().getFirst())).toList();
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
                .map(value -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(value.state().getBlock()))
                .anyMatch(id -> id.getNamespace().equals("create") && (id.getPath().equals("shaft")
                        || id.getPath().equals("andesite_casing") || id.getPath().equals("encased_fan")));
        helper.assertTrue(kinetic, "mine grammar must add a bounded Create visual network");
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> compiledBlocks = catalog.chunks()
                .values().stream().flatMap(value -> value.blocks().entrySet().stream()).collect(
                        java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,
                                value -> value.getValue().state()));
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

    private static boolean containsUnsafeRuntimeReference(net.minecraft.nbt.Tag tag) {
        if (tag instanceof net.minecraft.nbt.CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                String normalized = key.toLowerCase(java.util.Locale.ROOT);
                if (java.util.Set.of("controller", "target", "lastknownpos", "linkedpos", "globalpos",
                        "contraption", "movedcontraption", "running", "clientanglediff").contains(normalized)) {
                    return true;
                }
                net.minecraft.nbt.Tag nested = compound.get(key);
                if (nested != null && containsUnsafeRuntimeReference(nested)) return true;
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (net.minecraft.nbt.Tag nested : list) if (containsUnsafeRuntimeReference(nested)) return true;
        }
        return false;
    }

    private static BlockPos block(VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
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
        helper.assertValueEqual(gates.size(), 3,
                "the freight gatehouse must stay five blocks clear while three pedestrian gates compile once");
        helper.assertTrue(gates.stream().allMatch(value -> value.getValue(
                        net.minecraft.world.level.block.FenceGateBlock.OPEN)),
                "fresh-world settlement gates must begin open and remain manually closable");
        helper.assertTrue(decorations.stream().noneMatch(net.minecraft.world.level.block.state.BlockState::hasBlockEntity),
                "terrain-following public and industrial furniture must remain block-entity-free");
        var compiledBlocks = first.chunks().values().stream().flatMap(value -> value.blocks().values().stream())
                .map(value -> value.state()).toList();
        helper.assertTrue(compiledBlocks.stream().anyMatch(value -> value.is(
                        net.minecraft.world.level.block.Blocks.LANTERN)),
                "curated MineSite modules and explicit access must retain readable safety lighting");
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

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void foundryAuditsTheProductionCompiledPlan(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(9_182_733L, 0, new VisualPoint(8_000, 72, -4_000),
                FrontierClimate.TEMPERATE);
        var catalog = new FrontierGenesisCompiler().compile(java.util.List.of(seed));
        var report = new io.farfrontier.palemirror.visuals.foundry.FoundryAuditEngine()
                .auditCompiled(catalog, seed.planId());
        helper.assertTrue(report.findings().stream().noneMatch(value -> value.ruleId().equals(
                        "compiled.module.empty")),
                "Foundry must see compiled cells for every authored module");
        helper.assertTrue(report.findings().stream().noneMatch(value -> value.ruleId().equals(
                        "rail.node.missing")),
                "Foundry must see every canonical baseline-rail node");
        helper.assertTrue(report.metrics().stream().anyMatch(value -> value.id().equals("structure.components")),
                "Foundry must expose structural component metrics");
        helper.assertTrue(report.mapSamples().size() > 1_000,
                "Foundry must expose the complete authored settlement surface map");
        helper.assertTrue(report.passed(), "production compiled plan must pass the Foundry gate: "
                + report.findings().stream().filter(value -> value.severity().failsGate()).limit(20).toList());
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void foundryFailsClosedWhenCompiledCellsDisappear(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(9_182_733L, 0, new VisualPoint(8_000, 72, -4_000),
                FrontierClimate.TEMPERATE);
        var original = new FrontierGenesisCompiler().compile(java.util.List.of(seed));
        var broken = new io.farfrontier.palemirror.visuals.genesis.CompiledGenesisCatalog(original.version(),
                original.hash(), original.manifests(), java.util.Map.of());
        var report = new io.farfrontier.palemirror.visuals.foundry.FoundryAuditEngine()
                .auditCompiled(broken, seed.planId());
        helper.assertTrue(!report.passed(), "missing compiled address space must fail the Foundry gate");
        helper.assertTrue(report.findings().stream().anyMatch(value -> value.ruleId().equals("rail.node.missing")
                        && value.severity() == io.farfrontier.palemirror.api.FoundrySeverity.BLOCKER),
                "missing railway must be a locatable Foundry blocker");
        helper.assertTrue(report.findings().stream().anyMatch(value -> value.ruleId().equals("compiled.module.empty")),
                "missing module cells must be reported independently from railway failure");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 200)
    public static void foundryObservesASettledDefectAndItsRecovery(GameTestHelper helper) {
        BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
        VisualPoint anchor = new VisualPoint(testOrigin.getX(), testOrigin.getY() + 1, testOrigin.getZ());
        var seed = new FrontierRegionPlanner().plan(7_731_991L, 0, anchor, FrontierClimate.TEMPERATE,
                (x, z) -> anchor.y());
        BlockPos candidatePosition = helper.absolutePos(new BlockPos(2, 2, 2));
        var candidateState = net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState();
        long chunkKey = new net.minecraft.world.level.ChunkPos(candidatePosition).toLong();
        var slice = new io.farfrontier.palemirror.visuals.genesis.CompiledChunkSlice(chunkKey, "foundry-test",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.Map.of(candidatePosition,
                        new io.farfrontier.palemirror.visuals.genesis.CompiledChunkSlice.CompiledBlock(candidateState)));
        var catalog = new io.farfrontier.palemirror.visuals.genesis.CompiledGenesisCatalog(1, "foundry-test-hash",
                java.util.List.of(seed), java.util.Map.of(chunkKey, slice));
        helper.getLevel().setBlock(candidatePosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        var engine = new io.farfrontier.palemirror.visuals.foundry.FoundryAuditEngine();
        VisualPoint candidatePoint = new VisualPoint(candidatePosition.getX(), candidatePosition.getY(),
                candidatePosition.getZ());
        var inspection = engine.inspect(catalog, seed.planId(), helper.getLevel(), candidatePoint);
        helper.assertTrue(!inspection.expectedState().equals("unplanned") && inspection.actualState().startsWith(
                        "minecraft:air"),
                "test defect must address one loaded Foundry-owned non-air cell: " + inspection);
        var broken = engine.audit(catalog, seed.planId(), helper.getLevel(),
                io.farfrontier.palemirror.api.FoundryAuditPhase.SETTLED);
        helper.assertTrue(broken.findings().stream().anyMatch(value -> value.ruleId().equals("world.cell.mismatch")
                        && value.position().equals(candidatePoint)),
                "settled audit must locate an authored cell removed from a loaded chunk: " + broken.summary()
                        + " metrics=" + broken.metrics().stream().filter(value -> value.id().startsWith("world."))
                        .toList() + " candidate=" + candidatePoint);
        helper.getLevel().setBlock(candidatePosition, candidateState, 2);
        var recovered = engine.audit(catalog, seed.planId(), helper.getLevel(),
                io.farfrontier.palemirror.api.FoundryAuditPhase.RELOADED);
        helper.assertTrue(recovered.findings().stream().noneMatch(value -> value.ruleId().equals("world.cell.mismatch")
                        && value.position().equals(candidatePoint)),
                "reloaded audit must clear the exact defect after its physical cell is restored");
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
