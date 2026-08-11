package io.farfrontier.palemirror.internal.integration.vanilla;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * Isolated vanilla physical profile for early regional freight. It owns block
 * choices and pre/postconditions; generic campaign code only sees a bounded
 * write set and provenance signatures.
 */
public final class VanillaMinecartRailAdapter implements IntegrationAdapter {
    private static final int POWERED_RAIL_INTERVAL = 12;
    private static final int CLEARANCE_HEIGHT = 3;

    @Override
    public String id() { return "pale_mirror:vanilla_minecart_rail"; }

    @Override
    public AdapterHealth health() {
        return new AdapterHealth(AdapterHealth.Status.AVAILABLE,
                "Built-in PM-owned vanilla minecart corridor", Set.of(Capability.VANILLA_MINECART_ROUTE));
    }

    /** Plans one straight, grade-safe rail cell without touching unloaded chunks. */
    public VanillaMinecartSegmentPlan planSegment(ServerLevel level, BlockPos rail, Direction direction,
                                                  int nextRailY, int previousRailY, int index,
                                                  boolean receivingTerminal) {
        if (!direction.getAxis().isHorizontal()) throw new IllegalArgumentException("Minecart direction must be horizontal");
        if (!level.hasChunkAt(rail)) throw new IllegalStateException("Minecart segment chunk is not loaded at " + rail.toShortString());
        boolean powered = index > 0 && index % POWERED_RAIL_INTERVAL == 0;
        Map<BlockPos, BlockState> writes = new LinkedHashMap<>();
        BlockPos support = rail.below();
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                rail.getX(), rail.getZ());
        for (int y = surfaceY; y < support.getY(); y++) writes.put(new BlockPos(rail.getX(), y, rail.getZ()),
                Blocks.OAK_FENCE.defaultBlockState());
        writes.put(support, powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
        for (int y = 0; y < CLEARANCE_HEIGHT; y++) writes.put(rail.above(y), Blocks.AIR.defaultBlockState());
        writes.put(rail, railState(direction, powered, rail.getY(), nextRailY, previousRailY));
        if (receivingTerminal) appendReceivingPlatform(writes, rail, direction);
        return new VanillaMinecartSegmentPlan(rail, writes);
    }

    /** Initial construction accepts ordinary terrain but refuses irreplaceable or stateful blocks. */
    public boolean protectedInitialCell(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return state.getDestroySpeed(level, position) < 0.0F || state.hasBlockEntity();
    }

    public boolean postcondition(ServerLevel level, VanillaMinecartSegmentPlan plan) {
        return postconditionDiagnostic(level, plan).isBlank();
    }

    public String postconditionDiagnostic(ServerLevel level, VanillaMinecartSegmentPlan plan) {
        for (Map.Entry<BlockPos, BlockState> entry : plan.writes().entrySet()) {
            BlockState observed = level.getBlockState(entry.getKey());
            if (entry.getKey().equals(plan.railPosition())) {
                if (!observed.is(Blocks.RAIL) && !observed.is(Blocks.POWERED_RAIL)) {
                    return "rail=" + observed + " at " + entry.getKey().toShortString();
                }
            } else if (entry.getValue().is(Blocks.OAK_FENCE) ? !observed.is(Blocks.OAK_FENCE) : !observed.equals(entry.getValue())) {
                return "expected=" + entry.getValue() + ", observed=" + observed + " at " + entry.getKey().toShortString();
            }
        }
        return "";
    }

    /** Rails may legitimately recompute their shape when an adjacent authored cell arrives. */
    public boolean matchesProvenance(BlockState observed, String lastAppliedState) {
        if (lastAppliedState.contains("minecraft:powered_rail")) return observed.is(Blocks.POWERED_RAIL);
        if (lastAppliedState.contains("minecraft:rail")) return observed.is(Blocks.RAIL);
        if (lastAppliedState.contains("minecraft:oak_fence")) return observed.is(Blocks.OAK_FENCE);
        return signature(observed).equals(lastAppliedState);
    }

    public String signature(BlockState state) { return state.toString(); }

    public boolean criticalInfrastructure(String appliedState) {
        return appliedState.contains("minecraft:rail") || appliedState.contains("minecraft:powered_rail")
                || appliedState.contains("minecraft:redstone_block") || appliedState.contains("minecraft:gravel")
                || appliedState.contains("minecraft:oak_fence");
    }

