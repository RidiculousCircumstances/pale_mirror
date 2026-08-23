# Private SF3D volume-proposal environment

This directory contains the versioned installation contract for the private
Windows authoring workstation.  It is intentionally separate from Blender and
from gameplay assets.  It may create a trace-cut one-image mesh proposal, but
cannot edit the canonical `.blend`, change the primary trace or export PMMesh.

The first trial is pinned by
`pale-mirror-visuals/src/main/blender/harvester/bakeoff/collector_volume_bakeoff_v01.json`.
Its actual model weights and Hugging Face credentials stay on the private
workstation and are never copied into the repository.

Run the script only from the Windows host after the repository has been copied
to `E:\PaleMirror\workspace\pale-mirror`:

```powershell
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\sf3d\install_sf3d.ps1 -Phase Torch
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\sf3d\install_sf3d.ps1 -Phase Requirements
```

The script pins the CUDA 12.1 PyTorch wheel set known to work with the NVIDIA
driver, then installs the exact requirements of the checked-out SF3D source.
It writes an ignored, local `environment.json` for candidate provenance.  A
gated model download is deliberately not attempted by the installation phase.

Before `Inference`, the author must accept the SF3D Hugging Face model terms
and perform `huggingface-cli login` interactively on the private machine.  Do
not paste an access token into a shell history, source file, manifest or chat.

The prepared input is copied only to the fixed candidate directory.  Validate
the hand-off before any model download:

```powershell
powershell.exe -ExecutionPolicy Bypass -File E:\PaleMirror\workspace\pale-mirror\tools\sf3d\run_collector_bakeoff.ps1 -Phase Validate
```

After local Hugging Face authentication, `-Phase Inference` writes exactly one
unaccepted `candidate.glb` and an immutable provenance manifest under
`workspace\pale-mirror\candidates\biomass_collector\sf3d_v01`.  A separate
typed Blender operation must import and audit it; this script never does so.
