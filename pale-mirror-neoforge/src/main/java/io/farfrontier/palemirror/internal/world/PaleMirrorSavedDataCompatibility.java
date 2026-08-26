package io.farfrontier.palemirror.internal.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Startup preflight kept separate from the SavedData codec to keep failure policy explicit. */
final class PaleMirrorSavedDataCompatibility {
    private PaleMirrorSavedDataCompatibility() { }

    static void assertCompatible(Path worldRoot, String dataName, IntPredicate migratable,
                                 IntFunction<IllegalStateException> incompatible) {
        Path dataFile = worldRoot.resolve("data").resolve(dataName + ".dat");
        if (!Files.exists(dataFile)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
            CompoundTag tag = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
            int version = tag.contains("schemaVersion", Tag.TAG_INT) ? tag.getInt("schemaVersion") : 0;
            if (!migratable.test(version)) throw incompatible.apply(version);
        } catch (IOException failure) {
            throw new IllegalStateException("Pale Mirror cannot read canonical state " + dataFile
                    + "; server startup is stopped rather than replacing it", failure);
        }
    }

    /**
     * Validate a complete canonical record before Minecraft's data storage can
     * replace a failed load with a fresh instance.  Schema compatibility alone
     * is insufficient for source-shaped documents with cross-owner invariants.
     */
    static void assertHydratable(Path worldRoot, String dataName, Consumer<CompoundTag> hydrate, String stateLabel) {
        Path dataFile = worldRoot.resolve("data").resolve(dataName + ".dat");
        if (!Files.exists(dataFile)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
            CompoundTag tag = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
            hydrate.accept(tag);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Pale Mirror cannot hydrate " + stateLabel + " " + dataFile
                    + "; server startup is stopped rather than replacing it", failure);
        }
    }
}
