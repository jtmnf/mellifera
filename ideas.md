# Ideas

Done items are left in place with what was decided, so the reasoning does not have to be rebuilt from the commit later.

Things that could be added, roughly in the order they seem worth doing. Nothing here is a commitment, and none of it comes from playing the mod - it comes from reading the code, so anything about how something *feels* is inference.

## 1. Fluid ducts - done

The most obvious asymmetry now that the Cable exists: Forge Energy travels, liquid honey does not. The Squeezer makes it, the Tank holds it, and the Carpenter and the Engine drink it - but today they are connected by standing the blocks face to face or by carrying buckets by hand.

The pattern is already written. `CableBlockEntity` is a conductor with no buffer that breadth-first searches its own run for somewhere to put what is pushed into it; the fluid version is the same class against `FluidResource`. The model, the collar and `tools/gen_cable_textures.py` carry over.

## 2. An advancement tree - done

There is exactly one real advancement in the mod (`first_comb`); everything else under `advancement/recipes/` is a recipe unlock. A breeding mod is the case where this matters most: the first Princess, the first queen to die of old age, the first mutation, the first Serum, the first species at the top of a branch, the first machine running on your own honey.

It is data only, and it is the one thing on this list that changes what a new player sees in their first ten minutes. Right now the mod suggests nothing and waits to be discovered.

Nineteen of them, hanging off a root that fires the moment a wild hive is broken open, in four lines: the combs (Centrifuge, Royal Jelly, Squeezer, Carpenter, frames), the power (Engine, Capacitor, both pipes), the breeding (first mutation, a branch top), and the genetics bench (Isolator, Serum, Infuser).

Not data only, in the end. Two of the six items above name a *bee* rather than an item, and there was no way to say that: every Princess in the game is the same item id, and the one component-matching an advancement can do is exact, so asking about the species would have meant writing out all eight chromosomes and then only matching a bee that had precisely those. So `mellifera:bee` was registered as a data component predicate -- the extension point Vanilla's own `minecraft:enchantments` uses -- and takes a species list, a `min_tier`, or neither, which is "any bee". `min_tier: 1` is "this bee came out of a mutation", read off BeeProgression's existing table, so it counts a custom bee's crosses too.

"The first queen to die of old age" was dropped rather than built. A queen dies in a hive that no player need be standing near, and nothing in the mod tracks who placed a block -- so it would have meant inventing ownership for one advancement.

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

## 9. The genes Forestry breeds for - done, less humidity

The mod had eight chromosomes and Forestry has twelve. Night and rain were not genetic here at all: they were the Luminous and the Canopy frames and nothing else, which meant the stop was solved *outside* the bee, by a thing bought at the Carpenter that wears out, rather than inside it by breeding. That is the middle of Forestry's game and it was missing.

Three chromosomes added - nocturnal, tolerant flyer, cave dwelling - as one shared yes/no allele with NO dominant, so the useful half is always the recessive one and the Recessive frame finally has something to point at. The frame and the gene answer the same stop and `Foraging.Grounding.liftedBy` does not care which arrives.

Cave dwelling came with a rule that did not exist before: **a hive with no sky over it does not work.** Nothing in vanilla stops a beehive under a roof, so this is the mod asserting something, and it is a behaviour change for any world with an indoor apiary. `hasSkyLight` exempts the Nether and the End, which have no daylight to be denied and where the rule would otherwise be absurd.

Humidity was left out and is item 6 on this list, not this one. It needs a second climate axis in the world model; the other three needed only a gene each.

The side effect worth knowing: the Isolator and the Infuser size their grids off `BeeTrait.ALL`, so both went from eight cells to eleven and their windows were regenerated at four across by three down.

## 10. Unidentified bees - done

A bee's tooltip printed its whole genome, recessive alleles included, from the moment it was picked up. The one thing a player would otherwise have to work out was simply told to them, and the diploid system - the entire point of the mod - was played on an open board.

A bee now names its species and nothing else until a Beealyzer reads it, at a honey drop each. The species is deliberately *not* hidden, which departs from Forestry: this mod paints every bee its species' colour on the model, so refusing to name what is visible would be coy rather than mysterious. What is hidden is the half of the genome that is not on the outside of the bee - including the recessive species allele, which is the most valuable line on the tooltip.

The Beealyzer has no window, which is the other departure. Forestry's is a GUI fed one bee at a time; by the time a hive is running, that is forty clicks. This one reads the whole inventory in one press at the same price per bee.

It is a book rather than a gadget - the Apiarist Database's own silhouette in the machines' casing brown, with a brass inlay and a permanent glint - and it is good for 32 readings. One charge, not two: it briefly took a honey drop per bee as well, which made every press a sum of two dwindling things for nothing. The honey moved into the recipe, where it still does the job it was there for, because a drop comes out of a Centrifuge and so the first few hives are worked blind. That stretch is the whole point.

Not enchantable, which costs nothing to arrange - an item does not acquire the ENCHANTABLE component by having durability - but is worth saying out loud: Unbreaking would turn the wearing-out that is its only cost into a formality.

## 11. The Scoop - done

Wild hives dropped their bees to anything, including a fist. In Forestry a hive broken without a Scoop is a hive destroyed, and it is one of the first things a player of it does.

Six loot tables, one `minecraft:match_tool` condition per pool. The recipe is sticks and string and its unlock advancement fires on holding string, because a player who cannot work out the first step has no mod to play.

The hives also went from `instabreak` to thirty seconds, and the Scoop got a Tool component that cancels it back to one tick. That is not a difficulty knob - it is the only warning the game can give. A block that shatters instantly and yields nothing teaches a player that hives are worthless; a block that will not come apart teaches them they are holding the wrong thing. The two numbers are one number: the Scoop's speed is `HIVE_DESTROY_TIME * HARVEST_MODIFIER * 2`, read off the block, so they cannot drift.

## 7. Sounds of its own

There is no `sounds` directory: every sound in the mod is borrowed from Vanilla. Only one of them would really matter - an apiary that hums while it works.

## 8. Dead-end chains

Silk wisp exists only to make the Insulation frame, and pulsating propolis only the Mutagenic one. A player who does not want those two frames has two items with nowhere to go - the same problem honeydew, propolis, ice shards and peat had before 0.3.0.
