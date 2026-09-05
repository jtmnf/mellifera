Mellifera
=========

Beekeeping as a breeding game, for Minecraft 26.2 on NeoForge.

Find a wild hive, take the Princess and the Drones inside it, and put them in an Apiary. Every
bee carries a genome -- species, speed, lifespan, fertility, flowering, territory, temperature
tolerance and a special effect -- with a dominant and a recessive allele for each. What a queen
produces, how fast she works, and whether she will work at all where you put her all follow
from those genes.

Cross two species and they may mutate into a third. There are 58 species across twenty
branches, from the wild Forest and Meadows bees up to the Imperial, Demonic, Phantasmal,
Diamond and Ecstatic lines at the top of each one, reached through a mutation tree gated on
climate, biome, date and the parents you feed it. The Apiarist Database item lists every
species you have discovered, its products, and the pairs that lead to it.

Queens drop combs; the Centrifuge turns those into honey drops, wax, propolis, phosphor and
the resource drops of the deeper branches. The Squeezer presses those drops into liquid honey,
the Tank holds it, and the Carpenter spends it building the Frames that go back into the hive.
The Isolator extracts a single gene from a bee into a Serum, and the Infuser writes that Serum
back into another bee, so a line can be built deliberately instead of by luck.

The machines run on Forge Energy and work with any tech mod that provides it. Power is speed
and not permission: every one of them runs by hand at an eighth of the rate, so nothing in the
mod is gated behind having a generator.

The machines
============
- **Apiary** -- where a queen works. Three frame slots a level, and stacking them makes one
  taller hive that works faster and carries a row of frames per level.
- **Centrifuge** -- combs in, their contents out.
- **Squeezer** -- honey drops and honeydew in, liquid honey out. Four drops to a bucket, eight
  honeydew, and a bucket bay on the side for a player without pipes.
- **Tank** -- sixteen buckets of any fluid, filled and drained by hand or by pipe.
- **Carpenter** -- the plain Frame plus one ingredient and some honey becomes a specialised
  frame; a worn frame with nothing beside it becomes a fresh one for a quarter of what it cost
  to build.
- **Isolator** -- a bee in, its eight genes out as Serums. The bee is used up.
- **Infuser** -- a Serum and a bee in, that bee with the gene written into it out. Pollen
  powers the writing.

Frames
======
Every special frame is made in the Carpenter out of the plain one, and every one of them wears
out with use. Three of them lift a stop a player has no other answer to -- the climate band a
species will not work outside of (Insulation), the dark (Luminous) and the rain (Canopy) -- and
those three are the most expensive for that reason. The rest trade in what a working hive is
already doing: more combs, faster cycles, forced dominant or recessive inheritance, a higher
mutation rate, a queen ended after one cycle, or a hive that re-queens itself.

Requirements
============
- Minecraft 26.2
- NeoForge 26.2.0.56-beta or later
- JEI, optional. With it installed, every comb, centrifuge recipe, mutation, squeezer and
  carpenter recipe and both genetics machines get a page, and a bee can be dragged from JEI
  onto an Apiary to set it as that hive's breeding objective.

Bees of your own
================
Drop a JSON file in `config/mellifera/custom_bees/` and it becomes a real species: it appears
in the Apiarist Database and in JEI, it can be bred for, and it is filed under a branch like
any other. The file name is the id, so `obsidian.json` becomes `mellifera:obsidian`. A working
example is written into that folder on first launch.

```json
{
  "name": "Obsidian",
  "branch": "custom",
  "min_celsius": 0.0,
  "max_celsius": 60.0,
  "dominant": true,
  "primary_color": "#4B3A78",
  "secondary_color": "#FFDC16",

  "combs": [
    { "comb": "mellifera:stone", "chance": 0.20 },
    {
      "comb": {
        "id": "obsidian",
        "name": "Obsidian Comb",
        "primary_color": "#363534",
        "secondary_color": "#4B3A78",
        "centrifuge": [
          { "item": "mellifera:beeswax", "chance": 0.50 },
          { "item": "minecraft:obsidian", "count": 1, "chance": 0.05 }
        ]
      },
      "chance": 0.10
    }
  ],

  "traits": { "speed": "slowest", "lifespan": "long", "fertility": "low", "tolerance": "both_2" },

  "mutations": [
    { "parents": ["mellifera:rock", "mellifera:sinister"], "chance": 0.06 }
  ]
}
```

Only `name` and `combs` are required; everything else has a default. A comb entry either names
one of the 23 built-in combs or, as above, defines a new one on the spot -- with its own name,
its two colours and what a Centrifuge gets out of it. A second bee that wants that same comb
names it by id, since every file is read before anything registers. Leave `centrifuge` out and
the comb exists but the Centrifuge will not take it, which is worth doing on purpose and worth
not doing by accident.

Two things follow from these being read at registration time, when the species registry is
still open. Editing a file needs a restart. And a server and its clients must carry the same
folder: a client missing a bee the server has will show it by its id and render it grey rather
than crash, and a missing comb falls back to Honey Comb's name and colours, but neither will
be right.

Configuration
=============
Every drop chance in the mod lives in `config/mellifera/`, in two files:

- `bees.toml` -- per species, the chance of each comb it yields and of each non-comb drop
  (royal jelly, pollen, ice shards...). One section per species, grouped branch by branch in
  the same order the species file and the Apiarist Database use.
