package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;

import java.util.*;
import java.util.function.Supplier;

/** Closed deterministic developer branch registration; no runtime/manual tick dispatch. */
public final class FrontierV3DeveloperCommands {
    public enum Key {
        COMBAT_TRACE,
        STORAGE,
        BUILDING_AUTHORING
    }

    public record Registration(
            Key key, Supplier<LiteralArgumentBuilder<CommandSourceStack>> branch) {
        public Registration {
            Objects.requireNonNull(key);
            Objects.requireNonNull(branch);
        }
    }

    public static List<LiteralArgumentBuilder<CommandSourceStack>> branches() {
        return validate(
                List.of(
                        new Registration(Key.COMBAT_TRACE, FrontierV3CombatTraceCommands::branch),
                        new Registration(Key.STORAGE, FrontierV3StorageCommands::branch),
                        new Registration(
                                Key.BUILDING_AUTHORING, FrontierV3BuildingCommands::branch)));
    }

    public static List<LiteralArgumentBuilder<CommandSourceStack>> validate(
            List<Registration> registrations) {
        var registered = new EnumMap<Key, Registration>(Key.class);
        for (var registration : registrations)
            if (registered.putIfAbsent(registration.key(), registration) != null)
                throw new IllegalArgumentException("duplicate developer command owner");
        if (!registered.keySet().equals(EnumSet.allOf(Key.class)))
            throw new IllegalArgumentException("missing developer command owner");
        var result =
                Arrays.stream(Key.values()).map(k -> registered.get(k).branch().get()).toList();
        if (result.stream().map(LiteralArgumentBuilder::getLiteral).distinct().count()
                != result.size())
            throw new IllegalArgumentException("duplicate developer command branch");
        return result;
    }

    private FrontierV3DeveloperCommands() {}
}
