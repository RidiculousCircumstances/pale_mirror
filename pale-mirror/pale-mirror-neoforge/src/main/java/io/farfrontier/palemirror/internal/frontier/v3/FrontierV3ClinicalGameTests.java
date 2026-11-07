package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorItems;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Actual vanilla effects/pose serialization; domain scheduling and a player flow have separate checks. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ClinicalGameTests {
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=30)
    public static void dressingSpendsOneActualBandageWithoutInstantHealingAndSurvivesNativeReload(GameTestHelper h){
        var medic=person(h,2);var patient=person(h,4);patient.setHealth(10);medic.getInventory().setItem(0,new ItemStack(PaleMirrorItems.BANDAGE.get(),3));
        var tend=tend(medic,patient);
        var wall=h.absolutePos(new BlockPos(3,3,3));h.getLevel().setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(FrontierV3ClinicalTreatmentExecutor.dress(medic,patient,tend)==FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT&&medic.getInventory().getItem(0).getCount()==3,"a clinician cannot dress a patient through a room wall");
        h.getLevel().setBlock(wall,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(FrontierV3ClinicalTreatmentExecutor.dress(medic,patient,tend)==FrontierV3NativeEffect.Outcome.APPLIED,"real carried medicine applies once");
        h.assertTrue(medic.getInventory().getItem(0).getCount()==2&&patient.getHealth()==10,"dressing is distinct from gradual HP recovery");
        var saved=medic.saveWithoutId(new CompoundTag());medic.discard();var restored=EntityType.VILLAGER.create(h.getLevel());restored.load(saved);
        h.assertTrue(FrontierV3ClinicalTreatmentExecutor.dress(restored,patient,tend)==FrontierV3NativeEffect.Outcome.APPLIED&&restored.getInventory().getItem(0).getCount()==2,"native witness survives serialization and does not spend again");
        restored.discard();patient.discard();h.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=30)
    public static void newDamageRefusesOldDressingAndRecoveryPreimagesWithoutSpendingMedicine(GameTestHelper h){
        var medic=person(h,2);var patient=person(h,4);patient.setHealth(10);medic.getInventory().setItem(0,new ItemStack(PaleMirrorItems.BANDAGE.get(),3));var tend=tend(medic,patient);
        patient.setHealth(9);
        h.assertTrue(FrontierV3ClinicalTreatmentExecutor.dress(medic,patient,tend)==FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT&&medic.getInventory().getItem(0).getCount()==3,"new harm withdraws an obsolete dressing before consumption");
        var healing=new ClinicalHealing(tend.patient(),10*FixedScalar.SCALE,FixedScalar.SCALE,200,1,201);
        h.assertTrue(FrontierV3ClinicalHealingExecutor.heal(patient,healing)==FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT&&patient.getHealth()==9,"old recovery cannot overwrite a new hit");
        var fresh=new ClinicalHealing(tend.patient(),9*FixedScalar.SCALE,FixedScalar.SCALE,400,2,401);
        h.assertTrue(FrontierV3ClinicalHealingExecutor.heal(patient,fresh)==FrontierV3NativeEffect.Outcome.APPLIED&&patient.getHealth()==10,"finite recovery uses native heal");
        var saved=patient.saveWithoutId(new CompoundTag());patient.discard();var restored=EntityType.VILLAGER.create(h.getLevel());restored.load(saved);
        FrontierV3ClinicalHealingExecutor.heal(restored,fresh);h.assertTrue(restored.getHealth()==10,"saved recovery cannot heal twice");medic.discard();restored.discard();h.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=40)
    public static void bedPoseIsSupportedAndExclusiveAndCanBeExitedAfterTheBedBreaks(GameTestHelper h){
        var patient=person(h,2);var other=person(h,4);var level=h.getLevel();var foot=h.absolutePos(new BlockPos(3,2,3));var head=foot.north();
        level.setBlock(foot,Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT).setValue(BedBlock.FACING,Direction.NORTH),3);
        level.setBlock(head,Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD).setValue(BedBlock.FACING,Direction.NORTH),3);
        var owner=new SubjectId("clinical:native-bed");var actor=new SubjectId("resident:native-bed");
        var authority=new ActorActuationId(new ActorBodyId(actor,1),new ActorExecutionId(actor,ActorActivityKind.CLINICAL_CARE,owner,1));
        var otherId=new SubjectId("resident:other-bed");var otherAuthority=new ActorActuationId(new ActorBodyId(otherId,1),new ActorExecutionId(otherId,ActorActivityKind.CLINICAL_CARE,new SubjectId("clinical:other-bed"),1));
        h.assertTrue(FrontierV3RestPlaceExecutor.changePose(level,patient,head,foot,RestUse.Action.ENTER,authority,1)==FrontierV3NativeEffect.Outcome.APPLIED,"bedside actor enters a real bed");
        h.assertTrue(FrontierV3RestingPose.current(patient)&&FrontierV3BodyObservation.capture(patient).support().orElseThrow().support().equals(new BlockPosition(head.getX(),head.getY(),head.getZ())),
            "sleeping native dimensions retain exact bed support without a broad contact tolerance");
        h.assertTrue(FrontierV3RestPlaceExecutor.changePose(level,other,head,foot,RestUse.Action.ENTER,otherAuthority,1)==FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT,"another native sleeper cannot be displaced");
        patient.stopSleeping();
        var successor=new ActorActuationId(authority.body(),new ActorExecutionId(actor,ActorActivityKind.CLINICAL_CARE,owner,2));
        h.assertTrue(FrontierV3RestPlaceExecutor.changePose(level,patient,head,foot,RestUse.Action.ENTER,successor,1)==FrontierV3NativeEffect.Outcome.APPLIED&&patient.isSleeping(),
            "a later stay with the same task ID cannot reuse an old pose witness");
        level.setBlock(head,Blocks.AIR.defaultBlockState(),3);level.setBlock(foot,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(FrontierV3RestPlaceExecutor.changePose(level,patient,head,foot,RestUse.Action.EXIT,successor,2)==FrontierV3NativeEffect.Outcome.APPLIED&&!patient.isSleeping(),"removing a bed still permits waking the retained physical patient");
        patient.discard();other.discard();h.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=40)
    public static void actualNativeRecipeCraftsFourBandagesFromOneCloth(GameTestHelper h){
        var recipe=h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID,"bandages")).orElseThrow().value();
        var input=net.minecraft.world.item.crafting.CraftingInput.of(1,1,List.of(new ItemStack(PaleMirrorItems.CLOTH.get())));
        h.assertTrue(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe,"bandage production retains an actual native crafting recipe");
        var crafting=(net.minecraft.world.item.crafting.CraftingRecipe)recipe;
        var result=crafting.assemble(input,h.getLevel().registryAccess());
        h.assertTrue(crafting.matches(input,h.getLevel())&&result.is(PaleMirrorItems.BANDAGE.get())&&result.getCount()==4,"native recipe yields four bandages from one cloth without fuel");
        h.succeed();
    }
    @GameTest(batch="pm-frontier-v3-scene-local-navigation",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=40)
    public static void actualClinicalCatalogMatchesNativeRecipeAndFuelDefinitions(GameTestHelper h){
        try{FrontierV3NativeProductionDefinitions.validate(h.getLevel().getServer(),FrontierRulesets.installed("frontier-v3-clinical-care-r35").production());}
        catch(IllegalArgumentException mismatch){h.fail(mismatch.getMessage());return;}h.succeed();
    }
    private static Villager person(GameTestHelper h,int x){
        var floor=h.absolutePos(new BlockPos(x,1,3));h.getLevel().setBlock(floor,Blocks.STONE.defaultBlockState(),3);
        for(int y=1;y<=3;y++)h.getLevel().setBlock(floor.above(y),Blocks.AIR.defaultBlockState(),3);
        return h.spawnWithNoFreeWill(EntityType.VILLAGER,new net.minecraft.world.phys.Vec3(x+.5,2,3.5));
    }
    private static ClinicalTend tend(Villager medic,Villager patient){
        var actor=new SubjectId("resident:native-medic");var target=new SubjectId("resident:native-patient");var owner=new SubjectId("clinical:native-tend");
        var a=new ActorActuationId(new ActorBodyId(actor,1),new ActorExecutionId(actor,ActorActivityKind.CLINICAL_CARE,owner,1));
        var p=new ActorActuationId(new ActorBodyId(target,1),new ActorExecutionId(target,ActorActivityKind.CLINICAL_CARE,new SubjectId("clinical:native-patient"),1));
        var lot=new SubjectId("lot:native-bandage");var binding=new PhysicalStackBinding(new SubjectId("binding:native-bandage"),new SubjectId("custody:native-bandage"),
                new PhysicalStackAddress.ActorPocket(actor,medic.getUUID(),0),1,"pale_mirror:bandage",Map.of(lot,3),Map.of(),"");
        return new ClinicalTend(a,p,binding,lot,ClinicalWound.observed(target,FixedScalar.whole(10),FixedScalar.whole(20),0),700,4*FixedScalar.SCALE,1,0,100);
    }
    private FrontierV3ClinicalGameTests(){}
}
