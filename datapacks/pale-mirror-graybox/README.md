# Pale Mirror source graybox

This managed world datapack defines the disposable
`pale_mirror:frontier_graybox` level used only by the Java source-parity
`graybox_1_40` materialization.

It must be copied into `world/datapacks/` before the server creates the world.
The normal installer and the disposable server smoke workflow already copy every
top-level managed datapack before first boot. The `minecraft:overworld` dimension
type starts flat layers at Y=-64, so the one bedrock layer, 126 stone layers and
one light-grey concrete layer deliberately end at Y=63; the source layout
materializes at its first air block, Y=64. It intentionally enables no terrain
features, lakes, or structures.

The mod rejects `/pale_mirror frontier activate_graybox` if this level is absent.
It never creates or flattens the ordinary overworld as a fallback. A disposable
world created before this datapack was installed must be recreated for graybox
testing.
