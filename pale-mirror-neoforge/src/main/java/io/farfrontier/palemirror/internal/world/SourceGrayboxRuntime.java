package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Server-thread scheduler and SavedData owner for the source-parity graybox. */
public final class SourceGrayboxRuntime {
    private static final long DAY_INTERVAL_TICKS = 1_200L;
    private static final int MAXIMUM_CATCH_UP_DAYS = 24;
    private static final Map<MinecraftServer, SourceGrayboxRuntime> INSTANCES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final SourceGrayboxSavedData data;

    private SourceGrayboxRuntime(MinecraftServer server) {
        this.server = server;
        data = SourceGrayboxSavedData.get(server.overworld());
    }

    public static SourceGrayboxRuntime forServer(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, SourceGrayboxRuntime::new);
    }

    public static void stop(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    /** Returns true only after the source graybox has become the active campaign clock. */
    public boolean tick() {
        if (!data.activated()) return false;
        ServerLevel overworld = server.overworld();
        data.advanceDueDays(overworld.getGameTime(), DAY_INTERVAL_TICKS, MAXIMUM_CATCH_UP_DAYS);
        return true;
    }

    /** Explicit activation prevents a legacy campaign clock and source clock from running together. */
    public ReferenceGrayboxSnapshot activate() {
        data.activate(server.overworld().getGameTime());
        return data.snapshot();
    }

    public void advance(int days) {
        data.advance(days);
    }

    public ReferenceGrayboxSnapshot snapshot() {
        return data.snapshot();
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxResidentObservation observation) {
        return data.observe(observation);
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxBioformObservation observation) {
        return data.observe(observation);
    }

    public String status() {
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        return "profile=" + snapshot.profileId() + ", day=" + snapshot.day() + ", settlements=" + snapshot.settlements().size()
                + ", residents=" + snapshot.residents().size() + ", organs=" + snapshot.hiveOrgans().size()
                + ", bioforms=" + snapshot.bioforms().size() + ", materialization=" + (data.activated() ? "PENDING" : "DISABLED");
    }
}
