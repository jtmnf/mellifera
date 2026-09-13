# Mellifera

Find a wild hive, open it with a Scoop, take the Princess and the Drones inside it, and put them in an Apiary. Every bee carries a genome - species, speed, lifespan, fertility, flowering, territory, temperature tolerance, a special effect, and whether it flies at night, in the rain or with no sky above it - with a dominant and a recessive allele for each. What a queen produces, how fast she works, and whether she will work at all where you put her all follow from those genes.

And you are not told any of it. A bee names its species and nothing else until a Beealyzer reads it.

## Breeding

Cross two species and they may mutate into a third. There are **58 species across twenty branches**, from the wild Forest and Meadows bees up to the Imperial, Demonic, Phantasmal, Diamond and Ecstatic lines at the top of each one, reached through a mutation tree gated on climate, biome, date and the parents you feed it.

Inheritance is Mendelian and visible: a bee's expressed allele is not the whole story, and the recessive one it is quietly carrying is what the next generation may surface. The **Apiarist Database** lists every species you have discovered, its products, and the pairs that lead to it.

Climate is a real constraint, not flavour. A Tropical line will not work in a tundra unless you shelter it, and the temperature where a hive actually stands - torches and all - is what counts.

## The three stops

A hive stops for the dark, for the rain, and for having no sky over it. The first two can be answered with a frame or with a gene - the frame wears out, the gene does not - and the third only with a gene: an apiary under a roof works for a cave-dwelling line and for nothing else. All three genes are recessive, so crossing one into a line hides it, and getting it back is the work.

## Building a line deliberately

- **Frames** go in the Apiary and change how it runs: more comb per cycle, four times the production rate, forced dominant or recessive inheritance, multiplied mutation rolls, a queen ended after a single cycle, shelter from the climate, work after dark, work through rain, or a hive that requeens itself from its own brood. They wear out as they work; an ender pearl on an anvil makes one permanent.
- **The Isolator** extracts a single gene from a bee into a Serum, and **the Infuser** writes that Serum back into another bee - so a trait can be moved onto a line instead of being bred for and hoped for.
- **The Apiary stacks** three high: three more frame slots and more output per level, and a faster hive at each one.

## Products

Queens drop combs; the **Centrifuge** turns those into honey, wax, propolis, phosphor and the resource drops of the deeper branches. The **Squeezer** presses honey drops into liquid honey, the **Tank** holds it, and the **Carpenter** spends it building the frames - which is where every special frame is made, and where a worn one is repaired.

The **Engine** burns that same honey, or peat, into 80 FE a tick - two machines' worth - the **Capacitor** banks what nothing is asking for yet, and the **Energy Pipe** and **Fluid Pipe** carry power and liquid to where they are wanted - the fluid one in glass, so a working run reads as one from across the room. So an apiary can power itself, and no machine needs power to run: Forge Energy makes them eight times faster, and any tech mod that provides it works just as well.

## Advancements

Nineteen of them, starting when the first wild hive comes apart and branching into the combs, the power, the breeding and the genetics bench. Two of them name a bee rather than an item - a species neither parent was, and a species at the top of its branch - and a data pack can name one the same way, through the mod's own `mellifera:bee` predicate.

## Bees of your own

Drop a JSON file in `config/mellifera/custom_bees/` and it becomes a real species: it appears in the Apiarist Database and in JEI, it can be bred for, and it is filed under a branch like any other. It can define its own combs, with their own colours and centrifuge outputs. Every drop chance in the mod is configurable too, in two generated `.toml` files that document their own defaults - and in multiplayer the server's tables win.

## Requirements

- Minecraft 26.2
- NeoForge 26.2.0.76 or later
- **JEI** - optional. With it installed, every comb, centrifuge recipe, frame recipe, engine fuel and mutation gets a page, clicking the drive track in the middle of a machine opens that machine's page, and a bee can be dragged from JEI onto an Apiary to set that hive's breeding objective.

## Credits and licence

LGPL v3. The species data, the mutation tree and a number of textures are derived from [ForestryMC](https://github.com/ForestryMC/ForestryMC) (© SirSengir and contributors) and from [Binnie's Extra Bees](https://github.com/ForestryMC/Binnie) (© Binnie and contributors), both LGPL v3. The bees themselves are Mojang's own model and texture, recoloured per species on your own client - no art is redistributed. Full attribution is in the repository's README.
