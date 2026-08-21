import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const projects = path.join(root, 'pale-mirror-visuals', 'src', 'main', 'blockbench', 'harvester');
const creatureNames = ['biomass_collector', 'crusher_stalker', 'scythe_stalker'];
const sourceResolution = 1254;

const writeJson = (file, data) => fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
const hash = value => {
    let result = 2166136261;
    for (const character of value) result = Math.imul(result ^ character.charCodeAt(0), 16777619);
    return result >>> 0;
};
const uuid = value => {
    const part = salt => hash(`${value}:${salt}`).toString(16).padStart(8, '0');
    return `${part('a')}-${part('b').slice(0, 4)}-${part('c').slice(0, 4)}-${part('d').slice(0, 4)}-${part('e')}${part('f')}`;
};
const add = (a, b) => a.map((value, i) => value + b[i]);
const sub = (a, b) => a.map((value, i) => value - b[i]);
const mul = (a, value) => a.map(component => component * value);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const length = vector => Math.hypot(...vector);
const normal = vector => {
    const value = length(vector) || 1;
    return mul(vector, 1 / value);
};
const clone = value => JSON.parse(JSON.stringify(value));

function findOutlinerNode(nodes, id) {
    for (const node of nodes) {
        if (node.uuid === id) return node;
        const found = findOutlinerNode(node.children ?? [], id);
        if (found) return found;
    }
}

function stripElements(nodes) {
    return nodes.map(node => ({
        uuid: node.uuid,
        isOpen: node.isOpen ?? true,
        children: stripElements((node.children ?? []).filter(child => typeof child !== 'string'))
    }));
}

class MeshBuilder {
    constructor(name, bone, texture) {
        this.name = name;
        this.bone = bone;
        this.texture = texture;
        this.vertices = {};
        this.faces = {};
        this.vertexIndex = 0;
        this.faceIndex = 0;
    }

    vertex(position) {
        const id = `v${this.vertexIndex++}`;
        this.vertices[id] = position.map(value => Number(value.toFixed(4)));
        return id;
    }

    face(ids, uvs) {
        const id = `f${this.faceIndex++}`;
        this.faces[id] = {
            uv: Object.fromEntries(ids.map((vertex, index) => [vertex, uvs[index].map(value => Number(value.toFixed(3)))])),
            texture: this.texture,
            vertices: ids
        };
    }

    sphere(center, radius, options = {}) {
        const [rx, ry, rz] = Array.isArray(radius) ? radius : [radius, radius, radius];
        const rings = options.rings ?? 9;
        const sides = options.sides ?? 14;
        const seed = hash(`${this.name}:${this.vertexIndex}`);
        const rows = [];
        const top = this.vertex([center[0], center[1] + ry, center[2]]);
        for (let ring = 1; ring < rings; ring++) {
            const theta = Math.PI * ring / rings;
            const row = [];
            for (let side = 0; side < sides; side++) {
                const phi = Math.PI * 2 * side / sides;
                const wobble = 1 + .045 * Math.sin(phi * 3 + ring * 1.7 + (seed % 7));
                row.push(this.vertex([
                    center[0] + Math.cos(phi) * Math.sin(theta) * rx * wobble,
                    center[1] + Math.cos(theta) * ry,
                    center[2] + Math.sin(phi) * Math.sin(theta) * rz * wobble
                ]));
            }
            rows.push(row);
        }
        const bottom = this.vertex([center[0], center[1] - ry, center[2]]);
        for (let side = 0; side < sides; side++) {
            const next = (side + 1) % sides;
            const u1 = 20 + side / sides * (sourceResolution - 40);
            const u2 = 20 + (side + 1) / sides * (sourceResolution - 40);
            const v = 20 + (sourceResolution - 40) / rings;
            this.face([top, rows[0][side], rows[0][next]], [[(u1 + u2) / 2, 20], [u1, v], [u2, v]]);
        }
        for (let ring = 0; ring < rows.length - 1; ring++) for (let side = 0; side < sides; side++) {
            const next = (side + 1) % sides;
            const u1 = 20 + side / sides * (sourceResolution - 40);
            const u2 = 20 + (side + 1) / sides * (sourceResolution - 40);
            const v1 = 20 + (ring + 1) / rings * (sourceResolution - 40);
            const v2 = 20 + (ring + 2) / rings * (sourceResolution - 40);
            this.face([rows[ring][side], rows[ring][next], rows[ring + 1][next], rows[ring + 1][side]], [[u1, v1], [u2, v1], [u2, v2], [u1, v2]]);
        }
        for (let side = 0; side < sides; side++) {
            const next = (side + 1) % sides;
            const u1 = 20 + side / sides * (sourceResolution - 40);
            const u2 = 20 + (side + 1) / sides * (sourceResolution - 40);
            const v = sourceResolution - 20 - (sourceResolution - 40) / rings;
            this.face([rows.at(-1)[next], rows.at(-1)[side], bottom], [[u2, v], [u1, v], [(u1 + u2) / 2, sourceResolution - 20]]);
        }
    }

