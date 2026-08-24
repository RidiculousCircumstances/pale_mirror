import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const sourceRoot = path.join(root, 'pale-mirror-visuals', 'src', 'main');
const resourceRoot = path.join(sourceRoot, 'resources', 'assets', 'pale_mirror_visuals', 'geo', 'harvester');
const projectRoot = path.join(sourceRoot, 'blockbench', 'harvester');
const names = ['biomass_collector', 'crusher_stalker', 'scythe_stalker'];

const writeJson = (file, value) => fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
const hash = value => {
    let result = 2166136261;
    for (const character of value) result = Math.imul(result ^ character.charCodeAt(0), 16777619);
    return result >>> 0;
};
const uuid = value => {
    const part = salt => hash(`${value}:${salt}`).toString(16).padStart(8, '0');
    return `${part('a')}-${part('b').slice(0, 4)}-${part('c').slice(0, 4)}-${part('d').slice(0, 4)}-${part('e')}${part('f')}`;
};
const clone = value => JSON.parse(JSON.stringify(value));

function findOutlinerNode(nodes, groupId) {
    for (const node of nodes) {
        if (node.uuid === groupId) return node;
        const child = findOutlinerNode(node.children ?? [], groupId);
        if (child) return child;
    }
    return undefined;
}

function openModel(name) {
    const geoFile = path.join(resourceRoot, `${name}.geo.json`);
    const projectFile = path.join(projectRoot, `${name}.bbmodel`);
    const geo = JSON.parse(fs.readFileSync(geoFile, 'utf8'));
    const project = JSON.parse(fs.readFileSync(projectFile, 'utf8'));
    const model = geo['minecraft:geometry'][0];
    const bones = new Map(model.bones.map(bone => [bone.name, bone]));
    const groups = new Map(project.groups.map(group => [group.name, group]));
    const elementIndex = new Map(project.elements.map((element, index) => [element.name, index]));
    const textureUuid = project.textures[0].uuid;

    if (project.elements.some(element => element.name.startsWith('reference_detail_'))) {
        throw new Error(`${name}: reference-detail pass already applied`);
    }

    const setExisting = (elementName, patch) => {
        const index = elementIndex.get(elementName);
        if (index === undefined) throw new Error(`${name}: missing ${elementName}`);
        const element = project.elements[index];
        Object.assign(element, clone(patch));
        const cube = model.bones.flatMap(bone => bone.cubes ?? [])[index];
        if (!cube) throw new Error(`${name}: cannot find ${elementName} geometry cube`);
        const size = element.to.map((value, axis) => value - element.from[axis]);
        cube.origin = [-(element.from[0] + size[0]), element.from[1], element.from[2]];
        cube.size = size;
        if (element.inflate !== undefined) cube.inflate = element.inflate;
    };

    const add = (boneName, suffix, from, size, options = {}) => {
        const bone = bones.get(boneName);
        const group = groups.get(boneName);
        if (!bone || !group) throw new Error(`${name}: missing bone/group ${boneName}`);
        const fullName = `reference_detail_${suffix}`;
        const id = uuid(`${name}:cube:${fullName}`);
        const uv = [hash(`${fullName}:u`) % 180, hash(`${fullName}:v`) % 180];
        const to = from.map((value, axis) => value + size[axis]);
        const element = {
            name: fullName,
            from,
            to,
            origin: options.pivot ?? group.origin,
            uv_offset: uv,
            color: hash(fullName) % 8,
            autouv: 0,
            inflate: options.inflate ?? 0,
            faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(face => [face, {
                uv: [0, 0, 1, 1], texture: textureUuid
            }])),
            type: 'cube',
            uuid: id
        };
        if (options.rotation) element.rotation = options.rotation;
        project.elements.push(element);
        const cube = {
            origin: [-(from[0] + size[0]), from[1], from[2]],
            size,
            uv
        };
        if (element.inflate) cube.inflate = element.inflate;
        if (options.rotation) {
            const pivot = options.pivot ?? group.origin;
            cube.pivot = [-pivot[0], pivot[1], pivot[2]];
            cube.rotation = [-options.rotation[0], -options.rotation[1], options.rotation[2]];
        }
        (bone.cubes ??= []).push(cube);
        const outliner = findOutlinerNode(project.outliner, group.uuid);
        if (!outliner) throw new Error(`${name}: missing outliner node ${boneName}`);
        outliner.children.push(id);
    };
    return {geoFile, projectFile, geo, project, add, setExisting};
}

