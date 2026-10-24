package io.farfrontier.palemirror.frontier.v3.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "io.farfrontier.palemirror.frontier.v3",
        importOptions = {ImportOption.DoNotIncludeTests.class, FrontierArchitectureTest.ProductionClasses.class}
)
final class FrontierArchitectureTest {
    public static final class ProductionClasses implements ImportOption {
        @Override public boolean includes(com.tngtech.archunit.core.importer.Location location) {
            return !location.contains("/testFixtures/") && !location.contains("-test-fixtures.jar");
        }
    }

    @ArchTest
    static final ArchRule economic_solver_has_only_explicit_planning_owners = noClasses()
            .that().resideOutsideOfPackage("..model.economy..")
            .and().doNotHaveSimpleName("SettlementEconomicEvaluation")
            .and().doNotHaveSimpleName("SettlementSupplyPlanning")
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "invoke the economic solver") {
                @Override public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall call) {
                    return call.getTargetOwner().getSimpleName().equals("EconomicPlanner");
                }
            }).because("queries, work admission, diagnostics and replay consume accepted plans; only economic review evaluates policy");

    @ArchTest
    static final ArchRule only_economic_review_requests_evaluation = noClasses()
            .that().doNotHaveSimpleName("GoodsParticipantProcess")
            .and().doNotHaveSimpleName("SettlementEconomicPlanPreparation")
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "request economic or cultivation evaluation") {
                @Override public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall call) {
                    return java.util.Set.of("SettlementEconomicEvaluation", "CultivationEconomicEvaluation")
                            .contains(call.getTargetOwner().getSimpleName());
                }
            }).because("the participant process is the sole accepted-plan lifecycle owner");

    @ArchTest
    static final ArchRule group_coordination_does_not_inspect_mission_business = noClasses()
            .that().haveSimpleName("UnitGroupProcess").or().haveSimpleName("GroupFormation")
            .or().haveSimpleName("GroupMovementProvider").or().haveSimpleName("GroupMemberActivityCapability")
            .should().dependOnClassesThat().haveNameMatching(".*\\.(Shipment|TransportMission|GoodsTradeContract|SupplyContract|RouteOperation)")
            .because("mission Strategies own cargo, commercial and future combat policy; groups coordinate declared participants only");
    @ArchTest
    static final ArchRule service_access_coordinator_does_not_inspect_family_work = noClasses()
            .that().haveSimpleName("ServiceAccessCoordinator")
            .should().dependOnClassesThat().haveNameMatching(
                    ".*\\.(ProductionJob|StationWorkState|StationWorkGoal|ResourceSiteHarvestJob|ResourceSiteHarvestGoal|ResidentMeal)")
            .because("registered service Strategies interpret family stages; the shared arbiter receives complete nominal demands");

    @ArchTest
    static final ArchRule execution_coordinator_does_not_inspect_family_work = noClasses()
            .that().haveSimpleName("ActorExecutionCoordinator")
            .should().dependOnClassesThat().haveNameMatching(
                    ".*\\.(ProductionJob|StationWorkState|ResourceSiteHarvestJob|ResourceSiteHarvestProgress|"
                            + "FrontierProductionWorkSceneSupport|FrontierResourceSiteHarvestSceneSupport)")
            .because("family owners supply checkpoint Strategies; shared coordination cannot inspect their jobs");

    @ArchTest
    static final ArchRule frontier_is_independent_of_minecraft_and_legacy_domain = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "net.minecraft..",
                    "net.neoforged..",
                    "io.farfrontier.palemirror.domain..",
                    "io.farfrontier.palemirror.frontier.reference.."
            )
            .because("Frontier v3 is the independent canonical simulation and adapters materialize it");

    @ArchTest
    static final ArchRule frontier_does_not_use_ambient_random = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "java.util.Random",
                    "java.util.concurrent.ThreadLocalRandom"
            )
            .because("Frontier v3 decisions must use explicit deterministic decision keys");

    @ArchTest
    static final ArchRule frontier_does_not_use_wall_clock = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("java.time.Clock")
            .because("canonical progression is driven by SimInstant, not ambient wall time");

    @ArchTest
    static final ArchRule process_decisions_do_not_query_the_read_only_relationship_view = noClasses()
            .that().resideInAPackage("..process..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships$View")
            .because("post-admission process decisions must follow retained owner IDs, never inspect a derived relationship view");
}
