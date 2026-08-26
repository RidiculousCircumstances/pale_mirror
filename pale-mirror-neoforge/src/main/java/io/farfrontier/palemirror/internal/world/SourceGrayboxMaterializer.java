package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Loaded-chunks-only source-graybox projector.
 *
 * <p>It consumes only {@link ReferenceGrayboxSnapshot}. It never decides what
 * exists, aggregates people, or changes the simulation. Failed placement is a
 * visible retained conflict in the presentation ledger, not permission to
 * overwrite a foreign block.</p>
 */
final class SourceGrayboxMaterializer {
    static final String ENTITY_ID = "pale_mirror_source_graybox_id";
    static final String ENTITY_KIND = "pale_mirror_source_graybox_kind";
    static final String ENTITY_REVISION = "pale_mirror_source_graybox_revision";
    static final String LABEL_KIND = "LABEL_DISPLAY";
    private static final int SURFACE_Y = ReferenceGrayboxLayout.GROUND_Y;
    private static final int ENTITY_Y = SURFACE_Y + 1;
    /**
     * Labels are anchored to the local visible roof, rather than one distant
     * global sky plane.  The latter made an ordinary one-block structure look
     * disconnected from its text when viewed from the observation deck.
     */
    private static final int MAX_LABEL_Y = SURFACE_Y + 81;
    Report apply(ServerLevel level, ReferenceGrayboxSnapshot snapshot) { return apply(level, snapshot, null, new LinkedHashMap<>()); }

    Report apply(ServerLevel level, ReferenceGrayboxSnapshot snapshot, Map<String, Entity> admittedEntities) {
        return apply(level, snapshot, null, admittedEntities);
    }

    /**
     * Projects one immutable snapshot while retaining entities observed during level admission.
     *
     * <p>NeoForge can emit {@code EntityJoinLevelEvent} before the restored entity is available through
     * {@link ServerLevel#getEntity(UUID)}. The supplied map carries only those verified PM identities
     * across that publication window; it is presentation state, never a simulation owner.</p>
     */
    Report apply(ServerLevel level, ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxActorExecutionState actorExecution,
                 Map<String, Entity> admittedEntities) {
        Objects.requireNonNull(admittedEntities, "admitted entities");
        LinkedHashMap<String, SourceGrayboxPresentationPlan.Desired> desired = SourceGrayboxPresentationPlan.from(snapshot);
        SourceGrayboxPresentationLedger ledger = SourceGrayboxPresentationLedger.get(level);
        rebalanceInteractionWeights(ledger, desired);
        int conflicts = reconcileChangedFootprints(level, ledger, desired);
        conflicts += retireAbsent(level, ledger, desired.keySet());
        int placed = 0;
        for (SourceGrayboxPresentationPlan.Desired item : desired.values()) if (ensure(level, ledger, item)) placed++;

        Set<String> activeEntities = new LinkedHashSet<>();
        materializeLabels(level, ledger, snapshot, activeEntities, admittedEntities);
        // A source removal may deliberately leave a RETIRED lease behind until
        // the executor observes and acknowledges its old physical body.  Keep
        // every non-cold lease out of generic retirement; the coordinator is
        // the only component allowed to discard and acknowledge it.
        if (actorExecution != null) {
            for (ReferenceGrayboxActorExecutionState.ActorState actor : actorExecution.actors()) {
                if (actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD) {
                    activeEntities.add(entityKey(actor.id(), actor.kind()));
                }
            }
        }
        SourceGrayboxActorMaterializer.materialize(level, ledger, snapshot, actorExecution, activeEntities, admittedEntities);
        retireEntities(level, snapshot.bounds(), activeEntities, admittedEntities);
        ledger.releaseEntitiesExcept(activeEntities);
        return new Report(placed, desired.size(), conflicts, snapshot.stateRevision());
    }

    /**
     * Validates the immutable source projection before it reaches Minecraft.
     *
     * <p>Two canonical facts must never compete for the same physical block.
     * Co-located source facts retain their logical x/z address but are packed
     * onto deterministic compact vertical layers; a foreign player block
     * remains a separately visible presentation conflict in the persisted
     * ledger.</p>
     */
    static void validateProjection(ReferenceGrayboxSnapshot snapshot) { SourceGrayboxPresentationPlan.validate(snapshot); }