- `centrifuge.toml` -- per comb, the chance of each item it spins down into, plus the same for
  the handful of non-comb inputs the machine takes (silky propolis and friends).

Both are generated on first launch from the tables in the source, and every entry carries a
`# default` comment with the value it started at. Delete a file, or the whole folder, and the
transcribed Forestry/Extra Bees numbers come back exactly. Values must be chances between 0
and 1; anything else is clamped, logged, and corrected in the file. Entries missing from an
existing file are added on the next launch, so a species or an output introduced by a later
version becomes configurable without anyone regenerating anything.

These two files cover what the mod declares in Java, and nothing written in `custom_bees/`. A
bee or a comb defined there carries its chances in its own file, because these files are
generated and never pruned: a section for something a JSON file defined would outlive the file
that defined it, leaving chances configured for a bee nothing can breed. Deleting the JSON
deletes all of it.

Only chances are configurable. Stack sizes, centrifuge timings, mutation odds and the mutation
tree itself are not -- those are still Java tables.

In multiplayer the server decides. Each side reads its own copy of the folder, and a connected
server sends its effective tables at login; those take precedence on the client for as long as
it stays connected, and its own values come back on disconnect. So what drops was always the
server's decision, and the sync is what makes the Apiarist Database and JEI agree with it --
the two `.toml` files no longer have to be kept identical by hand. The `custom_bees/` folder
is the exception, and does have to match: chances sync, but a bee the client does not have is
a bee it can only show by its id.

Building
========
`./gradlew build` produces the jar in `build/libs`. `./gradlew runClient` and
`./gradlew runServer` launch a development instance against `run/`.

The workspace uses Mojang's official mapping names, which carry their own licence that anyone
working on this should be aware of:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Licence
=======
LGPL v3 -- see `COPYING.LESSER` and `COPYING`.

Not a free choice. The species data, the mutation tree and a number of textures are derived
from ForestryMC and from Binnie's Extra Bees, both LGPL v3 (see the third-party note below),
and a derivative of those cannot be released under more restrictive terms.

The build files, the Gradle wrapper and the CI workflow still come from the
[NeoForged MDK](https://github.com/NeoForged/MDK), which is MIT, (c) 2023 NeoForged project. MIT
asks for its notice to travel with those files, so it lives here.

Third-party assets
==================
The apiculture textures under `src/main/resources/assets/mellifera/textures/{item,block,gui}`
(princess/drone/queen bee icons, honey comb, frame, apiary block, the apiary GUI layout, the
six wild beehive blocks, and the centrifuge product icons -- honey drop, honeydew, royal
jelly, pollen, beeswax, refractory wax, phosphor and propolis) are adapted from
[ForestryMC](https://github.com/ForestryMC/ForestryMC), (c) SirSengir and contributors,
licensed under [LGPL v3](https://www.gnu.org/licenses/lgpl-3.0.txt). The bee icons and comb
were recomposited from Forestry's layered source textures; the apiary block, hive blocks and
GUI panel are Forestry's textures recoloured to this mod's palette, geometry unchanged; the
silky and pulsating propolis variants are Forestry's propolis sprite multiplied by the
variant colours from its own `EnumPropolis`. The centrifuge block and GUI are original.

The bees flying around a working apiary ship no art at all: they are Mojang's own bee model
and texture, with the abdomen repainted to the species colour on the player's own copy of the
sheet (see `BeeTextures`, `HiveBeeRenderer`). They replaced a particle drawn with Forestry's
`entity/particles/swarm_bee.png` and flown with Forestry's `ParticleBeeExplore` /
`ParticleBeeRoundTrip` model; neither the sprite nor that flight model is used any more.

The liquid honey sprites are not borrowed either, from Mojang or anyone else. They are
synthesised by `tools/gen_honey_textures.py`, which is kept in the repository because it, and
not the two PNGs it writes, is the editable form of that texture. What it takes from vanilla
water is measured rather than copied: the sheet sizes, the frame counts, the translucency, and
how nearly flat a liquid surface has to be to survive being tiled across a lake.

The apiculture *data* -- the first 44 species with their branches, colours, allele templates
and comb products; the first 14 comb types; the mutation tree with its chances and biome/
temperature/date gates; and the centrifuge recipes -- is likewise transcribed from Forestry's
`BeeDefinition`, `EnumHoneyComb`, `EnumAllele` and `ModuleApiculture` sources under the same
licence. Deviations are documented at the code that makes them.

The 13 resource bees (the Rocky, Metallic, Precious, Mineral, Gemstone and Energetic
branches), their 8 combs and their mutations are transcribed from
[Binnie's Mods / Extra Bees](https://github.com/ForestryMC/Binnie), (c) Binnie and
contributors, also licensed under [LGPL v3](https://www.gnu.org/licenses/lgpl-3.0.txt) --
species names, dominance, colours, allele templates, comb drop chances and mutation pairs
from its `ExtraBeeDefinition`, `ExtraBeeBranchDefinition` and `EnumHoneyComb`. Only the
species whose output has a Vanilla Minecraft equivalent are included, and the centrifuge
yields are rebalanced away from Extra Bees' ore dusts; both departures, and every other one,
are documented at the code that makes them. No Extra Bees textures are used -- the resource
combs reuse this mod's own comb sprite, tinted with Extra Bees' colours.