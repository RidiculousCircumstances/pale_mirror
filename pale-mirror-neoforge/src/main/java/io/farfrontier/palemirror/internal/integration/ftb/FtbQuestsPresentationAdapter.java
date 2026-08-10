package io.farfrontier.palemirror.internal.integration.ftb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Isolated FTB Quests presentation leaf. It seeds one static journal chapter
 * through FTB's documented config format and public commands. It never reads
 * or writes FTB team progress and PM never reads its completion state.
 */
public final class FtbQuestsPresentationAdapter implements IntegrationAdapter {
    private static final String MOD_ID = "ftbquests";
    private static final String PINNED_VERSION = "2101.1.30";
    private static final String QUEST_FILE_CLASS = "dev.ftb.mods.ftbquests.quest.ServerQuestFile";
    private static final String RESOURCE = "/ftb_quests_presentation/chapters/pale_mirror_first_living_region.snbt";
    private static final String FILE_NAME = "pale_mirror_first_living_region.snbt";
    private static final String MARKER = "pale_mirror_presentation_version: 1";
    private static final String CHAPTER_ID = "50A1E00000000001";
    private volatile String detail = "FTB Quests presentation has not been initialized";
    private volatile boolean presentationUsable = true;

    @Override public String id() { return "pale_mirror:ftb_quests_presentation"; }

    @Override
    public AdapterHealth health() {
        if (!ModList.get().isLoaded(MOD_ID)) return new AdapterHealth(AdapterHealth.Status.ABSENT,
                "FTB Quests is not installed; PM keeps its built-in journal", Set.of());
        String version = ModList.get().getModContainerById(MOD_ID).map(container ->
                container.getModInfo().getVersion().toString()).orElse("unknown");
        if (!PINNED_VERSION.equals(version)) return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                "FTB Quests " + version + " is installed; PM presentation is pinned to " + PINNED_VERSION, Set.of());
        try {
            Class.forName(QUEST_FILE_CLASS, false, getClass().getClassLoader());
            if (!presentationUsable) return new AdapterHealth(AdapterHealth.Status.BLOCKED, detail, Set.of());
            return new AdapterHealth(AdapterHealth.Status.AVAILABLE, detail, Set.of(Capability.FTB_QUEST_PRESENTATION));
        } catch (ClassNotFoundException exception) {
            return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                    "FTB Quests lacks the expected public quest-file surface", Set.of());
        }
    }

    /** Provision exactly one PM-owned static chapter; a user-owned collision is never overwritten. */
    public void onServerStarted(MinecraftServer server) {
        if (health().status() != AdapterHealth.Status.AVAILABLE) return;
        Path target = FMLPaths.CONFIGDIR.get().resolve("ftbquests").resolve("quests").resolve("chapters").resolve(FILE_NAME);
        try {
            if (Files.exists(target)) {
                String existing = Files.readString(target);
                if (!existing.contains(MARKER)) {
                    detail = "FTB Quests chapter collision at " + target + "; PM did not overwrite it";
                    presentationUsable = false;
                    return;
                }
                detail = "PM First Living Region journal is already installed";
                return;
            }
            Files.createDirectories(target.getParent());
            try (InputStream source = FtbQuestsPresentationAdapter.class.getResourceAsStream(RESOURCE)) {
                if (source == null) throw new IOException("Bundled FTB Quests chapter is missing");
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), "ftbquests reload quests");
            detail = "Installed PM First Living Region journal through FTB Quests reload";
        } catch (IOException | RuntimeException exception) {
            detail = "Could not install PM FTB Quests journal: " + exception.getClass().getSimpleName();
            presentationUsable = false;
        }
    }

    /** Public command used only as a player-facing link; it carries no PM state mutation. */
    public Optional<String> journalOpenCommand() {
        return health().status() == AdapterHealth.Status.AVAILABLE ? Optional.of("/ftbquests open_book " + CHAPTER_ID) : Optional.empty();
    }
}