    tube(points, radii, options = {}) {
        const sides = options.sides ?? 9;
        const flatness = options.flatness ?? 1;
        const rings = [];
        for (let index = 0; index < points.length; index++) {
            const tangent = normal(sub(points[Math.min(index + 1, points.length - 1)], points[Math.max(0, index - 1)]));
            const helper = Math.abs(tangent[1]) < .84 ? [0, 1, 0] : [1, 0, 0];
            const axisA = normal(cross(tangent, helper));
            const axisB = normal(cross(tangent, axisA));
            const radius = Array.isArray(radii[index]) ? radii[index] : [radii[index], radii[index] * flatness];
            const ring = [];
            for (let side = 0; side < sides; side++) {
                const angle = side / sides * Math.PI * 2;
                ring.push(this.vertex(add(points[index], add(mul(axisA, Math.cos(angle) * radius[0]), mul(axisB, Math.sin(angle) * radius[1])))));
            }
            rings.push(ring);
        }
        for (let index = 0; index < rings.length - 1; index++) for (let side = 0; side < sides; side++) {
            const next = (side + 1) % sides;
            const u1 = 18 + index / (rings.length - 1) * (sourceResolution - 36);
            const u2 = 18 + (index + 1) / (rings.length - 1) * (sourceResolution - 36);
            const v1 = 18 + side / sides * (sourceResolution - 36);
            const v2 = 18 + (side + 1) / sides * (sourceResolution - 36);
            this.face([rings[index][side], rings[index][next], rings[index + 1][next], rings[index + 1][side]], [[u1, v1], [u1, v2], [u2, v2], [u2, v1]]);
        }
    }

    blade(points, widths, depths) {
        const rings = [];
        for (let index = 0; index < points.length; index++) {
            const tangent = normal(sub(points[Math.min(index + 1, points.length - 1)], points[Math.max(0, index - 1)]));
            // This normal stays in the X/Y silhouette plane, producing a
            // genuinely broad scythe rather than a round tentacle.
            const side = normal([-tangent[1], tangent[0], 0]);
            const depth = [0, 0, depths[index]];
            const wide = mul(side, widths[index]);
            rings.push([
                this.vertex(add(add(points[index], wide), depth)),
                this.vertex(add(add(points[index], wide), mul(depth, -1))),
                this.vertex(add(sub(points[index], wide), mul(depth, -1))),
                this.vertex(add(sub(points[index], wide), depth))
            ]);
        }
        for (let index = 0; index < rings.length - 1; index++) {
            const u1 = 20 + index / (rings.length - 1) * (sourceResolution - 40);
            const u2 = 20 + (index + 1) / (rings.length - 1) * (sourceResolution - 40);
            const v1 = 48;
            const v2 = sourceResolution - 48;
            this.face([rings[index][0], rings[index][1], rings[index + 1][1], rings[index + 1][0]], [[u1, v1], [u1, v2], [u2, v2], [u2, v1]]);
            this.face([rings[index][3], rings[index + 1][3], rings[index + 1][2], rings[index][2]], [[u1, v1], [u2, v1], [u2, v2], [u1, v2]]);
            this.face([rings[index][0], rings[index + 1][0], rings[index + 1][3], rings[index][3]], [[u1, v1], [u2, v1], [u2, v2], [u1, v2]]);
            this.face([rings[index][1], rings[index][2], rings[index + 1][2], rings[index + 1][1]], [[u1, v1], [u1, v2], [u2, v2], [u2, v1]]);
        }
    }

