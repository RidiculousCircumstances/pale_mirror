package io.farfrontier.palemirror.visuals.genesis;

import com.mojang.serialization.Codec;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persisted chunk-local proofs for authored placement and its late-feature finalization. */
public final class VisualGenesisAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(
            net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            PaleMirrorVisualsMod.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> GENESIS_STAMP = TYPES.register(
            "genesis_stamp", () -> AttachmentType.builder(() -> "").serialize(Codec.STRING, value -> !value.isBlank()).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> FINALIZATION_STAMP = TYPES.register(
            "finalization_stamp",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING, value -> !value.isBlank()).build());

    private VisualGenesisAttachments() { }

    public static void register(IEventBus bus) { TYPES.register(bus); }
}
