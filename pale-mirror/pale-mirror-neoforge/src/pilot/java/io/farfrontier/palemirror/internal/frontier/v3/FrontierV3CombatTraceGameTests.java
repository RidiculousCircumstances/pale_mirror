package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.*;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Native damage subscribers and actual JFR export, without changing combat decisions. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3CombatTraceGameTests {
    @GameTest(batch="pm-frontier-v3-scene-strikes",templateNamespace="minecraft",template="bastion/treasure/big_air_full",timeoutTicks=1200)
    public static void nativePositionsAndDamageProduceACompleteReadOnlyCapture(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        var position=helper.absolutePos(new BlockPos(4,8,4));player.setPos(position.getX()+0.5,position.getY(),position.getZ()+0.5);
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(5,8,4));target.setNoAi(true);target.setNoGravity(true);
        var server=helper.getLevel().getServer();float before=target.getHealth();var stage=new AtomicInteger();var tick=new AtomicInteger();
        String opened=FrontierV3CombatTrace.start(server,player,1,30);
        String id=opened.substring(opened.indexOf("capture=")+8,opened.indexOf(" events="));
        var file=server.getServerDirectory().resolve("debug/pale-mirror/combat/combat-"+id+".jsonl");
        helper.onEachTick(()->{
            try {
                String status=FrontierV3CombatTrace.status(server);
                helper.assertFalse(status.startsWith("FAILED"),status);
                if(stage.get()==0 && status.startsWith("RECORDING")) {
                    target.hurt(player.damageSources().playerAttack(player),1);
                    helper.assertTrue(target.getHealth()==before-1,"trace does not change the actual one-point native damage");stage.set(1);tick.set(0);
                } else if(stage.get()==1 && tick.incrementAndGet()>=3) {
                    FrontierV3CombatTrace.stop(server,"TEST_STOP");stage.set(2);
                } else if(stage.get()==2 && status.startsWith("SAVED")) {
                    var rows=Files.readAllLines(file).stream().map(line->JsonParser.parseString(line).getAsJsonObject()).toList();
                    helper.assertTrue(rows.getLast().get("complete").getAsBoolean(),"diagnostic export includes all committed observations");
                    helper.assertTrue(rows.stream().anyMatch(row->row.get("kind").getAsString().equals("position")
                            && row.getAsJsonObject("body").get("uuid").getAsString().equals(player.getUUID().toString())),"native player coordinates recorded");
                    helper.assertTrue(rows.stream().anyMatch(row->row.get("kind").getAsString().equals("damage_applied")
                            && row.getAsJsonObject("target").get("uuid").getAsString().equals(target.getUUID().toString())
                            && row.get("damage").getAsFloat()==1),"real post-damage event recorded separately from positions");
                    Files.delete(file);Files.delete(file.resolveSibling("combat-"+id+".jfr"));
                    target.discard();player.discard();helper.succeed();
                }
            } catch(Exception failure) {
                FrontierV3CombatTrace.stop(server,"TEST_FAILED");throw new IllegalStateException("native combat trace failed",failure);
            }
        });
    }
    private FrontierV3CombatTraceGameTests() { }
}
