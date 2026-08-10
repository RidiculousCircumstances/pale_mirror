package io.farfrontier.palemirror.internal.presentation;

import java.util.List;

import io.farfrontier.palemirror.domain.ScenarioInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Player-facing projection of offered scenarios; canonical state remains in the domain. */
public final class ScenarioCommandPresentation {
    private ScenarioCommandPresentation() { }

    public static Component offered(List<ScenarioInstance> scenarios) {
        if (scenarios.isEmpty()) return Component.literal("No offered scenarios.");
        MutableComponent output = Component.literal("Offered scenarios:").withStyle(ChatFormatting.GOLD);
        for (ScenarioInstance scenario : scenarios) {
            String command = "/pale_mirror scenario accept " + scenario.id();
            output.append(Component.literal("\n- " + scenario.id() + " [" + scenario.status() + "] "));
            output.append(Component.literal("[ACCEPT]").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))));
        }
        return output;
    }
}