    export() {
        return {
            name: this.name,
            color: hash(this.name) % 8,
            origin: [0, 0, 0],
            rotation: [0, 0, 0],
            shading: 'smooth',
            export: true,
            visibility: true,
            locked: false,
            render_order: 'default',
            scope: 0,
            allow_mirror_modeling: true,
            vertices: this.vertices,
            faces: this.faces,
            type: 'mesh',
            uuid: uuid(`${this.name}:mesh`)
        };
    }
}

function createProject(name) {
    const fallback = JSON.parse(fs.readFileSync(path.join(projects, `${name}.bbmodel`), 'utf8'));
    const project = {
        meta: {...clone(fallback.meta), box_uv: false},
        name: `${name}_reference_hifi`,
        model_identifier: `${name}_reference_hifi`,
        resolution: {width: sourceResolution, height: sourceResolution},
        elements: [],
        groups: clone(fallback.groups),
        outliner: stripElements(fallback.outliner),
        textures: [{
            name: `${name}_v2_source.png`, folder: '', namespace: '', id: '0', particle: false,
            render_mode: 'normal', visible: true, mode: 'link',
            relative_path: `material_studies/${name}_v2_source.png`,
            uv_width: sourceResolution, uv_height: sourceResolution,
            uuid: uuid(`${name}:hifi:texture`)
        }],
        animations: clone(fallback.animations)
    };
    const groups = new Map(project.groups.map(group => [group.name, group]));
    const attach = builder => {
        const mesh = builder.export();
        project.elements.push(mesh);
        const group = groups.get(builder.bone);
        const node = group && findOutlinerNode(project.outliner, group.uuid);
        if (!node) throw new Error(`${name}: missing hifi bone ${builder.bone}`);
        node.children.push(mesh.uuid);
    };
    const mesh = (label, bone) => new MeshBuilder(`hifi_${name}_${label}`, bone, 0);
    return {project, attach, mesh};
}

function buildCollector() {
    const {project, attach, mesh} = createProject('biomass_collector');
    const torso = mesh('body', 'carapace');
    torso.sphere([1.5, 15.1, 0], [23.2, 4.8, 6.4], {rings: 8, sides: 16});
    const sacs = [[-16, 19.4, 4.0], [-10, 21.1, 4.6], [-3.2, 22.8, 5.2], [4.3, 22.5, 5.2], [11.5, 20.9, 4.7], [18, 19.1, 3.9]];
    sacs.forEach(([x, y, scale], index) => {
        torso.sphere([x, y, 0], [scale, scale * .84, 6.8 - Math.abs(index - 2.5) * .36], {rings: 9, sides: 16});
        torso.sphere([x + .35, y + .15, -5.2], [scale * .54, scale * .48, 1.45], {rings: 6, sides: 10});
        torso.sphere([x + .35, y + .15, 5.2], [scale * .54, scale * .48, 1.45], {rings: 6, sides: 10});
    });
    attach(torso);
    const head = mesh('head', 'head');
    head.sphere([-22.4, 14.9, 0], [4.8, 4.0, 5.4], {rings: 8, sides: 14});
    head.tube([[-23.5, 15.4, -4.2], [-27.4, 15.3, -5.6], [-31.0, 16.0, -7.7], [-34.2, 17.1, -9.4]], [1.0, .7, .46, .18], {sides: 7});
    head.tube([[-23.5, 15.4, 4.2], [-27.4, 15.3, 5.6], [-31.0, 16.0, 7.7], [-34.2, 17.1, 9.4]], [1.0, .7, .46, .18], {sides: 7});
    attach(head);
    const tail = mesh('tail', 'tail_base');
    tail.tube([[20, 14.6, 0], [26, 14.1, 0], [32, 14.4, .2], [38, 15.4, .4], [44, 16.6, .5], [51, 17.0, .5]], [3.9, 3.2, 2.55, 1.75, .95, .18], {sides: 11});
    attach(tail);
    [-15, -9, -3, 4, 11, 17].forEach((x, index) => ['l', 'r'].forEach(side => {
        const sign = side === 'l' ? -1 : 1;
        const leg = mesh(`leg_${index}_${side}`, `leg_${index + 1}_${side}_upper`);
        leg.tube([[x, 14, sign * 4.5], [x - .7, 9.2, sign * 6.7], [x + 1.0, 4.0, sign * 7.4], [x + 2.0, 1.3, sign * 7.1]], [2.05, 1.8, 1.5, 1.2], {sides: 9});
        [-1.25, 0, 1.25].forEach(offset => leg.tube([[x + 1.5, 1.25, sign * 7.1 + offset], [x + 3.3, .45, sign * 7.2 + offset * 1.15]], [.65, .18], {sides: 6}));
        attach(leg);
    }));
    return project;
}