function collector() {
    const model = openModel('biomass_collector');
    const {add, setExisting} = model;

    // Replace the oversized rectangular "nodules" with a layered, breathing
    // row of individual sacs.  The base plates remain the structural shell.
    const shellCenters = [-15, -8.5, -1, 7, 14.5, 21];
    const shellHeights = [19.8, 22.1, 24.1, 23.7, 21.9, 19.5];
    shellCenters.forEach((center, index) => {
        const top = shellHeights[index];
        setExisting(`nodule_${index + 1}`, {
            from: [center - 1.75, top - .9, -3.6],
            to: [center + 1.75, top + 2.2, 3.6],
            inflate: .95
        });
        add('carapace', `collector_sac_${index}_center`, [center - 1.7, top + .4, -3.25], [3.4, 2.6, 6.5], {inflate: 1.08});
        add('carapace', `collector_sac_${index}_front`, [center - 3.0, top - .25, -3.0], [2.55, 2.45, 6.0], {inflate: .9});
        add('carapace', `collector_sac_${index}_rear`, [center + .45, top - .25, -3.0], [2.55, 2.45, 6.0], {inflate: .9});
        add('carapace', `collector_sac_${index}_left`, [center - 1.35, top + .05, -6.25], [2.7, 2.3, 2.45], {inflate: .82});
        add('carapace', `collector_sac_${index}_right`, [center - 1.35, top + .05, 3.8], [2.7, 2.3, 2.45], {inflate: .82});
        add('carapace', `collector_sac_${index}_seam`, [center - .42, top + 2.25, -4.6], [.84, .72, 9.2], {inflate: .22});
    });
    // Raised, uneven dorsal fibres break up the formerly flat horizontal top.
    [-17.5, -13, -10.2, -6.2, -3.5, .5, 3.6, 7.8, 11.2, 15.8, 19.2, 22.2].forEach((x, index) => {
        const y = 20.6 + Math.max(0, 4.1 - Math.abs(x - 2) * .18);
        add('carapace', `collector_dorsal_bead_${index}`, [x, y, -1.35], [1.3, 1.1 + (index % 3) * .26, 2.7], {inflate: .58});
    });
    // Segmented elephantine legs: joint rings and split toe pads read much
    // closer to the heavy, wrinkled supports in the supplied collector art.
    for (let index = 1; index <= 6; index++) for (const side of ['l', 'r']) {
        const tag = `${index}_${side}`;
        const upper = `leg_${tag}_upper`;
        const lower = `leg_${tag}_lower`;
        const foot = `leg_${tag}_foot`;
        const z = side === 'l' ? -6 : 6;
        for (let band = 0; band < 3; band++) {
            add(upper, `collector_${tag}_upper_ring_${band}`, [-17 + (index - 1) * 6 - .18, 8.35 + band * 1.62, z - 2.1], [3.36, .62, 4.2], {inflate: .25});
            add(lower, `collector_${tag}_lower_ring_${band}`, [-16.8 + (index - 1) * 6 - .12, 2.45 + band * 1.34, z - 1.78], [3.04, .55, 3.56], {inflate: .2});
        }
        [-1.45, 0, 1.45].forEach((toe, toeIndex) => {
            add(foot, `collector_${tag}_toe_${toeIndex}`, [-17.2 + (index - 1) * 6 + toe, -.15, z - 2.3], [1.55, 1.1, 3.8], {inflate: .28});
        });
    }
    for (let segment = 2; segment <= 5; segment++) {
        add(`tail_${segment}`, `collector_tail_frill_${segment}`, [-25 - (segment - 2) * 5, 13.4 + (segment - 2) * .35, -1.0], [3.6, 2.0, 2.0], {inflate: .45});
    }
    for (const side of ['l', 'r']) for (let segment = 1; segment <= 5; segment++) {
        const sign = side === 'l' ? -1 : 1;
        add(`feeler_${side}_${segment}`, `collector_feeler_${side}_${segment}`, [-25 - (segment - 1) * 3, 14.3 + (segment - 1) * .6, sign * (4.3 + (segment - 1) * 1.1) - 1.05], [3.9, .9, 2.1], {inflate: .22});
    }
    return model;
}

