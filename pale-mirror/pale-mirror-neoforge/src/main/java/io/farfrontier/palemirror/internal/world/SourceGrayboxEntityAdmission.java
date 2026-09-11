package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Custody boundary for the disposable source-graybox dimension.
 *
 * <p>The source simulation owns every non-player living actor represented in
 * this dimension.  Allowing ambient or mod-native mobs to enter would let an
 * unrelated Minecraft combat loop delete canonical residents or bioforms.
 * The source materializer attaches deterministic identity before admitting
 * its own Villagers and Zombies, so those exact carriers remain allowed.</p>
 */
public final class SourceGrayboxEntityAdmission {
    private SourceGrayboxEntityAdmission() { }

    /** True when a non-source mob must be denied before it can affect the source projection. */
    public static boolean rejects(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level");
        return rejects(level, entity, FrontierV3ServerLifecycle.observeSourceJoin(level, entity));
    }

    /** The exact lifecycle/firewall composition used by the ordinary EntityJoinLevelEvent. */
    public static boolean rejects(ServerLevel level, Entity entity, FrontierV3ServerLifecycle.JoinFirewallProof proof) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(entity, "entity"); Objects.requireNonNull(proof, "join proof");
        return rejectsSourceMob(proof, level.dimension().equals(SourceGrayboxWorldBoundary.DIMENSION),
                SourceGrayboxMaterializer.recognizesManagedEntity(entity));
    }

    static boolean rejects(ResourceKey<Level> dimension, Entity entity) {
        return rejects(dimension, entity, false);
    }

    /** Package-visible seam: the event bridge supplies only a strict canonical V3 proof. */
    static boolean rejects(ResourceKey<Level> dimension, Entity entity, boolean verifiedV3Carrier) {
        Objects.requireNonNull(dimension, "dimension"); Objects.requireNonNull(entity, "entity");
        return entity instanceof Mob && rejectsSourceMob(dimension.equals(SourceGrayboxWorldBoundary.DIMENSION),
                SourceGrayboxMaterializer.recognizesManagedEntity(entity), verifiedV3Carrier);
    }

    /**
     * Exact firewall decision used by the Entity callback after it has established mob/source
     * facts.  A retained V3 proof must pass this same gate; otherwise an unindexed restored body
     * could be canceled after its lifecycle join was accepted.
     */
    public static boolean rejectsSourceMob(boolean sourceDimension, boolean sourceManaged, boolean verifiedV3Carrier) {
        return sourceDimension && !sourceManaged && !verifiedV3Carrier;
    }

    /** Carrier-test delegate of the production composition above; its proof is never caller-supplied. */
    public static boolean rejectsSourceMob(FrontierV3ServerLifecycle.JoinFirewallProof proof,
                                           boolean sourceDimension, boolean sourceManaged) {
        Objects.requireNonNull(proof, "join proof");
        return proof.lifecycleAdmission() == FrontierV3ServerLifecycle.EntityJoinAdmission.DUPLICATE_UNINDEXED
                || rejectsSourceMob(sourceDimension, sourceManaged, proof.verifiedV3Carrier());
    }
}