    SourceGrayboxPresentationLedger.Claim claimAt(ServerLevel level, BlockPos position) {
        return SourceGrayboxPresentationLedger.get(level).at(position);
    }

    /** Finds one exact actor body without making absence permission to spawn or load a chunk. */
    Entity actorEntity(ServerLevel level, Map<String, Entity> admittedEntities,
                       ReferenceGrayboxActorExecutionState.ActorState actor) {
        return SourceGrayboxActorMaterializer.actorEntity(level, admittedEntities, actor);
    }

    void recordBlockConflict(ServerLevel level, BlockPos position) {
        SourceGrayboxPresentationLedger.Claim claim = claimAt(level, position);
        if (claim != null) SourceGrayboxPresentationLedger.get(level).conflict(claim.id());
    }

    void consumeBlockClaim(ServerLevel level, BlockPos position) {
        SourceGrayboxPresentationLedger.Claim claim = claimAt(level, position);
        if (claim != null) SourceGrayboxPresentationLedger.get(level).consume(claim.id());
    }

    /** Retains a non-owning container obstruction so the ordinary conflict-label path can explain it. */
    void recordWarehouseConflict(ServerLevel level, String id, String subjectId, String revision, BlockPos position, boolean installed) {
        SourceGrayboxPresentationLedger ledger = SourceGrayboxPresentationLedger.get(level);
        if (ledger.claim(id) == null) {
            ledger.put(new SourceGrayboxPresentationLedger.Claim(id, subjectId, "WAREHOUSE_CONTAINER", revision,
                    position.getX(), position.getY(), position.getZ(), 1, 1, 1, false, "", 0.0d, false, installed));
        }
        ledger.conflict(id);
    }

    static ManagedEntity managed(Entity entity) {
        String id = entity.getPersistentData().getString(ENTITY_ID);
        String kind = entity.getPersistentData().getString(ENTITY_KIND);
        String revision = entity.getPersistentData().getString(ENTITY_REVISION);
        if (id.isBlank() || !revision.matches("[0-9a-f]{64}") || !(kind.equals("RESIDENT") || kind.equals("BIOFORM"))) return null;
        if (kind.equals("RESIDENT") && !id.startsWith("resident:")) return null;
        if (kind.equals("BIOFORM") && !id.startsWith("bioform:")) return null;
        return new ManagedEntity(id, kind, revision);
    }

    static boolean recognizesManagedEntity(Entity entity) {
        String id = entity.getPersistentData().getString(ENTITY_ID);
        String kind = entity.getPersistentData().getString(ENTITY_KIND);
        if (id.isBlank()) return false;
        return switch (kind) {
            case "RESIDENT" -> id.startsWith("resident:") && entity instanceof Villager
                    && entity.getUUID().equals(uuid("resident", id));
            case "BIOFORM" -> id.startsWith("bioform:") && entity instanceof Zombie
                    && entity.getUUID().equals(uuid("bioform", id));
            // LABEL is the persisted pre-display form.  It remains recognized
            // only long enough for a later publication pass to retire it; new
            // labels use a distinct deterministic UUID and cannot collide with
            // an old serialized ArmorStand during migration.
            case "LABEL" -> entity instanceof ArmorStand && entity.getUUID().equals(uuid("label", id));
            case LABEL_KIND -> entity instanceof Display.TextDisplay && entity.getUUID().equals(uuid("label-display", id));
            default -> false;
        };
    }

    static void rememberAdmittedEntity(Map<String, Entity> admittedEntities, Entity entity) {
        if (!recognizesManagedEntity(entity)) return;
        admittedEntities.putIfAbsent(entityKey(entity.getPersistentData().getString(ENTITY_ID),
                entity.getPersistentData().getString(ENTITY_KIND)), entity);
    }

