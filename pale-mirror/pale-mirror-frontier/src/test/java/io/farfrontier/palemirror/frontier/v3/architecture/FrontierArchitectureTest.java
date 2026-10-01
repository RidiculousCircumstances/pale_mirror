package io.farfrontier.palemirror.frontier.v3.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "io.farfrontier.palemirror.frontier.v3",
        importOptions = ImportOption.DoNotIncludeTests.class
)
final class FrontierArchitectureTest {
    @ArchTest
    static final ArchRule execution_coordinator_does_not_inspect_family_work = noClasses()
            .that().haveSimpleName("ActorExecutionCoordinator")
            .should().dependOnClassesThat().haveNameMatching(
                    ".*\\.(ProductionJob|BakeryWorkState|ResourceSiteHarvestJob|ResourceSiteHarvestProgress|"
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
