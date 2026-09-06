# Primary-image trace sources

These files are the primary-image constraint and acceptance authority for
Blender Harvester authoring. Each trace stores the exact source image, its SHA-256 digest, image
dimensions, pinned ground line, primary camera mapping, and pixel-coordinate
outlines for the visible silhouette and large anatomical boundaries.

Every Blender session must retain the matching source image as a locked plane.
The required trace-overlay render is a hard authoring gate. The trace must not
be used to regenerate a Collector mesh from rails, cages or primitive masses:
the active Collector process directly edits a protected working copy of pinned
raw v05 topology. Supporting turntables may inform depth but cannot change a
point in the primary trace.

Do not replace a failed candidate by adjusting spheres, tubes, or a generated
turntable until a new versioned trace has been reviewed against the supplied
base image.

`biomass_collector_primary_v03.json` is generated reproducibly from the pinned
Collector source image by `tools/harvester_primary_trace.py`. It contains the
two-pixel silhouette runs for the locked 1280×720 primary camera. The direct,
non-fitted render overlay is produced by `tools/harvester_primary_trace_overlay.py`.

`v02` remains immutable historical evidence for the first SF3D trial. It has
the same source-pixel mask as `v03`, but recorded the 18-unit vertical image
extent as Blender's `orthographic_scale`; Blender interprets that property as
the 32-unit width of this 16:9 frame. `v03` corrects only that camera scalar.
