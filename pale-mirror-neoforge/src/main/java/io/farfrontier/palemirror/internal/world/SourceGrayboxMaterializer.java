package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
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
    private static final int LABEL_Y = SURFACE_Y + 17;
    private static final int MAX_LABEL_Y = LABEL_Y + 64;
    Report apply(ServerLevel level, ReferenceGrayboxSnapshot snapshot) {
        return apply(level, snapshot, new LinkedHashMap<>());
    }

    /**
     * Projects one immutable snapshot while retaining entities observed during level admission.
     *
     * <p>NeoForge can emit {@code EntityJoinLevelEvent} before the restored entity is available through
     * {@link ServerLevel#getEntity(UUID)}. The supplied map carries only those verified PM identities
     * across that publication window; it is presentation state, never a simulation owner.</p>
     */
    Report apply(ServerLevel level, ReferenceGrayboxSnapshot snapshot, Map<String, Entity> admittedEntities) {
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
        for (ReferenceGrayboxSnapshot.Resident resident : snapshot.residents()) {
            ensureResident(level, ledger, snapshot.stateRevision(), resident, activeEntities, admittedEntities);
        }
        for (ReferenceGrayboxSnapshot.Bioform bioform : snapshot.bioforms()) {
            ensureBioform(level, ledger, snapshot.stateRevision(), bioform, activeEntities, admittedEntities);
        }
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
    static void validateProjection(ReferenceGrayboxSnapshot snapshot) {
        SourceGrayboxPresentationPlan.validate(snapshot);
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

    static boolean recognizesManagedEntity(Entity entity) {
        String id = entity.getPersistentData().getString(ENTITY_ID);
        String kind = entity.getPersistentData().getString(ENTITY_KIND);
        if (id.isBlank()) return false;
        return switch (kind) {
            case "RESIDENT" -> id.startsWith("resident:") && entity instanceof Villager
                    && entity.getUUID().equals(uuid("resident", id));
            case "BIOFORM" -> id.startsWith("bioform:") && entity instanceof Zombie
                    && entity.getUUID().equals(uuid("bioform", id));
            case "LABEL" -> entity instanceof ArmorStand && entity.getUUID().equals(uuid("label", id));
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
        LabelPositions labels = new LabelPositions();
        for (ReferenceGrayboxSnapshot.Settlement settlement : snapshot.settlements()) {
            label(level, active, admittedEntities, labels, "settlement:" + settlement.id(), "[S] " + settlement.name() + " | pop=" + number(settlement.population())
                    + " | " + settlement.civicState() + " threat=" + number(settlement.threat()) + " food="
                    + number(settlement.foodReserveDays()) + "d ration=" + number(settlement.rationFraction()),
                    settlement.rectangle().centreX(), settlement.rectangle().centreZ());
        }
        for (ReferenceGrayboxSnapshot.Facility facility : snapshot.facilities()) label(level, active, admittedEntities, labels, "facility:" + facility.id(),
                "[F] " + facility.kind() + " level=" + number(facility.level()), facility.rectangle().centreX(), facility.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.ResourceSite site : snapshot.resourceSites()) label(level, active, admittedEntities, labels, "site:" + site.id(),
                "[R] " + site.kind() + " capacity=" + number(site.capacity()) + " condition=" + number(site.condition())
                        + " contamination=" + number(site.contamination()), site.rectangle().centreX(), site.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.HiveOrgan organ : snapshot.hiveOrgans()) label(level, active, admittedEntities, labels, "organ:" + organ.id(),
                "[H] " + organ.kind() + " biomass=" + number(organ.biomass()) + " vitality=" + number(organ.vitality())
                        + (organ.feral() ? " FERAL" : ""), organ.rectangle().centreX(), organ.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Cargo cargo : snapshot.cargoes()) label(level, active, admittedEntities, labels, "cargo:" + cargo.id(),
                "[CARGO] " + cargo.ownerKind() + "=" + cargo.ownerId() + " " + cargo.resource() + "=" + number(cargo.quantity()),
                cargo.rectangle().centreX(), cargo.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Route route : snapshot.routes()) label(level, active, admittedEntities, labels, "route:" + route.id(),
                "[T] " + route.id() + " capacity=" + number(route.capacity()) + " risk=" + number(route.risk())
                        + " infection=" + number(route.infection()) + (route.quarantined() ? " QUARANTINED" : route.disrupted() ? " DISRUPTED" : " OPEN"),
                midpoint(route.start().x(), route.end().x()), midpoint(route.start().z(), route.end().z()));
        for (ReferenceGrayboxSnapshot.FieldPost post : snapshot.fieldPosts()) label(level, active, admittedEntities, labels, "field-post:" + post.id(),
                "[P] " + post.kind() + " " + post.status() + " integrity=" + number(post.integrity()) + " garrison=" + post.garrison() + " wounded=" + post.wounded()
                        + " modules=" + String.join(",", post.modules()), post.rectangle().centreX(), post.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.FieldLink link : snapshot.fieldLinks()) {
            ReferenceGrayboxLayout.Point label = link.slots().get(link.slots().size() / 2);
            label(level, active, admittedEntities, labels, "field-link:" + link.id(), "[L] " + link.kind() + " " + link.status()
                    + " integrity=" + number(link.integrity()), label.x(), label.z());
        }
        for (ReferenceGrayboxSnapshot.Activity activity : snapshot.activities()) if (!activity.terminal()) label(level, active, admittedEntities, labels,
                "activity:" + activity.id(), "[A] " + activity.family() + " " + activity.kind() + " " + activity.phase()
                        + " personnel=" + number(activity.personnel()) + " indicator=" + number(activity.indicator()),
                activity.position().x(), activity.position().z());
        for (ReferenceGrayboxSnapshot.Sector sector : snapshot.sectors()) label(level, active, admittedEntities, labels, "sector:" + sector.key(),
                "[V2] " + sector.key() + " " + sector.control() + " infection=" + number(sector.infection()) + " spores="
                        + number(sector.sporeLoad()) + " access=" + number(sector.humanAccess()) + " hive=" + number(sector.hiveInfluence())
                        + (sector.supplied() ? " SUPPLIED" : " UNSUPPLIED"), sector.rectangle().centreX(), sector.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Chrysalis chrysalis : snapshot.chrysalises()) label(level, active, admittedEntities, labels, "chrysalis:" + chrysalis.organId(),
                "[C] organ=" + chrysalis.organId() + " " + chrysalis.status() + " days=" + chrysalis.daysRemaining()
                        + " biomass=" + number(chrysalis.biomassCommitted()), chrysalis.rectangle().centreX(), chrysalis.rectangle().centreZ());
        for (ReferenceGrayboxSnapshot.Interaction interaction : snapshot.interactions()) {
            ReferenceGrayboxLayout.Point point = interaction.slots().get(interaction.slots().size() / 2);
            label(level, active, admittedEntities, labels, "interaction:" + interaction.id(),
                    SourceGrayboxInteractionPresentation.labelText(ledger, interaction), point.x(), point.z());
        }
        for (ReferenceGrayboxSnapshot.Readout readout : snapshot.readouts()) label(level, active, admittedEntities, labels, "readout:" + readout.id(),
                "[" + readout.category() + "] " + readout.text(), readout.position().x(), readout.position().z());
        snapshot.cells().stream().filter(cell -> cell.infection() > 0.01d || cell.signal() > 0.01d)
                .sorted(Comparator.comparingDouble((ReferenceGrayboxSnapshot.Cell cell) -> cell.infection() + cell.signal()).reversed()
                        .thenComparingInt(ReferenceGrayboxSnapshot.Cell::x).thenComparingInt(ReferenceGrayboxSnapshot.Cell::y)).limit(96)
                .forEach(cell -> label(level, active, admittedEntities, labels, "cell:" + cell.x() + ":" + cell.y(), "[E] " + cell.x() + "," + cell.y()
                        + " infection=" + number(cell.infection()) + " organic=" + number(cell.organicMass()) + " moisture="
                        + number(cell.moisture()) + " signal=" + number(cell.signal()), cell.rectangle().x() + 1, cell.rectangle().z() + 1));
        int firstEvent = Math.max(0, snapshot.events().size() - 12);
        for (int index = firstEvent; index < snapshot.events().size(); index++) label(level, active, admittedEntities, labels, "event:" + index,
                "[D" + snapshot.day() + "] " + snapshot.events().get(index), snapshot.bounds().minX() + 8,
                snapshot.bounds().minZ() + 8 + (index - firstEvent) * 2);
    }

    private static void ensureResident(ServerLevel level, SourceGrayboxPresentationLedger ledger, String revision,
                                       ReferenceGrayboxSnapshot.Resident resident, Set<String> active, Map<String, Entity> admittedEntities) {
        String key = entityKey(resident.id(), "RESIDENT");
        active.add(key);
        BlockPos position = new BlockPos(resident.position().x(), ENTITY_Y, resident.position().z());
        if (!ready(level, position)) return;
        Entity current = existingEntity(level, admittedEntities, resident.id(), "RESIDENT", uuid("resident", resident.id()));
        if (current != null && !(current instanceof Villager && identityMatches(current, resident.id(), "RESIDENT"))) return;
        if (current instanceof Villager known) {
            ledger.claimEntity(key);
            known.setVillagerData(known.getVillagerData().setProfession(VillagerProfession.NONE));
            configure(known, resident.id(), "RESIDENT", revision, resident.occupation() + " | " + resident.location(),
                    SourceGrayboxPalette.residentHat(resident.occupation(), resident.condition()), false);
            known.setPos(Vec3.atBottomCenterOf(position));
            return;
        }
        if (ledger.entityClaimed(key)) return;
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setUUID(uuid("resident", resident.id()));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        configure(villager, resident.id(), "RESIDENT", revision, resident.occupation() + " | " + resident.location(),
                SourceGrayboxPalette.residentHat(resident.occupation(), resident.condition()), false);
        villager.setPos(Vec3.atBottomCenterOf(position));
        ledger.claimEntity(key);
        if (level.addFreshEntity(villager)) admittedEntities.put(key, villager);
    }

    private static void ensureBioform(ServerLevel level, SourceGrayboxPresentationLedger ledger, String revision,
                                      ReferenceGrayboxSnapshot.Bioform bioform, Set<String> active, Map<String, Entity> admittedEntities) {
        String key = entityKey(bioform.id(), "BIOFORM");
        active.add(key);
        BlockPos position = new BlockPos(bioform.position().x(), ENTITY_Y, bioform.position().z());
        if (!ready(level, position)) return;
        Entity current = existingEntity(level, admittedEntities, bioform.id(), "BIOFORM", uuid("bioform", bioform.id()));
        if (current != null && !(current instanceof Zombie && identityMatches(current, bioform.id(), "BIOFORM"))) return;
        if (current instanceof Zombie known) {
            ledger.claimEntity(key);
            configure(known, bioform.id(), "BIOFORM", revision, bioform.kind() + " | " + bioform.phase(),
                    SourceGrayboxPalette.bioformHat(bioform.kind()), true);
            known.setPos(Vec3.atBottomCenterOf(position));
            return;
        }
        if (ledger.entityClaimed(key)) return;
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.setUUID(uuid("bioform", bioform.id()));
        configure(zombie, bioform.id(), "BIOFORM", revision, bioform.kind() + " | " + bioform.phase(),
                SourceGrayboxPalette.bioformHat(bioform.kind()), true);
        zombie.setPos(Vec3.atBottomCenterOf(position));
        ledger.claimEntity(key);
        if (level.addFreshEntity(zombie)) admittedEntities.put(key, zombie);
    }

    private static void label(ServerLevel level, Set<String> active, Map<String, Entity> admittedEntities, LabelPositions labels,
                              String id, String text, int x, int z) {
        String key = entityKey(id, "LABEL");
        active.add(key);
        BlockPos position = new BlockPos(x, labels.nextY(x, z), z);
        if (!ready(level, position)) return;
        Entity current = existingEntity(level, admittedEntities, id, "LABEL", uuid("label", id));
        if (current != null && !(current instanceof ArmorStand && identityMatches(current, id, "LABEL"))) return;
        SourceGrayboxPresentationLedger ledger = SourceGrayboxPresentationLedger.get(level);
        if (current instanceof ArmorStand known) {
            ledger.claimEntity(key);
            known.setInvisible(true);
            known.setNoGravity(true);
            known.setCustomName(Component.literal(text));
            known.setCustomNameVisible(true);
            attach(known, id, "LABEL", "0000000000000000000000000000000000000000000000000000000000000000");
            known.setPos(Vec3.atBottomCenterOf(position));
            return;
        }
        if (ledger.entityClaimed(key)) return;
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.setUUID(uuid("label", id));
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setCustomName(Component.literal(text));
        stand.setCustomNameVisible(true);
        attach(stand, id, "LABEL", "0000000000000000000000000000000000000000000000000000000000000000");
        stand.setPos(Vec3.atBottomCenterOf(position));
        ledger.claimEntity(key);
        if (level.addFreshEntity(stand)) admittedEntities.put(key, stand);
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

    private static Entity existingEntity(ServerLevel level, Map<String, Entity> admittedEntities, String id, String kind, UUID expectedUuid) {
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

    private static boolean ready(ServerLevel level, BlockPos position) {
        BlockPos ground = new BlockPos(position.getX(), SURFACE_Y - 1, position.getZ());
        return level.hasChunkAt(position) && !level.getBlockState(ground).isAir()
                && level.getFluidState(ground).isEmpty();
    }

    static UUID uuid(String kind, String id) {
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

    /** Separates otherwise coincident nameplates without changing their source x/z address. */
    private static final class LabelPositions {
        private final Map<LabelColumn, Integer> nextByColumn = new LinkedHashMap<>();

        int nextY(int x, int z) {
            LabelColumn column = new LabelColumn(x, z);
            int result = nextByColumn.getOrDefault(column, LABEL_Y);
            nextByColumn.put(column, result + 2);
            return result;
        }
    }

    private record LabelColumn(int x, int z) { }

    record ManagedEntity(String id, String kind, String revision) { }
    record Report(int placed, int desired, int conflicts, String revision) { }
}
