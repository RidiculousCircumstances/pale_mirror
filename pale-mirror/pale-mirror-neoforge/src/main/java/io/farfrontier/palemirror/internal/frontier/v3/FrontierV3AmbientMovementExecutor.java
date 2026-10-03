package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.OperationExecutionAuthority;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorDied;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorObserved;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseRestartAbsenceObserved;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMealHotReturned;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementHotObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMealHotAccessCleared;
import io.farfrontier.palemirror.frontier.v3.model.ServiceAccessCoordinator;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMeal;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMealHotArrived;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementAssaultBattlefield;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationJourney;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResidentTransitAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ScoutPatrolAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.HivePhysiologySupport;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflicted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationDiagnosticProducer;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody;
import io.farfrontier.palemirror.frontier.v3.process.HiveScoutPatrolProcess;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssembly;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyDeferral;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyDeferred;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkOrder;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.Settlement;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAccessPort;
import io.farfrontier.palemirror.frontier.v3.model.SettlementStructure;
import io.farfrontier.palemirror.frontier.v3.model.StructureKind;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3AmbientActorExecutor.*;

/** Controlled ambient movement and typed directed-arrival observations. */
final class FrontierV3AmbientMovementExecutor {
    private FrontierV3AmbientMovementExecutor() { }

