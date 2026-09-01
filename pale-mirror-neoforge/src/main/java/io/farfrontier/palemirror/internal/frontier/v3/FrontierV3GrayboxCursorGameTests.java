package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Loaded-demand selection must not wait for unrelated, unloaded world geometry. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3GrayboxCursorGameTests {
    private FrontierV3GrayboxCursorGameTests() { }

    @GameTest(batch = "pm-frontier-v3-graybox", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void naturallyLoadedChunkIsSelectedAheadOfEarlierUnloadedGeometry(GameTestHelper helper) {
        GrayboxCell west = cell(-256, 64, 0, "organ:west");
        GrayboxCell eastA = cell(256, 64, 0, "organ:east-a");
        GrayboxCell eastB = cell(257, 64, 0, "organ:east-b");
        FrontierV3GrayboxExecutor.Cursor cursor = FrontierV3GrayboxExecutor.Cursor.fromCells(List.of(west, eastA, eastB), null);
        helper.assertValueEqual(cursor.nextNaturallyLoaded(cell -> cell.position().x() >= 0).orElseThrow().ownerId().value(), "organ:east-a",
                "one player-loaded east chunk is not delayed behind a remote west chunk");
        helper.assertValueEqual(cursor.nextNaturallyLoaded(cell -> cell.position().x() >= 0).orElseThrow().ownerId().value(), "organ:east-b",
                "the local loaded chunk keeps bounded in-chunk progress");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void publicAccessSurfaceRequiresSupportAndRetainsHallProvenance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(16, 8, 0));
        GrayboxCell surface = new GrayboxCell(new BlockPosition(position.getX(), position.getY(), position.getZ()),
                new SubjectId("structure:access-hall"), GrayboxMaterial.ROUTE, GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, surface), FrontierV3GrayboxExecutor.ProjectionResult.DEFERRED,
                "an unsupported public access sill must defer rather than float or adopt terrain");
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, surface), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "a supported public access sill must materialize through ordinary owned projection");
        helper.assertTrue(level.getBlockState(position).is(Blocks.GRAY_CARPET) && ledger.claim(position) != null
                        && ledger.claim(position).owner().equals("structure:access-hall")
                        && ledger.claim(position).semanticPart().equals(GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE.name()),
                "public access must retain exact Hall provenance rather than become anonymous route decoration");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void worksiteStagingRequiresRealSupportAndKeepsProjectProvenance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(20, 8, 0));
        GrayboxCell staging = new GrayboxCell(new BlockPosition(position.getX(), position.getY(), position.getZ()),
                new SubjectId("construction:staging-proof"), GrayboxMaterial.WORKSITE, GrayboxSemanticPart.WORKSITE_STAGING);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, staging), FrontierV3GrayboxExecutor.ProjectionResult.DEFERRED,
                "a temporary work floor may not float merely to make a scene materialize");
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, staging), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "a naturally supported worksite floor must become a real visible block");
        helper.assertTrue(level.getBlockState(position).is(Blocks.LIGHT_BLUE_CONCRETE) && ledger.claim(position) != null
                        && ledger.claim(position).owner().equals("construction:staging-proof")
                        && ledger.claim(position).semanticPart().equals(GrayboxSemanticPart.WORKSITE_STAGING.name()),
                "the floor must retain its temporary project identity rather than masquerade as built route infrastructure");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void worksiteRetirementIndexSurvivesLedgerReloadWithoutScanningStructuralClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        for (int offset = 0; offset < 512; offset++) {
            BlockPos position = helper.absolutePos(new BlockPos(offset, 8, 4));
            ledger.applied(position, "structure:static-" + offset, GrayboxMaterial.HALL.name(), GrayboxSemanticPart.FOUNDATION.name());
        }
        BlockPos first = helper.absolutePos(new BlockPos(0, 8, 8));
        BlockPos second = helper.absolutePos(new BlockPos(1, 8, 8));
        ledger.applied(first, "construction:index-a", GrayboxMaterial.WORKSITE.name(), GrayboxSemanticPart.WORKSITE_STAGING.name());
        ledger.applied(second, "construction:index-b", GrayboxMaterial.WORKSITE.name(), GrayboxSemanticPart.WORKSITE_STAGING.name());
        FrontierV3GrayboxLedger restored = FrontierV3GrayboxLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(restored.claimsWithSemanticPart(GrayboxSemanticPart.WORKSITE_STAGING.name()).stream()
                        .map(value -> value.position().asLong()).toList(), List.of(first.asLong(), second.asLong()),
                "only exact retained temporary claims are returned in stable order after restart; unrelated structural provenance is not retirement work");
        restored.retire(first, "construction:index-a", GrayboxMaterial.WORKSITE.name(), GrayboxSemanticPart.WORKSITE_STAGING.name());
        helper.assertValueEqual(restored.claimsWithSemanticPart(GrayboxSemanticPart.WORKSITE_STAGING.name()).size(), 1,
                "retiring one owned temporary floor immediately removes it from the bounded index");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void declaredSteppedRouteEnvelopeUsesItsActualTerrainDatums(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(24, 8, 0));
        BlockPos upper = lower.east();
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        GrayboxCell lowerSurface = routeSurface(lower), upperFoundation = routeFoundation(upper), upperSurface = routeSurface(upper.above());

        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, upperSurface), FrontierV3GrayboxExecutor.ProjectionResult.DEFERRED,
                "a declared raised datum never floats merely because the route compiler contains a grade");
        level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(upper.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, lowerSurface), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "the lower declared terrain datum materializes its route surface");
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, upperFoundation), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "the provider-owned graybox footing materializes before the raised deck");
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, upperSurface), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "the adjacent raised declared terrain datum materializes the same owned route");
        helper.assertTrue(level.getBlockState(lower).is(Blocks.GRAY_CARPET) && level.getBlockState(upper).is(Blocks.GRAY_CONCRETE)
                        && level.getBlockState(upper.above()).is(Blocks.GRAY_CARPET) && ledger.claim(lower) != null
                        && ledger.claim(upper) != null && ledger.claim(upper.above()) != null,
                "one physical stepped corridor retains exact visible deck and footing provenance at both terrain levels");
        helper.succeed();
    }

    private static GrayboxCell cell(int x, int y, int z, String owner) {
        return new GrayboxCell(new BlockPosition(x, y, z), new SubjectId(owner), GrayboxMaterial.HIVE_STORE, GrayboxSemanticPart.HIVE_TISSUE);
    }

    private static GrayboxCell routeSurface(BlockPos position) {
        return new GrayboxCell(new BlockPosition(position.getX(), position.getY(), position.getZ()),
                new SubjectId("route:frontier-network"), GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE);
    }

    private static GrayboxCell routeFoundation(BlockPos position) {
        return new GrayboxCell(new BlockPosition(position.getX(), position.getY(), position.getZ()),
                new SubjectId("route:frontier-network"), GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.ROUTE_FOUNDATION);
    }
}