function crusher() {
    const model = openModel('crusher_stalker');
    const {add, setExisting} = model;
    setExisting('torso_core', {inflate: .82});
    // A deep humped back made from interlocking muscle masses, not one box.
    [-5.8, -2.8, .3, 3.4, 6.4].forEach((x, index) => {
        const y = index === 2 ? 21.5 : index === 1 || index === 3 ? 20.9 : 20.0;
        add('torso', `crusher_hump_${index}_core`, [x, y, -4.4], [3.45, 3.1, 8.8], {inflate: 1.05});
        add('torso', `crusher_hump_${index}_left_fibre`, [x + .4, y + .45, -6.0], [2.4, 1.9, 2.3], {inflate: .65});
        add('torso', `crusher_hump_${index}_right_fibre`, [x + .4, y + .45, 3.7], [2.4, 1.9, 2.3], {inflate: .65});
        add('torso', `crusher_hump_${index}_ridge`, [x + .72, y + 2.55, -3.6], [2.1, .75, 7.2], {inflate: .28});
    });
    [-5.5, -3.1, -.7, 1.7, 4.1, 6.5].forEach((x, index) => {
        add('torso', `crusher_spinal_cord_${index}`, [x, 22.1 + (index % 2) * .55, -1.05], [1.15, 1.2, 2.1], {inflate: .48});
    });
    add('head', 'crusher_brow_left', [-15.8, 17.4, -4.9], [4.9, 1.55, 2.25], {inflate: .55, rotation: [0, 0, -11], pivot: [-10, 16, 0]});
    add('head', 'crusher_brow_right', [-15.8, 17.4, 2.65], [4.9, 1.55, 2.25], {inflate: .55, rotation: [0, 0, -11], pivot: [-10, 16, 0]});
    for (const side of ['l', 'r']) {
        const sign = side === 'l' ? -1 : 1;
        const arm = `crusher_arm_${side}`;
        const forearm = `${arm}_forearm`;
        const fist = `${arm}_fist`;
        for (let band = 0; band < 4; band++) {
            add(arm, `crusher_${side}_upper_fibre_${band}`, [-10.25, 13.0 + band * 1.35, sign * 5.0], [5.4, .64, 3.85], {inflate: .25});
        }
        for (let band = 0; band < 3; band++) {
            add(forearm, `crusher_${side}_fore_fibre_${band}`, [-13.45, 8.15 + band * 1.48, sign * 4.75], [5.1, .62, 4.35], {inflate: .25});
        }
        [-1.65, 0, 1.65].forEach((offset, digit) => {
            add(fist, `crusher_${side}_knuckle_${digit}`, [-17.4, 5.15, sign * 6.0 + offset], [3.5, 2.7, 1.3], {inflate: .58});
            add(fist, `crusher_${side}_talon_${digit}`, [-18.6, 4.35, sign * 6.0 + offset], [2.45, 1.25, 1.0], {inflate: .25, rotation: [0, 0, sign * -16], pivot: [-13, 8, sign * 6.5]});
        });
    }
    for (let leg = 1; leg <= 4; leg++) {
        const sign = leg % 2 ? -1 : 1;
        const upper = `hind_leg_${leg}_upper`;
        const lower = `hind_leg_${leg}_lower`;
        for (let band = 0; band < 3; band++) {
            add(upper, `crusher_leg_${leg}_upper_ring_${band}`, [3.4 + (leg > 2 ? 6 : 0), 10.0 + band * 1.52, sign * 5.5 - 1.75], [3.35, .58, 3.5], {inflate: .22});
            add(lower, `crusher_leg_${leg}_lower_ring_${band}`, [5.2 + (leg > 2 ? 6 : 0), 3.3 + band * 1.62, sign * 5.0 - 1.45], [2.9, .54, 2.9], {inflate: .2});
        }
        [-1.25, 0, 1.25].forEach((offset, claw) => add(lower, `crusher_leg_${leg}_claw_${claw}`, [4.5 + (leg > 2 ? 6 : 0) + offset, .65, sign * 5.5 - 1.3], [1.1, 1.0, 3.1], {inflate: .2}));
    }
    for (let segment = 1; segment <= 5; segment++) for (let fibre = 0; fibre < 2; fibre++) {
        add(`rear_tendril_${segment}`, `crusher_tendril_${segment}_${fibre}`, [7.8 + (segment - 1) * 4, 17.0 + (segment % 2), fibre ? .55 : -2.0], [4.8, .72, 1.3], {inflate: .18});
    }
    return model;
}