    /** Read-only topology observation; no provenance or ownership is implied. */
    public RailShape observedShape(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof BaseRailBlock rail)) return null;
        return rail.getRailDirection(state, level, position, null);
    }

    /** Creates exactly one non-canonical visual carrier after a persisted PM effect lease authorizes it. */
    public VisualCart spawnRepresentativeCart(ServerLevel level, BlockPos rail, String routeId) {
        if (!level.hasChunkAt(rail)) throw new IllegalStateException("Minecart start chunk is not loaded");
        Entity cart = EntityType.MINECART.create(level);
        if (cart == null) throw new IllegalStateException("Vanilla chest minecart type is unavailable");
        cart.moveTo(rail.getX() + 0.5D, rail.getY() + 0.1D, rail.getZ() + 0.5D, 0.0F, 0.0F);
        cart.setNoGravity(true);
        cart.setInvulnerable(true);
        cart.setDeltaMovement(Vec3.ZERO);
        cart.setCustomName(Component.literal("Iron freight — Pale Mirror"));
        cart.getPersistentData().putString("pale_mirror_route", routeId);
        cart.getPersistentData().putString("pale_mirror_role", "representative_minecart");
        if (!level.addFreshEntity(cart)) throw new IllegalStateException("Vanilla minecart spawn was rejected");
        stabilizeRepresentativeCart(cart, rail);
        Display.BlockDisplay cargo = createRepresentativeCargo(level, cart, routeId);
        return new VisualCart(cart.getUUID(), cargo.getUUID());
    }

    /**
     * Selects one already-loaded carrier for this route and removes every
     * duplicate or orphan visual. Unloaded entities are never guessed absent;
     * they will be reconciled when their chunk becomes naturally loaded.
     */
    public RepresentativeReconciliation reconcileRepresentatives(ServerLevel level, String routeId,
                                                                  UUID preferredCartId, UUID preferredCargoId) {
        List<Entity> carts = new ArrayList<>();
        List<Entity> cargos = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!routeId.equals(entity.getPersistentData().getString("pale_mirror_route"))) continue;
            String role = entity.getPersistentData().getString("pale_mirror_role");
            if ("representative_minecart".equals(role)) carts.add(entity);
            else if ("representative_cargo".equals(role)) cargos.add(entity);
        }
        Comparator<Entity> byIdentity = Comparator.comparing(entity -> entity.getUUID().toString());
        carts.sort(byIdentity);
        cargos.sort(byIdentity);

        Entity keeper = carts.stream().filter(entity -> entity.getUUID().equals(preferredCartId)).findFirst()
                .orElseGet(() -> preferredCargoId == null ? null : carts.stream()
                        .filter(entity -> entity.getPassengers().stream()
                                .anyMatch(passenger -> passenger.getUUID().equals(preferredCargoId)))
                        .findFirst().orElse(null));
        if (keeper == null && !carts.isEmpty()) keeper = carts.getFirst();
        if (keeper == null) {
            int removed = cargos.size();
            cargos.forEach(Entity::discard);
            return new RepresentativeReconciliation(null, removed);
        }

        Entity selectedCart = keeper;
        Entity cargo = cargos.stream().filter(entity -> entity.getUUID().equals(preferredCargoId)
                        && entity.getVehicle() == selectedCart).findFirst()
                .orElseGet(() -> selectedCart.getPassengers().stream()
                        .filter(passenger -> "representative_cargo".equals(
                                passenger.getPersistentData().getString("pale_mirror_role")))
                        .findFirst().orElse(null));
        int removed = 0;
        for (Entity duplicate : carts) if (duplicate != selectedCart) {
            duplicate.discard();
            removed++;
        }
        for (Entity orphan : cargos) if (orphan != cargo) {
            orphan.discard();
            removed++;
        }
        if (cargo == null) cargo = createRepresentativeCargo(level, selectedCart, routeId);
        else if (cargo.getVehicle() != selectedCart && !cargo.startRiding(selectedCart, true)) {
            cargo.discard();
            cargo = createRepresentativeCargo(level, selectedCart, routeId);
            removed++;
        }
        return new RepresentativeReconciliation(new VisualCart(selectedCart.getUUID(), cargo.getUUID()), removed);
    }

    /** Applies the visual carrier's authoritative, non-physical pose every tick. */
    public void stabilizeRepresentativeCart(Entity cart, BlockPos rail) {
        stabilizeRepresentativeCart(cart, rail.getX() + 0.5D, rail.getY() + 0.1D, rail.getZ() + 0.5D);
    }

    public void stabilizeRepresentativeCart(Entity cart, double x, double y, double z) {
        cart.setNoGravity(true);
        cart.setInvulnerable(true);
        cart.noPhysics = true;
        cart.setDeltaMovement(Vec3.ZERO);
        cart.fallDistance = 0.0F;
        cart.moveTo(x, y, z, cart.getYRot(), cart.getXRot());
    }

    private Display.BlockDisplay createRepresentativeCargo(ServerLevel level, Entity cart, String routeId) {
        Display.BlockDisplay cargo = EntityType.BLOCK_DISPLAY.create(level);
        if (cargo == null) {
            cart.discard();
            throw new IllegalStateException("Vanilla block display type is unavailable");
        }
        CompoundTag displayData = new CompoundTag();
        displayData.put("block_state", NbtUtils.writeBlockState(Blocks.CHEST.defaultBlockState()));
        cargo.load(displayData);
        cargo.setInvulnerable(true);
        cargo.noPhysics = true;
        cargo.getPersistentData().putString("pale_mirror_route", routeId);
        cargo.getPersistentData().putString("pale_mirror_role", "representative_cargo");
        cargo.moveTo(cart.getX(), cart.getY() + 0.35D, cart.getZ(), 0F, 0F);
        if (!level.addFreshEntity(cargo) || !cargo.startRiding(cart, true)) {
            cargo.discard();
            cart.discard();
            throw new IllegalStateException("Vanilla representative cargo spawn was rejected");
        }
        return cargo;
    }

    public static boolean isRepresentative(Entity entity) {
        return "representative_minecart".equals(entity.getPersistentData().getString("pale_mirror_role"))
                || "representative_cargo".equals(entity.getPersistentData().getString("pale_mirror_role"));
    }

    public record VisualCart(java.util.UUID cartId, java.util.UUID cargoId) { }
    public record RepresentativeReconciliation(VisualCart keeper, int removedEntities) { }

    private static BlockState railState(Direction direction, boolean powered, int railY, int nextRailY, int previousRailY) {
        RailShape shape = shape(direction, railY, nextRailY, previousRailY);
        return powered ? Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape)
                : Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
    }

    private static RailShape shape(Direction direction, int railY, int nextRailY, int previousRailY) {
        boolean nextHigher = nextRailY > railY;
        boolean previousHigher = previousRailY > railY;
        if (nextHigher) return ascending(direction);
        if (previousHigher) return ascending(direction.getOpposite());
        return direction.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
    }

    private static RailShape ascending(Direction direction) {
        return switch (direction) {
            case NORTH -> RailShape.ASCENDING_NORTH;
            case SOUTH -> RailShape.ASCENDING_SOUTH;
            case EAST -> RailShape.ASCENDING_EAST;
            case WEST -> RailShape.ASCENDING_WEST;
            default -> throw new IllegalArgumentException("Rail direction must be horizontal");
        };
    }

    /**
     * A deliberately modest receiving site: no container or stock is created
     * here. The platform is only a readable physical endpoint for the
     * abstract RouteContract; the separate PM depot remains the item boundary.
     */
    private static void appendReceivingPlatform(Map<BlockPos, BlockState> writes, BlockPos rail, Direction direction) {
        Direction side = direction.getClockWise();
        BlockPos deck = rail.below();
        for (int width = -2; width <= 2; width++) {
            for (int length = 0; length <= 1; length++) {
                BlockPos position = deck.relative(side, width).relative(direction.getOpposite(), length);
                if (!position.equals(deck)) writes.put(position, Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
        BlockPos buffer = rail.relative(direction);
        writes.put(buffer, Blocks.OAK_FENCE.defaultBlockState());
        writes.put(buffer.above(), Blocks.LANTERN.defaultBlockState());
    }
}
