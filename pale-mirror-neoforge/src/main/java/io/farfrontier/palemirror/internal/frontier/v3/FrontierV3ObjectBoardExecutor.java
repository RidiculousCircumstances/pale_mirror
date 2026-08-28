package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.math.Transformation;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.model.FrontierObjectBoard;
import io.farfrontier.palemirror.frontier.v3.model.FrontierReadabilityPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded loaded-chunk materializer for the pure v3 object-local board plan. */
final class FrontierV3ObjectBoardExecutor {
    private static final int MAX_BOARDS_PER_TICK = 8;
    private static final String OWNER_KEY = "pale_mirror.frontier_v3.board_owner";
    private static final Map<FrontierV3ServerRuntime<?, ?>, Cursor> CURSORS = new IdentityHashMap<>();

    enum ProjectionResult { APPLIED, CURRENT, UPDATED, CONFLICT, DEFERRED }

    private FrontierV3ObjectBoardExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElse(null);
        if (checkpoint == null) return;
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null || !cursor.revision().equals(checkpoint.revision())) {
            FrontierReadabilityPlan plan = FrontierReadabilityPlan.compile(runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive")));
            cursor = new Cursor(checkpoint.revision(), plan.boards().values().stream().sorted(Comparator.comparing(value -> value.ownerId().value())).toList());
            CURSORS.put(runtime, cursor);
        }
        FrontierV3ObjectBoardLedger ledger = FrontierV3ObjectBoardLedger.get(level);
        for (int count = 0; count < MAX_BOARDS_PER_TICK && cursor.hasNext(); count++) project(level, ledger, cursor.next());
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

    static ProjectionResult project(ServerLevel level, FrontierV3ObjectBoardLedger ledger, FrontierObjectBoard board) {
        BlockPos position = position(board);
        if (!level.hasChunkAt(position)) return ProjectionResult.DEFERRED;
        String owner = board.ownerId().value(); UUID uuid = uuid(owner); FrontierV3ObjectBoardLedger.Claim claim = ledger.claim(owner);
        if (claim != null && (claim.conflicted() || claim.position() != position.asLong() || !claim.uuid().equals(uuid.toString()))) return ProjectionResult.CONFLICT;
        Display.TextDisplay display = level.getEntity(uuid) instanceof Display.TextDisplay known ? known : null;
        if (claim != null && display == null) { ledger.conflict(owner); return ProjectionResult.CONFLICT; }
        if (display != null && (!owner.equals(display.getPersistentData().getString(OWNER_KEY)) || !display.blockPosition().equals(position))) {
            if (claim != null) ledger.conflict(owner);
            return ProjectionResult.CONFLICT;
        }
        if (display == null) {
            display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level); display.setUUID(uuid); configure(display, board); display.setPos(Vec3.atBottomCenterOf(position));
            // Claim before adding the presentation entity. A crash or failed admission therefore
            // becomes an inspectable missing-board conflict rather than authority to retry over
            // an unknown world entity.
            ledger.applied(owner, position.asLong(), uuid.toString());
            return level.addFreshEntity(display) ? ProjectionResult.APPLIED : ProjectionResult.DEFERRED;
        }
        String expected = board.text();
        if (expected.equals(display.getCustomName() == null ? null : display.getCustomName().getString())) return ProjectionResult.CURRENT;
        configure(display, board); return ProjectionResult.UPDATED;
    }

    private static BlockPos position(FrontierObjectBoard board) { return new BlockPos(board.position().x(), board.position().y(), board.position().z()); }
    private static UUID uuid(String owner) { return UUID.nameUUIDFromBytes(("pale-mirror-frontier-v3-board:" + owner).getBytes(StandardCharsets.UTF_8)); }
    private static void configure(Display.TextDisplay display, FrontierObjectBoard board) {
        CompoundTag data = display.saveWithoutId(new CompoundTag());
        Component text = Component.literal(board.text()).withStyle(colour(board.tone()), ChatFormatting.BOLD);
        data.putString(Display.TextDisplay.TAG_TEXT, Component.Serializer.toJson(text, display.registryAccess()));
        data.putInt("line_width", 256); data.putByte("text_opacity", (byte) 0xFF); data.putInt("background", 0xE0000000);
        data.putBoolean("shadow", true); data.putBoolean("see_through", true); data.putString("alignment", "center"); data.putFloat("view_range", 1.5F);
        data.putFloat("width", 16.0F); data.putFloat("height", 4.0F); data.putInt("glow_color_override", glow(board.tone())); data.putBoolean("Glowing", true);
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.25F), new Quaternionf()))
                .ifSuccess(value -> data.put("transformation", value));
        Display.BillboardConstraints.CODEC.encodeStart(NbtOps.INSTANCE, Display.BillboardConstraints.CENTER).ifSuccess(value -> data.put("billboard", value));
        Brightness.CODEC.encodeStart(NbtOps.INSTANCE, Brightness.FULL_BRIGHT).ifSuccess(value -> data.put("brightness", value));
        display.load(data); display.setNoGravity(true); display.setCustomName(Component.literal(board.text())); display.setCustomNameVisible(false);
        display.getPersistentData().putString(OWNER_KEY, board.ownerId().value());
    }
    private static ChatFormatting colour(FrontierObjectBoard.Tone tone) {
        return switch (tone) { case SETTLEMENT -> ChatFormatting.GOLD; case HIVE -> ChatFormatting.LIGHT_PURPLE; case WARNING -> ChatFormatting.RED; };
    }
    private static int glow(FrontierObjectBoard.Tone tone) {
        return switch (tone) { case SETTLEMENT -> 0xFFAA00; case HIVE -> 0xD77CFF; case WARNING -> 0xFF5555; };
    }
    private static final class Cursor {
        private final Revision revision;
        private final List<FrontierObjectBoard> boards;
        private int index;
        Cursor(Revision revision, List<FrontierObjectBoard> boards) { this.revision = revision; this.boards = boards; }
        Revision revision() { return revision; }
        boolean hasNext() { return !boards.isEmpty(); }
        FrontierObjectBoard next() { FrontierObjectBoard value = boards.get(index); index = (index + 1) % boards.size(); return value; }
    }
}
