package io.farfrontier.palemirror.internal.calendar;

import io.farfrontier.palemirror.frontier.v3.time.SimulationCalendar;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.payload.ClientboundCustomSetTimePayload;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

/** Dimension-scoped projection adapter. It cannot advance canonical time or simulate vanilla physics. */
public final class MinecraftCalendarPresentation {
    public static final int MINECRAFT_DAY_TICKS = 24_000;
    public static final int MINECRAFT_LUNAR_DAYS = 8;
    private static final Map<ServerLevel, Binding> BINDINGS = new IdentityHashMap<>();

    private MinecraftCalendarPresentation() { }

    /** The composition root chooses the world and supplies already-committed canonical instants. */
    public static void attach(ServerLevel level, SimulationCalendar calendar, Supplier<OptionalLong> source) {
        Objects.requireNonNull(level, "calendar world"); Objects.requireNonNull(calendar, "calendar");
        Objects.requireNonNull(source, "canonical instant source");
        if (BINDINGS.containsKey(level)) throw new IllegalStateException("world already has a calendar owner");
        var base = (CalendarLevelDataAccess) level;
        var server = (CalendarServerLevelDataAccess) level;
        var original = server.calendar$getServerLevelData();
        if (base.calendar$getLevelData() != original) throw new IllegalStateException("Minecraft level-data aliases diverged");
        var reading = calendar.at(source.get().orElseThrow(() -> new IllegalStateException("calendar needs a committed instant")));
        var data = new CalendarLevelData(original, project(reading));
        BINDINGS.put(level, new Binding(calendar, source, original, data, reading.canonicalTick()));
        base.calendar$setLevelData(data); server.calendar$setServerLevelData(data);
    }

    /** Called after the simulation turn; partial advances and held targets expose only actual progress. */
    public static void synchronize(MinecraftServer server) {
        for (var entry : BINDINGS.entrySet()) {
            ServerLevel level = entry.getKey();
            if (level.getServer() != server) continue;
            Binding binding = entry.getValue();
            binding.refresh();
            if (level.players().isEmpty()) continue;
            // Clients do not interpolate an independent clock: each bounded server turn supplies the phase.
            var vanilla = new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), false);
            var neo = new ClientboundCustomSetTimePayload(level.getGameTime(), level.getDayTime(), false, 0.0F, 0.0F);
            for (var player : level.players()) {
                if (player.connection.hasChannel(ClientboundCustomSetTimePayload.TYPE)) player.connection.send(neo);
                else player.connection.send(vanilla);
            }
        }
    }

    public static void detach(ServerLevel level) {
        Binding binding = BINDINGS.remove(level);
        if (binding == null) return;
        ((CalendarLevelDataAccess) level).calendar$setLevelData(binding.original);
        ((CalendarServerLevelDataAccess) level).calendar$setServerLevelData(binding.original);
    }

    public static void detach(MinecraftServer server) {
        BINDINGS.keySet().stream().filter(level -> level.getServer() == server).toList()
                .forEach(MinecraftCalendarPresentation::detach);
    }

    public static long project(SimulationCalendar.Reading reading) {
        return reading.cycleTick(MINECRAFT_DAY_TICKS, MINECRAFT_LUNAR_DAYS);
    }

    private static final class Binding {
        private final SimulationCalendar calendar;
        private final Supplier<OptionalLong> source;
        private final net.minecraft.world.level.storage.ServerLevelData original;
        private final CalendarLevelData data;
        private long lastInstant;

        private Binding(SimulationCalendar calendar, Supplier<OptionalLong> source,
                        net.minecraft.world.level.storage.ServerLevelData original, CalendarLevelData data, long instant) {
            this.calendar = calendar; this.source = source; this.original = original; this.data = data; lastInstant = instant;
        }

        private void refresh() {
            OptionalLong instant = source.get();
            if (instant.isEmpty()) return; // Quarantined/stopped authority freezes its last committed presentation.
            long actual = instant.getAsLong();
            if (actual < lastInstant) throw new IllegalStateException("canonical calendar cannot rewind");
            data.project(project(calendar.at(actual))); lastInstant = actual;
        }
    }
}
