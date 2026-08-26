package io.farfrontier.palemirror.visuals.runtime;

import static io.farfrontier.palemirror.visuals.runtime.FrontierGrayboxProjectionSupport.*;

import io.farfrontier.palemirror.api.FrontierPhysicalObservation;
import io.farfrontier.palemirror.api.FrontierProjection;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Loaded-chunks-only graybox projector. All geometry is derived from one immutable API projection;
 * persistent claims prevent later state changes from overwriting foreign blocks after a restart.
 */
final class FrontierGrayboxRuntime {
    static final String ENTITY_ID = "pale_mirror_frontier_id";
    static final String ENTITY_KIND = "pale_mirror_frontier_kind";
    static final String MATERIALIZATION_ID = "pale_mirror_frontier_materialization";
    static final String REVISION = "pale_mirror_frontier_revision";
    private static final int CELL_BLOCKS = 16;
    private static final int ORIGIN = -512;
    static final int SURFACE_Y = 64;
    private static final int WORLD_MIN = -512;
    private static final int WORLD_MAX = 511;
    private static final int MAX_PENDING_OBSERVATIONS = 8_192;
    private final Map<String, ProjectionState> states = new LinkedHashMap<>();
    private final Map<String, Map<String, Entity>> joinedManagedEntities = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, FrontierPhysicalObservation>> observations = new LinkedHashMap<>();
    private final FrontierGrayboxBioformMaterialization bioforms = new FrontierGrayboxBioformMaterialization();

