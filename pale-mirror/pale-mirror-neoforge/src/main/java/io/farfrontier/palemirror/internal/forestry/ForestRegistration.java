package io.farfrontier.palemirror.internal.forestry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;
import java.util.Optional;
import java.util.function.Supplier;

/** Compact native chunk facts; no separate inventory, effect journal or ticking manager. */
public final class ForestRegistration {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "pale_mirror");
    public static final Supplier<AttachmentType<Optional<GeneratedForest>>> GENERATED = ATTACHMENTS.register("generated_forest",
            () -> AttachmentType.<Optional<GeneratedForest>>builder(Optional::empty)
                    .serialize(GeneratedForest.CODEC.optionalFieldOf("forest").codec()).build());
    private ForestRegistration() { }
    public static void register(IEventBus bus) { ATTACHMENTS.register(bus); }
}
