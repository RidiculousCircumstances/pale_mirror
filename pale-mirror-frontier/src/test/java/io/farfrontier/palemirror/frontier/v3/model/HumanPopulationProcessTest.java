package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HumanPopulationProcessTest {
    @Test
    void bootstrapRegistersEveryExactResidentInBoundedHouseholdsAndRoundTripsIt() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:humans"), 91L));

        assertEquals(state.bootstrap().residentCount(), state.humanPopulation().residents().size());
        assertEquals(state.actorLocations().keySet().stream().filter(id -> id.value().startsWith("resident:")).count(), state.humanPopulation().residents().size());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void exactBirthAndMigrationChangeOnlyTheNamedPersonAndRejectDuplicateOrForeignHousehold() {
        WorldId world = new WorldId("frontier:human-events");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(world, 91L));
        FrontierWorldState before = state(engine);
        ResidentProfile parent = before.humanPopulation().resident(new SubjectId("resident:1-1"));
        ResidentProfile newborn = new ResidentProfile(new SubjectId("resident:1-born-1"), parent.householdId(), parent.settlementId(), ResidentRole.FARMER,
                0L, parent.skills());
        ResidentBorn birth = new ResidentBorn(newborn, new BlockPosition(-358, 64, -338));

        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(world, engine, "command:birth-1", birth)));
        FrontierWorldState born = state(engine);
        assertEquals(newborn, born.humanPopulation().resident(newborn.id()));
        assertEquals(birth.position(), born.actorLocations().get(newborn.id()).position());
        assertEquals(birth, FrontierWorldRuntimeDefinition.payloadCodecs().decode(birth.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(birth)));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(world, engine, "command:birth-duplicate", birth)));

        ResidentProfile destinationResident = born.humanPopulation().resident(new SubjectId("resident:2-1"));
        ResidentMigrated migration = new ResidentMigrated(newborn.id(), destinationResident.householdId(), destinationResident.settlementId(), new BlockPosition(-118, 64, -338));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(world, engine, "command:migration-1", migration)));
        FrontierWorldState migrated = state(engine);
        assertEquals(destinationResident.settlementId(), migrated.humanPopulation().resident(newborn.id()).settlementId());
        assertEquals(migration.destination(), migrated.actorLocations().get(newborn.id()).position());
        assertEquals(migration, FrontierWorldRuntimeDefinition.payloadCodecs().decode(migration.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(migration)));

        ResidentMigrated foreignHousehold = new ResidentMigrated(newborn.id(), parent.householdId(), destinationResident.settlementId(), migration.destination());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(world, engine, "command:migration-foreign-household", foreignHousehold)));
        assertNotNull(state(engine).humanPopulation().resident(newborn.id()));

        AmbientActorDied death = new AmbientActorDied(newborn.id(), migration.destination(), "test:physical-casualty");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(world, engine, "command:birth-casualty", death)));
        FrontierWorldState dead = state(engine);
        assertEquals(ActorLifeStatus.DEAD, dead.actorLocations().get(newborn.id()).condition().status());
        assertEquals(newborn.id(), dead.humanPopulation().resident(newborn.id()).id());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(world, engine, "command:migration-dead", migration)));
    }

    private static FrontierWorldState state(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }
    private static FrontierCommand command(WorldId world, io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine,
                                           String id, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CommandId command = new CommandId(id); var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), payload);
    }
}
