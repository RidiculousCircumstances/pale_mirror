package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Pilot-source-only command; vanilla {@code stop} remains entirely unchanged. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class FrontierV3PilotNaturalDemandStopCommand {
    static final String COMMAND = "pale_mirror_pilot_graceful_stop";

    private FrontierV3PilotNaturalDemandStopCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND)
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("admission", StringArgumentType.word()).executes(context -> {
                    return 0;
                }).then(Commands.argument("attempt", StringArgumentType.word()).executes(context -> {
                    try {
                        FrontierV3PilotNaturalDemandObserver.admitGracefulStop(context.getSource().getServer(),
                                StringArgumentType.getString(context, "admission"),
                                StringArgumentType.getString(context, "attempt"),
                                () -> context.getSource().getServer().halt(false));
                        return 1;
                    } catch (IllegalStateException failure) {
                        context.getSource().sendFailure(Component.literal(failure.getMessage()));
                        return 0;
                    }
                }))));
    }
}
