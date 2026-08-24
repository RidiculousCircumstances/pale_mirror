# Historical Collector reconstruction operations

The subdirectories below are preserved only as reproducible negative evidence.
They are deliberately excluded from the Windows sync, the Blender operation
allowlist, the CLI and MCP.  They must never create or modify an active
Collector working source.

- `collector_trace_reconstruction/` contains the rejected trace/cage/mass
  builders.  Their error was conceptual rather than syntactic: they rebuilt an
  organism from profile rails and ring volumes.
- `collector_v05_sculpt_v03/` contains the rejected fixed v03 direct passes.
  They did start from raw v05 topology, but their independent, non-sequential
  masks made a coherent full-composition correction impossible.
- `sf3d_v01/` and `hunyuan2mv_v01_v05/` retain rejected generated-proposal
  import, review and proxy operations. They are executable only by an explicit
  historical investigation, never by an active CLI, MCP or Windows sync.

The active path is `collector_direct_*`: immutable raw v05 -> protected
working copy -> one reviewed sequential direct-mesh pass -> audit decision or
exact rollback.  The primary trace is an acceptance constraint and guide, not
a mesh generator.
