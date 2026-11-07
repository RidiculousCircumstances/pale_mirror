package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.BlockGetter;
import java.util.*;

/** Native bed pose evidence, independent of profession or care. It grants no rest reservation or movement. */
final class FrontierV3RestingPose {
    static boolean sleepingDimensions(LivingEntity entity){return entity.isSleeping()&&entity.getPose()==Pose.SLEEPING&&entity.getSleepingPos().isPresent();}
    static boolean otherSleeper(net.minecraft.server.level.ServerLevel level,BlockPos head,BlockPos foot,LivingEntity actor){
        return !level.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(head).minmax(new net.minecraft.world.phys.AABB(foot)).inflate(.5),
            other->other!=actor&&other.isSleeping()&&other.getSleepingPos().filter(p->p.equals(head)||p.equals(foot)).isPresent()).isEmpty();
    }
    static boolean at(Entity entity,BlockGetter geometry,BlockPos support){
        if(!(entity instanceof LivingEntity living)||!living.isSleeping()||living.getPose()!=Pose.SLEEPING
                ||!living.getSleepingPos().equals(Optional.of(support))||!geometry.getBlockState(support).isBed(entity.level(),support,living))return false;
        return Math.abs(entity.getX()-(support.getX()+0.5))<0.001&&Math.abs(entity.getZ()-(support.getZ()+0.5))<0.001
                &&Math.abs(entity.getY()-(support.getY()+0.6875))<0.001;
    }
    static boolean current(LivingEntity entity){
        var position=entity.getSleepingPos();
        if(position.isEmpty()||!(entity.level() instanceof net.minecraft.server.level.ServerLevel level))return false;
        var pos=position.orElseThrow();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk!=null&&at(entity,chunk,pos);
    }
    private FrontierV3RestingPose(){}
}
