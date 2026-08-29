package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical-to-canonical proof for a declared interaction cube and its immediate republish. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxPhysicalObservationGameTests {
    private SourceGrayboxPhysicalObservationGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void unchangedSourceReadsReuseOneFrameButAcceptedTimeAdvancementRefreshesIt(GameTestHelper helper) {
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        ReferenceGrayboxSnapshot first = data.snapshot();
        helper.assertTrue(first == data.snapshot(),
                "unchanged source reads must reuse their immutable presentation frame instead of rebuilding the whole world each tick");

        data.activate(0L);
        data.advance(1);
        ReferenceGrayboxSnapshot advanced = data.snapshot();
        helper.assertTrue(advanced != first && advanced.day() == first.day() + 1,
                "a real canonical day must invalidate the cached frame and publish the new source day exactly once");
        helper.assertTrue(advanced == data.snapshot(),
                "the refreshed immutable frame must remain reusable until another accepted source mutation");
        helper.succeed();
    }

    // Each case publishes through the level-wide durable presentation ledger.
    // Keep those publications in separate GameTest batches: the runner starts
    // one batch's independent fixtures concurrently, and concurrent complete
    // snapshots are deliberately mutually exclusive in production.
    @GameTest(batch = "pm-source-graybox-observation-route", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void brokenRouteSlotUpdatesCanonicalCapacityAndItsReadableRemainingFact(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 56);
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        data.activate(0L);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        ReferenceGrayboxSnapshot before = routePresentation(anchor, data.snapshot(), "route-break");
        materializer.apply(helper.getLevel(), before);

        ReferenceGrayboxSnapshot.Interaction interaction = before.interactions().getFirst();
        ReferenceGrayboxLayout.Point slot = interaction.slots().getFirst();
        BlockPos position = new BlockPos(slot.x(), ReferenceGrayboxLayout.GROUND_Y + interaction.yOffset(), slot.z());
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(helper.getLevel(), position);
        double initialCapacity = interaction.totalWeight();
        double exactSlotWeight = claim.interactionWeight();
        String beforeRevision = data.snapshot().stateRevision();

        helper.assertTrue(SourceGrayboxBlockObservation.observe(data, materializer, helper.getLevel(), position, "gametest:route-slot"),
                "a declared elevated slot must be handled as a typed source observation");
        helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        ReferenceGrayboxSnapshot after = routePresentation(anchor, data.snapshot(), "route-break");
        materializer.apply(helper.getLevel(), after);

        ReferenceGrayboxSnapshot.Interaction remaining = after.interactions().getFirst();
        int availableSlots = remaining.slots().size() - 1;
        double expectedCapacity = initialCapacity - exactSlotWeight;
        helper.assertTrue(!beforeRevision.equals(after.stateRevision()),
                "an accepted physical slot must immediately advance the canonical source revision");
        helper.assertValueEqual(remaining.totalWeight(), expectedCapacity,
                "the source route capacity must lose precisely the broken physical slot's represented weight");
        helper.assertTrue(materializer.claimAt(helper.getLevel(), position).consumed(),
                "an accepted slot remains consumed after the source frame is republished");
        helper.assertValueEqual(helper.getLevel().getBlockState(position).getBlock(), Blocks.AIR,
                "Minecraft's completed break keeps the consumed source cube visibly absent");

        String label = SourceGrayboxPlayerBriefing.interactionLabel(after, SourceGrayboxPresentationLedger.get(helper.getLevel()), remaining);
        List<Display.TextDisplay> labels = helper.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new AABB(anchor).inflate(64, 64, 64), value ->
                value.hasCustomName() && value.getCustomName().getString().equals(label));
        helper.assertTrue(labels.size() == 1,
                "the refreshed action board must remain truthful after its source capacity changes");

        String afterRevision = after.stateRevision();
        helper.assertTrue(SourceGrayboxBlockObservation.observe(data, materializer, helper.getLevel(), position, "gametest:route-slot"),
                "a replayed physical claim is handled as a visible conflict rather than falling through to arbitrary block logic");
        materializer.apply(helper.getLevel(), after);
        helper.assertValueEqual(data.snapshot().stateRevision(), afterRevision,
                "a replayed exact event must not damage the canonical route a second time");
        SourceGrayboxPresentationLedger.Claim replayed = materializer.claimAt(helper.getLevel(), position);
        helper.assertTrue(replayed.consumed() && replayed.conflicted(),
                "a replay keeps the accepted slot absent while retaining the rejected second change as a durable conflict");
        String conflictLabel = "[!] conflict " + replayed.kind() + " target=" + replayed.subjectId()
                + " outcome=replayed-event source=retained";
        List<Display.TextDisplay> conflicts = helper.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new AABB(anchor).inflate(64, 64, 64), value ->
                value.hasCustomName() && value.getCustomName().getString().equals(conflictLabel));
        helper.assertTrue(conflicts.size() == 1,
                "a rejected replay must remain visible to the tester instead of being silently swallowed by the presentation ledger");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-observation-explosion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void externalExplosionPersistsThenReconcilesTheActualDestroyedRouteSlot(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 56);
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        source.activate(0L);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        ReferenceGrayboxSnapshot presentation = routePresentation(anchor, source.snapshot(), "external-explosion");
        materializer.apply(helper.getLevel(), presentation);
        ReferenceGrayboxSnapshot.Interaction interaction = presentation.interactions().getFirst();
        ReferenceGrayboxLayout.Point slot = interaction.slots().getFirst();
        BlockPos position = new BlockPos(slot.x(), ReferenceGrayboxLayout.GROUND_Y + interaction.yOffset(), slot.z());
        double capacity = interaction.totalWeight();

        SourceGrayboxSavedData survived = SourceGrayboxSavedData.fresh(42L);
        survived.activate(0L);
        helper.assertTrue(survived.pendingExplosions().capture(helper.getLevel().getGameTime(), List.of(position)),
                "the negative case must retain an explosion candidate before its physical postcondition is inspected");
        String beforeSurvived = survived.snapshot().stateRevision();
        helper.assertTrue(!SourceGrayboxExplosionReconciliation.reconcile(survived, materializer, helper.getLevel()),
                "a listed explosion target which Minecraft did not actually destroy must not mutate canonical state");
        helper.assertValueEqual(survived.snapshot().stateRevision(), beforeSurvived,
                "the external-effect boundary must use the real post-impact block state, not merely the pre-impact target list");

        helper.assertTrue(source.pendingExplosions().capture(helper.getLevel().getGameTime(), List.of(position)),
                "a real external explosion must persist its exact PM target before block removal");
        CompoundTag saved = source.save(new CompoundTag(), null);
        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(saved, null);
        helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(), 3);

        helper.assertTrue(SourceGrayboxExplosionReconciliation.reconcile(restored, materializer, helper.getLevel()),
                "the persisted explosion target must become a typed source observation only after Minecraft actually removed the block");
        helper.assertValueEqual(restored.snapshot().routes().stream().filter(route -> route.id().equals(presentation.routes().getFirst().id()))
                        .findFirst().orElseThrow().capacity(), capacity - materializer.claimAt(helper.getLevel(), position).interactionWeight(),
                "a restart between detonation and reconciliation must preserve the exact route capacity loss");
        helper.assertTrue(!SourceGrayboxExplosionReconciliation.reconcile(restored, materializer, helper.getLevel()),
                "a drained external explosion observation must not replay a second canonical loss");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-observation-entity", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void onlyTheProjectedEntityIdentityCanReportAnExactResidentDeath(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        data.activate(0L);
        ReferenceGrayboxSnapshot baseline = data.snapshot();
        String residentId = baseline.residents().getLast().id();
        String before = data.snapshot().stateRevision();
        Villager carrier = projectedResident(helper, anchor, residentId, before);

        Villager forged = new Villager(net.minecraft.world.entity.EntityType.VILLAGER, helper.getLevel());
        forged.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, residentId);
        forged.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, "RESIDENT");
        forged.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION, before);
        helper.assertTrue(!SourceGrayboxEntityObservation.observe(data, forged, "gametest:forged-resident"),
                "matching provenance text without the deterministic source UUID must not mutate canonical state");
        helper.assertValueEqual(data.snapshot().stateRevision(), before,
                "a forged carrier must leave the current canonical revision untouched");

        helper.assertTrue(SourceGrayboxEntityObservation.observe(data, carrier, "gametest:resident-death"),
                "the deterministic materialized Villager must report exactly its source resident death");
        helper.assertTrue(data.snapshot().residents().stream().noneMatch(resident -> resident.id().equals(residentId)),
                "the exact canonical resident must disappear after the accepted physical death");
        String after = data.snapshot().stateRevision();
        helper.assertTrue(!before.equals(after), "an accepted entity observation must advance the canonical revision");
        helper.succeed();
    }

    private static Villager projectedResident(GameTestHelper helper, BlockPos anchor, String residentId, String revision) {
        Villager carrier = new Villager(EntityType.VILLAGER, helper.getLevel());
        carrier.setUUID(SourceGrayboxMaterializer.uuid("resident", residentId));
        carrier.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, residentId);
        carrier.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, "RESIDENT");
        carrier.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION, revision);
        carrier.moveTo(anchor.getX() + 0.5d, anchor.getY(), anchor.getZ() + 0.5d);
        helper.getLevel().addFreshEntity(carrier);
        return carrier;
    }

    private static ReferenceGrayboxSnapshot routePresentation(BlockPos anchor, ReferenceGrayboxSnapshot source, String fixtureId) {
        ReferenceGrayboxSnapshot.Route canonical = source.routes().stream()
                .filter(route -> route.capacity() > 0.0d).findFirst().orElseThrow();
        ReferenceGrayboxLayout.Point start = new ReferenceGrayboxLayout.Point(anchor.getX() + 4, anchor.getZ() + 4);
        ReferenceGrayboxLayout.Point end = new ReferenceGrayboxLayout.Point(anchor.getX() + 44, anchor.getZ() + 4);
        ReferenceGrayboxSnapshot.Route route = new ReferenceGrayboxSnapshot.Route(canonical.id(), canonical.settlementA(), canonical.settlementB(),
                start, end, canonical.capacity(), canonical.risk(), canonical.infection(), canonical.quarantined(), canonical.disrupted(),
                canonical.colour());
        List<ReferenceGrayboxLayout.Point> slots = ReferenceGrayboxLayout.routeSlots(start, end);
        ReferenceGrayboxSnapshot.Interaction interaction = new ReferenceGrayboxSnapshot.Interaction("route:" + route.id() + ":" + fixtureId, route.id(),
                "route_damaged", route.capacity(), 3, slots, route.colour());
        return new ReferenceGrayboxSnapshot(source.day(), source.profileId(), source.stateRevision(), source.bounds(), source.cells(),
                List.of(), List.of(), List.of(), List.of(route), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(interaction), List.of(), List.of(), List.of());
    }

}
