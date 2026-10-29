package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Source;
import java.util.*;

/** Nominal validation reads. Mission implementations declare their own group
 * dependencies; the common inverse index supplies no behavioral authority. */
public final class UnitGroupValidationReads {
    static final Source<FrontierWorldState, SubjectId, io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup> GROUPS =
            new Source<>(s -> s.unitGroups().groups());
    static final Source<FrontierWorldState, SubjectId, TransportMission> MISSIONS = new Source<>(s -> s.shipments().missions());
    private static final Source<FrontierWorldState, SubjectId, Shipment> SHIPMENTS = new Source<>(s -> s.shipments().shipments());
    private static final Source<FrontierWorldState, SubjectId, ActorKind> KINDS = new Source<>(s -> ActorIdentityDeclarations.declaredKinds(s.bootstrap(), s.actorLocations()));
    private static final Source<FrontierWorldState, SubjectId, ActorLifeStatus> LIFE = new Source<>(s -> ActorIdentityDeclarations.lifeStatuses(s.bootstrap(), s.actorLocations()));
    private static final Source<FrontierWorldState, SubjectId, ResidentProfile> PEOPLE = new Source<>(s -> s.humanPopulation().residents());
    private static final Source<FrontierWorldState, SubjectId, io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecution> EXECUTIONS = new Source<>(s -> s.actorExecutions().actors());
    private static final Source<FrontierWorldState, SubjectId, CustodyAccount> ACCOUNTS = new Source<>(s -> s.inventory().fungibleResources().accounts());
    private static final Source<FrontierWorldState, SubjectId, ClaimAllocation> CLAIMS = new Source<>(s -> s.inventory().fungibleResources().claims());
    private static final Source<FrontierWorldState, SubjectId, EconomicAccount> MONEY = new Source<>(s -> s.inventory().economics().accounts());
    private static final Source<FrontierWorldState, SubjectId, FinancialReservation> PAYMENTS = new Source<>(s -> s.inventory().economics().reservations());
    private static final Source<FrontierWorldState, SubjectId, Company> COMPANIES = new Source<>(s -> s.companies().companies());
    private static final Source<FrontierWorldState, SubjectId, io.farfrontier.palemirror.frontier.v3.model.expedition.TransportAsset> ASSETS = new Source<>(s -> s.transportFleet().assets());
    private enum ResourceImage { CURRENT }
    // A prepared physical selection reads the complete fungible image. Ordinary
    // loaded/historical supplies depend only on their explicitly addressed rows.
    private static final Source<FrontierWorldState, ResourceImage, FungibleResourceLedger> RESOURCES =
            new Source<>(s -> Map.of(ResourceImage.CURRENT, s.inventory().fungibleResources()));
    static final List<Source<FrontierWorldState, ?, ?>> SOURCES;
    static {
        var values = new ArrayList<Source<FrontierWorldState, ?, ?>>(ShipmentEndpointReferences.SOURCES);
        values.addAll(List.of(GROUPS, MISSIONS, SHIPMENTS, KINDS, LIFE, PEOPLE, EXECUTIONS, ACCOUNTS, CLAIMS, MONEY, PAYMENTS, COMPANIES, ASSETS, RESOURCES));
        SOURCES = List.copyOf(values);
    }
    private UnitGroupValidationReads() { }
    public static List<ImmutableReferenceIndex.Read<FrontierWorldState>> transport(FrontierWorldState state, UnitGroup group) {
        var reads = new ArrayList<ImmutableReferenceIndex.Read<FrontierWorldState>>();
        reads.add(MISSIONS.at(group.mission().id()));
        var mission = state.shipments().missions().get(group.mission().id());
        if (mission == null) return List.copyOf(reads);
        reads.add(MONEY.at(mission.organization().sponsor().id())); reads.add(COMPANIES.at(mission.organization().sponsor().id()));
        reads.addAll(ShipmentEndpointComposition.validationReads(mission.organization().home()));
        for (var member : group.members()) {
            reads.add(KINDS.at(member.actorId())); reads.add(PEOPLE.at(member.actorId())); reads.add(EXECUTIONS.at(member.actorId()));
        }
        for (var id : mission.shipmentIds()) reads.add(SHIPMENTS.at(id));
        mission.transportAssetId().ifPresent(id -> reads.add(ASSETS.at(id)));
        return List.copyOf(reads);
    }
    static List<ImmutableReferenceIndex.Read<FrontierWorldState>> group(FrontierWorldState state, UnitGroup group) {
        var reads = new ArrayList<>(io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts.require(group).validationReads(state, group));
        group.members().forEach(member -> reads.add(LIFE.at(member.actorId())));
        return List.copyOf(reads);
    }
    static List<ImmutableReferenceIndex.Read<FrontierWorldState>> mission(FrontierWorldState state, TransportMission mission) {
        var reads = new ArrayList<ImmutableReferenceIndex.Read<FrontierWorldState>>(); reads.add(GROUPS.at(mission.groupId()));
        var group = state.unitGroups().groups().get(mission.groupId());
        if (group != null) for (var member : group.members()) { reads.add(PEOPLE.at(member.actorId())); reads.add(LIFE.at(member.actorId())); }
        mission.shipmentIds().forEach(id -> reads.add(SHIPMENTS.at(id)));
        mission.supplies().ifPresent(load -> load.allocations().forEach(a -> {
            reads.add(CLAIMS.at(a.claimId())); reads.add(ACCOUNTS.at(a.destinationAccountId()));
            if (!a.loaded() && !a.withdrawn()) reads.add(ACCOUNTS.at(a.sourceAccountId()));
            a.pending().ifPresent(step -> { reads.add(EXECUTIONS.at(step.observation().actuation().execution().actorId())); reads.add(RESOURCES.at(ResourceImage.CURRENT)); });
        }));
        mission.replenishment().ifPresent(t -> {
            reads.add(ShipmentEndpointReferences.SURFACES.at(t.containerId())); reads.add(CLAIMS.at(t.claimId()));
            reads.add(ACCOUNTS.at(t.sourceAccountId())); reads.add(ACCOUNTS.at(t.destinationAccountId())); reads.add(EXECUTIONS.at(t.execution().actorId()));
            mission.transportAssetId().ifPresent(id -> reads.add(ASSETS.at(id)));
            mission.replenishmentPurchase().ifPresent(p -> reads.add(PAYMENTS.at(p.payment().id())));
        });
        return List.copyOf(reads);
    }
}
