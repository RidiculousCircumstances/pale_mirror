package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/** Bounded projection of local source facts into readable in-world boards. */
final class SourceGrayboxLabelMaterializer {
    private SourceGrayboxLabelMaterializer() { }

    static void materialize(ServerLevel level, SourceGrayboxPresentationLedger ledger, ReferenceGrayboxSnapshot snapshot,
                            Set<String> active, Map<String, Entity> admittedEntities) {
        SourceGrayboxLabelPositions labels = new SourceGrayboxLabelPositions(ledger);
        BlockPos deck = SourceGrayboxWorldBoundary.observationDeckFooting();
        label(level, active, admittedEntities, labels, "legend:sector-metrics",
                "[KEY] V2 towers: red infection 0..1; purple spores 0..10; cyan human access 0..1; lime hive influence 0..1. Height=1..10.", deck.getX(), deck.getZ());
        label(level, active, admittedEntities, labels, "legend:inspect",
                "[GUIDE] Right-click a labelled structure, Villager or hive zombie for its state, cause, risk and next action. The REPORT and TIMELINE boards explain the region.", deck.getX(), deck.getZ());
        // A current canonical effect is the strongest local player cue.  Give
        // it the source anchor before ordinary infrastructure boards reserve
        // a nearby lateral slot; it is still presentation only and carries no
        // authority beyond the immutable snapshot.
        for (ReferenceGrayboxSnapshot.Effect value : snapshot.effects()) {
            label(level, active, admittedEntities, labels, "effect:" + value.id(),
                    SourceGrayboxLiveBriefing.effectLabel(value), value.position().x(), value.position().z());
        }
        for (ReferenceGrayboxSnapshot.Settlement value : snapshot.settlements()) {
            label(level, active, admittedEntities, labels, "settlement:" + value.id(),
                    SourceGrayboxPlayerBriefing.settlementLabel(snapshot, value), value.rectangle().centreX(), value.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.Facility value : snapshot.facilities()) {
            label(level, active, admittedEntities, labels, "facility:" + value.id(),
                    SourceGrayboxPlayerBriefing.facilityLabel(value), value.rectangle().centreX(), value.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.Warehouse value : snapshot.warehouses()) {
            label(level, active, admittedEntities, labels, "warehouse:" + value.id(),
                    SourceGrayboxWarehouseBriefing.label(snapshot, value), value.rectangle().centreX(), value.rectangle().centreZ(),
                    SourceGrayboxWarehouseRuntime.labelFloorY());
        }
        for (ReferenceGrayboxSnapshot.ResourceSite value : snapshot.resourceSites()) {
            label(level, active, admittedEntities, labels, "site:" + value.id(),
                    SourceGrayboxPlayerBriefing.resourceSiteLabel(value), value.rectangle().centreX(), value.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.HiveOrgan value : snapshot.hiveOrgans()) {
            label(level, active, admittedEntities, labels, "organ:" + value.id(),
                    SourceGrayboxPlayerBriefing.organLabel(value), value.rectangle().centreX(), value.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.Cargo value : snapshot.cargoes()) label(level, active, admittedEntities, labels, "cargo:" + value.id(), SourceGrayboxPlayerBriefing.cargoLabel(value), value.rectangle().centreX(), value.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Route value : snapshot.routes()) {
            label(level, active, admittedEntities, labels, "route:" + value.id(),
                    SourceGrayboxPlayerBriefing.routeLabel(snapshot, value),
                    midpoint(value.start().x(), value.end().x()), midpoint(value.start().z(), value.end().z()));
        }
        for (ReferenceGrayboxSnapshot.FieldPost value : snapshot.fieldPosts()) {
            label(level, active, admittedEntities, labels, "field-post:" + value.id(),
                    SourceGrayboxPlayerBriefing.fieldPostLabel(value), value.rectangle().centreX(), value.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.FieldLink value : snapshot.fieldLinks()) {
            ReferenceGrayboxLayout.Point point = value.slots().get(value.slots().size() / 2);
            label(level, active, admittedEntities, labels, "field-link:" + value.id(), SourceGrayboxPlayerBriefing.fieldLinkLabel(value), point.x(), point.z());
        }
        for (ReferenceGrayboxSnapshot.Activity value : snapshot.activities()) {
            if (!value.terminal()) label(level, active, admittedEntities, labels, "activity:" + value.id(),
                    SourceGrayboxLiveBriefing.activityLabel(value), value.position().x(), value.position().z());
        }
        for (ReferenceGrayboxSnapshot.Sector value : SourceGrayboxLabelLayout.labelledSectors(snapshot)) {
            String text = "[V2] " + value.key() + " " + value.control().toUpperCase(Locale.ROOT)
                    + (value.supplied() ? " SUPPLIED" : "");
            label(level, active, admittedEntities, labels, "sector:" + value.key(), text,
                    value.rectangle().centreX(), value.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.Chrysalis value : snapshot.chrysalises()) {
            label(level, active, admittedEntities, labels, "chrysalis:" + value.organId(),
                    SourceGrayboxPlayerBriefing.chrysalisLabel(value), value.rectangle().centreX(), value.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.Interaction value : snapshot.interactions()) {
            ReferenceGrayboxLayout.Point point = value.slots().get(value.slots().size() / 2);
            label(level, active, admittedEntities, labels, "interaction:" + value.id(), SourceGrayboxPlayerBriefing.interactionLabel(snapshot, ledger, value), point.x(), point.z());
        }
        conflictBoards(ledger).forEach((subjectId, claim) -> label(level, active, admittedEntities, labels, "conflict:" + subjectId, SourceGrayboxConflictPresentation.labelText(claim), claim.x() + claim.width() / 2, claim.z() + claim.depth() / 2));
        label(level, active, admittedEntities, labels, "dashboard:summary", SourceGrayboxPlayerBriefing.frontierReportLabel(snapshot), deck.getX(), deck.getZ());
        label(level, active, admittedEntities, labels, "events:summary", SourceGrayboxPlayerBriefing.timelineLabel(snapshot), deck.getX(), deck.getZ());
    }

    private static Map<String, SourceGrayboxPresentationLedger.Claim> conflictBoards(SourceGrayboxPresentationLedger ledger) {
        Map<String, SourceGrayboxPresentationLedger.Claim> result = new LinkedHashMap<>();
        ledger.claims().stream().filter(SourceGrayboxPresentationLedger.Claim::conflicted)
                .filter(claim -> SourceGrayboxConflictPresentation.needsLocalBoard(claim.kind()))
                .sorted(Comparator.comparing(SourceGrayboxPresentationLedger.Claim::id))
                .forEach(claim -> result.merge(claim.subjectId(), claim, SourceGrayboxConflictPresentation::preferredBoard));
        return result;
    }

    private static void label(ServerLevel level, Set<String> active, Map<String, Entity> admittedEntities, SourceGrayboxLabelPositions labels, String id, String text, int x, int z) {
        label(level, active, admittedEntities, labels, id, text, x, z, ReferenceGrayboxLayout.GROUND_Y + 3);
    }

    private static void label(ServerLevel level, Set<String> active, Map<String, Entity> admittedEntities, SourceGrayboxLabelPositions labels, String id, String text, int x, int z, int minimumY) {
        String key = SourceGrayboxMaterializer.entityKey(id, SourceGrayboxMaterializer.LABEL_KIND);
        active.add(key);
        BlockPos position = labels.next(id, x, z, minimumY);
        if (!SourceGrayboxMaterializer.ready(level, position)) return;
        Entity current = SourceGrayboxMaterializer.existingEntity(level, admittedEntities, id, SourceGrayboxMaterializer.LABEL_KIND, SourceGrayboxMaterializer.uuid("label-display", id));
        if (current != null && !(current instanceof Display.TextDisplay && SourceGrayboxMaterializer.identityMatches(current, id, SourceGrayboxMaterializer.LABEL_KIND))) return;
        SourceGrayboxPresentationLedger presentation = SourceGrayboxPresentationLedger.get(level);
        if (current instanceof Display.TextDisplay known) {
            presentation.claimEntity(key);
            SourceGrayboxLabelPresentation.configure(known, id, text);
            known.setPos(Vec3.atBottomCenterOf(position));
            return;
        }
        if (presentation.entityClaimed(key)) return;
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setUUID(SourceGrayboxMaterializer.uuid("label-display", id));
        SourceGrayboxLabelPresentation.configure(display, id, text);
        display.setPos(Vec3.atBottomCenterOf(position));
        presentation.claimEntity(key);
        if (level.addFreshEntity(display)) admittedEntities.put(key, display);
    }

    private static int midpoint(int first, int second) { return first + (second - first) / 2; }
}