    private static void rebalanceInteractionWeights(SourceGrayboxPresentationLedger ledger,
                                                    Map<String, SourceGrayboxPresentationPlan.Desired> desired) {
        Map<String, List<SourceGrayboxPresentationPlan.Desired>> grouped = new LinkedHashMap<>();
        desired.values().stream().filter(item -> !item.interactionId().isEmpty()).forEach(item ->
                grouped.computeIfAbsent(item.interactionId(), ignored -> new ArrayList<>()).add(item));
        for (List<SourceGrayboxPresentationPlan.Desired> group : grouped.values()) {
            List<SourceGrayboxPresentationPlan.Desired> active = group.stream().filter(item -> {
                SourceGrayboxPresentationLedger.Claim prior = ledger.claim(item.id());
                return prior == null || !prior.consumed();
            }).toList();
            if (active.isEmpty()) continue;
            double weight = active.getFirst().interactionWeight() / active.size();
            active.forEach(item -> desired.put(item.id(), item.withInteractionWeight(weight)));
        }
    }

    private static int retireAbsent(ServerLevel level, SourceGrayboxPresentationLedger ledger, Set<String> desired) {
        int conflicts = 0;
        for (SourceGrayboxPresentationLedger.Claim claim : ledger.claims()) {
            if (claim.consumed() && !desired.contains(claim.id())) {
                ledger.remove(claim.id());
                continue;
            }
            if (desired.contains(claim.id()) || claim.conflicted() || !loaded(level, claim)) continue;
            if (!clearOwnedClaim(level, claim)) {
                ledger.conflict(claim.id());
                conflicts++;
                continue;
            }
            ledger.remove(claim.id());
        }
        return conflicts;
    }

    /** Repositions only unmodified PM-owned geometry when its compact layer changes. */
    private static int reconcileChangedFootprints(ServerLevel level, SourceGrayboxPresentationLedger ledger,
                                                  Map<String, SourceGrayboxPresentationPlan.Desired> desired) {
        int conflicts = 0;
        for (SourceGrayboxPresentationLedger.Claim claim : ledger.claims()) {
            SourceGrayboxPresentationPlan.Desired target = desired.get(claim.id());
            if (target == null || sameFootprint(claim, target) || claim.conflicted() || claim.consumed() || !loaded(level, claim)) continue;
            if (!clearOwnedClaim(level, claim)) {
                ledger.conflict(claim.id());
                conflicts++;
                continue;
            }
            ledger.remove(claim.id());
        }
        return conflicts;
    }

