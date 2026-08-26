package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void brokenRouteSlotUpdatesCanonicalCapacityAndItsReadableRemainingFact(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 56);
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        data.activate(0L);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        ReferenceGrayboxSnapshot before = routePresentation(anchor, data.snapshot());
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
        ReferenceGrayboxSnapshot after = routePresentation(anchor, data.snapshot());
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

        String label = "[X] route_damaged target=" + remaining.subjectId() + " total=" + number(expectedCapacity)
                + " available-slots=" + availableSlots + " each=" + number(expectedCapacity / availableSlots);
        List<Display.TextDisplay> labels = helper.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new AABB(anchor).inflate(64, 64, 64), value ->
                value.hasCustomName() && value.getCustomName().getString().equals(label));
        helper.assertTrue(labels.size() == 1,
                "the refreshed [X] label must expose the exact remaining source capacity and remaining breakable slots");

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

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void onlyTheProjectedEntityIdentityCanReportAnExactResidentDeath(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        SourceGrayboxSavedData data = SourceGrayboxSavedData.fresh(42L);
        data.activate(0L);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        ReferenceGrayboxSnapshot baseline = data.snapshot();
        String residentId = baseline.residents().getFirst().id();
        ReferenceGrayboxSnapshot presentation = SourceGrayboxMaterializerGameTests.fixture(anchor, baseline, residentId, 1.0d, "entity-observation");
        materializer.apply(helper.getLevel(), presentation);
        Villager carrier = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(24), entity ->
                entity.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).stream().findFirst().orElseThrow();
        String before = data.snapshot().stateRevision();

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

    private static ReferenceGrayboxSnapshot routePresentation(BlockPos anchor, ReferenceGrayboxSnapshot source) {
        ReferenceGrayboxSnapshot.Route canonical = source.routes().stream()
                .filter(route -> route.capacity() > 0.0d).findFirst().orElseThrow();
        ReferenceGrayboxLayout.Point start = new ReferenceGrayboxLayout.Point(anchor.getX() + 4, anchor.getZ() + 4);
        ReferenceGrayboxLayout.Point end = new ReferenceGrayboxLayout.Point(anchor.getX() + 44, anchor.getZ() + 4);
        ReferenceGrayboxSnapshot.Route route = new ReferenceGrayboxSnapshot.Route(canonical.id(), canonical.settlementA(), canonical.settlementB(),
                start, end, canonical.capacity(), canonical.risk(), canonical.infection(), canonical.quarantined(), canonical.disrupted(),
                canonical.colour());
        List<ReferenceGrayboxLayout.Point> slots = ReferenceGrayboxLayout.routeSlots(start, end);
        ReferenceGrayboxSnapshot.Interaction interaction = new ReferenceGrayboxSnapshot.Interaction("route:" + route.id(), route.id(),
                "route_damaged", route.capacity(), 3, slots, route.colour());
        return new ReferenceGrayboxSnapshot(source.day(), source.profileId(), source.stateRevision(), source.bounds(), source.cells(),
                List.of(), List.of(), List.of(), List.of(route), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(interaction), List.of(), List.of(), List.of());
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
