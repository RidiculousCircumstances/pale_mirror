package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private static final int SURFACE_Y = ReferenceGrayboxLayout.GROUND_Y;
    private static final int ENTITY_Y = SURFACE_Y + 1;
    private static final int LABEL_Y = SURFACE_Y + 4;

    Report apply(ServerLevel level, ReferenceGrayboxSnapshot snapshot) {
        requireProfile(snapshot);
        LinkedHashMap<String, Desired> desired = desired(snapshot);
        if (desired.size() > SourceGrayboxPresentationLedger.MAX_CLAIMS) {
            throw new IllegalStateException("source graybox projection exceeds its bounded claim ledger");
        }
        SourceGrayboxPresentationLedger ledger = SourceGrayboxPresentationLedger.get(level);
        rebalanceInteractionWeights(ledger, desired);
        int conflicts = retireAbsent(level, ledger, desired.keySet());
        int placed = 0;
        for (Desired item : desired.values()) if (ensure(level, ledger, item)) placed++;

        Set<String> activeEntities = new LinkedHashSet<>();
        materializeLabels(level, snapshot, activeEntities);
        for (ReferenceGrayboxSnapshot.Resident resident : snapshot.residents()) {
            ensureResident(level, snapshot.stateRevision(), resident, activeEntities);
        }
        for (ReferenceGrayboxSnapshot.Bioform bioform : snapshot.bioforms()) {
            ensureBioform(level, snapshot.stateRevision(), bioform, activeEntities);
        }
        retireEntities(level, snapshot.bounds(), activeEntities);
        return new Report(placed, desired.size(), conflicts, snapshot.stateRevision());
    }

    SourceGrayboxPresentationLedger.Claim claimAt(ServerLevel level, BlockPos position) {
        return SourceGrayboxPresentationLedger.get(level).at(position);
    }

    void recordBlockConflict(ServerLevel level, BlockPos position) {
        SourceGrayboxPresentationLedger.Claim claim = claimAt(level, position);
        if (claim != null) SourceGrayboxPresentationLedger.get(level).conflict(claim.id());
    }

    void consumeBlockClaim(ServerLevel level, BlockPos position) {
        SourceGrayboxPresentationLedger.Claim claim = claimAt(level, position);
        if (claim != null) SourceGrayboxPresentationLedger.get(level).consume(claim.id());
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

    private static LinkedHashMap<String, Desired> desired(ReferenceGrayboxSnapshot snapshot) {
        LinkedHashMap<String, Desired> result = new LinkedHashMap<>();
        for (ReferenceGrayboxSnapshot.Cell cell : snapshot.cells()) {
            add(result, marker("cell:" + cell.x() + ":" + cell.y(), "cell:" + cell.x() + ":" + cell.y(), "CELL",
                    snapshot.stateRevision(), cell.rectangle().x() + 1, cell.rectangle().z() + 1, cell.colour()));
        }
        for (ReferenceGrayboxSnapshot.Facility facility : snapshot.facilities()) {
            add(result, rectangle("facility:" + facility.id(), facility.id(), "FACILITY", snapshot.stateRevision(), facility.rectangle(), 1,
                    facility.colour()));
        }
        for (ReferenceGrayboxSnapshot.ResourceSite site : snapshot.resourceSites()) {
            add(result, rectangle("resource-site:" + site.id(), "site:" + site.id(), "RESOURCE_SITE", snapshot.stateRevision(), site.rectangle(), 1,
                    site.colour()));
        }
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : snapshot.hiveOrgans()) {
            add(result, rectangle("hive-organ:" + organ.id(), "organ:" + organ.id(), "HIVE_ORGAN", snapshot.stateRevision(), organ.rectangle(), 2,
                    organ.colour()));
        }
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) {
            add(result, rectangle("cargo-pallet:" + cargo.id(), cargo.id(), "CARGO", snapshot.stateRevision(), cargo.rectangle(), 1, cargo.colour()));
        }
        for (ReferenceGrayboxSnapshot.FieldPost post : snapshot.fieldPosts()) {
            add(result, rectangle("field-post:" + post.id(), "field-post:" + post.id(), "FIELD_POST", snapshot.stateRevision(), post.rectangle(), 1,
                    post.colour()));
        }
        for (ReferenceGrayboxSnapshot.Sector sector : snapshot.sectors()) {
            add(result, marker("sector:" + sector.key(), "sector:" + sector.key(), "SECTOR", snapshot.stateRevision(),
                    sector.rectangle().centreX(), sector.rectangle().centreZ(), sector.colour()));
        }
        for (ReferenceGrayboxSnapshot.Chrysalis chrysalis : snapshot.chrysalises()) {
            int x = chrysalis.rectangle().centreX() - 2;
            int z = chrysalis.rectangle().centreZ() - 2;
            add(result, new Desired("chrysalis:" + chrysalis.organId(), "chrysalis:" + chrysalis.organId(), "CHRYSALIS",
                    snapshot.stateRevision(), x, SURFACE_Y, z, 4, 4, 2, chrysalis.colour()));
        }
        for (ReferenceGrayboxSnapshot.Interaction interaction : snapshot.interactions()) {
            for (int index = 0; index < interaction.slots().size(); index++) {
                ReferenceGrayboxLayout.Point slot = interaction.slots().get(index);
                add(result, interactionSlot(interaction, index, snapshot.stateRevision(), slot));
            }
        }
        for (ReferenceGrayboxSnapshot.Activity activity : snapshot.activities()) {
            if (!activity.terminal()) add(result, marker("activity:" + activity.id(), activity.id(), "ACTIVITY", snapshot.stateRevision(),
                    activity.position().x(), activity.position().z(), activity.colour()));
        }
        return result;
    }

    private static void add(Map<String, Desired> values, Desired item) {
        if (values.putIfAbsent(item.id(), item) != null) throw new IllegalStateException("duplicate source graybox materialization ID: " + item.id());
    }

    private static Desired marker(String id, String subject, String kind, String revision, int x, int z, String colour) {
        return new Desired(id, subject, kind, revision, x, SURFACE_Y, z, 1, 1, 1, colour);
    }

    private static Desired rectangle(String id, String subject, String kind, String revision, ReferenceGrayboxLayout.Rectangle area,
                                     int height, String colour) {
        return new Desired(id, subject, kind, revision, area.x(), SURFACE_Y, area.z(), area.width(), area.depth(), height, colour);
    }

    private static Desired interactionSlot(ReferenceGrayboxSnapshot.Interaction interaction, int index, String revision,
                                           ReferenceGrayboxLayout.Point slot) {
        return new Desired("interaction:" + interaction.id() + ":" + index, interaction.subjectId(), "INTERACTION", revision,
                slot.x(), SURFACE_Y + interaction.yOffset(), slot.z(), 1, 1, 1, interaction.colour(), interaction.id(),
                interaction.kind(), interaction.totalWeight());
    }

    private static void rebalanceInteractionWeights(SourceGrayboxPresentationLedger ledger, Map<String, Desired> desired) {
        Map<String, List<Desired>> grouped = new LinkedHashMap<>();
        desired.values().stream().filter(item -> !item.interactionId().isEmpty()).forEach(item ->
                grouped.computeIfAbsent(item.interactionId(), ignored -> new ArrayList<>()).add(item));
        for (List<Desired> group : grouped.values()) {
            List<Desired> active = group.stream().filter(item -> {
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
            if (positions(claim).stream().anyMatch(position -> !level.getBlockState(position).isAir()
                    && !SourceGrayboxPalette.managed(level.getBlockState(position).getBlock()))) {
                ledger.conflict(claim.id());
                conflicts++;
                continue;
            }
            Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
            boolean complete = true;
            for (BlockPos position : positions(claim)) {
                BlockState before = level.getBlockState(position);
                if (before.isAir()) continue;
                previous.put(position, before);
                if (!level.setBlock(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)) {
                    complete = false;
                    break;
                }
            }
            if (!complete) {
                previous.forEach((position, state) -> level.setBlock(position, state, 3));
                continue;
            }
            ledger.remove(claim.id());
        }
        return conflicts;
    }

    private static boolean ensure(ServerLevel level, SourceGrayboxPresentationLedger ledger, Desired item) {
        SourceGrayboxPresentationLedger.Claim before = ledger.claim(item.id());
        if (before != null && (before.conflicted() || before.consumed() || !sameFootprint(before, item))) return false;
        List<BlockPos> positions = positions(item);
        if (!loaded(level, positions) || !flat(level, item)) return false;
        BlockState desired = SourceGrayboxPalette.block(item.colour());
        for (BlockPos position : positions) {
            BlockState actual = level.getBlockState(position);
            if (before == null ? !actual.isAir() : !actual.isAir() && !SourceGrayboxPalette.managed(actual.getBlock())) return false;
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
                item.z(), item.width(), item.depth(), item.height(), false, item.interactionKind(), item.interactionWeight(), false));
        return true;
    }

    private static boolean sameFootprint(SourceGrayboxPresentationLedger.Claim claim, Desired item) {
        return claim.x() == item.x() && claim.y() == item.y() && claim.z() == item.z() && claim.width() == item.width()
                && claim.depth() == item.depth() && claim.height() == item.height();
    }

    private static boolean loaded(ServerLevel level, SourceGrayboxPresentationLedger.Claim claim) {
        return loaded(level, positions(claim));
    }

    private static boolean loaded(ServerLevel level, List<BlockPos> positions) {
        return positions.stream().allMatch(level::hasChunkAt);
    }

    private static boolean flat(ServerLevel level, Desired item) {
        for (int x = item.x(); x < item.x() + item.width(); x++) for (int z = item.z(); z < item.z() + item.depth(); z++) {
            BlockPos ground = new BlockPos(x, SURFACE_Y - 1, z);
            if (level.getBlockState(ground).isAir() || !level.getFluidState(ground).isEmpty()) return false;
        }
        return true;
    }

    private static List<BlockPos> positions(SourceGrayboxPresentationLedger.Claim claim) {
        return positions(claim.x(), claim.y(), claim.z(), claim.width(), claim.depth(), claim.height());
    }

    private static List<BlockPos> positions(Desired item) {
        return positions(item.x(), item.y(), item.z(), item.width(), item.depth(), item.height());
    }

    private static List<BlockPos> positions(int x, int y, int z, int width, int depth, int height) {
        List<BlockPos> result = new ArrayList<>(width * depth * height);
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) for (int dy = 0; dy < height; dy++) {
            result.add(new BlockPos(x + dx, y + dy, z + dz));
        }
        return result;
    }

    private static void materializeLabels(ServerLevel level, ReferenceGrayboxSnapshot snapshot, Set<String> active) {
        for (ReferenceGrayboxSnapshot.Settlement settlement : snapshot.settlements()) {
            label(level, active, "settlement:" + settlement.id(), "[S] " + settlement.name() + " | pop=" + number(settlement.population())
                    + " | " + settlement.civicState() + " threat=" + number(settlement.threat()) + " food="
                    + number(settlement.foodReserveDays()) + "d ration=" + number(settlement.rationFraction()),
                    settlement.rectangle().centreX(), settlement.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.Facility facility : snapshot.facilities()) label(level, active, "facility:" + facility.id(),
                "[F] " + facility.kind() + " level=" + number(facility.level()), facility.rectangle().centreX(), facility.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.ResourceSite site : snapshot.resourceSites()) label(level, active, "site:" + site.id(),
                "[R] " + site.kind() + " capacity=" + number(site.capacity()) + " condition=" + number(site.condition())
                        + " contamination=" + number(site.contamination()), site.rectangle().centreX(), site.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : snapshot.hiveOrgans()) label(level, active, "organ:" + organ.id(),
                "[H] " + organ.kind() + " biomass=" + number(organ.biomass()) + " vitality=" + number(organ.vitality())
                        + (organ.feral() ? " FERAL" : ""), organ.rectangle().centreX(), organ.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) label(level, active, "cargo:" + cargo.id(),
                "[CARGO] op=" + cargo.operationId() + " " + cargo.resource() + "=" + number(cargo.quantity()),
                cargo.rectangle().centreX(), cargo.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Route route : snapshot.routes()) label(level, active, "route:" + route.id(),
                "[T] " + route.id() + " capacity=" + number(route.capacity()) + " risk=" + number(route.risk())
                        + " infection=" + number(route.infection()) + (route.quarantined() ? " QUARANTINED" : route.disrupted() ? " DISRUPTED" : " OPEN"),
                midpoint(route.start().x(), route.end().x()), midpoint(route.start().z(), route.end().z()));
        for (ReferenceGrayboxSnapshot.FieldPost post : snapshot.fieldPosts()) label(level, active, "field-post:" + post.id(),
                "[P] " + post.kind() + " " + post.status() + " garrison=" + post.garrison() + " wounded=" + post.wounded()
                        + " modules=" + String.join(",", post.modules()), post.rectangle().centreX(), post.rectangle().centreZ());
        Map<Integer, ReferenceGrayboxSnapshot.FieldPost> posts = new HashMap<>();
        snapshot.fieldPosts().forEach(post -> posts.put(post.id(), post));
        for (ReferenceGrayboxSnapshot.FieldLink link : snapshot.fieldLinks()) {
            ReferenceGrayboxSnapshot.FieldPost a = posts.get(link.postA());
            ReferenceGrayboxSnapshot.FieldPost b = posts.get(link.postB());
            if (a != null && b != null) label(level, active, "field-link:" + link.id(), "[L] " + link.kind() + " " + link.status(),
                    midpoint(a.rectangle().centreX(), b.rectangle().centreX()), midpoint(a.rectangle().centreZ(), b.rectangle().centreZ()));
        }
        for (ReferenceGrayboxSnapshot.Activity activity : snapshot.activities()) if (!activity.terminal()) label(level, active,
                "activity:" + activity.id(), "[A] " + activity.family() + " " + activity.kind() + " " + activity.phase()
                        + " personnel=" + number(activity.personnel()) + " indicator=" + number(activity.indicator()),
                activity.position().x(), activity.position().z());
        for (ReferenceGrayboxSnapshot.Sector sector : snapshot.sectors()) label(level, active, "sector:" + sector.key(),
                "[V2] " + sector.key() + " " + sector.control() + " infection=" + number(sector.infection()) + " spores="
                        + number(sector.sporeLoad()) + " access=" + number(sector.humanAccess()) + " hive=" + number(sector.hiveInfluence())
                        + (sector.supplied() ? " SUPPLIED" : " UNSUPPLIED"), sector.rectangle().centreX(), sector.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Chrysalis chrysalis : snapshot.chrysalises()) label(level, active, "chrysalis:" + chrysalis.organId(),
                "[C] organ=" + chrysalis.organId() + " " + chrysalis.status() + " days=" + chrysalis.daysRemaining()
                        + " biomass=" + number(chrysalis.biomassCommitted()), chrysalis.rectangle().centreX(), chrysalis.rectangle().centreZ());
        snapshot.cells().stream().filter(cell -> cell.infection() > 0.01d || cell.signal() > 0.01d)
                .sorted(Comparator.comparingDouble((ReferenceGrayboxSnapshot.Cell cell) -> cell.infection() + cell.signal()).reversed()
                        .thenComparingInt(ReferenceGrayboxSnapshot.Cell::x).thenComparingInt(ReferenceGrayboxSnapshot.Cell::y)).limit(96)
                .forEach(cell -> label(level, active, "cell:" + cell.x() + ":" + cell.y(), "[E] " + cell.x() + "," + cell.y()
                        + " infection=" + number(cell.infection()) + " organic=" + number(cell.organicMass()) + " moisture="
                        + number(cell.moisture()) + " signal=" + number(cell.signal()), cell.rectangle().x() + 1, cell.rectangle().z() + 1));
        int firstEvent = Math.max(0, snapshot.events().size() - 12);
        for (int index = firstEvent; index < snapshot.events().size(); index++) label(level, active, "event:" + index,
                "[D" + snapshot.day() + "] " + snapshot.events().get(index), snapshot.bounds().minX() + 8,
                snapshot.bounds().minZ() + 8 + (index - firstEvent) * 2);
    }

    private static void ensureResident(ServerLevel level, String revision, ReferenceGrayboxSnapshot.Resident resident, Set<String> active) {
        String key = entityKey(resident.id(), "RESIDENT");
        active.add(key);
        BlockPos position = new BlockPos(resident.position().x(), ENTITY_Y, resident.position().z());
        if (!ready(level, position)) return;
        Entity current = level.getEntity(uuid("resident", resident.id()));
        if (current != null && !(current instanceof Villager && identityMatches(current, resident.id(), "RESIDENT"))) return;
        Villager villager = current instanceof Villager known ? known : new Villager(EntityType.VILLAGER, level);
        if (current == null) villager.setUUID(uuid("resident", resident.id()));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        configure(villager, resident.id(), "RESIDENT", revision, resident.occupation() + " | " + resident.location(),
                SourceGrayboxPalette.residentHat(resident.occupation(), resident.condition()), false);
        villager.setPos(Vec3.atBottomCenterOf(position));
        if (current == null) level.addFreshEntity(villager);
    }

    private static void ensureBioform(ServerLevel level, String revision, ReferenceGrayboxSnapshot.Bioform bioform, Set<String> active) {
        String key = entityKey(bioform.id(), "BIOFORM");
        active.add(key);
        BlockPos position = new BlockPos(bioform.position().x(), ENTITY_Y, bioform.position().z());
        if (!ready(level, position)) return;
        Entity current = level.getEntity(uuid("bioform", bioform.id()));
        if (current != null && !(current instanceof Zombie && identityMatches(current, bioform.id(), "BIOFORM"))) return;
        Zombie zombie = current instanceof Zombie known ? known : new Zombie(EntityType.ZOMBIE, level);
        if (current == null) zombie.setUUID(uuid("bioform", bioform.id()));
        configure(zombie, bioform.id(), "BIOFORM", revision, bioform.kind() + " | " + bioform.phase(),
                SourceGrayboxPalette.bioformHat(bioform.kind()), true);
        zombie.setPos(Vec3.atBottomCenterOf(position));
        if (current == null) level.addFreshEntity(zombie);
    }

    private static void label(ServerLevel level, Set<String> active, String id, String text, int x, int z) {
        String key = entityKey(id, "LABEL");
        active.add(key);
        BlockPos position = new BlockPos(x, LABEL_Y, z);
        if (!ready(level, position)) return;
        Entity current = level.getEntity(uuid("label", id));
        if (current != null && !(current instanceof ArmorStand && identityMatches(current, id, "LABEL"))) return;
        ArmorStand stand = current instanceof ArmorStand known ? known : new ArmorStand(EntityType.ARMOR_STAND, level);
        if (current == null) stand.setUUID(uuid("label", id));
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setCustomName(Component.literal(text));
        stand.setCustomNameVisible(true);
        attach(stand, id, "LABEL", "0000000000000000000000000000000000000000000000000000000000000000");
        stand.setPos(Vec3.atBottomCenterOf(position));
        if (current == null) level.addFreshEntity(stand);
    }

    private static void configure(Mob entity, String id, String kind, String revision, String name, Item helmet, boolean nameVisible) {
        entity.setPersistenceRequired();
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setCustomName(Component.literal(name));
        entity.setCustomNameVisible(nameVisible);
        entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        attach(entity, id, kind, revision);
    }

    private static void attach(Entity entity, String id, String kind, String revision) {
        entity.getPersistentData().putString(ENTITY_ID, id);
        entity.getPersistentData().putString(ENTITY_KIND, kind);
        entity.getPersistentData().putString(ENTITY_REVISION, revision);
    }

    private static boolean identityMatches(Entity entity, String id, String kind) {
        return entity.getPersistentData().getString(ENTITY_ID).equals(id) && entity.getPersistentData().getString(ENTITY_KIND).equals(kind);
    }

    private static void retireEntities(ServerLevel level, ReferenceGrayboxLayout.Bounds bounds, Set<String> active) {
        AABB arena = new AABB(bounds.minX(), SURFACE_Y, bounds.minZ(), bounds.minX() + bounds.width(), LABEL_Y + 4,
                bounds.minZ() + bounds.depth());
        for (Entity entity : level.getEntities((Entity) null, arena, value -> !value.getPersistentData().getString(ENTITY_ID).isBlank())) {
            String key = entityKey(entity.getPersistentData().getString(ENTITY_ID), entity.getPersistentData().getString(ENTITY_KIND));
            if (!active.contains(key)) entity.discard();
        }
    }

    private static boolean ready(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position) && !level.getBlockState(position.below().below()).isAir()
                && level.getFluidState(position.below().below()).isEmpty();
    }

    private static UUID uuid(String kind, String id) {
        return UUID.nameUUIDFromBytes(("pale-mirror-source-graybox:" + kind + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static String entityKey(String id, String kind) {
        return kind + ":" + id;
    }

    private static int midpoint(int first, int second) {
        return first + (second - first) / 2;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void requireProfile(ReferenceGrayboxSnapshot snapshot) {
        if (!snapshot.profileId().equals("graybox_1_40") || snapshot.bounds().groundY() != SURFACE_Y
                || snapshot.bounds().width() != ReferenceGrayboxLayout.ARENA_BLOCKS_X
                || snapshot.bounds().depth() != ReferenceGrayboxLayout.ARENA_BLOCKS_Z
                || snapshot.bounds().blocksPerCell() != ReferenceGrayboxLayout.BLOCKS_PER_CELL) {
            throw new IllegalStateException("source graybox materializer rejected an incompatible profile");
        }
    }

    record ManagedEntity(String id, String kind, String revision) { }
    record Report(int placed, int desired, int conflicts, String revision) { }
    private record Desired(String id, String subjectId, String kind, String revision, int x, int y, int z, int width, int depth,
                           int height, String colour, String interactionId, String interactionKind, double interactionWeight) {
        private Desired(String id, String subjectId, String kind, String revision, int x, int y, int z, int width, int depth,
                        int height, String colour) {
            this(id, subjectId, kind, revision, x, y, z, width, depth, height, colour, "", "", 0.0d);
        }

        private Desired withInteractionWeight(double weight) {
            return new Desired(id, subjectId, kind, revision, x, y, z, width, depth, height, colour, interactionId, interactionKind, weight);
        }
    }
}