    synchronized void apply(ServerLevel level, FrontierProjection projection) {
        if (!projection.profileId().equals("graybox-10")) return;
        String dimension = level.dimension().location().toString();
        ProjectionState state = new ProjectionState(projection, new HashMap<>(), new LinkedHashSet<>(),
                rehydrateManagedEntities(level, joinedManagedEntities.get(dimension)));
        states.put(dimension, state);
        FrontierGrayboxLedger ledger = FrontierGrayboxLedger.get(level);
        Map<String, FrontierProjection.Operation> operations = projection.operations().stream()
                .collect(java.util.stream.Collectors.toMap(FrontierProjection.Operation::facilityId, value -> value));
        Map<String, FrontierProjection.EcologyCell> ecology = projection.ecology().stream().collect(java.util.stream.Collectors.toMap(
                value -> ecologyKey(value.cellX(), value.cellZ()), value -> value, (left, right) -> {
                    throw new IllegalArgumentException("duplicate Frontier ecology projection cell");
                }, LinkedHashMap::new));
        for (FrontierProjection.Settlement settlement : projection.settlements()) {
            BlockPos anchor = position(settlement.cellX(), settlement.cellZ(), SURFACE_Y + 2);
            if (flatAt(level, anchor)) ensureLabel(level, state, "settlement:" + settlement.id(), settlementLabel(settlement), anchor);
        }
        for (FrontierProjection.Facility facility : projection.facilities()) {
            FrontierProjection.Operation operation = operations.get(facility.id());
            BlockState color = facilityBlock(facility.kind(), facility.state());
            String objectId = "facility:" + facility.id();
            if (ensureCube(level, ledger, state, objectId, facility.id(), "FACILITY", "frontier:facility:" + facility.id(),
                    facility.revision(), cellX(facility.cellX()), SURFACE_Y, cellZ(facility.cellZ()), 10, 8, 6, color)) {
                String operationState = operation == null ? "UNBOUND" : operation.state();
                ensureLabel(level, state, objectId + ":label", "[F] " + facility.kind() + " | " + operationState,
                        new BlockPos(cellX(facility.cellX()), SURFACE_Y + 7, cellZ(facility.cellZ()) - 5));
            }
        }
        for (FrontierProjection.HiveOrgan organ : projection.hiveOrgans()) {
            BlockState color = organBlock(organ.kind(), organ.state());
            String objectId = "hive-organ:" + organ.id();
            if (ensureCube(level, ledger, state, objectId, organ.id(), "HIVE_ORGAN", organ.materializationId(), organ.revision(),
                    cellX(organ.cellX()), SURFACE_Y, cellZ(organ.cellZ()), 8, 8, 5, color)) {
                FrontierProjection.EcologyCell cell = ecology.get(ecologyKey(organ.cellX(), organ.cellZ()));
                ensureLabel(level, state, objectId + ":label", organLabel(organ, cell),
                        new BlockPos(cellX(organ.cellX()), SURFACE_Y + 6, cellZ(organ.cellZ()) - 4));
            }
        }
        for (FrontierProjection.Hive hive : projection.hives()) {
            BlockPos anchor = position(hive.cellX(), hive.cellZ(), SURFACE_Y + 2);
            if (flatAt(level, anchor)) ensureLabel(level, state, "hive:" + hive.id(), "[HIVE] " + hive.state()
                    + " | biomass=" + hive.biomass() + " | genetic-milli=" + hive.geneticMaterial()
                    + " | brood-signal=" + hive.broodSignal() + adaptationLabel(projection.adaptations()), anchor);
        }
        for (FrontierProjection.MorphogenesisProject project : projection.morphogenesisProjects()) {
            if (!project.state().equals("GROWING")) continue;
            BlockPos anchor = position(project.cellX(), project.cellZ(), SURFACE_Y + 2);
            // A temporary label makes committed growth legible without placing a second unowned
            // cube into a tissue cell. The final cube appears only after the canonical project completes.
            if (flatAt(level, anchor)) ensureLabel(level, state, "morphogenesis:" + project.id(), "[M] " + project.kind()
                    + " | " + project.state() + " | days=" + project.remainingDays() + "/" + project.requiredDays(), anchor);
        }
        for (FrontierProjection.HarvesterRun run : projection.harvesterRuns()) {
            if (run.state().equals("COMPLETED") || run.state().equals("ABORTED")) continue;
            BlockPos anchor = position(run.cellX(), run.cellZ(), SURFACE_Y + 3);
            if (flatAt(level, anchor)) ensureLabel(level, state, "harvester:" + run.id(), harvesterLabel(run), anchor);
        }
        for (FrontierProjection.PropagationRun run : projection.propagationRuns()) {
            if (!run.state().equals("OUTBOUND")) continue;
            BlockPos anchor = position(run.cellX(), run.cellZ(), SURFACE_Y + 3);
            if (flatAt(level, anchor)) ensureLabel(level, state, "propagation-run:" + run.id(), propagationRunLabel(run), anchor);
        }
        for (FrontierProjection.LatentColony colony : projection.latentColonies()) {
            String objectId = "latent-colony:" + colony.id();
            if (ensureCube(level, ledger, state, objectId, colony.id(), "LATENT_COLONY", colony.materializationId(), colony.revision(),
                    cellX(colony.cellX()), SURFACE_Y, cellZ(colony.cellZ()), 1, 1, 1, Blocks.BLUE_WOOL.defaultBlockState())) {
                ensureLabel(level, state, objectId + ":label", latentColonyLabel(colony),
                        new BlockPos(cellX(colony.cellX()), SURFACE_Y + 2, cellZ(colony.cellZ())));
            }
        }
        for (FrontierProjection.Assault assault : projection.assaults()) {
            if (assault.state().equals("COMPLETED") || assault.state().equals("ABORTED")) continue;
            BlockPos anchor = position(assault.cellX(), assault.cellZ(), SURFACE_Y + 3);
            if (flatAt(level, anchor)) ensureLabel(level, state, "assault:" + assault.id(),
                    "[A] " + assault.kind() + " -> " + shortId(assault.targetSettlementId()) + " | " + assault.state()
                            + " | forms=" + assault.participantIds().size(), anchor);
        }
        for (FrontierProjection.FieldOperation operation : projection.fieldOperations()) {
            if (operation.state().equals("COMPLETED") || operation.state().equals("ABORTED")) continue;
            BlockPos anchor = position(operation.cellX(), operation.cellZ(), SURFACE_Y + 5);
            if (flatAt(level, anchor)) ensureLabel(level, state, "field-operation:" + operation.id(),
                    "[D] " + operation.kind() + " -> " + shortId(operation.targetAssaultId()) + " | " + operation.state()
                            + " | people=" + operation.livingParticipants() + "/" + operation.participantIds().size(), anchor);
        }
        FrontierGrayboxCampaignMaterialization.materialize(level, this, state, projection);
        materializeResidents(level, state, projection);
        bioforms.materialize(level, this, state, projection);
        materializeCargo(level, state, projection);
        removeRetiredEntities(level, ledger, state);
    }

