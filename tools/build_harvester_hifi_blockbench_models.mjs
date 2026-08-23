import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const projects = path.join(root, 'pale-mirror-visuals', 'src', 'main', 'blockbench', 'harvester');
const creatureNames = ['biomass_collector', 'crusher_stalker', 'scythe_stalker'];
const sourceResolution = 1254;
const check = process.argv.slice(2).includes('--check');

if (process.argv.slice(2).some(argument => argument !== '--check')) {
    throw new Error('Usage: node tools/build_harvester_hifi_blockbench_models.mjs [--check]');
}

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

function polygonArea(points) {
    return points.reduce((area, point, index) => {
        const next = points[(index + 1) % points.length];
        return area + point[0] * next[1] - next[0] * point[1];
    }, 0) / 2;
}

function pointInTriangle(point, a, b, c) {
    const cross = (left, right, value) =>
        (right[0] - left[0]) * (value[1] - left[1]) - (right[1] - left[1]) * (value[0] - left[0]);
    const ab = cross(a, b, point);
    const bc = cross(b, c, point);
    const ca = cross(c, a, point);
    return (ab >= 0 && bc >= 0 && ca >= 0) || (ab <= 0 && bc <= 0 && ca <= 0);
}

// A reusable image-first profile triangulator.  Organic silhouettes such as a
// hanging curtain are not tubes: their reference-visible contour is authored
// explicitly, then given finite depth for the diagnostic camera views.
function triangulateProfile(points) {
    const indices = points.map((_, index) => index);
    if (polygonArea(points) < 0) indices.reverse();
    const triangles = [];
    while (indices.length > 3) {
        let found = false;
        for (let cursor = 0; cursor < indices.length; cursor++) {
            const previous = indices[(cursor - 1 + indices.length) % indices.length];
            const current = indices[cursor];
            const next = indices[(cursor + 1) % indices.length];
            const a = points[previous];
            const b = points[current];
            const c = points[next];
            const turn = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            if (turn <= 0) continue;
            if (indices.some(index => index !== previous && index !== current && index !== next && pointInTriangle(points[index], a, b, c))) continue;
            triangles.push([previous, current, next]);
            indices.splice(cursor, 1);
            found = true;
            break;
        }
        if (!found) throw new Error('Image profile is self-intersecting or cannot be triangulated');
    }
    triangles.push(indices);
    return triangles;
}

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

    // A controllable organic superellipsoid.  The high-value shell masses in
    // the supplied images are neither circles nor generic noise: their broad
    // screen-space arcs need independent horizontal/vertical squareness while
    // keeping a true rounded volume in the other audit cameras.
    superellipsoid(center, radius, options = {}) {
        const [rx, ry, rz] = Array.isArray(radius) ? radius : [radius, radius, radius];
        const rings = options.rings ?? 14;
        const sides = options.sides ?? 24;
        const latitudePower = options.latitudePower ?? .72;
        const longitudePower = options.longitudePower ?? .72;
        const leanX = options.leanX ?? 0;
        const leanZ = options.leanZ ?? 0;
        const signedPower = (value, exponent) => Math.sign(value) * Math.pow(Math.abs(value), exponent);
        const rows = [];
        const top = this.vertex([center[0] + leanX, center[1] + ry, center[2] + leanZ]);
        for (let ring = 1; ring < rings; ring++) {
            const theta = Math.PI * ring / rings;
            const vertical = Math.cos(theta);
            const radial = Math.pow(Math.sin(theta), latitudePower);
            const row = [];
            for (let side = 0; side < sides; side++) {
                const phi = Math.PI * 2 * side / sides;
                const horizontalX = signedPower(Math.cos(phi), longitudePower);
                const horizontalZ = signedPower(Math.sin(phi), longitudePower);
                // The lean is strongest at the high attachment and decays to
                // the lower lobe, preserving a deliberately asymmetrical sac.
                const upperBias = Math.max(0, vertical) * radial;
                row.push(this.vertex([
                    center[0] + horizontalX * radial * rx + leanX * upperBias,
                    center[1] + vertical * ry,
                    center[2] + horizontalZ * radial * rz + leanZ * upperBias
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
        if (options.caps) {
            const cap = (ring, point, reverse) => {
                const center = this.vertex(point);
                for (let side = 0; side < sides; side++) {
                    const next = (side + 1) % sides;
                    const vertices = reverse ? [center, ring[next], ring[side]] : [center, ring[side], ring[next]];
                    this.face(vertices, [[sourceResolution / 2, sourceResolution / 2], [18, 18], [sourceResolution - 18, sourceResolution - 18]]);
                }
            };
            cap(rings[0], points[0], true);
            cap(rings.at(-1), points.at(-1), false);
        }
    }

    profile(points, depth) {
        if (!Array.isArray(points) || points.length < 3 || points.some(point => !Array.isArray(point) || point.length !== 2)) {
            throw new Error(`${this.name}: profile requires at least three [x, y] points`);
        }
        if (!(depth > 0)) throw new Error(`${this.name}: profile depth must be positive`);
        const triangles = triangulateProfile(points);
        const front = points.map(([x, y]) => this.vertex([x, y, depth]));
        const back = points.map(([x, y]) => this.vertex([x, y, -depth]));
        const minX = Math.min(...points.map(([x]) => x));
        const maxX = Math.max(...points.map(([x]) => x));
        const minY = Math.min(...points.map(([, y]) => y));
        const maxY = Math.max(...points.map(([, y]) => y));
        const uv = ([x, y]) => [
            18 + (x - minX) / Math.max(.001, maxX - minX) * (sourceResolution - 36),
            18 + (y - minY) / Math.max(.001, maxY - minY) * (sourceResolution - 36)
        ];
        for (const [a, b, c] of triangles) {
            this.face([front[a], front[b], front[c]], [uv(points[a]), uv(points[b]), uv(points[c])]);
            this.face([back[c], back[b], back[a]], [uv(points[c]), uv(points[b]), uv(points[a])]);
        }
        for (let index = 0; index < points.length; index++) {
            const next = (index + 1) % points.length;
            this.face(
                [front[index], back[index], back[next], front[next]],
                [[18, 18], [18, sourceResolution - 18], [sourceResolution - 18, sourceResolution - 18], [sourceResolution - 18, 18]]
            );
        }
    }

    // A volume built from repeated traced silhouette rings.  Unlike `profile`,
    // this has a genuinely rounded cross-section through depth, so it can
    // preserve a reference contour without becoming a flat cardboard cut-out
    // in the diagnostic Blockbench views.
    loftProfile(points, sections) {
        if (!Array.isArray(points) || points.length < 3 || !Array.isArray(sections) || sections.length < 2) {
            throw new Error(`${this.name}: loftProfile requires a contour and two or more sections`);
        }
        const triangles = triangulateProfile(points);
        const ring = section => points.map(([x, y]) => this.vertex([
            section.x + x * (section.scaleX ?? 1),
            section.y + y * (section.scaleY ?? 1),
            section.z
        ]));
        const rings = sections.map(ring);
        const uv = index => [
            18 + index / points.length * (sourceResolution - 36),
            18 + index / points.length * (sourceResolution - 36)
        ];
        for (let section = 0; section < rings.length - 1; section++) {
            for (let index = 0; index < points.length; index++) {
                const next = (index + 1) % points.length;
                this.face(
                    [rings[section][index], rings[section][next], rings[section + 1][next], rings[section + 1][index]],
                    [uv(index), uv(next), uv(next), uv(index)]
                );
            }
        }
        for (const [a, b, c] of triangles) {
            this.face([rings[0][c], rings[0][b], rings[0][a]], [uv(c), uv(b), uv(a)]);
            const last = rings.at(-1);
            this.face([last[a], last[b], last[c]], [uv(a), uv(b), uv(c)]);
        }
    }

    // A reference-traced hanging volume.  Each row owns the visible outer and
    // inner contour points; the paired, bowed surfaces then turn that contour
    // into a deep organic mass.  Unlike an extruded profile or a capped tube,
    // it has neither a vertical cut nor a planar end cap.  It is deliberately
    // reusable for drapes, membranes and other asymmetrical organic sheets.
    curtain(sections, columns = 12) {
        if (!Array.isArray(sections) || sections.length < 3 || !Number.isInteger(columns) || columns < 3) {
            throw new Error(`${this.name}: curtain requires at least three sections and three columns`);
        }
        for (const section of sections) {
            if (!Array.isArray(section.outer) || section.outer.length !== 2 || !Array.isArray(section.inner) || section.inner.length !== 2 || !(section.depth >= 0)) {
                throw new Error(`${this.name}: invalid curtain section`);
            }
        }
        const surfaces = [-1, 1].map(sign => sections.map(section => {
            const row = [];
            for (let column = 0; column <= columns; column++) {
                const progress = column / columns;
                const bow = Math.sin(Math.PI * progress);
                const outerWeight = 1 - progress;
                const x = section.outer[0] * outerWeight + section.inner[0] * progress + (section.xBow ?? 0) * bow * bow;
                const y = section.outer[1] * outerWeight + section.inner[1] * progress - (section.ySag ?? 0) * bow * bow;
                // One controllable recessed fold lets a continuous curtain
                // carry a real layered inner crease without being assembled
                // from separate tentacles or helper volumes.
                const foldAt = section.foldAt ?? .7;
                const foldWidth = section.foldWidth ?? .12;
                const fold = (section.foldDepth ?? 0) * Math.exp(-Math.pow((progress - foldAt) / foldWidth, 2));
                row.push(this.vertex([x, y, sign * section.depth * Math.max(0, bow - fold)]));
            }
            return row;
        }));
        const uv = (row, column) => [
            18 + row / (sections.length - 1) * (sourceResolution - 36),
            18 + column / columns * (sourceResolution - 36)
        ];
        for (const surface of surfaces) {
            for (let row = 0; row < sections.length - 1; row++) for (let column = 0; column < columns; column++) {
                this.face(
                    [surface[row][column], surface[row][column + 1], surface[row + 1][column + 1], surface[row + 1][column]],
                    [uv(row, column), uv(row, column + 1), uv(row + 1, column + 1), uv(row + 1, column)]
                );
            }
        }
        // The two surfaces meet at both traced outline rails.  Explicitly join
        // them instead of leaving a hidden seam: this is a closed volume even
        // though its top and ground contacts taper down to a single point.
        for (const column of [0, columns]) for (let row = 0; row < sections.length - 1; row++) {
            this.face(
                [surfaces[0][row][column], surfaces[0][row + 1][column], surfaces[1][row + 1][column], surfaces[1][row][column]],
                [[18, 18], [18, sourceResolution - 18], [sourceResolution - 18, sourceResolution - 18], [sourceResolution - 18, 18]]
            );
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
    // The reference is a low, heavy carrier rather than an arthropod with a
    // narrow thorax.  Keep the belly broad and let the six sacks, not a high
    // tail, establish the complete upper contour.
    torso.sphere([.8, 14.1, 0], [24.2, 5.8, 7.0], {rings: 9, sides: 18});
    attach(torso);
    // The reference owns six large, overlapping, low-frequency dorsal masses.
    // Their widths deliberately overlap by roughly a third: the silhouette is
    // a continuous descending carrier cascade, not six separated beads.
    const sacs = [
        {center: [-22.25, 18.55, -1.95], radius: [14.6, 12.1, 11.45], latitudePower: .66, longitudePower: .74, leanX: -1.25, leanZ: -.55},
        {center: [-7.0, 18.15, 1.9], radius: [15.0, 11.9, 12.7], latitudePower: .65, longitudePower: .74, leanX: .72, leanZ: .46},
        {center: [6.75, 16.85, .42], radius: [13.8, 10.25, 11.5], latitudePower: .68, longitudePower: .76, leanX: .56, leanZ: .22},
        {center: [12.3, 16.6, -.05], radius: [9.55, 7.0, 7.65], latitudePower: .70, longitudePower: .78, leanX: .42, leanZ: .1},
        {center: [20.55, 14.85, .02], radius: [7.05, 5.1, 5.8], latitudePower: .72, longitudePower: .8, leanX: .28, leanZ: .04},
        {center: [27.0, 13.15, 0], radius: [4.9, 3.5, 4.1], latitudePower: .75, longitudePower: .82, leanX: .14, leanZ: 0}
    ];
    sacs.forEach((sac, index) => {
        const shell = mesh(`dorsal_sac_${index + 1}`, 'carapace');
        shell.superellipsoid(sac.center, sac.radius, sac);
        attach(shell);
    });
    const head = mesh('head', 'head');
    // The buried attachment gives the semantic head genuine volume without
    // inventing a separate face.  The visible front is the asymmetric curtain
    // below, whose contour is traced from the supplied reference.
    head.sphere([-15.45, 21.1, 0], [5.8, 3.8, 5.6], {rings: 11, sides: 18});
    const mantle = mesh('leading_folded_front_mantle', 'head');
    // The reference is a low wrapped sheet, not a capped monolith.  These
    // paired rails trace the asymmetric front perimeter directly: a broad
    // upper attachment, a convex outer descent, and a single wide grounded
    // hem.  Their deep bowed surfaces retain the layered, organic volume in
    // diagnostic views without leaving a vertical side plate behind it.
    mantle.curtain([
        {outer: [-24.8, 27.1], inner: [-16.4, 25.35], depth: 5.8, xBow: -.34, ySag: .05, foldDepth: .16},
        {outer: [-28.7, 26.3], inner: [-16.9, 22.7], depth: 8.15, xBow: -.76, ySag: .18, foldDepth: .4},
        {outer: [-33.1, 24.3], inner: [-17.8, 19.25], depth: 10.2, xBow: -1.08, ySag: .35, foldDepth: .76},
        {outer: [-36.8, 21.0], inner: [-19.1, 15.4], depth: 11.25, xBow: -1.24, ySag: .55, foldDepth: 1.05},
        {outer: [-39.25, 17.0], inner: [-20.6, 12.0], depth: 11.65, xBow: -1.2, ySag: .74, foldDepth: 1.22},
        {outer: [-40.05, 12.9], inner: [-22.35, 8.7], depth: 11.2, xBow: -1.02, ySag: .82, foldDepth: 1.12},
        {outer: [-39.15, 8.2], inner: [-24.5, 5.8], depth: 10.1, xBow: -.88, ySag: .7, foldDepth: .88},
        {outer: [-36.15, 4.0], inner: [-26.8, 2.7], depth: 8.45, xBow: -.62, ySag: .44, foldDepth: .56},
        {outer: [-33.0, 1.15], inner: [-29.05, .70], depth: 6.55, xBow: -.34, ySag: .16, foldDepth: .24},
        {outer: [-30.6, .18], inner: [-30.6, .16], depth: 5.15, xBow: -.08, ySag: .02, foldDepth: .05}
    ], 24);
    attach(mantle);
    attach(head);
    const tail = mesh('tail', 'tail_base');
    // A short taper holds the rear together before the reference's fine,
    // subordinate trailing fibres.  It is deliberately not a set of lasers.
    tail.tube([[20.0, 15.3, 0], [25.2, 14.65, 0], [30.6, 13.45, 0], [35.4, 11.7, 0]], [3.25, 2.55, 1.55, .5], {sides: 13, flatness: .78});
    [-2.7, -1.35, 0, 1.35, 2.7].forEach((z, index) => tail.tube([
        [31.8, 12.85 + index * .1, z], [37.2, 11.5 + (index % 2) * .24, z * 1.18],
        [43.7, 10.25 - index * .13, z * 1.52], [49.0, 8.55 - index * .25, z * 1.85]
    ], [.46, .28, .13, .035], {sides: 6, flatness: .72}));
    attach(tail);
    // Six depth-staggered supports carry the body in the reference. Their
    // long taper and alternating planted feet form the entire lower rhythm;
    // each starts within the belly, folds at a heavy knee, then resolves into
    // a broad terminal pad rather than a repeated short boot.
    [
        {
            bone: 'leg_1_l_upper', z: 5.85,
            points: [[-16.4, 14.3], [-21.0, 11.35], [-23.0, 6.15], [-20.4, 1.25]],
            radii: [4.55, 4.15, 3.25, 2.58], foot: [-20.9, .62], footRadius: [4.85, 1.18, 4.15]
        },
        {
            bone: 'leg_2_r_upper', z: 4.45,
            points: [[-10.0, 13.95], [-12.7, 10.25], [-13.45, 5.2], [-10.2, 1.02]],
            radii: [4.1, 3.75, 2.95, 2.3], foot: [-9.55, .54], footRadius: [4.35, 1.08, 3.75]
        },
        {
            bone: 'leg_3_l_upper', z: 5.35,
            points: [[-3.05, 13.75], [-5.35, 9.7], [-4.4, 4.62], [-.95, .92]],
            radii: [3.85, 3.5, 2.72, 2.1], foot: [-.42, .49], footRadius: [4.08, 1.0, 3.48]
        },
        {
            bone: 'leg_4_r_upper', z: 4.05,
            points: [[4.25, 13.42], [2.55, 9.18], [4.15, 4.25], [7.2, .85]],
            radii: [3.45, 3.12, 2.4, 1.82], foot: [7.72, .44], footRadius: [3.74, .92, 3.12]
        },
        {
            bone: 'leg_5_l_upper', z: 5.0,
            points: [[11.55, 13.15], [10.65, 8.75], [12.55, 4.0], [15.3, .78]],
            radii: [3.08, 2.76, 2.08, 1.55], foot: [15.76, .39], footRadius: [3.28, .82, 2.78]
        },
        {
            bone: 'leg_6_r_upper', z: 3.72,
            points: [[18.15, 12.72], [17.95, 8.38], [19.85, 3.72], [22.25, .72]],
            radii: [2.72, 2.4, 1.76, 1.3], foot: [22.62, .36], footRadius: [2.82, .72, 2.4]
        }
    ].forEach((support, index) => {
        const leg = mesh(`carrier_support_${index + 1}`, support.bone);
        const points = support.points.map(([x, y], pointIndex) => [x, y, support.z * (1 + pointIndex * .065)]);
        leg.tube(points, support.radii, {sides: 17, flatness: .91});
        // Three overlapping heavy segment bulges preserve the organic,
        // tapered reference rhythm instead of exposing one smooth pipe.
        points.slice(1).forEach((point, pointIndex) => leg.sphere(
            point,
            [support.radii[pointIndex + 1] * .98, support.radii[pointIndex + 1] * .86, support.radii[pointIndex + 1] * .9],
            {rings: 9, sides: 15}
        ));
        leg.sphere([support.foot[0], support.foot[1], support.z * 1.2], support.footRadius, {rings: 10, sides: 16});
        attach(leg);
    });
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
    const output = path.join(projects, `${name}_reference_hifi.bbmodel`);
    const rendered = `${JSON.stringify(project, null, 2)}\n`;
    if (check) {
        if (!fs.existsSync(output) || fs.readFileSync(output, 'utf8') !== rendered) {
            throw new Error(`${name}: committed image-faithful mesh is stale; run node tools/build_harvester_hifi_blockbench_models.mjs`);
        }
    } else {
        writeJson(output, project);
    }
    console.log(`${name}: ${project.elements.length} image-faithful meshes / ${faces} quads${check ? ' (verified)' : ''}`);
}