    static boolean pursueLocalGoal(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        if (lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT) {
            FrontierV3ActorMovementNavigation.pursue(level, state, body, lease,
                    state.actorMovements().get(actorId));
            return false;
        }
        if (lease.goal() == AmbientGoalKind.MEAL) {
            ResidentMeal meal = state.humanPopulation().meals().get(actorId);
            if (meal != null && meal.phase() == ResidentMeal.Phase.MOVE
                    && FrontierV3AmbientServiceOccupancy.observe(level, runtime, state, actorId, body, lease)) return true;
            FrontierV3ResidentMealNavigation.pursue(level, state, body, lease,
                    meal);
            return false;
        }
        if (lease.goal() == AmbientGoalKind.TRANSIT) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
            if (journey == null || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving()
                    || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(journey.nextColdPosition())))) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
        }
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL && HiveScoutPatrolProcess.active(state, actorId).isEmpty()) {
            var started = HiveScoutPatrolProcess.start(state, actorId);
            try { HiveScoutPatrolProcess.reduceStarted(state, state.bootstrap().hive().id(), started); }
            catch (IllegalArgumentException unavailable) { FrontierV3GoalNavigation.stop(body); return false; }
            var result = submit(runtime, "scout-patrol-admission", actorId.value(), started);
            FrontierV3DiagnosticTrace.record(level.getServer(), "scout-patrol:" + actorId.value(), "scout_patrol_started", actorId, result);
            return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
        }
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL && !lease.goalBody().equals(state.actorLocations().get(actorId).body())) {
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            pursueDeclaredGoal(level, state, body, lease);
            return false;
        }
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY) {
            HiveTaskAssembly.Member member = hiveAssemblyMember(state, actorId, lease);
            HiveMobilization mobilization = assemblingMobilization(state, actorId);
            if (mobilization == null || member == null || member.arrived()) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
            if (!assembly.safeAdvances().contains(actorId)) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget)) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            if (!FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-hive-assembly-blocked",
                        actorId.value(), HiveMobilizationDiagnosticProducer.ASSEMBLY_PATH_BLOCKED.create(mobilization.id(),
                                new io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyBlockage(actorId,
                                        member.cursor(), member.nextSurface())));
                FrontierV3DiagnosticTrace.record(level.getServer(), "hive-assembly:" + mobilization.id().value(),
                        "hive_assembly_path_blocked", actorId, result);
                FrontierV3GoalNavigation.stop(body);
                return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
            }
            pursueDeclaredGoal(level, state, body, lease);
            return false;
        }
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_RETURN) {
            return FrontierV3HiveReturnMotion.pursue(level, runtime, state, actorId, body, lease);
        }
        if (lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
            OperationAssembly.Member member = assemblyMember(state, actorId, lease);
            if (member == null) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            RouteOperation operation = assemblingOperation(state, actorId);
            OperationAssembly assembly = operation.activeAssembly().orElseThrow();
            if (assembly.deferral().isPresent() && !assembly.deferral().orElseThrow().actorId().equals(actorId)) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            if (!assembly.safeAdvances().contains(actorId)) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            BlockPosition obstruction = assemblyObstruction(level, state, operation, lease.goalBody().supportingSurface().support());
            if (obstruction != null) {
                OperationAssemblyDeferral deferral = new OperationAssemblyDeferral(actorId, lease.goalBody().supportingSurface(), new SurfaceAnchor(obstruction),
                        OperationAssemblyDeferral.Reason.LOADED_WORLD_OBSTRUCTION);
                if (!assembly.deferral().filter(deferral::equals).isPresent()) {
                    io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-operation-assembly-deferred", actorId.value(),
                            new OperationAssemblyDeferred(operation.id(), deferral, OperationExecutionAuthority.assemblyCurrent(state, operation)));
                    FrontierV3DiagnosticTrace.record(level.getServer(), "operation-assembly:" + operation.id().value(), "operation_assembly_deferred", actorId, result);
                    FrontierV3GoalNavigation.stop(body);
                    return true;
                }
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            pursueDeclaredGoal(level, state, body, lease);
            return false;
        }
        if (lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            EngineeringWorkAssembly.Member member = engineeringAssemblyMember(state, actorId, lease);
            if (member == null || member.arrived()) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            EngineeringWorkAssembly assembly = engineeringProject(state, actorId).assembly().orElseThrow();
            if (!assembly.safeAdvances().contains(actorId)) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                FrontierV3GoalNavigation.stop(body);
                return false;
            }
            pursueDeclaredGoal(level, state, body, lease);
            return false;
        }
        if (FrontierV3AmbientActorLocalTargets.directedGoal(lease)) {
            pursueDeclaredGoal(level, state, body, lease);
        } else {
            // Idle presentation is not a second locomotion owner or permission to orbit.
            FrontierV3ControlledMobMotion.retireLocalActuation(body);
            if (FrontierV3AmbientServiceOccupancy.observe(level, runtime, state, actorId, body, lease)) return true;
            if (FrontierV3ServicePointClearance.pursue(level, state, body, lease)) return false;
            FrontierV3GoalNavigation.stop(body);
        }
        return false;
    }

    private static void pursueDeclaredGoal(ServerLevel level, FrontierWorldState state, Mob body, AmbientActorLease lease) {
        FrontierV3GoalNavigation.pursue(level, body, FrontierV3GoalNavigation.Goal.station(lease.goalBody().supportingSurface(),
                new FrontierV3NavigationScope.ObservedWorld(state.bootstrap().bounds())));
    }
    static boolean observeDirectedArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                   SubjectId actorId, Mob body, AmbientActorLease lease) {
        if (FrontierV3BodyObservation.capture(body).support().isEmpty()) return false;
        if (lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT) {
            var movement = state.actorMovements().get(actorId);
            if (movement == null) return false;
            BodyPosition observed = observedBody(body);
            boolean arrived = movement.order().arrivedAt(observed.supportingSurface());
            boolean exited = ServiceAccessCoordinator.witnessedActorMovementExit(state, movement, observed);
            if (!arrived && !exited) return false;
            if (arrived) FrontierV3GoalNavigation.stop(body);
            var result = submit(runtime, "ambient-actor-movement-observed", actorId.value(),
                    new ActorMovementHotObserved(actorId, movement.order().goalRevision(), lease.revision(), observed, movement.executionId()));
            FrontierV3DiagnosticTrace.record(level.getServer(), "actor-movement:" + actorId.value(),
                    arrived ? "actor_movement_arrived" : "actor_movement_exited_service", actorId, result);
            return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
        }
        if (lease.goal() == AmbientGoalKind.MEAL) {
            ResidentMeal meal = state.humanPopulation().meals().get(actorId);
            if (meal == null) return false;
            if (meal.phase() == ResidentMeal.Phase.MOVE && observedBody(body).equals(lease.goalBody())) {
                ResidentMealHotArrived arrival = new ResidentMealHotArrived(actorId, lease.revision(), observedBody(body), meal.executionId());
                // The HOT body may have reached the socket while another resident
                // took the turn. Keep its exact meal and body; retry observation
                // when that turn clears instead of quarantining the whole world.
                if (!ResidentMealProcess.hotArrivalHasServiceTurn(state, actorId, arrival)) return false;
                FrontierV3GoalNavigation.stop(body);
                var result = submit(runtime, "ambient-meal-arrived", actorId.value(), arrival);
                FrontierV3DiagnosticTrace.record(level.getServer(), "resident-meal:" + actorId.value(),
                        "resident_meal_hot_arrived", actorId, result);
                return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
            }
            if (meal.phase() == ResidentMeal.Phase.RETURN && observedBody(body).equals(lease.goalBody())) {
                FrontierV3GoalNavigation.stop(body);
                var result = submit(runtime, "ambient-meal-cleared", actorId.value(),
                        new ResidentMealHotReturned(actorId, lease.revision(), observedBody(body), meal.executionId()));
                FrontierV3DiagnosticTrace.record(level.getServer(), "resident-meal:" + actorId.value(),
                        "resident_meal_hot_cleared", actorId, result);
                return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
            }
            if (ServiceAccessCoordinator.witnessedMealExit(state, meal, observedBody(body))
                    || meal.phase() == ResidentMeal.Phase.CLEAR_ACCESS
                        && ServiceAccessCoordinator.boundary(state, meal.depotId()).cleared(observedBody(body))) {
                var result = submit(runtime, "ambient-meal-access-cleared", actorId.value(),
                        new ResidentMealHotAccessCleared(actorId, lease.revision(), observedBody(body), meal.executionId()));
                FrontierV3DiagnosticTrace.record(level.getServer(), "resident-meal:" + actorId.value(),
                        "resident_meal_hot_access_cleared", actorId, result);
                return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
            }
            return FrontierV3ResidentMealPhysicalEffect.tick(level, runtime, state, actorId, body, lease);
        }
        if (lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) return observeAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) return observeEngineeringAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY) return observeHiveAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_RETURN) return FrontierV3HiveReturnMotion.observeArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL) return observeScoutPatrolArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.WORK) {
            return FrontierV3SurfaceObservation.at(body, lease.goalBody().supportingSurface())
                    && drainAfterDemandHysteresis(level, runtime, actorId, body);
        }
        if (lease.goal() != AmbientGoalKind.TRANSIT) return false;
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (journey == null || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving()
                || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(journey.nextColdPosition())))) return false;
        if (!observedBody(body).equals(lease.goalBody())) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-transit", actorId.value(),
                new ResidentTransitAdvanced(actorId, journey.nextRouteIndex(), journey.executionId()));
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-transit:" + actorId.value(), "resident_transit_advanced", actorId, result);
        return true;
    }
    private static boolean observeAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                   SubjectId actorId, Mob body, AmbientActorLease lease) {
        OperationAssembly.Member member = assemblyMember(state, actorId, lease);
        if (member == null || member.arrived() || !observedBody(body).equals(lease.goalBody())) return false;
        RouteOperation operation = assemblingOperation(state, actorId); OperationAssembly assembly = operation.activeAssembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) {
            FrontierV3GoalNavigation.stop(body);
            return false;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-operation-assembly", actorId.value(),
                new OperationAssemblyAdvanced(operation.id(), assembly.advance(actorId), OperationExecutionAuthority.assemblyCurrent(state, operation)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "operation-assembly:" + operation.id().value(), "operation_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeEngineeringAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                              FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        EngineeringWorkAssembly.Member member = engineeringAssemblyMember(state, actorId, lease);
        EngineeringWorkOrder project = engineeringProject(state, actorId);
        if (project == null || member == null || member.arrived()
                || !observedBody(body).equals(lease.goalBody())) return false;
        EngineeringWorkAssembly assembly = project.assembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-engineering-assembly", actorId.value(),
                assemblyAdvanced(project, assembly.advance(actorId)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "engineering:" + project.id().value(),
                "engineering_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeHiveAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                       FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        HiveMobilization mobilization = assemblingMobilization(state, actorId);
        HiveTaskAssembly.Member member = hiveAssemblyMember(state, actorId, lease);
        if (mobilization == null || member == null || member.arrived() || !observedBody(body).equals(lease.goalBody())) return false;
        HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) {
            FrontierV3GoalNavigation.stop(body);
            return false;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-hive-assembly", actorId.value(),
                new HiveMobilizationAssemblyAdvanced(mobilization.id(), actorId, member.cursor(),
                        io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyExecutionAuthority.current(state, mobilization.id(), actorId)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "hive-assembly:" + mobilization.id().value(),
                "hive_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeScoutPatrolArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                      SubjectId actorId, Mob body, AmbientActorLease lease) {
        BlockPosition current = state.actorLocations().get(actorId).supportingSurface().support();
        if (!observedBody(body).equals(lease.goalBody())) return false;
        BlockPosition expected = HiveScoutPatrolProcess.nextPosition(state, actorId, current);
        if (!lease.goalBody().supportingSurface().support().equals(expected)) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-scout-patrol", actorId.value(),
                new ScoutPatrolAdvanced(HiveScoutPatrolProcess.requireExecution(state, actorId),
                        runtime.canonicalState().orElseThrow().instant().ticks(), lease.goalBody().supportingSurface().support(), current));
        FrontierV3DiagnosticTrace.record(level.getServer(), "scout-patrol:" + actorId.value(), "scout_patrol_advanced", actorId, result);
        return true;
    }
    private static RouteOperation assemblingOperation(FrontierWorldState state, SubjectId actorId) {
        return state.operations().values().stream().filter(operation -> operation.stage() == OperationStage.ASSEMBLING)
                .filter(operation -> operation.activeAssembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .findFirst().orElse(null);
    }
    private static BlockPosition assemblyObstruction(ServerLevel level, FrontierWorldState state, RouteOperation operation, BlockPosition target) {
        if (!FrontierV3StandingPosition.hasExactStandingColumn(level, target)) return target;
        Settlement settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(operation.settlementId())).findFirst().orElse(null);
        SettlementStructure hall = settlement == null ? null : settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst().orElse(null);
        BlockPosition throat = hall == null ? null : SettlementAccessPort.forHall(hall).throatSurface().support();
        return throat != null && !FrontierV3StandingPosition.hasExactStandingColumn(level, throat) ? throat : null;
    }
    static OperationAssembly.Member assemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        RouteOperation operation = assemblingOperation(state, actorId);
        if (operation == null || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY) return null;
        OperationAssembly.Member member = operation.activeAssembly().orElseThrow().members().get(actorId);
        SurfaceAnchor expected = member.arrived() ? member.currentSurface() : member.nextSurface();
        return lease.goalBody().equals(expected.standingBody()) ? member : null;
    }
    private static EngineeringWorkOrder engineeringProject(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.routeConstructions().values().stream(), state.routeMaintenances().values().stream())
                .filter(project -> project.assembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .reduce((left, right) -> { throw new IllegalStateException("engineering assembly owner is ambiguous"); }).orElse(null);
    }
    static EngineeringWorkAssembly.Member engineeringAssemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        EngineeringWorkOrder project = engineeringProject(state, actorId);
        if (project == null || lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY) return null;
        EngineeringWorkAssembly.Member member = project.assembly().orElseThrow().members().get(actorId);
        BlockPosition expected = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
        return lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(expected))) ? member : null;
    }
    private static HiveMobilization assemblingMobilization(FrontierWorldState state, SubjectId actorId) {
        return io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyExecutionAuthority.owner(state, actorId)
                .filter(mobilization -> mobilization.status() == HiveMobilizationStatus.ASSEMBLING)
                .orElse(null);
    }
    static HiveTaskAssembly.Member hiveAssemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        HiveMobilization mobilization = assemblingMobilization(state, actorId);
        if (mobilization == null || lease.goal() != AmbientGoalKind.HIVE_TASK_ASSEMBLY) return null;
        HiveTaskAssembly.Member member = mobilization.assembly().orElseThrow().members().get(actorId);
        SurfaceAnchor expected = member.arrived() ? member.currentSurface() : member.nextSurface();
        return lease.goalBody().equals(expected.standingBody()) ? member : null;
    }
    private static FrontierPayload assemblyAdvanced(EngineeringWorkOrder project, EngineeringWorkAssembly assembly) {
        return switch (project) {
            case RouteConstruction construction -> new RouteConstructionAssemblyAdvanced(construction.id(), assembly);
            case RouteMaintenance maintenance -> new RouteMaintenanceAssemblyAdvanced(maintenance.id(), assembly);
        };
    }
}
