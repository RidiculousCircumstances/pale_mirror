package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3NbtOutput;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/** Pinned native file boundary only; stream-based NBT and chunk storage are unchanged. */
@Mixin(NbtIo.class)
abstract class FrontierV3NbtOutputMixin {
    @Redirect(method = "writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V",
            at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;newOutputStream(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/OutputStream;"))
    private static OutputStream frontierV3$durableFile(Path path, OpenOption[] options) throws IOException {
        return FrontierV3NbtOutput.open(path);
    }
}
