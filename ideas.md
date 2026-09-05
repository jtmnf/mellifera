# Ideas

Done items are left in place with what was decided, so the reasoning does not have to be rebuilt from the commit later.

Things that could be added, roughly in the order they seem worth doing. Nothing here is a commitment, and none of it comes from playing the mod - it comes from reading the code, so anything about how something *feels* is inference.

## 1. Fluid ducts - done

The most obvious asymmetry now that the Cable exists: Forge Energy travels, liquid honey does not. The Squeezer makes it, the Tank holds it, and the Carpenter and the Engine drink it - but today they are connected by standing the blocks face to face or by carrying buckets by hand.

The pattern is already written. `CableBlockEntity` is a conductor with no buffer that breadth-first searches its own run for somewhere to put what is pushed into it; the fluid version is the same class against `FluidResource`. The model, the collar and `tools/gen_cable_textures.py` carry over.

## 2. An advancement tree

There is exactly one real advancement in the mod (`first_comb`); everything else under `advancement/recipes/` is a recipe unlock. A breeding mod is the case where this matters most: the first Princess, the first queen to die of old age, the first mutation, the first Serum, the first species at the top of a branch, the first machine running on your own honey.

It is data only, and it is the one thing on this list that changes what a new player sees in their first ten minutes. Right now the mod suggests nothing and waits to be discovered.

## 3. Tests

`src/test` does not exist. There is pure logic here that needs no Minecraft to exercise and currently has nothing proving it:

- Mendelian inheritance, and what the Dominant and Recessive frames do to it.
- `MutationEngine` and its climate, biome, date and parent gates.
- `Foraging.Grounding.liftedBy` - the truth table for which frame lifts which stop. Written from scratch, never verified.
- `MelliferaCarpenterRecipes.repairMb` and `SqueezerBlockEntity.yieldOf`, both single sources of truth that several other places read.
- The clamping and rewriting `MelliferaOutputConfig` does to out-of-range values.

The least visible item on this list and the one that protects the rest of it.

## 4. Comparator output on the machines - done

The Tank has it. The Centrifuge, Squeezer, Carpenter, Engine and Apiary do not. For anyone automating, this is the default expectation: read the progress, the tank level, or how much life a queen has left.

## 5. A capacitor - done

The Engine's buffer is 20,000 FE and the Cable holds nothing, so honey burned overnight cannot be saved for the morning's work. It is the third power block and it closes the set.

## 6. Redstone control - done

Switching an Engine or a hive off without breaking the block. Pairs with the comparator output above.

## 7. Sounds of its own

There is no `sounds` directory: every sound in the mod is borrowed from Vanilla. Only one of them would really matter - an apiary that hums while it works.

## 8. Dead-end chains

Silk wisp exists only to make the Insulation frame, and pulsating propolis only the Mutagenic one. A player who does not want those two frames has two items with nowhere to go - the same problem honeydew, propolis, ice shards and peat had before 0.3.0.
