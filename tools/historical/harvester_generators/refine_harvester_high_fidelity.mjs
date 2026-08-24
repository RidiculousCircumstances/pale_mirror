import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const root = path.resolve(import.meta.dirname, '..');
const sourceRoot = path.join(root, 'pale-mirror-visuals', 'src', 'main');
const assets = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals');
const names = ['biomass_collector', 'crusher_stalker', 'scythe_stalker'];
const textureResolution = 256;
const writeJson = (file, value) => fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);

const textureBuild = spawnSync('python3', [path.join(root, 'tools', 'build_harvester_texture_sheets.py'), root], {
    encoding: 'utf8'
});
if (textureBuild.status !== 0) throw new Error(textureBuild.stderr || 'Could not build harvester texture sheets');

for (const name of names) {
    const geoFile = path.join(assets, 'geo', 'harvester', `${name}.geo.json`);
    const projectFile = path.join(sourceRoot, 'blockbench', 'harvester', `${name}.bbmodel`);
    const geo = JSON.parse(fs.readFileSync(geoFile, 'utf8'));
    const project = JSON.parse(fs.readFileSync(projectFile, 'utf8'));
    const description = geo['minecraft:geometry'][0].description;

    if (project.resolution.width !== textureResolution || project.resolution.height !== textureResolution) {
        const scale = textureResolution / project.resolution.width;
        project.resolution = {width: textureResolution, height: textureResolution};
        for (const element of project.elements) element.uv_offset = element.uv_offset.map(value => value * scale);
    }
    description.texture_width = textureResolution;
    description.texture_height = textureResolution;

    const geoCubes = geo['minecraft:geometry'][0].bones.flatMap(bone => bone.cubes ?? []);
    if (geoCubes.length !== project.elements.length) throw new Error(`${name}: geometry/source cube count drifted`);
    for (const [index, element] of project.elements.entries()) {
        // Bedrock's native box UV layout is used here. Keep the source and
        // exported Geo model in lockstep so a normal Blockbench view is a
        // faithful preview of the runtime texture mapping.
        element.autouv = 0;
        element.faces = Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(face => [face, {
            uv: [0, 0, 1, 1], texture: project.textures[0].uuid
        }]));
        geoCubes[index].uv = [...element.uv_offset];
    }
    writeJson(geoFile, geo);
    writeJson(projectFile, project);
}