function scythe() {
    const model = openModel('scythe_stalker');
    const {add, setExisting} = model;
    setExisting('abdomen_core', {inflate: .72});
    [-4.8, -1.6, 1.6, 4.8, 8].forEach((x, index) => {
        const y = index === 2 ? 20.7 : index === 1 || index === 3 ? 20.2 : 19.5;
        add('abdomen', `scythe_back_lump_${index}`, [x, y, -3.9], [3.0, 2.55, 7.8], {inflate: .95});
        add('abdomen', `scythe_back_lump_${index}_ridge`, [x + .65, y + 2.05, -4.75], [1.7, .9, 9.5], {inflate: .26});
        add('abdomen', `scythe_back_lump_${index}_side_l`, [x + .3, y + .25, -6.0], [2.45, 1.9, 2.25], {inflate: .65});
        add('abdomen', `scythe_back_lump_${index}_side_r`, [x + .3, y + .25, 3.75], [2.45, 1.9, 2.25], {inflate: .65});
    });
    for (const side of ['l', 'r']) {
        const sign = side === 'l' ? -1 : 1;
        const arm = `scythe_arm_${side}`;
        const forearm = `${arm}_fore`;
        const blade = `${arm}_blade`;
        for (let band = 0; band < 3; band++) {
            add(arm, `scythe_${side}_upper_cord_${band}`, [-13.2, 13.4 + band * 1.55, sign * 4.05], [5.45, .64, 3.15], {inflate: .2});
            add(forearm, `scythe_${side}_fore_cord_${band}`, [-17.1, 8.8 + band * 1.3, sign * 4.85], [5.85, .55, 2.95], {inflate: .18});
        }
        const curve = [[-21.7, 6.0, 4.5, 1.35], [-23.2, 4.35, 3.8, 1.15], [-24.4, 2.8, 3.1, 1.0], [-25.1, 1.55, 2.35, .85], [-25.45, .55, 1.7, .7]];
        curve.forEach(([x, y, width, height], index) => {
            add(blade, `scythe_${side}_blade_lamella_${index}`, [x, y, sign * 5.5], [width, height, 2.15 - index * .16], {
                inflate: .17,
                rotation: [0, 0, sign * (-11 - index * 7)],
                pivot: [-16, 10, sign * 6.5]
            });
        });
        curve.slice(1).forEach(([x, y, width], index) => {
            add(blade, `scythe_${side}_blade_edge_${index}`, [x - .55, y - .5, sign * 5.3], [width * .64, .62, 2.35 - index * .2], {
                inflate: .12,
                rotation: [0, 0, sign * (-16 - index * 8)],
                pivot: [-16, 10, sign * 6.5]
            });
        });
    }
    for (let leg = 1; leg <= 6; leg++) {
        const sign = leg % 2 ? -1 : 1;
        const upper = `stalker_leg_${leg}_upper`;
        const lower = `stalker_leg_${leg}_lower`;
        const x = leg <= 2 ? -2 : leg <= 4 ? 5 : 10;
        for (let band = 0; band < 3; band++) {
            add(upper, `scythe_leg_${leg}_upper_ring_${band}`, [x - 1.5, 9.0 + band * 1.37, sign * 4.8 - 1.55], [3.0, .48, 3.1], {inflate: .17});
            add(lower, `scythe_leg_${leg}_lower_ring_${band}`, [x + sign * .45 - 1.25, 2.25 + band * 1.55, sign * 4.7 - 1.35], [2.65, .46, 2.65], {inflate: .15});
        }
        [-1.2, 0, 1.2].forEach((offset, toe) => add(lower, `scythe_leg_${leg}_toe_${toe}`, [x + sign * .25 + offset, .15, sign * 4.65 - 1.5], [1.0, .72, 3.15], {inflate: .13}));
    }
    for (const side of ['l', 'r']) for (let segment = 1; segment <= 5; segment++) {
        const sign = side === 'l' ? -1 : 1;
        add(`whip_${side}_${segment}`, `scythe_whip_${side}_${segment}`, [-13 + (segment - 1) * 4, 18.3 + (segment % 2), sign * (4.0 + segment) - .75], [5.2, .68, 1.5], {inflate: .12});
    }
    return model;
}

const models = [collector(), crusher(), scythe()];
for (const model of models) {
    writeJson(model.geoFile, model.geo);
    writeJson(model.projectFile, model.project);
}

for (const name of names) {
    const geo = JSON.parse(fs.readFileSync(path.join(resourceRoot, `${name}.geo.json`), 'utf8'));
    const project = JSON.parse(fs.readFileSync(path.join(projectRoot, `${name}.bbmodel`), 'utf8'));
    const cubes = geo['minecraft:geometry'][0].bones.reduce((total, bone) => total + (bone.cubes?.length ?? 0), 0);
    console.log(`${name}: ${geo['minecraft:geometry'][0].bones.length} bones, ${cubes} runtime cubes, ${project.elements.length} Blockbench cubes`);
}
