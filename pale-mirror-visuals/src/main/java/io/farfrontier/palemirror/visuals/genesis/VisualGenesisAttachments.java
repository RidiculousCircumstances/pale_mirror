package io.farfrontier.palemirror.visuals.genesis;

import com.mojang.serialization.Codec;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persisted chunk-local proof that the exact compiled genesis slice ran. */
public final class VisualGenesisAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(
            net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            PaleMirrorVisualsMod.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> GENESIS_STAMP = TYPES.register(
            "genesis_stamp", () -> AttachmentType.builder(() -> "").serialize(Codec.STRING, value -> !value.isBlank()).build());

    private VisualGenesisAttachments() { }

    public static void register(IEventBus bus) { TYPES.register(bus); }
}
