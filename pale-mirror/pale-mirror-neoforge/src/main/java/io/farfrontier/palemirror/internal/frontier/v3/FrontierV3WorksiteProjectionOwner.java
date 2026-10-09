package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Optional;

/** Owning family supplies cells and authority; the common adapter owns only physical writes. */
interface FrontierV3WorksiteProjectionOwner {
    CellMutationKey.OwnerFamily family();
    List<WorksiteBlock> declarations(FrontierWorldState state);
    WorksiteBlock current(FrontierWorldState state, WorksiteBlock declared);
    void beforeTurn(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime);
    Optional<FrontierV3WorksiteBlockWitness> committedEffect(FrontierWorldState state, FrontierV3WorksiteBlockWitness witness);
    boolean effectResumption(FrontierWorldState state, WorksiteBlock cell, FrontierV3WorksiteBlockWitness witness,
            io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block actual);
    boolean beforeProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, WorksiteBlock declared);
    void afterProjection(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime);
    void departure(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, net.minecraft.world.level.chunk.LevelChunk chunk);
    void shutdown(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime);
}
