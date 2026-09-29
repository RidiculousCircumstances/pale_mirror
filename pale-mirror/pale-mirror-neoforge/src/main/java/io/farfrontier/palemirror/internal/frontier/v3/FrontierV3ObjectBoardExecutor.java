package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.math.Transformation;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.model.FrontierObjectBoard;
import io.farfrontier.palemirror.frontier.v3.model.FrontierReadabilityPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrgan;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
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
        // Text displays are player-facing explanation only.  In an empty COLD dimension they
        // have no observer and no authority over canonical progress, actor custody, or physical
        // geometry.  Deferring their derived-plan refresh until this exact level has a player
        // prevents a long COLD fast-forward from repeatedly JIT-compiling presentation work;
        // the current canonical board is still materialized in place on the first observation.
        if (level.players().isEmpty()) return;
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElse(null);
        if (checkpoint == null) return;
        FrontierWorldState state = runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        FrontierReadabilityPlan.ReadabilityInput input = FrontierReadabilityPlan.input(state);
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null || !cursor.input().matchesStableBaseline(input)) {
            FrontierReadabilityPlan baseline = FrontierReadabilityPlan.compileStableBaseline(state);
            FrontierReadabilityPlan dynamicHive = FrontierReadabilityPlan.compileDynamicHiveOverlay(state, 256 - baseline.boards().size());
            cursor = Cursor.from(input, sorted(baseline), sorted(dynamicHive), cursor);
            CURSORS.put(runtime, cursor);
        } else if (!cursor.input().matchesDynamicHiveOverlay(input)) {
            FrontierReadabilityPlan dynamicHive = FrontierReadabilityPlan.compileDynamicHiveOverlay(state, cursor.dynamicHiveCapacity());
            cursor = cursor.withDynamicHiveOverlay(input, sorted(dynamicHive));
            CURSORS.put(runtime, cursor);
        }
        FrontierV3ObjectBoardLedger ledger = FrontierV3ObjectBoardLedger.get(level);
        for (int count = 0; count < MAX_BOARDS_PER_TICK && cursor.hasNext(); count++) project(level, ledger, runtime, state, cursor.next());
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

    private static List<FrontierObjectBoard> sorted(FrontierReadabilityPlan plan) {
        return plan.boards().values().stream().sorted(Comparator.comparing(value -> value.ownerId().value())).toList();
    }

    static ProjectionResult project(ServerLevel level, FrontierV3ObjectBoardLedger ledger, FrontierObjectBoard board) {
        BlockPos position = position(board);
        // A loaded block column is not evidence that its saved display entities
        // have arrived. Neither declare disappearance nor create a duplicate
        // before the shared entity-storage boundary is ready.
        if (!FrontierV3SceneExecutor.entityStorageReady(level, position)) return ProjectionResult.DEFERRED;
        String owner = board.ownerId().value(); UUID uuid = uuid(owner); FrontierV3ObjectBoardLedger.Claim claim = ledger.claim(owner);
        if (claim != null && (claim.position() != position.asLong() || !claim.uuid().equals(uuid.toString()))) return ProjectionResult.CONFLICT;
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
        if (claim != null) ledger.observeExactReturn(owner, position.asLong(), uuid.toString());
        String expected = board.text();
        if (expected.equals(display.getCustomName() == null ? null : display.getCustomName().getString())) return ProjectionResult.CURRENT;
        configure(display, board); return ProjectionResult.UPDATED;
    }

    /** Production-only first-visibility gate; the fixture overload remains object-generic. */
    private static ProjectionResult project(ServerLevel level, FrontierV3ObjectBoardLedger ledger,
                                            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                            FrontierObjectBoard board) {
        boolean coherentHive = !isHiveOrgan(state, board.ownerId()) || FrontierV3GrayboxExecutor.publishedHiveExpectations(runtime)
                .flatMap(expectations -> FrontierV3InfectionOverlayExecutor.publishedOverlay(runtime)
                        .map(overlay -> FrontierV3HiveFoundryAudit.runtimeNestCoherence(board.ownerId(), level, expectations, overlay)
                        == FrontierV3HiveFoundryAudit.RuntimeCoherence.CURRENT)
                )
                .orElse(false);
        if (isHiveOrgan(state, board.ownerId()) && !coherentHive) {
            // A legacy board can already be present when an upgraded world first exposes its
            // hibernaculum. Keep the same owned display identity but make its non-interactable
            // blocked state explicit; silently retaining ACTIVE would advertise a usable organ
            // over missing or foreign geometry.
            return project(level, ledger, blockedHiveBoard(board));
        }
        if (state.resourceSiteDescriptors().containsKey(board.ownerId())
                && state.resourceSites().site(board.ownerId()).phase() == ResourceSitePhase.READY) {
            var claim = FrontierV3ResourceSiteLedger.get(level).fieldClaim(board.ownerId());
            if (claim == null || claim instanceof FrontierV3ResourceSiteLedger.FieldInitialization) {
                return project(level, ledger, fieldProjectionBoard(board, "FIELD MATERIALIZING"));
            }
            var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) claim;
            if (owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) {
                return project(level, ledger, fieldProjectionBoard(board, "FIELD PHYSICAL CONFLICT"));
            }
            var cycle = state.resourceSites().cycle(board.ownerId());
            if (!owner.witness().matchesCycle(cycle) || cycle.layout().cells().stream().anyMatch(cell -> {
                var physical = owner.witness().cell(cell.id());
                return physical.pending().isPresent() || physical.foreign().isPresent()
                        || !physical.committed().equals(ResourceFieldPhysicalSurface.Condition.of(cycle.cell(cell.id())));
            })) {
                return project(level, ledger, fieldProjectionBoard(board, "CROPS UPDATING"));
            }
        }
        return project(level, ledger, board);
    }

    static FrontierObjectBoard fieldProjectionBoard(FrontierObjectBoard board, String status) {
        String settlement = board.text().split("\\n", -1)[0];
        return new FrontierObjectBoard(board.ownerId(), board.position(), FrontierObjectBoard.Tone.WARNING,
                board.scope(), settlement + "\nWHEAT FIELD\n" + status);
    }

    private static boolean isHiveOrgan(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .map(HiveOrgan::id).anyMatch(owner::equals);
    }

    private static FrontierObjectBoard blockedHiveBoard(FrontierObjectBoard board) {
        String[] lines = board.text().split("\\n", -1);
        String kind = lines.length > 1 ? lines[1] : "ORGAN";
        return new FrontierObjectBoard(board.ownerId(), board.position(), FrontierObjectBoard.Tone.WARNING, board.scope(),
                "HIVE\\n" + kind + "\\nBLOCKED · ORGAN NOT CURRENT");
    }

    /**
     * Confirms that a clicked display is the currently claimed materialization of this exact
     * canonical board.  A custom-named lookalike must never become a presentation authority.
     */
    static boolean isCurrentOwnedBoard(ServerLevel level, Entity entity, FrontierObjectBoard board) {
        if (!(entity instanceof Display.TextDisplay display) || !level.hasChunkAt(display.blockPosition())) return false;
        String owner = board.ownerId().value();
        FrontierV3ObjectBoardLedger.Claim claim = FrontierV3ObjectBoardLedger.get(level).claim(owner);
        return claim != null && !claim.conflicted() && claim.position() == position(board).asLong()
                && claim.uuid().equals(uuid(owner).toString()) && display.getUUID().equals(uuid(owner))
                && display.blockPosition().equals(position(board))
                && owner.equals(display.getPersistentData().getString(OWNER_KEY));
    }

    private static BlockPos position(FrontierObjectBoard board) { return new BlockPos(board.position().x(), board.position().y(), board.position().z()); }
    private static UUID uuid(String owner) { return UUID.nameUUIDFromBytes(("pale-mirror-frontier-v3-board:" + owner).getBytes(StandardCharsets.UTF_8)); }
    private static void configure(Display.TextDisplay display, FrontierObjectBoard board) {
        CompoundTag data = display.saveWithoutId(new CompoundTag());
        Component text = Component.literal(board.text()).withStyle(colour(board.tone()), ChatFormatting.BOLD);
        data.putString(Display.TextDisplay.TAG_TEXT, Component.Serializer.toJson(text, display.registryAccess()));
        BoardRenderProfile profile = BoardRenderProfile.forScope(board.scope());
        data.putInt("line_width", profile.lineWidth()); data.putByte("text_opacity", (byte) 0xFF); data.putInt("background", 0xB0000000);
        // Object boards belong to an object in physical space. Rendering through its building
        // makes remote local facts overlap and falsely look like a global HUD.
        data.putBoolean("shadow", true); data.putBoolean("see_through", false); data.putString("alignment", "center"); data.putFloat("view_range", profile.viewRange());
        data.putFloat("width", profile.width()); data.putFloat("height", profile.height()); data.putInt("glow_color_override", glow(board.tone())); data.putBoolean("Glowing", true);
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(profile.scale()), new Quaternionf()))
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
    /** Compact physical board grammar.  Display view range is expressed in 64-block units. */
    private record BoardRenderProfile(int lineWidth, float viewRange, float width, float height, float scale) {
        static BoardRenderProfile forScope(FrontierObjectBoard.Scope scope) {
            return switch (scope) {
                case LANDMARK -> new BoardRenderProfile(176, 1.50F, 8.0F, 3.0F, 0.95F);
                case LOCAL -> new BoardRenderProfile(144, 0.70F, 6.0F, 2.5F, 0.70F);
            };
        }
    }
    /**
     * A canonical revision is not a board epoch.  The simulation can advance a moving actor
     * position more quickly than the loaded-world budget can inspect every board.  Keep the
     * round-robin position whenever the exact board dependencies and stable slots are unchanged,
     * otherwise remote settlement work could permanently starve an already-loaded later hive
     * board.
     */
    static final class Cursor {
        private final FrontierReadabilityPlan.ReadabilityInput input;
        private final List<FrontierObjectBoard> baselineBoards;
        private final List<FrontierObjectBoard> dynamicHiveBoards;
        private int baselineIndex;
        private int dynamicHiveIndex;
        private boolean baselineNext;

        Cursor(FrontierReadabilityPlan.ReadabilityInput input, List<FrontierObjectBoard> baselineBoards, List<FrontierObjectBoard> dynamicHiveBoards,
               int baselineIndex, int dynamicHiveIndex, boolean baselineNext) {
            this.input = input; this.baselineBoards = baselineBoards; this.dynamicHiveBoards = dynamicHiveBoards;
            this.baselineIndex = baselineIndex; this.dynamicHiveIndex = dynamicHiveIndex; this.baselineNext = baselineNext;
        }
        static Cursor from(FrontierReadabilityPlan.ReadabilityInput input, List<FrontierObjectBoard> boards, Cursor prior) {
            return from(input, boards, List.of(), prior);
        }
        static Cursor from(FrontierReadabilityPlan.ReadabilityInput input, List<FrontierObjectBoard> baselineBoards,
                           List<FrontierObjectBoard> dynamicHiveBoards, Cursor prior) {
            int baselineIndex = prior != null && sameSlots(prior.baselineBoards, baselineBoards)
                    ? prior.baselineIndex % Math.max(1, baselineBoards.size()) : 0;
            int dynamicHiveIndex = prior != null && sameSlots(prior.dynamicHiveBoards, dynamicHiveBoards)
                    ? prior.dynamicHiveIndex % Math.max(1, dynamicHiveBoards.size()) : 0;
            return new Cursor(input, baselineBoards, dynamicHiveBoards, baselineIndex, dynamicHiveIndex,
                    prior == null || prior.baselineNext);
        }
        Cursor withDynamicHiveOverlay(FrontierReadabilityPlan.ReadabilityInput input, List<FrontierObjectBoard> dynamicHiveBoards) {
            int next = sameSlots(this.dynamicHiveBoards, dynamicHiveBoards) ? dynamicHiveIndex % Math.max(1, dynamicHiveBoards.size()) : 0;
            return new Cursor(input, baselineBoards, dynamicHiveBoards, baselineIndex, next, baselineNext);
        }
        /** Test-only slot-order probe; production cursors always retain an exact readability input. */
        static Cursor from(Revision ignored, List<FrontierObjectBoard> boards, Cursor prior) {
            return from((FrontierReadabilityPlan.ReadabilityInput) null, boards, prior);
        }
        FrontierReadabilityPlan.ReadabilityInput input() { return input; }
        int dynamicHiveCapacity() { return 256 - baselineBoards.size(); }
        boolean hasNext() { return !baselineBoards.isEmpty() || !dynamicHiveBoards.isEmpty(); }
        FrontierObjectBoard next() {
            if (baselineBoards.isEmpty()) return nextDynamicHive();
            if (dynamicHiveBoards.isEmpty()) return nextBaseline();
            return baselineNext ? nextBaseline() : nextDynamicHive();
        }
        private FrontierObjectBoard nextBaseline() {
            FrontierObjectBoard value = baselineBoards.get(baselineIndex);
            baselineIndex = (baselineIndex + 1) % baselineBoards.size(); baselineNext = false;
            return value;
        }
        private FrontierObjectBoard nextDynamicHive() {
            FrontierObjectBoard value = dynamicHiveBoards.get(dynamicHiveIndex);
            dynamicHiveIndex = (dynamicHiveIndex + 1) % dynamicHiveBoards.size(); baselineNext = true;
            return value;
        }
        private static boolean sameSlots(List<FrontierObjectBoard> prior, List<FrontierObjectBoard> next) {
            if (prior.size() != next.size()) return false;
            for (int index = 0; index < prior.size(); index++) {
                FrontierObjectBoard left = prior.get(index); FrontierObjectBoard right = next.get(index);
                if (!left.ownerId().equals(right.ownerId()) || !left.position().equals(right.position())) return false;
            }
            return true;
        }
    }
}
