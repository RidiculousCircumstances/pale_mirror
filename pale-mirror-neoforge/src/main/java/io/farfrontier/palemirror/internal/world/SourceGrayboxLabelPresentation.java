package io.farfrontier.palemirror.internal.world;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Native TextDisplay styling for server-owned source-graybox labels.
 *
 * <p>The supplied text is already a bounded presentation of an immutable
 * snapshot. This class owns only how it is rendered; it owns no simulation
 * fact, interaction, or persistence decision.</p>
 */
final class SourceGrayboxLabelPresentation {
    private SourceGrayboxLabelPresentation() { }

    static void configure(Display.TextDisplay display, String id, String text) {
        CompoundTag data = display.saveWithoutId(new CompoundTag());
        // A TextDisplay background is the board itself. Native signs are too
        // small and directional to serve as map-facing test instrumentation.
        // Keep one compact board per source object, instead of a detached row
        // of technical text floating over the whole map.
        Component content = Component.literal(SourceGrayboxLabelBoard.text(text)).withStyle(colour(id), ChatFormatting.BOLD);
        data.putString(Display.TextDisplay.TAG_TEXT, Component.Serializer.toJson(content, display.registryAccess()));
        data.putInt("line_width", 256);
        data.putByte("text_opacity", (byte) 0xFF);
        data.putInt("background", 0xE0000000);
        data.putBoolean("shadow", true);
        data.putBoolean("see_through", true);
        data.putString("alignment", "center");
        data.putFloat("view_range", viewRange(id));
        data.putFloat("width", 16.0f);
        data.putFloat("height", 4.0f);
        data.putInt("glow_color_override", glowColour(id));
        data.putBoolean("Glowing", true);
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(scale(id), scale(id), scale(id)), new Quaternionf()))
                .ifSuccess(encoded -> data.put("transformation", encoded));
        Display.BillboardConstraints.CODEC.encodeStart(NbtOps.INSTANCE, Display.BillboardConstraints.CENTER)
                .ifSuccess(encoded -> data.put("billboard", encoded));
        Brightness.CODEC.encodeStart(NbtOps.INSTANCE, Brightness.FULL_BRIGHT)
                .ifSuccess(encoded -> data.put("brightness", encoded));
        display.load(data);
        display.setNoGravity(true);
        // Retain an unrendered plain-text mirror for server-side recovery and GameTest assertions.
        display.setCustomName(Component.literal(text));
        display.setCustomNameVisible(false);
        display.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, id);
        display.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, SourceGrayboxMaterializer.LABEL_KIND);
        display.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION,
                "0000000000000000000000000000000000000000000000000000000000000000");
    }

    private static ChatFormatting colour(String id) {
        if (id.startsWith("organ:") || id.startsWith("chrysalis:")) return ChatFormatting.LIGHT_PURPLE;
        if (id.startsWith("route:") || id.startsWith("field-link:")) return ChatFormatting.AQUA;
        if (id.startsWith("settlement:") || id.startsWith("field-post:")) return ChatFormatting.GOLD;
        if (id.startsWith("interaction:") || id.startsWith("conflict:")) return ChatFormatting.RED;
        if (id.startsWith("facility:") || id.startsWith("site:") || id.startsWith("cargo:")) return ChatFormatting.YELLOW;
        return ChatFormatting.WHITE;
    }

    private static int glowColour(String id) {
        return switch (colour(id)) {
            case LIGHT_PURPLE -> 0xD77CFF;
            case AQUA -> 0x55FFFF;
            case GOLD -> 0xFFAA00;
            case RED -> 0xFF5555;
            case YELLOW -> 0xFFFF55;
            default -> 0xFFFFFF;
        };
    }

    /**
     * Map landmarks should orient a distant player; exact local facts should
     * appear only once their associated greybox object is close enough to be
     * inspected.  Otherwise twelve settlements' worth of state turns the
     * whole horizon into overlapping text.
     */
    static float viewRange(String id) {
        if (id.startsWith("settlement:") || id.startsWith("organ:")) return 3.0f;
        if (id.startsWith("route:") || id.startsWith("field-link:") || id.startsWith("legend:")) return 1.75f;
        return 1.25f;
    }

    /**
     * TextDisplays use block-scale glyphs, so values greater than one obscure
     * the source object at ordinary player distance.  Landmark names remain
     * deliberately larger than detailed labels without becoming a billboard.
     */
    static float scale(String id) {
        return id.startsWith("settlement:") || id.startsWith("organ:") ? 0.95f : 0.80f;
    }

}