    synchronized List<FrontierPhysicalObservation> drain(ServerLevel level) {
        LinkedHashMap<String, FrontierPhysicalObservation> pending = observations.remove(level.dimension().location().toString());
        return pending == null ? List.of() : List.copyOf(pending.values());
    }

    synchronized boolean observeEntityDeath(ServerLevel level, Entity entity, String causationId) {
        String kind = entity.getPersistentData().getString(ENTITY_KIND);
        FrontierPhysicalObservation.Type type = switch (kind) {
            case "RESIDENT" -> FrontierPhysicalObservation.Type.RESIDENT_DIED;
            case "BIOFORM" -> FrontierPhysicalObservation.Type.BIOFORM_DIED;
            case "CARGO" -> FrontierPhysicalObservation.Type.CARGO_LOST;
            default -> null;
        };
        if (type == null) return false;
        queue(level, type, entity.getPersistentData().getString(ENTITY_ID), entity.getPersistentData().getString(MATERIALIZATION_ID),
                entity.getPersistentData().getLong(REVISION), causationId + ":" + entity.getUUID());
        return true;
    }

    /**
     * EntityJoinLevelEvent is dispatched while a saved entity is becoming visible to a level, before
     * the first Frontier projection can safely recreate anything. Retaining that object closes the
     * PersistentEntitySectionManager UUID-index publication window on server restart.
     */
    synchronized void observeEntityJoin(ServerLevel level, Entity entity) {
        String id = entity.getPersistentData().getString(ENTITY_ID);
        String kind = entity.getPersistentData().getString(ENTITY_KIND);
        if (id.isBlank() || kind.isBlank()) return;
        String dimension = level.dimension().location().toString();
        String key = managedKey(id, kind);
        joinedManagedEntities.computeIfAbsent(dimension, ignored -> new HashMap<>()).put(key, entity);
        FrontierGrayboxLedger.get(level).claimEntity(key);
        ProjectionState state = states.get(dimension);
        if (state != null) state.managedEntities().put(key, entity);
    }

    synchronized boolean observeBlockBreak(ServerLevel level, BlockPos position, String causationId) {
        ProjectionState state = states.get(level.dimension().location().toString());
        StructureBinding binding = state == null ? null : state.blocks().get(position.asLong());
        if (binding == null) return false;
        FrontierPhysicalObservation.Type type = switch (binding.kind()) {
            case "FACILITY" -> FrontierPhysicalObservation.Type.FACILITY_DAMAGED;
            case "HIVE_ORGAN" -> FrontierPhysicalObservation.Type.HIVE_ORGAN_DESTROYED;
            case "LATENT_COLONY" -> FrontierPhysicalObservation.Type.LATENT_COLONY_CLEARED;
            default -> null;
        };
        if (type == null) return false;
        queue(level, type, binding.subjectId(), binding.materializationId(), binding.revision(), causationId);
        return true;
    }

