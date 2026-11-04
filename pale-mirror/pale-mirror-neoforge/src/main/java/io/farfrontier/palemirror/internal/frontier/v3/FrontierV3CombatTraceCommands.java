package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/** Operator diagnostics only; no canonical command, body, inventory or combat permission. */
public final class FrontierV3CombatTraceCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("combat_trace").requires(source->source.hasPermission(4))
                .then(Commands.literal("start").then(Commands.argument("player",EntityArgument.player())
                    .executes(context->start(context.getSource(),EntityArgument.getPlayer(context,"player"),2,180))
                    .then(Commands.argument("interval",IntegerArgumentType.integer(1,20))
                        .executes(context->start(context.getSource(),EntityArgument.getPlayer(context,"player"),IntegerArgumentType.getInteger(context,"interval"),180))
                        .then(Commands.argument("seconds",IntegerArgumentType.integer(1,600))
                            .executes(context->start(context.getSource(),EntityArgument.getPlayer(context,"player"),
                                IntegerArgumentType.getInteger(context,"interval"),IntegerArgumentType.getInteger(context,"seconds")))))))
                .then(Commands.literal("stop").executes(context->reply(context.getSource(),FrontierV3CombatTrace.stop(context.getSource().getServer(),"OPERATOR_STOP"))))
                .then(Commands.literal("status").executes(context->reply(context.getSource(),FrontierV3CombatTrace.status(context.getSource().getServer()))));
    }
    private static int start(CommandSourceStack source,net.minecraft.server.level.ServerPlayer player,int interval,int seconds) {
        try {return reply(source,FrontierV3CombatTrace.start(source.getServer(),player,interval,seconds));}
        catch(IllegalArgumentException | IllegalStateException failure) {source.sendFailure(Component.literal(failure.getMessage()));return 0;}
    }
    private static int reply(CommandSourceStack source,String message) {source.sendSuccess(()->Component.literal(message),false);return 1;}
    private FrontierV3CombatTraceCommands() { }
}
