package io.farfrontier.palemirror.internal.frontier.v3;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.ArgumentCommandNode;

import org.junit.jupiter.api.Test;

import java.util.*;

class FrontierV3BuildingCommandsTest {
    @Test
    void
            exportConsumesTheCompleteNamespacedStructureAndRegistrationRejectsMissingAndCompetingOwners()
                    throws Exception {
        var root = FrontierV3BuildingCommands.branch().build();
        var identity = (ArgumentCommandNode<?, ?>) root.getChild("export").getChild("structure");
        assertNotNull(root.getChild("equipment").getChild("draft").getChild("workplace").getChild("equipment").getChild("front"));
        assertNotNull(root.getChild("preview").getChild("draft"));
        var reader = new StringReader("structure:1-workshop");
        assertEquals("structure:1-workshop", identity.getType().parse(reader).toString());
        assertFalse(reader.canRead());
        assertThrows(
                IllegalArgumentException.class,
                () -> FrontierV3DeveloperCommands.validate(List.of()));
        var declaration =
                new FrontierV3DeveloperCommands.Registration(
                        FrontierV3DeveloperCommands.Key.BUILDING_AUTHORING,
                        FrontierV3BuildingCommands::branch);
        assertThrows(
                IllegalArgumentException.class,
                () -> FrontierV3DeveloperCommands.validate(List.of(declaration, declaration)));
        assertEquals(3, FrontierV3DeveloperCommands.branches().size());
    }
}
