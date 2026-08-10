package com.joaonf.mellifera.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.joaonf.mellifera.Mellifera;
import com.joaonf.mellifera.bee.CentrifugeRecipe;
import com.joaonf.mellifera.bee.CombType;
import com.joaonf.mellifera.bee.ItemProduct;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/// A comb that does not exist yet, written inline where a custom bee lists its products:
/// `"combs": [{ "comb": { "id": "obsidian", ... }, "chance": 0.10 }]`.
///
/// WHY INLINE AND NOT ITS OWN FOLDER. A new comb exists because some bee makes it -- there is
/// no such thing as a comb nothing produces, and a `custom_combs/` folder beside the bees
/// would mean two files to add one idea, plus a comb that loads fine and is unobtainable when
/// somebody edits only one of them. Defining it where it is first produced keeps the whole
/// bee, its comb and what that comb spins down into in the one file a player is already
/// editing. A second bee that wants the same comb names it by id, exactly as it would name
/// `mellifera:stone` -- every file is read before anything registers, so which file defines
/// it does not matter.
///
/// The comb ends up in the same registry as the 23 built-in ones (see MelliferaCombTypes), so
/// the item, its two-layer tint, the Centrifuge, JEI and the creative tab all pick it up
/// without knowing it came from a file.
///
/// Its chances, though, live only here. They are not written into `centrifuge.toml` beside the
/// built-in combs': that file is generated and never pruned, so a row for a comb defined in a
/// JSON file would outlive the file, leaving chances configured for a comb that no longer
/// exists. One file defines the comb and everything it does, and deleting it deletes all of it.
///
/// @param id the registry path, in this mod's namespace: `obsidian` becomes
///           `mellifera:obsidian`, which is what another bee's `combs` list would name
/// @param name shown in-game, and the whole item name rather than a prefix -- a comb is named
///            after what it is, not after the bee that made it, so write "Obsidian Comb".
///            Used as a translation key, so a resource pack can localise it and anything
///            without one simply displays the string as written
/// @param primaryColor the cell walls. The ore combs share 0x363534 for a reason -- see the
///                     family note in MelliferaCombTypes before picking something brighter
/// @param secondaryColor the cell fill, which is the colour the comb reads as
/// @param processTicks how long a Centrifuge takes over one, 20 (one second) like every
///                     built-in comb unless there is a reason
/// @param centrifuge what it spins down into. May be left out, which makes a comb the
///                   Centrifuge will not accept at all -- worth doing on purpose (Forestry's
///                   Irradiated comb is the joke of the Vengeful branch), and worth not doing
///                   by accident
public record CustomCombDefinition(
    Identifier id,
    String name,
    int primaryColor,
    int secondaryColor,
    int processTicks,
    List<Output> centrifuge
) {
    /// MelliferaCentrifugeRecipes.STANDARD_TICKS, repeated here rather than read from it:
    /// touching that class from this codec's initialiser would pull in its item table, which
    /// cannot be built until the item registry is full. Keep the two in step.
    ///
    /// Ticks of *progress*, so this is the unpowered time -- 8 seconds, an eighth of that on FE.
    private static final int DEFAULT_TICKS = 160;

    private static final Codec<Identifier> ID = Codec.STRING.comapFlatMap(
        CustomCombDefinition::parseId, Identifier::getPath);

    public static final Codec<CustomCombDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ID.fieldOf("id").forGetter(CustomCombDefinition::id),
        Codec.STRING.fieldOf("name").forGetter(CustomCombDefinition::name),
        ConfigCodecs.COLOR.optionalFieldOf("primary_color", 0x363534).forGetter(CustomCombDefinition::primaryColor),
        ConfigCodecs.COLOR.optionalFieldOf("secondary_color", 0xE8D56A).forGetter(CustomCombDefinition::secondaryColor),
        Codec.intRange(1, 20 * 60).optionalFieldOf("centrifuge_ticks", DEFAULT_TICKS).forGetter(CustomCombDefinition::processTicks),
        Output.CODEC.listOf().optionalFieldOf("centrifuge", List.of()).forGetter(CustomCombDefinition::centrifuge)
    ).apply(instance, CustomCombDefinition::new));

    /// A comb id is a registry path, and the rules for that are narrower than for a JSON
    /// string. `mellifera:obsidian` is accepted as well as `obsidian`, because that is how the
    /// same id is written two lines further down in the `combs` list of the next bee, and a
    /// file that has to spell it one way here and another way there is a file that gets it
    /// wrong. Any other namespace is refused rather than silently re-homed: this registers
    /// into mellifera, and an id claiming otherwise would be a lie about where the comb lives.
    private static DataResult<Identifier> parseId(String raw) {
        String prefix = Mellifera.MODID + ":";
        if (raw.contains(":") && !raw.startsWith(prefix)) {
            return DataResult.error(() -> raw + " is not a usable comb id -- a new comb belongs to "
                + Mellifera.MODID + ", so write \"obsidian\" or \"" + prefix + "obsidian\"");
        }

        String path = raw.startsWith(prefix) ? raw.substring(prefix.length()) : raw;
        Identifier id = Identifier.tryBuild(Mellifera.MODID, path);
        return id == null
            ? DataResult.error(() -> raw + " is not a usable comb id -- use lower-case letters, digits and underscores")
            : DataResult.success(id);
    }

    /// One thing a Centrifuge hands back, rolled on its own like every other centrifuge
    /// output -- so a comb listing wax, a honey drop and obsidian can give all three from one
    /// spin, or only some of them.
    ///
    /// @param item any item id, this mod's or another's
    /// @param count how many, on the rolls that succeed
    /// @param chance per-spin odds, edited here rather than in `centrifuge.toml`
    public record Output(Identifier item, int count, float chance) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(Output::item),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Output::count),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Output::chance)
        ).apply(instance, Output::new));
    }

    public CombType toCombType() {
        return new CombType(name, primaryColor, secondaryColor);
    }

    /// The centrifuge row, or empty when the comb declares no outputs.
    ///
    /// An item that does not exist is dropped with a warning rather than failing the comb: a
    /// custom bee whose comb names an item from a mod that is not installed should still be a
    /// bee, and a comb the player can hold, rather than a load error about a line they may not
    /// have written. Resolved here rather than at every roll because by this point the item
    /// registry is full -- comb types register after it.
    public Optional<CentrifugeRecipe> toRecipe() {
        List<ItemProduct> outputs = new ArrayList<>(centrifuge.size());

        for (Output output : centrifuge) {
            if (!BuiltInRegistries.ITEM.containsKey(output.item())) {
                Mellifera.LOGGER.warn("comb {} spins down into {}, which is not an item -- that output is dropped",
                    id, output.item());
                continue;
            }

            Item item = BuiltInRegistries.ITEM.getValue(output.item());
            outputs.add(new ItemProduct(() -> item, output.count(), output.chance()));
        }

        return outputs.isEmpty()
            ? Optional.empty()
            : Optional.of(new CentrifugeRecipe(processTicks, List.copyOf(outputs)));
    }
}
