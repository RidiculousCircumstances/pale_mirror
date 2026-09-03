package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrgan;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrganKind;
import io.farfrontier.palemirror.frontier.v3.model.TerrainSurfacePlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3HiveFoundryAuditTest {
    @Test
    void compiledRaisedOrganScopeProvesEveryProviderOwnedHiveroot() {
        FrontierBootstrap flat = FrontierBootstrapper.create(new WorldId("frontier:hive-foundry-flat"), 91L);
        var west = flat.hive().seedNests().getFirst();
        TerrainSurfacePlan terrain = flat.terrain();
        for (HiveOrgan organ : flat.hive().organs().stream().filter(value -> value.nestId().equals(west.id())).toList()) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                terrain = terrain.withSurveyedSupport(organ.anchor().x() + x, organ.anchor().z() + z, 67);
            }
        }
        HiveOrgan flatGanglion = flat.hive().organs().stream().filter(value -> value.nestId().equals(west.id()) && value.kind() == HiveOrganKind.GANGLION).findFirst().orElseThrow();
        terrain = terrain.withSurveyedSupport(flatGanglion.anchor().x() - 2, flatGanglion.anchor().z() - 2, 63);
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:hive-foundry-raised"), 91L,
                FrontierRulesets.production(), terrain));
        HiveOrgan ganglion = state.bootstrap().hive().organs().stream().filter(value -> value.id().equals(flatGanglion.id())).findFirst().orElseThrow();

        var report = FrontierV3HiveFoundryAudit.auditCompiled(state, ganglion.id());

        assertTrue(report.passed(), report::summary);
        assertTrue(metric(report, "frontier.hive.hiveroot.cells") > 0D,
                "the elevated organ must retain real provider-owned roots, not a hidden flat anchor");
        assertEquals(0D, metric(report, "frontier.hive.hiveroot.invalid"));
    }

    @Test
    void runtimeClassificationRejectsMatchingLookingUnclaimedOrConflictedHiveroot() {
        GrayboxCell root = new GrayboxCell(new BlockPosition(8, 65, 8), new SubjectId("organ:test-ganglion"),
                GrayboxMaterial.HIVE_GANGLION, GrayboxSemanticPart.FOUNDATION);
        FrontierV3GrayboxLedger.Claim claim = new FrontierV3GrayboxLedger.Claim("organ:test-ganglion", "HIVE_GANGLION", "FOUNDATION", false);
        FrontierV3GrayboxLedger.Claim conflicted = new FrontierV3GrayboxLedger.Claim("organ:test-ganglion", "HIVE_GANGLION", "FOUNDATION", true);

        assertEquals(FrontierV3HiveFoundryAudit.RuntimeCellStatus.PENDING,
                FrontierV3HiveFoundryAudit.classify(root, null, FrontierV3HiveFoundryAudit.ObservedCell.AIR));
        assertEquals(FrontierV3HiveFoundryAudit.RuntimeCellStatus.MISMATCH,
                FrontierV3HiveFoundryAudit.classify(root, null, FrontierV3HiveFoundryAudit.ObservedCell.EXPECTED),
                "a matching-looking player/world block must not become organ provenance");
        assertEquals(FrontierV3HiveFoundryAudit.RuntimeCellStatus.MISMATCH,
                FrontierV3HiveFoundryAudit.classify(root, conflicted, FrontierV3HiveFoundryAudit.ObservedCell.EXPECTED));
        assertEquals(FrontierV3HiveFoundryAudit.RuntimeCellStatus.CURRENT,
                FrontierV3HiveFoundryAudit.classify(root, claim, FrontierV3HiveFoundryAudit.ObservedCell.EXPECTED));
    }

    @Test
    void wakingCocoonWaitsForItsFirstProjectionButNeverAdoptsAnAlteredClaim() {
        SubjectId bioform = new SubjectId("bioform:test-waking");
        FrontierV3GrayboxLedger.Claim exact = new FrontierV3GrayboxLedger.Claim(bioform.value(), "HIVE_COCOON", "COCOON", false);
        FrontierV3GrayboxLedger.Claim changed = new FrontierV3GrayboxLedger.Claim(bioform.value(), "HIVE_COCOON", "COCOON", true);

        assertEquals(FrontierV3HiveMobilizationExecutor.CocoonProjection.PENDING,
                FrontierV3HiveMobilizationExecutor.cocoonProjection(null, bioform, false),
                "absence before a bounded first projection is not a physical loss");
        assertEquals(FrontierV3HiveMobilizationExecutor.CocoonProjection.PRESENT,
                FrontierV3HiveMobilizationExecutor.cocoonProjection(exact, bioform, true));
        assertEquals(FrontierV3HiveMobilizationExecutor.CocoonProjection.CONFLICT,
                FrontierV3HiveMobilizationExecutor.cocoonProjection(exact, bioform, false),
                "an exact prior claim makes a later changed block a real conflict");
        assertEquals(FrontierV3HiveMobilizationExecutor.CocoonProjection.CONFLICT,
                FrontierV3HiveMobilizationExecutor.cocoonProjection(changed, bioform, true));
    }

    private static double metric(io.farfrontier.palemirror.api.FoundryAuditReport report, String id) {
        return report.metrics().stream().filter(metric -> metric.id().equals(id)).findFirst().orElseThrow().value();
    }
}
