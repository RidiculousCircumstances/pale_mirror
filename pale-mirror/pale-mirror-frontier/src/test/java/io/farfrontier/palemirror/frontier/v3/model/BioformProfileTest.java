package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BioformProfileTest {
    @Test
    void bootstrapSeparatesSentinelScoutDefenderExplosiveAssaulterAndExactOverseerWithoutCohorts() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:bioform-profile"), 91L));

        assertEquals(48, state.bootstrap().hive().bioforms().size());
        assertEquals(12L, state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).count());
        assertEquals(12L, state.bootstrap().hive().bioforms().stream().filter(Bioform::isDefender).count());
        assertEquals(10L, state.bootstrap().hive().bioforms().stream().filter(Bioform::isExplosiveAssaulter).count());
        assertEquals(2L, state.bootstrap().hive().bioforms().stream().filter(Bioform::isOverseer).count());
        assertTrue(state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout)
                .allMatch(value -> value.chassis() == BioformChassis.SENTINEL && value.mutations().isEmpty()));
        assertTrue(state.bootstrap().hive().bioforms().stream().filter(Bioform::isDefender)
                .allMatch(value -> value.chassis() == BioformChassis.RUNT && value.mutations().contains(BioformMutation.ARMORED)));
        assertTrue(state.bootstrap().hive().bioforms().stream().filter(Bioform::isExplosiveAssaulter)
                .allMatch(value -> value.chassis() == BioformChassis.RUNT && value.mutations().equals(Set.of(BioformMutation.EXPLOSIVE))));
        assertTrue(state.bootstrap().hive().bioforms().stream().filter(Bioform::isOverseer)
                .allMatch(value -> value.chassis() == BioformChassis.OVERSEER
                        && value.mutations().isEmpty() && value.assignment() == BioformAssignment.WATCH));
    }

    @Test
    void profileRoundTripsExactMutationSetAndNeverAliasesMutableInput() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:bioform-profile-recovery"), 91L));
        SubjectId hive = initial.bootstrap().hive().id(); SubjectId nest = new SubjectId("nest:seed-east");
        Bioform profile = new Bioform(new SubjectId("bioform:east-profile"), hive, nest, BioformChassis.OVERSEER,
                Set.of(BioformMutation.CARRIER, BioformMutation.MUCUS), BioformAssignment.CARRY, new BlockPosition(432, 64, 432));
        FrontierWorldState state = initial.spawnBioform(profile);

        assertEquals(profile, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)).hiveColony().spawnedBioforms().get(profile.id()));
        assertFalse(profile.isScout());
        assertFalse(profile.isExplosiveAssaulter());
        assertThrows(UnsupportedOperationException.class, () -> profile.mutations().add(BioformMutation.SPORE));
    }
}
