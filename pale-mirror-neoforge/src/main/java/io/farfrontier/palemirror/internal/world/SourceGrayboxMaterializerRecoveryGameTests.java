package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Recovery proofs for entity identity during source-graybox reload admission. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxMaterializerRecoveryGameTests {
    private SourceGrayboxMaterializerRecoveryGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void presentationClaimSurvivesRestartEncodingAndKeepsItsConflict(GameTestHelper helper) {
        SourceGrayboxPresentationLedger ledger = SourceGrayboxPresentationLedger.get(helper.getLevel());
        SourceGrayboxPresentationLedger.Claim claim = new SourceGrayboxPresentationLedger.Claim("fixture:claim", "fixture", "FACILITY",
                "a".repeat(64), 1, 64, 1, 2, 2, 1, false, "", 0.0d, false);
        ledger.put(claim);
        ledger.conflict(claim.id());
        ledger.claimEntity("LABEL:facility:fixture:workshop");

        SourceGrayboxPresentationLedger restored = SourceGrayboxPresentationLedger.load(
                ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);

        helper.assertTrue(restored.claim(claim.id()) != null && restored.claim(claim.id()).conflicted(),
                "a source-graybox structural conflict must survive persistence instead of being silently repaired after restart");
        helper.assertTrue(restored.entityClaimed("LABEL:facility:fixture:workshop"),
                "an entity reservation must survive persistence so a restart cannot recreate an entity while its serialized predecessor loads");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void admittedRestoredLabelWinsBeforeUuidIndexPublication(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(
                anchor, baseline, baseline.residents().getFirst().id(), 1.0d, "restored-label");
        String labelId = "facility:restored-label:workshop";
        ArmorStand restoring = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
        restoring.setUUID(SourceGrayboxMaterializer.uuid("label", labelId));
        restoring.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, labelId);
        restoring.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, "LABEL");
        restoring.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION, "0".repeat(64));
        Map<String, Entity> admitted = new LinkedHashMap<>();
        SourceGrayboxMaterializer.rememberAdmittedEntity(admitted, restoring);

        new SourceGrayboxMaterializer().apply(helper.getLevel(), snapshot, admitted);

        BlockPos facility = anchor.offset(2, 0, 2);
        helper.assertTrue(labels(helper, facility).isEmpty(),
                "a restored label admitted before ServerLevel UUID publication must be reused, never recreated");
        helper.assertTrue(restoring.hasCustomName() && restoring.getCustomName().getString().startsWith("[F] workshop"),
                "the admitted restored object must receive the current deterministic label text");
        helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel()).entityClaimed("LABEL:" + labelId),
                "reusing a restored label must retain its durable duplicate-prevention claim");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void unavailableClaimFailsClosedUntilItsCanonicalRecordRetires(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot snapshot = SourceGrayboxMaterializerGameTests.fixture(
                anchor, baseline, baseline.residents().getFirst().id(), 1.0d, "reserved-label");
        String labelId = "facility:reserved-label:workshop";
        SourceGrayboxPresentationLedger ledger = SourceGrayboxPresentationLedger.get(helper.getLevel());
        ledger.claimEntity("LABEL:" + labelId);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        BlockPos facility = anchor.offset(2, 0, 2);

        materializer.apply(helper.getLevel(), snapshot);
        helper.assertTrue(labels(helper, facility).isEmpty(),
                "a claimed but unavailable entity must remain a presentation gap instead of risking a duplicate UUID");

        materializer.apply(helper.getLevel(), SourceGrayboxMaterializerGameTests.withoutPresentationRecords(baseline));
        helper.assertTrue(!ledger.entityClaimed("LABEL:" + labelId),
                "only source retirement may release a missing entity reservation");
        materializer.apply(helper.getLevel(), snapshot);
        helper.assertTrue(labels(helper, facility).size() == 1,
                "a later canonical record may materialize once no serialized predecessor remains claimed");
        helper.succeed();
    }

    private static java.util.List<ArmorStand> labels(GameTestHelper helper, BlockPos facility) {
        return helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(facility).inflate(3, 32, 3), value ->
                value.hasCustomName() && value.getCustomName().getString().startsWith("[F] workshop"));
    }
}
