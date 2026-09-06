package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.FrontierCommand;
import io.farfrontier.palemirror.frontier.FrontierCommandOutcome;
import io.farfrontier.palemirror.frontier.FrontierCommandProcessor;
import io.farfrontier.palemirror.frontier.FrontierProfile;
import io.farfrontier.palemirror.frontier.FrontierWorldFactory;
import io.farfrontier.palemirror.frontier.FrontierWorldState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Separate Frontier persistence while the legacy campaign data is still present during the replacement cut. */
public final class FrontierSavedData extends SavedData {
    public static final String DATA_NAME = "pale_mirror_frontier";
    // NBT v15 adopts the Python campaign enum contract. Older envelopes cannot prove the
    // exact terminal commitment, so disposable Frontier worlds fail closed.
    private static final int CURRENT_SCHEMA = 14;
    private final FrontierWorldState state;
    private boolean physicalProjectionEnabled;
    private final FrontierCommandProcessor commands = new FrontierCommandProcessor();

    private FrontierSavedData(FrontierWorldState state) { this(state, false); }
    private FrontierSavedData(FrontierWorldState state, boolean physicalProjectionEnabled) {
        this.state = state;
        this.physicalProjectionEnabled = physicalProjectionEnabled;
    }

    public static FrontierSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> new FrontierSavedData(FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, overworld.getSeed())),
                FrontierSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }
    public FrontierWorldState state() { return state; }
    public boolean physicalProjectionEnabled() { return physicalProjectionEnabled; }
    public void enablePhysicalProjection() { if (!physicalProjectionEnabled) { physicalProjectionEnabled = true; setDirty(); } }
    public FrontierCommandOutcome execute(FrontierCommand command) {
        FrontierCommandOutcome outcome = commands.execute(state, command);
        if (outcome.accepted() || !outcome.events().isEmpty()) setDirty();
        return outcome;
    }
    public static FrontierSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != CURRENT_SCHEMA) {
            throw new IllegalStateException("incompatible Frontier SavedData schema");
        }
        return new FrontierSavedData(FrontierStateCodec.read(tag.getCompound("state")), tag.getBoolean("physicalProjectionEnabled"));
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", CURRENT_SCHEMA);
        tag.putBoolean("physicalProjectionEnabled", physicalProjectionEnabled);
        tag.put("state", FrontierStateCodec.write(state));
        return tag;
    }
}
