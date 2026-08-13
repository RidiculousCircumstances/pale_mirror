# Far Frontier Spore zones

Spore's stock structure sets are much denser than Far Frontier's exploration
model (the stock biomass tower set is every 3 chunks). This datapack preserves
all of Spore's structures but changes every structure set to a 4096-chunk
random-spread grid with a 3072-chunk separation. Its biome restrictions remain
owned by Spore.

Together with the Spore startup configuration, this makes each generated Spore
structure a local contamination site: natural infected spawning is confined to
vanilla mushroom fields, infection conversion and block spread are disabled,
and hiveminds cannot dispatch raids or keep chunks loaded.

This is not a world-tier gate. Minecraft selects structure placement during
chunk generation, and neither Spore nor Ravents exposes a stable data-driven
hook to make that selection conditional on a later Ravents tier. Access and
activation can be gated only by a dedicated integration implementation after
the core zone behavior has been performance-tested.
