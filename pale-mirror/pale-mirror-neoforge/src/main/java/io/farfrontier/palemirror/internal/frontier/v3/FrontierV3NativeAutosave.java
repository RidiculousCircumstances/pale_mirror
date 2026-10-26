package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.HolderLookup;
import java.io.File;
import java.util.function.BooleanSupplier;

/** Explicit implicit-save capability. It cannot change an owner's direct durable effect port. */
public final class FrontierV3NativeAutosave {
    public interface Target { void submitAutosave(File file, HolderLookup.Provider registries); }
    private static final ThreadLocal<Boolean> AUTOMATIC = new ThreadLocal<>();
    private FrontierV3NativeAutosave() { }
    public static boolean active() { return Boolean.TRUE.equals(AUTOMATIC.get()); }
    public static boolean automatic(BooleanSupplier save) {
        var previous = AUTOMATIC.get(); AUTOMATIC.set(true);
        try { return save.getAsBoolean(); }
        finally { if (previous == null) AUTOMATIC.remove(); else AUTOMATIC.set(previous); }
    }
}