    private void materializeResidents(ServerLevel level, ProjectionState state, FrontierProjection projection) {
        Map<String, Integer> indices = new HashMap<>();
        Map<String, FrontierProjection.Settlement> settlements = projection.settlements().stream()
                .collect(java.util.stream.Collectors.toMap(FrontierProjection.Settlement::id, value -> value));
        Map<String, FrontierProjection.FieldOperation> fieldOperations = new HashMap<>();
        for (FrontierProjection.FieldOperation operation : projection.fieldOperations()) {
            if (operation.state().equals("ASSEMBLING") || operation.state().equals("COMPLETED") || operation.state().equals("ABORTED")) continue;
            for (String participant : operation.participantIds()) fieldOperations.put(participant, operation);
        }
        for (FrontierProjection.Resident resident : projection.residents().stream().sorted(Comparator.comparing(FrontierProjection.Resident::id)).toList()) {
            FrontierProjection.Settlement settlement = settlements.get(resident.settlementId());
            if (settlement == null) continue;
            int index = indices.merge(resident.settlementId(), 1, Integer::sum) - 1;
            FrontierProjection.FieldOperation fieldOperation = fieldOperations.get(resident.id());
            BlockPos position = fieldOperation == null ? FrontierGrayboxCampaignMaterialization.residentPosition(projection,
                    resident.id(), residentPosition(settlement, index)) : fieldResidentPosition(fieldOperation, fieldOperation.participantIds().indexOf(resident.id()));
            if (resident.alive()) ensureVillager(level, state, resident, position);
            else retireEntity(level, resident.id());
        }
    }

    private void materializeCargo(ServerLevel level, ProjectionState state, FrontierProjection projection) {
        Map<String, FrontierProjection.Settlement> settlements = projection.settlements().stream()
                .collect(java.util.stream.Collectors.toMap(FrontierProjection.Settlement::id, value -> value));
        for (FrontierProjection.Cargo cargo : projection.cargo()) {
            FrontierProjection.Settlement source = settlements.get(cargo.sourceSettlementId());
            FrontierProjection.Settlement destination = settlements.get(cargo.destinationSettlementId());
            if (source == null || destination == null) continue;
            int x = (cellX(source.cellX()) + cellX(destination.cellX())) / 2;
            int z = (cellZ(source.cellZ()) + cellZ(destination.cellZ())) / 2;
            ensureCargo(level, state, cargo, new BlockPos(x, SURFACE_Y + 1, z));
        }
    }

