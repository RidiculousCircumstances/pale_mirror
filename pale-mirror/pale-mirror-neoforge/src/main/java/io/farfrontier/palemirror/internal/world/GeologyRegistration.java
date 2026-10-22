package io.farfrontier.palemirror.internal.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.Optional;
import java.util.function.Supplier;

public final class GeologyRegistration {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "pale_mirror");
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, "pale_mirror");
    public static final Supplier<AttachmentType<Optional<GeneratedGeology>>> BASELINE = ATTACHMENTS.register("generated_geology",
            () -> AttachmentType.<Optional<GeneratedGeology>>builder(Optional::empty)
                    .serialize(GeneratedGeology.CODEC.optionalFieldOf("baseline").codec()).build());
    static {
        FEATURES.register("graybox_geology", io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GrayboxGeologyFeature::new);
    }
    private GeologyRegistration() { }
    public static void register(IEventBus bus) { ATTACHMENTS.register(bus); FEATURES.register(bus); }
}
