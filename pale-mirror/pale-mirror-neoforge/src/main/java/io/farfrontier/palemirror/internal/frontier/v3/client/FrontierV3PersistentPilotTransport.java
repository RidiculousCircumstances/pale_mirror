package io.farfrontier.palemirror.internal.frontier.v3.client;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/**
 * The ordinary client transport boundary for a persistent pilot session. The session-control
 * files decide whether an action is legal; this class only performs the resulting normal client
 * disconnect or reconnect and owns no scenario/canonical state.
 */
final class FrontierV3PersistentPilotTransport {
    private FrontierV3PersistentPilotTransport() { }

    static void closeIfRequested(Minecraft minecraft) {
        try {
            if (!FrontierV3PilotSessionControl.requestFinalClose()) return;
            minecraft.execute(() -> {
                if (minecraft.getConnection() == null) {
                    throw new IllegalStateException("persistent pilot has no live final connection to close");
                }
                minecraft.getConnection().getConnection().disconnect(Component.literal("Frontier v3 persistent-pilot complete"));
                minecraft.disconnect();
            });
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("persistent pilot final close control is invalid", failure);
        }
    }

    /** Performs the isolated supervisor's distinct, lifecycle-directory close protocol. */
    static void closeLifecycleIfRequested(Minecraft minecraft) {
        try {
            if (!FrontierV3PilotSessionControl.requestLifecycleFinalClose()) return;
            minecraft.execute(() -> {
                if (minecraft.getConnection() == null) {
                    throw new IllegalStateException("isolated pilot has no live final connection to close");
                }
                minecraft.getConnection().getConnection().disconnect(Component.literal("Frontier v3 isolated-pilot terminal"));
                minecraft.disconnect();
            });
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("isolated pilot final close control is invalid", failure);
        }
    }

    static void reconnectIfRequested(Minecraft minecraft, String server) {
        if (!FrontierV3PilotSessionControl.awaitingResume()) return;
        try {
            if (!ServerAddress.isValidAddress(server)) {
                throw new IllegalStateException("persistent pilot has no valid replacement server address");
            }
            FrontierV3PilotSessionControl.requestReconnect(server);
            if (!FrontierV3PilotSessionControl.reconnectInFlight()) return;
            FrontierV3PilotSessionControl.executeReconnectTransportDeparture(() -> ConnectScreen.startConnecting(minecraft.screen, minecraft, ServerAddress.parseString(server),
                    new ServerData("Frontier v3 disposable pilot", server, ServerData.Type.OTHER), false, null));
            PaleMirrorMod.LOGGER.info("PMV3_PILOT session_reconnect_requested runId={}", FrontierV3PilotSessionControl.runId());
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("persistent pilot reconnect control is invalid", failure);
        }
    }

    /**
     * A normal client disconnect can briefly retain its last LocalPlayer while the network
     * connection is already gone.  The session boundary is transport ownership, not a stale
     * presentation object: otherwise a resumed persistent pilot can idle forever after the
     * runner has admitted its replacement server.
     */
}