    private static boolean clearOwnedClaim(ServerLevel level, SourceGrayboxPresentationLedger.Claim claim) {
        // A claim may describe a source object that was blocked by a foreign
        // block before PM ever wrote it.  Retiring that object may clear the
        // ledger record, but it must never become authority to erase the
        // obstruction.
        if (!claim.installed()) return true;
        List<BlockPos> positions = positions(claim);
        if (positions.stream().anyMatch(position -> !level.getBlockState(position).isAir()
                && !SourceGrayboxPalette.managed(level.getBlockState(position).getBlock()))) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            BlockState before = level.getBlockState(position);
            if (before.isAir()) continue;
            previous.put(position, before);
            if (!level.setBlock(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)) {
                previous.forEach((changedPosition, state) -> level.setBlock(changedPosition, state, 3));
                return false;
            }
        }
        return true;
    }

    private static boolean ensure(ServerLevel level, SourceGrayboxPresentationLedger ledger, SourceGrayboxPresentationPlan.Desired item) {
        SourceGrayboxPresentationLedger.Claim before = ledger.claim(item.id());
        if (before != null && (before.conflicted() || before.consumed() || !sameFootprint(before, item))) return false;
        List<BlockPos> positions = positions(item);
        if (!loaded(level, positions) || !flat(level, item)) return false;
        BlockState desired = SourceGrayboxPalette.block(item.colour());
        for (BlockPos position : positions) {
            BlockState actual = level.getBlockState(position);
            if (before == null ? !actual.isAir() : !actual.isAir() && !SourceGrayboxPalette.managed(actual.getBlock())) {
                // Retain the exact desired footprint even when no PM block
                // was placed.  Otherwise a growing infection would retry and
                // eventually overwrite the same player block every publish,
                // while giving neither the player nor recovery code a visible
                // conflict record.
                if (before == null) {
                    ledger.put(new SourceGrayboxPresentationLedger.Claim(item.id(), item.subjectId(), item.kind(), item.revision(), item.x(), item.y(),
                            item.z(), item.width(), item.depth(), item.height(), false, item.interactionKind(), item.interactionWeight(), false, false));
                }
                ledger.conflict(item.id());
                return false;
            }
        }
        Map<BlockPos, BlockState> changed = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            BlockState actual = level.getBlockState(position);
            if (actual.equals(desired)) continue;
            changed.put(position, actual);
            if (!level.setBlock(position, desired, 3)) {
                changed.forEach((changedPosition, prior) -> level.setBlock(changedPosition, prior, 3));
                return false;
            }
        }
        if (positions.stream().anyMatch(position -> !level.getBlockState(position).equals(desired))) {
            changed.forEach((changedPosition, prior) -> level.setBlock(changedPosition, prior, 3));
            return false;
        }
        ledger.put(new SourceGrayboxPresentationLedger.Claim(item.id(), item.subjectId(), item.kind(), item.revision(), item.x(), item.y(),
                item.z(), item.width(), item.depth(), item.height(), false, item.interactionKind(), item.interactionWeight(), false, true));
        return true;
    }

    private static boolean sameFootprint(SourceGrayboxPresentationLedger.Claim claim, SourceGrayboxPresentationPlan.Desired item) {
        return claim.x() == item.x() && claim.y() == item.y() && claim.z() == item.z() && claim.width() == item.width()
                && claim.depth() == item.depth() && claim.height() == item.height();
    }

    private static boolean loaded(ServerLevel level, SourceGrayboxPresentationLedger.Claim claim) {
        return loaded(level, positions(claim));
    }

    private static boolean loaded(ServerLevel level, List<BlockPos> positions) {
        return positions.stream().allMatch(level::hasChunkAt);
    }

    private static boolean flat(ServerLevel level, SourceGrayboxPresentationPlan.Desired item) {
        for (int x = item.x(); x < item.x() + item.width(); x++) for (int z = item.z(); z < item.z() + item.depth(); z++) {
            BlockPos ground = new BlockPos(x, SURFACE_Y - 1, z);
            if (level.getBlockState(ground).isAir() || !level.getFluidState(ground).isEmpty()) return false;
        }
        return true;
    }

    private static List<BlockPos> positions(SourceGrayboxPresentationLedger.Claim claim) {
        return positions(claim.x(), claim.y(), claim.z(), claim.width(), claim.depth(), claim.height());
    }

    private static List<BlockPos> positions(SourceGrayboxPresentationPlan.Desired item) {
        return SourceGrayboxPresentationPlan.positions(item).stream()
                .map(position -> new BlockPos(position.x(), position.y(), position.z())).toList();
    }

    private static List<BlockPos> positions(int x, int y, int z, int width, int depth, int height) {
        List<BlockPos> result = new ArrayList<>(width * depth * height);
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) for (int dy = 0; dy < height; dy++) {
            result.add(new BlockPos(x + dx, y + dy, z + dz));
        }
        return result;
    }

    private static void materializeLabels(ServerLevel level, SourceGrayboxPresentationLedger ledger, ReferenceGrayboxSnapshot snapshot, Set<String> active,
                                          Map<String, Entity> admittedEntities) {
        SourceGrayboxLabelPositions labels = new SourceGrayboxLabelPositions(ledger);
        BlockPos deck = SourceGrayboxWorldBoundary.observationDeckFooting();
        label(level, active, admittedEntities, labels, "legend:sector-metrics",
                "[KEY] V2 towers: red infection 0..1; purple spores 0..10; cyan human access 0..1; lime hive influence 0..1. Height=1..10.",
                deck.getX(), deck.getZ());
        label(level, active, admittedEntities, labels, "legend:inspect",
                "[GUIDE] Right-click a labelled structure, Villager or hive zombie for its state, cause, risk and next action. The REPORT and TIMELINE boards explain the region.", deck.getX(), deck.getZ());
        for (ReferenceGrayboxSnapshot.Settlement settlement : snapshot.settlements()) {
            label(level, active, admittedEntities, labels, "settlement:" + settlement.id(), SourceGrayboxPlayerBriefing.settlementLabel(snapshot, settlement),
                    settlement.rectangle().centreX(), settlement.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.Facility facility : snapshot.facilities()) label(level, active, admittedEntities, labels, "facility:" + facility.id(),
                SourceGrayboxPlayerBriefing.facilityLabel(facility), facility.rectangle().centreX(), facility.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Warehouse warehouse : snapshot.warehouses()) label(level, active, admittedEntities, labels,
                "warehouse:" + warehouse.id(), SourceGrayboxWarehouseBriefing.label(snapshot, warehouse),
                warehouse.rectangle().centreX(), warehouse.rectangle().centreZ(), SourceGrayboxWarehouseRuntime.labelFloorY());
        for (ReferenceGrayboxSnapshot.ResourceSite site : snapshot.resourceSites()) label(level, active, admittedEntities, labels, "site:" + site.id(),
                SourceGrayboxPlayerBriefing.resourceSiteLabel(site),
                site.rectangle().centreX(), site.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : snapshot.hiveOrgans()) label(level, active, admittedEntities, labels, "organ:" + organ.id(),
                SourceGrayboxPlayerBriefing.organLabel(organ), organ.rectangle().centreX(), organ.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) label(level, active, admittedEntities, labels, "cargo:" + cargo.id(),
                SourceGrayboxPlayerBriefing.cargoLabel(cargo),
                cargo.rectangle().centreX(), cargo.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Route route : snapshot.routes()) label(level, active, admittedEntities, labels, "route:" + route.id(),
                SourceGrayboxPlayerBriefing.routeLabel(snapshot, route),
                midpoint(route.start().x(), route.end().x()), midpoint(route.start().z(), route.end().z()));
        for (ReferenceGrayboxSnapshot.FieldPost post : snapshot.fieldPosts()) label(level, active, admittedEntities, labels, "field-post:" + post.id(),
                SourceGrayboxPlayerBriefing.fieldPostLabel(post), post.rectangle().centreX(), post.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.FieldLink link : snapshot.fieldLinks()) {
            ReferenceGrayboxLayout.Point label = link.slots().get(link.slots().size() / 2);
            label(level, active, admittedEntities, labels, "field-link:" + link.id(), SourceGrayboxPlayerBriefing.fieldLinkLabel(link), label.x(), label.z());
        }
        for (ReferenceGrayboxSnapshot.Activity activity : snapshot.activities()) if (!activity.terminal()) label(level, active, admittedEntities, labels,
                "activity:" + activity.id(), SourceGrayboxPlayerBriefing.activityLabel(activity),
                activity.position().x(), activity.position().z());
        for (ReferenceGrayboxSnapshot.Sector sector : SourceGrayboxLabelLayout.labelledSectors(snapshot)) label(level, active, admittedEntities, labels, "sector:" + sector.key(),
                "[V2] " + sector.key() + " " + sector.control().toUpperCase(Locale.ROOT) + (sector.supplied() ? " SUPPLIED" : ""),
                sector.rectangle().centreX(), sector.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Chrysalis chrysalis : snapshot.chrysalises()) label(level, active, admittedEntities, labels, "chrysalis:" + chrysalis.organId(),
                SourceGrayboxPlayerBriefing.chrysalisLabel(chrysalis), chrysalis.rectangle().centreX(), chrysalis.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Interaction interaction : snapshot.interactions()) {
            ReferenceGrayboxLayout.Point point = interaction.slots().get(interaction.slots().size() / 2);
            label(level, active, admittedEntities, labels, "interaction:" + interaction.id(),
                    SourceGrayboxPlayerBriefing.interactionLabel(snapshot, ledger, interaction), point.x(), point.z());
        }
        ledger.claims().stream().filter(SourceGrayboxPresentationLedger.Claim::conflicted)
                .sorted(Comparator.comparing(SourceGrayboxPresentationLedger.Claim::id))
                .forEach(claim -> label(level, active, admittedEntities, labels, "conflict:" + claim.id(),
                        SourceGrayboxConflictPresentation.labelText(claim), claim.x() + claim.width() / 2, claim.z() + claim.depth() / 2));
        label(level, active, admittedEntities, labels, "dashboard:summary", SourceGrayboxPlayerBriefing.frontierReportLabel(snapshot), deck.getX(), deck.getZ());
        label(level, active, admittedEntities, labels, "events:summary", SourceGrayboxPlayerBriefing.timelineLabel(snapshot), deck.getX(), deck.getZ());
    }

    private static void label(ServerLevel level, Set<String> active, Map<String, Entity> admittedEntities, SourceGrayboxLabelPositions labels,
                              String id, String text, int x, int z) {
        label(level, active, admittedEntities, labels, id, text, x, z, ReferenceGrayboxLayout.GROUND_Y + 3);
    }

    private static void label(ServerLevel level, Set<String> active, Map<String, Entity> admittedEntities, SourceGrayboxLabelPositions labels,
                              String id, String text, int x, int z, int minimumY) {
        String key = entityKey(id, LABEL_KIND);
        active.add(key);
        BlockPos position = labels.next(id, x, z, minimumY);
        if (!ready(level, position)) return;
        Entity current = existingEntity(level, admittedEntities, id, LABEL_KIND, uuid("label-display", id));
        if (current != null && !(current instanceof Display.TextDisplay && identityMatches(current, id, LABEL_KIND))) return;
        SourceGrayboxPresentationLedger ledger = SourceGrayboxPresentationLedger.get(level);
        if (current instanceof Display.TextDisplay known) {
            ledger.claimEntity(key);
            SourceGrayboxLabelPresentation.configure(known, id, text);
            known.setPos(Vec3.atBottomCenterOf(position));
            return;
        }
        if (ledger.entityClaimed(key)) return;
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setUUID(uuid("label-display", id));
        SourceGrayboxLabelPresentation.configure(display, id, text);
        display.setPos(Vec3.atBottomCenterOf(position));
        ledger.claimEntity(key);
        if (level.addFreshEntity(display)) admittedEntities.put(key, display);
    }

    static boolean identityMatches(Entity entity, String id, String kind) {
        return entity.getPersistentData().getString(ENTITY_ID).equals(id) && entity.getPersistentData().getString(ENTITY_KIND).equals(kind);
    }

    static Entity existingEntity(ServerLevel level, Map<String, Entity> admittedEntities, String id, String kind, UUID expectedUuid) {
        String key = entityKey(id, kind);
        Entity admitted = admittedEntities.get(key);
        if (admitted != null && !admitted.isRemoved()) return admitted;
        admittedEntities.remove(key);
        Entity indexed = level.getEntity(expectedUuid);
        if (indexed != null) admittedEntities.put(key, indexed);
        return indexed;
    }

    private static void retireEntities(ServerLevel level, ReferenceGrayboxLayout.Bounds bounds, Set<String> active,
                                       Map<String, Entity> admittedEntities) {
        AABB arena = new AABB(bounds.minX(), SURFACE_Y, bounds.minZ(), bounds.minX() + bounds.width(), MAX_LABEL_Y,
                bounds.minZ() + bounds.depth());
        for (Entity entity : level.getEntities((Entity) null, arena, value -> !value.getPersistentData().getString(ENTITY_ID).isBlank())) {
            String key = entityKey(entity.getPersistentData().getString(ENTITY_ID), entity.getPersistentData().getString(ENTITY_KIND));
            if (!active.contains(key)) entity.discard();
        }
        admittedEntities.entrySet().removeIf(entry -> entry.getValue().isRemoved() || !active.contains(entry.getKey()));
    }

    static boolean ready(ServerLevel level, BlockPos position) {
        BlockPos ground = new BlockPos(position.getX(), SURFACE_Y - 1, position.getZ());
        return level.hasChunkAt(position) && !level.getBlockState(ground).isAir()
                && level.getFluidState(ground).isEmpty();
    }

    static UUID uuid(String kind, String id) {
        return UUID.nameUUIDFromBytes(("pale-mirror-source-graybox:" + kind + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    static String entityKey(String id, String kind) {
        return kind + ":" + id;
    }

    static String entityKey(String id, ReferenceGrayboxActorExecutionState.ActorKind kind) {
        return entityKey(id, kind.name());
    }

    private static int midpoint(int first, int second) {
        return first + (second - first) / 2;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    record ManagedEntity(String id, String kind, String revision) { }
    record Report(int placed, int desired, int conflicts, String revision) { }
}