function buildCrusher() {
    const {project, attach, mesh} = createProject('crusher_stalker');
    const torso = mesh('torso', 'torso');
    torso.sphere([1.2, 16.3, 0], [12.0, 6.7, 6.5], {rings: 10, sides: 16});
    [[-4.8, 20.6, 4.5], [-.9, 22.3, 5.3], [3.2, 21.4, 4.7], [6.5, 19.7, 3.7]].forEach(([x, y, r], index) => {
        torso.sphere([x, y, 0], [r, r * .66, 6.25 - index * .34], {rings: 8, sides: 14});
        torso.tube([[x - 2.7, y + .1, -5.2], [x, y + 1.1, -6.2], [x + 2.7, y - .3, -5.3]], [.55, .72, .48], {sides: 7});
        torso.tube([[x - 2.7, y + .1, 5.2], [x, y + 1.1, 6.2], [x + 2.7, y - .3, 5.3]], [.55, .72, .48], {sides: 7});
    });
    attach(torso);
    const head = mesh('head', 'head');
    head.sphere([-11.5, 14.5, 0], [6.8, 4.4, 5.1], {rings: 9, sides: 14});
    head.tube([[-13.0, 15.1, -3.8], [-17.0, 13.4, -3.6], [-19.4, 11.3, -2.6]], [1.3, .78, .2], {sides: 7, flatness: .62});
    head.tube([[-13.0, 15.1, 3.8], [-17.0, 13.4, 3.6], [-19.4, 11.3, 2.6]], [1.3, .78, .2], {sides: 7, flatness: .62});
    attach(head);
    ['l', 'r'].forEach(side => {
        const sign = side === 'l' ? -1 : 1;
        const arm = mesh(`arm_${side}`, `crusher_arm_${side}`);
        arm.tube([[-5.0, 18.0, sign * 5.5], [-9.0, 14.5, sign * 7.0], [-12.6, 9.2, sign * 7.7], [-16.4, 6.0, sign * 7.5]], [2.55, 2.25, 2.1, 2.8], {sides: 10});
        arm.sphere([-17.3, 5.5, sign * 7.5], [3.5, 2.8, 3.0], {rings: 7, sides: 11});
        [-1.55, 0, 1.55].forEach(offset => arm.tube([[-18, 5.2, sign * 7.5 + offset], [-21.4, 3.8, sign * 7.8 + offset * 1.15]], [.85, .16], {sides: 6, flatness: .55}));
        attach(arm);
    });
    [[5, -5.5], [5, 5.5], [11, -5], [11, 5]].forEach(([x, z], index) => {
        const leg = mesh(`hind_${index}`, `hind_leg_${index + 1}_upper`);
        leg.tube([[x, 15, z], [x + 3.3, 10.6, z * 1.25], [x + 4.2, 5.3, z * 1.35], [x + 5.4, 1.3, z * 1.32]], [1.7, 1.55, 1.25, .85], {sides: 8});
        [-.85, 0, .85].forEach(offset => leg.tube([[x + 5.4, 1.3, z * 1.32 + offset], [x + 7.2, .5, z * 1.44 + offset]], [.42, .12], {sides: 5}));
        attach(leg);
    });
    const tendrils = mesh('tendrils', 'rear_tendril_1');
    [-2.8, -1.4, 0, 1.4, 2.8].forEach((z, index) => tendrils.tube([[8, 18.3 + (index % 2) * .5, z], [15, 19.5 + index * .18, z * 1.15], [23, 20.4 + index * .45, z * 1.55], [30, 22.2 + index * .7, z * 2.1]], [.72, .52, .3, .08], {sides: 6}));
    attach(tendrils);
    return project;
}