    private boolean ensureCube(ServerLevel level, FrontierGrayboxLedger ledger, ProjectionState state, String objectId,
                               String subjectId, String kind, String materializationId, long revision,
                               int centerX, int baseY, int centerZ, int width, int depth, int height, BlockState desired) {
        List<BlockPos> positions = cube(centerX, baseY, centerZ, width, depth, height);
        if (positions.stream().anyMatch(position -> !level.hasChunkAt(position)) || !flatFootprint(level, positions)) return false;
        boolean claimed = ledger.claimed(objectId);
        for (BlockPos position : positions) {
            BlockState actual = level.getBlockState(position);
            if (!canReplace(actual, desired, claimed)) return false;
        }
        Map<BlockPos, BlockState> replaced = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            BlockState actual = level.getBlockState(position);
            if (actual.equals(desired)) continue;
            replaced.put(position, actual);
            if (!level.setBlock(position, desired, Block.UPDATE_ALL)) {
                restore(level, replaced);
                return false;
            }
        }
        if (positions.stream().anyMatch(position -> !level.getBlockState(position).equals(desired))) {
            restore(level, replaced);
            return false;
        }
        if (!claimed) ledger.claim(objectId);
        StructureBinding binding = new StructureBinding(subjectId, kind, materializationId, revision);
        positions.forEach(position -> state.blocks().put(position.asLong(), binding));
        return true;
    }

    private void ensureVillager(ServerLevel level, ProjectionState state, FrontierProjection.Resident resident, BlockPos position) {
        String key = managedKey(resident.id(), "RESIDENT");
        state.entityKeys().add(key);
        if (!flatAt(level, position) || !level.isPositionEntityTicking(position)) return;
        Entity existing = existingEntity(level, state, resident.id(), "RESIDENT", uuid("resident", resident.id()));
        if (existing instanceof Villager villager && managed(villager, resident.id(), "RESIDENT")) {
            FrontierGrayboxLedger.get(level).claimEntity(key);
            configure(villager, resident.id(), "RESIDENT", resident.materializationId(), resident.revision(), resident.role(), roleHat(resident.role()), false);
            villager.setPos(Vec3.atBottomCenterOf(position));
            return;
        }
        if (existing != null) return;
        FrontierGrayboxLedger ledger = FrontierGrayboxLedger.get(level);
        if (ledger.entityClaimed(key)) return;
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setUUID(uuid("resident", resident.id()));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        configure(villager, resident.id(), "RESIDENT", resident.materializationId(), resident.revision(), resident.role(), roleHat(resident.role()), false);
        villager.setPos(Vec3.atBottomCenterOf(position));
        ledger.claimEntity(key);
        level.addFreshEntity(villager);
        state.managedEntities().put(managedKey(resident.id(), "RESIDENT"), villager);
    }

    void ensureZombie(ServerLevel level, ProjectionState state, FrontierProjection.Bioform bioform, BlockPos position) {
        String key = managedKey(bioform.id(), "BIOFORM");
        state.entityKeys().add(key);
        if (!flatAt(level, position) || !level.isPositionEntityTicking(position)) return;
        Entity existing = existingEntity(level, state, bioform.id(), "BIOFORM", uuid("bioform", bioform.id()));
        if (existing instanceof Zombie zombie && managed(zombie, bioform.id(), "BIOFORM")) {
            FrontierGrayboxLedger.get(level).claimEntity(key);
            configure(zombie, bioform.id(), "BIOFORM", bioform.materializationId(), bioform.revision(), bioform.kind(), bioformHat(bioform.kind()), false);
            zombie.setPos(Vec3.atBottomCenterOf(position));
            return;
        }
        if (existing != null) return;
        FrontierGrayboxLedger ledger = FrontierGrayboxLedger.get(level);
        if (ledger.entityClaimed(key)) return;
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.setUUID(uuid("bioform", bioform.id()));
        configure(zombie, bioform.id(), "BIOFORM", bioform.materializationId(), bioform.revision(), bioform.kind(), bioformHat(bioform.kind()), false);
        zombie.setPos(Vec3.atBottomCenterOf(position));
        ledger.claimEntity(key);
        level.addFreshEntity(zombie);
        state.managedEntities().put(managedKey(bioform.id(), "BIOFORM"), zombie);
    }

    private void ensureCargo(ServerLevel level, ProjectionState state, FrontierProjection.Cargo cargo, BlockPos position) {
        String key = managedKey(cargo.id(), "CARGO");
        state.entityKeys().add(key);
        if (!flatAt(level, position) || !level.isPositionEntityTicking(position)) return;
        Entity existing = existingEntity(level, state, cargo.id(), "CARGO", uuid("cargo", cargo.id()));
        if (existing instanceof ArmorStand stand && managed(stand, cargo.id(), "CARGO")) {
            FrontierGrayboxLedger.get(level).claimEntity(key);
            configure(stand, cargo.id(), "CARGO", cargo.materializationId(), cargo.revision(), cargo.resource() + " x" + cargo.amount()
                    + " | credits=" + cargo.creditValue(), Items.CHEST, true);
            return;
        }
        if (existing != null) return;
        FrontierGrayboxLedger ledger = FrontierGrayboxLedger.get(level);
        if (ledger.entityClaimed(key)) return;
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.setUUID(uuid("cargo", cargo.id()));
        configure(stand, cargo.id(), "CARGO", cargo.materializationId(), cargo.revision(), cargo.resource() + " x" + cargo.amount()
                + " | credits=" + cargo.creditValue(), Items.CHEST, true);
        stand.setPos(Vec3.atBottomCenterOf(position));
        ledger.claimEntity(key);
        level.addFreshEntity(stand);
        state.managedEntities().put(managedKey(cargo.id(), "CARGO"), stand);
    }

    void ensureLabel(ServerLevel level, ProjectionState state, String id, String text, BlockPos position) {
        String key = managedKey(id, "LABEL");
        state.entityKeys().add(key);
        if (!flatAt(level, position) || !level.isPositionEntityTicking(position)) return;
        Entity existing = existingEntity(level, state, id, "LABEL", uuid("label", id));
        if (existing instanceof ArmorStand stand && managed(stand, id, "LABEL")) {
            FrontierGrayboxLedger.get(level).claimEntity(key);
            configure(stand, id, "LABEL", "frontier:label:" + id, 0, text, null, true);
            // Operation labels are physical projections too. Leaving an existing stand at its
            // old cell makes a canonical phase transition appear to happen somewhere else.
            stand.setPos(Vec3.atBottomCenterOf(position));
            return;
        }
        if (existing != null) return;
        FrontierGrayboxLedger ledger = FrontierGrayboxLedger.get(level);
        if (ledger.entityClaimed(key)) return;
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.setUUID(uuid("label", id));
        stand.setInvisible(true);
        configure(stand, id, "LABEL", "frontier:label:" + id, 0, text, null, true);
        stand.setPos(Vec3.atBottomCenterOf(position));
        ledger.claimEntity(key);
        level.addFreshEntity(stand);
        state.managedEntities().put(managedKey(id, "LABEL"), stand);
    }

    /**
     * On a server restart, PersistentEntitySectionManager restores an entity into its chunk before
     * it publishes that entity through ServerLevel#getEntity(UUID). The physical projector runs on
     * the first player tick in exactly that interval. Rehydrate from the saved PM identity as well
     * as the UUID index so restart never relies on the manager rejecting a duplicate UUID.
     */
    private static Map<String, Entity> rehydrateManagedEntities(ServerLevel level, Map<String, Entity> joined) {
        AABB bounds = new AABB(WORLD_MIN, SURFACE_Y, WORLD_MIN, WORLD_MAX + 1, SURFACE_Y + 32, WORLD_MAX + 1);
        Map<String, Entity> result = joined == null ? new HashMap<>() : new HashMap<>(joined);
        for (Entity entity : level.getEntities((Entity) null, bounds,
                value -> !value.getPersistentData().getString(ENTITY_ID).isBlank())) {
            String id = entity.getPersistentData().getString(ENTITY_ID);
            String kind = entity.getPersistentData().getString(ENTITY_KIND);
            if (!kind.isBlank()) result.putIfAbsent(managedKey(id, kind), entity);
        }
        return result;
    }

    private static Entity existingEntity(ServerLevel level, ProjectionState state, String id, String kind, UUID expectedUuid) {
        Entity restored = state.managedEntities().get(managedKey(id, kind));
        if (restored != null && !restored.isRemoved()) return restored;
        Entity indexed = level.getEntity(expectedUuid);
        if (indexed != null) state.managedEntities().put(managedKey(id, kind), indexed);
        return indexed;
    }

    private void removeRetiredEntities(ServerLevel level, FrontierGrayboxLedger ledger, ProjectionState state) {
        AABB bounds = new AABB(WORLD_MIN, SURFACE_Y, WORLD_MIN, WORLD_MAX + 1, SURFACE_Y + 32, WORLD_MAX + 1);
        for (Entity entity : level.getEntities((Entity) null, bounds, entity -> !entity.getPersistentData().getString(ENTITY_ID).isBlank())) {
            String key = managedKey(entity.getPersistentData().getString(ENTITY_ID), entity.getPersistentData().getString(ENTITY_KIND));
            if (!state.entityKeys().contains(key)) entity.discard();
        }
        ledger.releaseEntitiesExcept(state.entityKeys());
        Map<String, Entity> joined = joinedManagedEntities.get(level.dimension().location().toString());
        if (joined != null) joined.keySet().removeIf(key -> !state.entityKeys().contains(key));
    }

    void retireEntity(ServerLevel level, String id) {
        Entity existing = level.getEntity(uuid("resident", id));
        if (existing == null) existing = level.getEntity(uuid("bioform", id));
        if (existing != null && !existing.getPersistentData().getString(ENTITY_ID).isBlank()) existing.discard();
        FrontierGrayboxLedger ledger = FrontierGrayboxLedger.get(level);
        ledger.releaseEntity(managedKey(id, "RESIDENT"));
        ledger.releaseEntity(managedKey(id, "BIOFORM"));
    }

    private void configure(net.minecraft.world.entity.Mob entity, String id, String kind, String materializationId,
                           long revision, String name, net.minecraft.world.item.Item helmet, boolean visibleName) {
        entity.setPersistenceRequired();
        entity.setNoAi(true);
        entity.setCustomName(Component.literal(name));
        entity.setCustomNameVisible(visibleName);
        if (helmet != null) entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        attach(entity, id, kind, materializationId, revision);
    }

    private void configure(ArmorStand entity, String id, String kind, String materializationId,
                           long revision, String name, net.minecraft.world.item.Item helmet, boolean visibleName) {
        entity.setNoGravity(true);
        entity.setCustomName(Component.literal(name));
        entity.setCustomNameVisible(visibleName);
        if (helmet != null) entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        attach(entity, id, kind, materializationId, revision);
    }

    private static void attach(Entity entity, String id, String kind, String materializationId, long revision) {
        entity.getPersistentData().putString(ENTITY_ID, id);
        entity.getPersistentData().putString(ENTITY_KIND, kind);
        entity.getPersistentData().putString(MATERIALIZATION_ID, materializationId);
        entity.getPersistentData().putLong(REVISION, revision);
    }
    private static boolean managed(Entity entity, String id, String kind) {
        return entity.getPersistentData().getString(ENTITY_ID).equals(id) && entity.getPersistentData().getString(ENTITY_KIND).equals(kind);
    }
    private static String managedKey(String id, String kind) { return kind + ":" + id; }
    private void queue(ServerLevel level, FrontierPhysicalObservation.Type type, String subjectId,
                       String materializationId, long revision, String causationId) {
        if (subjectId.isBlank() || materializationId.isBlank()) return;
        String observationId = "frontier:physical:" + type + ":" + subjectId + ":" + causationId;
        LinkedHashMap<String, FrontierPhysicalObservation> pending = observations.computeIfAbsent(
                level.dimension().location().toString(), ignored -> new LinkedHashMap<>());
        pending.putIfAbsent(observationId, new FrontierPhysicalObservation(type, observationId, subjectId,
                materializationId, revision, causationId));
        while (pending.size() > MAX_PENDING_OBSERVATIONS) pending.remove(pending.firstEntry().getKey());
    }
    static boolean flatAt(ServerLevel level, BlockPos position) { return FrontierGrayboxProjectionSupport.flatAt(level, position); }
    static BlockPos bioformPosition(FrontierProjection.Hive hive, int index) { return FrontierGrayboxProjectionSupport.bioformPosition(hive, index); }
    static BlockPos bioformPosition(int cellX, int cellZ, int index) { return FrontierGrayboxProjectionSupport.bioformPosition(cellX, cellZ, index); }
    static int cellX(int cellX) { return FrontierGrayboxProjectionSupport.cellX(cellX); }
    static int cellZ(int cellZ) { return FrontierGrayboxProjectionSupport.cellZ(cellZ); }
    private static UUID uuid(String kind, String id) { return UUID.nameUUIDFromBytes(("pale-mirror-frontier:" + kind + ":" + id).getBytes(StandardCharsets.UTF_8)); }
    record StructureBinding(String subjectId, String kind, String materializationId, long revision) { }
    record ProjectionState(FrontierProjection projection, Map<Long, StructureBinding> blocks, Set<String> entityKeys,
                           Map<String, Entity> managedEntities) { }
}
