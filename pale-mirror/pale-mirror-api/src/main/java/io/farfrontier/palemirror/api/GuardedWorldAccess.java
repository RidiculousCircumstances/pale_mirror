package io.farfrontier.palemirror.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Only mutation surface exposed to visual executors for long-lived block work. */
public interface GuardedWorldAccess {
    Result setBlock(SemanticSlotKey slot, BlockPos position, BlockState desired, int flags);

    record Result(Status status, String diagnostic) {
        public static Result applied() { return new Result(Status.APPLIED, ""); }
        public static Result unchanged() { return new Result(Status.UNCHANGED, ""); }
        public static Result blocked(String diagnostic) { return new Result(Status.BLOCKED, diagnostic); }
    }

    enum Status { APPLIED, UNCHANGED, BLOCKED }
}