function buildScythe() {
    const {project, attach, mesh} = createProject('scythe_stalker');
    const abdomen = mesh('abdomen', 'abdomen');
    abdomen.sphere([2.3, 15.9, 0], [12.8, 5.8, 5.9], {rings: 9, sides: 16});
    [[-3.8, 19.5, 3.9], [.1, 21.2, 4.6], [4.0, 20.4, 4.2], [7.7, 18.9, 3.4]].forEach(([x, y, r], index) => {
        abdomen.sphere([x, y, 0], [r, r * .65, 5.7 - index * .3], {rings: 8, sides: 14});
        abdomen.tube([[x - 2.0, y + .1, -4.7], [x + .3, y + 1.1, -5.7], [x + 2.1, y - .1, -4.8]], [.42, .66, .38], {sides: 6});
    });
    attach(abdomen);
    const head = mesh('head', 'head');
    head.sphere([-10.7, 14.5, 0], [6.0, 3.8, 4.7], {rings: 8, sides: 14});
    head.tube([[-13, 15.8, -3.0], [-16.5, 15.0, -4.1], [-19.4, 13.3, -4.8]], [1.0, .58, .16], {sides: 6});
    head.tube([[-13, 15.8, 3.0], [-16.5, 15.0, 4.1], [-19.4, 13.3, 4.8]], [1.0, .58, .16], {sides: 6});
    attach(head);
    // A separate foreground pair ensures the signature hooked scythes remain
    // legible from the reference's side profile as well as from free orbit.
    const foregroundBlades = mesh('foreground_blades', 'root');
    [-1, 1].forEach(side => foregroundBlades.blade(
        [[-9.0, 15.2, side * 6.1], [-13.5, 13.0, side * 7.0], [-18.0, 8.5, side * 7.25], [-20.5, 3.4, side * 7.0], [-20.0, .3, side * 6.7]],
        [1.05, 1.75, 2.35, 1.55, .08],
        [.52, .58, .5, .36, .05]
    ));
    attach(foregroundBlades);
    ['l', 'r'].forEach(side => {
        const sign = side === 'l' ? -1 : 1;
        const arm = mesh(`scythe_arm_${side}`, `scythe_arm_${side}`);
        arm.tube([[-7.0, 17.0, sign * 4.4], [-11.3, 13.6, sign * 6.1], [-15.2, 10.1, sign * 6.6], [-18.0, 8.2, sign * 6.5]], [1.55, 1.3, .95, .62], {sides: 8});
        arm.tube([[-18, 8.2, sign * 6.5], [-22.2, 6.3, sign * 6.45], [-25.3, 3.5, sign * 6.2], [-26.5, .4, sign * 6.0]], [[2.25, .64], [2.0, .5], [1.25, .32], [.1, .06]], {sides: 8});
        attach(arm);
    });
    [[-2, -5], [-2, 5], [5, -5.2], [5, 5.2], [10, -4.5], [10, 4.5]].forEach(([x, z], index) => {
        const leg = mesh(`leg_${index}`, `stalker_leg_${index + 1}_upper`);
        const rear = index >= 4 ? 2.1 : index >= 2 ? 1.3 : 0;
        leg.tube([[x, 14.2, z], [x + rear + 2.0, 9.4, z * 1.25], [x + rear + 4.1, 4.5, z * 1.45], [x + rear + 5.5, .9, z * 1.45]], [1.15, 1.0, .65, .38], {sides: 7});
        [-.65, 0, .65].forEach(offset => leg.tube([[x + rear + 5.5, .9, z * 1.45 + offset], [x + rear + 7.2, .2, z * 1.55 + offset]], [.23, .06], {sides: 5}));
        attach(leg);
    });
    const whips = mesh('whips', 'whip_l_1');
    [-2.8, -1.4, 0, 1.4, 2.8].forEach((z, index) => whips.tube([[-10, 18.0, z], [-2, 20.0 + index * .2, z * 1.2], [8, 19.4 + index * .4, z * 1.7], [18, 20.8 + index * .65, z * 2.25]], [.52, .34, .19, .05], {sides: 6}));
    attach(whips);
    return project;
}

const outputs = {
    biomass_collector: buildCollector(),
    crusher_stalker: buildCrusher(),
    scythe_stalker: buildScythe()
};
for (const name of creatureNames) {
    const project = outputs[name];
    const faces = project.elements.reduce((sum, mesh) => sum + Object.keys(mesh.faces).length, 0);
    writeJson(path.join(projects, `${name}_reference_hifi.bbmodel`), project);
    console.log(`${name}: ${project.elements.length} hifi meshes / ${faces} quads`);
}
