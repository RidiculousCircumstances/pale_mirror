package io.farfrontier.palemirror.internal.client;

import com.google.gson.Gson;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Client distribution manifest, never a world or simulation owner. External author archives remain separate. */
final class ResidentResourcePacks {
    record Archive(String file,String sha512){}
    record Manifest(int schema,boolean enabled,String rig,List<Archive> packs){}
    private static boolean enabled;
    static boolean enabled(){return enabled;}
    static void register(AddPackFindersEvent event){
        if(event.getPackType()!=PackType.CLIENT_RESOURCES)return;
        var directory=FMLPaths.CONFIGDIR.get().resolve("pale_mirror/resident-packs");var path=directory.resolve("manifest.json");
        enabled=false;if(!Files.isRegularFile(path))return;
        try{
            Manifest manifest;try(var reader=Files.newBufferedReader(path)){manifest=new Gson().fromJson(reader,Manifest.class);}
            validate(manifest);if(!manifest.enabled())return;
            if(!ModList.get().isLoaded("entity_model_features") || !ModList.get().isLoaded("entity_texture_features"))
                throw new IllegalStateException("Human residents require the client EMF and ETF mods; run the pack updater");
            var children=new ArrayList<Pack>();
            for(var archive:manifest.packs()){
                var file=directory.resolve(archive.file());
                if(!Files.isRegularFile(file) || Files.size(file)>32*1024*1024L)throw new IllegalStateException("Missing resident resource archive: "+archive.file());
                var digest=MessageDigest.getInstance("SHA-512");try(var input=Files.newInputStream(file)){byte[] buffer=new byte[65536];for(int read;(read=input.read(buffer))>=0;)digest.update(buffer,0,read);}
                if(!HexFormat.of().formatHex(digest.digest()).equals(archive.sha512()))throw new IllegalStateException("Resident archive checksum mismatch: "+archive.file());
                var pack=Pack.readMetaAndCreate(new PackLocationInfo("pale_mirror/resident/"+archive.file(),Component.literal(archive.file()),PackSource.BUILT_IN,Optional.empty()),
                    new FilePackResources.FileResourcesSupplier(file),PackType.CLIENT_RESOURCES,new PackSelectionConfig(false,Pack.Position.TOP,false));
                if(pack==null)throw new IllegalStateException("Invalid resident resource archive: "+archive.file());children.add(pack);
            }
            var own=ModList.get().getModContainerById(PaleMirrorMod.MOD_ID).orElseThrow().getModInfo().getOwningFile().getFile().findResource("resourcepacks/resident_bundle");
            var parent=Pack.readMetaAndCreate(new PackLocationInfo("pale_mirror/human_residents",Component.literal("Pale Mirror: human residents"),PackSource.BUILT_IN,Optional.empty()),
                BuiltInPackSource.fromName(info->new PathPackResources(info,own)),PackType.CLIENT_RESOURCES,new PackSelectionConfig(true,Pack.Position.TOP,true));
            if(parent==null)throw new IllegalStateException("Missing resident bundle metadata");var group=parent.withChildren(children);
            enabled=true;event.addRepositorySource(consumer->consumer.accept(group));
        }catch(Exception error){throw new IllegalStateException("Cannot load managed human resident resources: "+path,error);}
    }
    static void validate(Manifest value){
        if(value==null || value.schema()!=1 || !"human_era_3_91_5".equals(value.rig()) || value.packs()==null || value.packs().size()!=3)
            throw new IllegalArgumentException("Unsupported resident appearance manifest");
        var files=new HashSet<String>();for(var pack:value.packs())if(pack==null || pack.file()==null || !pack.file().matches("[A-Za-z0-9_.+-]+\\.zip")
                || pack.sha512()==null || !pack.sha512().matches("[a-f0-9]{128}") || !files.add(pack.file()))throw new IllegalArgumentException("Invalid resident archive declaration");
    }
    private ResidentResourcePacks(){}
}
