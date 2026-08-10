# utils

Standalone tools for working on Mellifera. Nothing here ships in the jar and nothing here is
built by Gradle. Open the files directly.

## bee-generator.html

Builds a custom bee file for `config/mellifera/custom_bees/`, and previews the result in 3D with
the game's own model and textures.

Open it by double-clicking. No web server, no install, no network: one HTML file with its CSS and
JS inline, no imports and no `fetch`.

### What it covers

- the species: name, branch, temperature band, dominance, both colours, glint
- all seven trait alleles, every accepted value in a dropdown
- the combs it produces, either naming one of the 23 built-ins or defining a new comb inline,
  with that comb's colours, Centrifuge time and rolled outputs
- the crosses that breed it, both parents picked from the 58 built-in species
- live JSON, download under the right file name, and validation against every rule the mod's
  codecs enforce at load time
- reading an existing file back in, so editing a bee does not mean retyping it

Fields left at their default are omitted from the output, the same way a Java definition only
names the alleles it changes.

### The 3D preview is the real thing, not a lookalike

Nothing about the preview is drawn from imagination:

| What | Where it comes from |
| --- | --- |
| Bee geometry | `AdultBeeModel.createBodyLayer()`, part for part, including the wing `CubeDeformation` and the mirrored left wing |
| Texture unwrap | `ModelPart.Cube`'s six faces and `ModelPart.Polygon`'s UV assignment, transcribed |
| Part transforms | `ModelPart.translateAndRotate`: offset, then rotate Z, Y, X |
| Animation | `BeeModel.setupAnim` and `AdultBeeModel.bobUpAndDown`, in the flying, not-angry state an item bee is held in |
| Pose in the slot | `BeeSpecialRenderer.transform`: scale, the 19-pixel bone height, then -25 degrees pitch and 200 degrees yaw |
| Bee recolour | `BeeTextures`: the same abdomen and antenna texel rectangles, the same luminance normalised against the brightest texel in the region |
| Lighting | `assets/minecraft/shaders/include/light.glsl`'s `minecraft_mix_light`, with the two directions built the way `com.mojang.blaze3d.platform.Lighting` builds them: `ITEMS_FLAT` for the comb sprite, `ITEMS_3D` for the bee. This is the part that decides whether a colour on screen is the colour in the file |
| Caste scale, stinger, gold antennae | `assets/mellifera/items/{princess,drone,queen}_bee.json` |
| Comb sprite | The mod's own `comb_base.png` and `comb_overlay.png`, embedded, layered and tinted exactly as `items/honey_comb.json` says: base takes the secondary colour, overlay takes the primary |
| Comb shape | `minecraft:item/generated`, so the flat sprite one sixteenth thick with a rim wherever an opaque texel meets a transparent one |
| Built-in comb colours | `MelliferaCombTypes`, so naming an existing comb previews what the game ships |

Rendering is hand-written WebGL. A library would have meant a CDN, and a CDN would have meant
this file no longer opens from disk on a machine with no network.

### The three textures

All three are base64 data URIs in the `<script>` block: `BEE_PNG`, `COMB_BASE_PNG` and
`COMB_OVERLAY_PNG`. They have to be embedded rather than read from a folder beside this file,
because the recolour and the tint both need the *pixels*, and Chrome and Edge refuse to hand a page
the pixels of a `file://` image. Anything else means a tool that works in one browser.

`BEE_PNG` is `assets/minecraft/textures/entity/bee/bee.png` from the client jar, 422 bytes of
Mojang's art living in a developer tool that ships in no jar. The other two are the mod's own
sprites from `assets/mellifera/textures/item/`.

Never retype one of these by hand. A single wrong character makes a PNG that still decodes, just
with fewer opaque texels, and the failure looks like a layer whose colour does nothing rather than
like a broken file. Rewrite them from the source files instead:

```sh
python - <<'EOF'
import base64, re
html = open('utils/bee-generator.html', encoding='utf-8').read()
sources = {
    'BEE_PNG': 'bee.png',  # extract from the client jar first, see below
    'COMB_BASE_PNG': 'src/main/resources/assets/mellifera/textures/item/comb_base.png',
    'COMB_OVERLAY_PNG': 'src/main/resources/assets/mellifera/textures/item/comb_overlay.png',
}
for name, path in sources.items():
    good = base64.b64encode(open(path, 'rb').read()).decode()
    match = re.search(name + r' = "data:image/png;base64,([^"]+)"', html)
    print(name, 'ok' if match.group(1) == good else 'rewritten')
    html = html[:match.start(1)] + good + html[match.end(1):]
open('utils/bee-generator.html', 'w', encoding='utf-8').write(html)
EOF
```

To get a fresh `bee.png` out of the client jar in the Gradle cache:

```sh
unzip -o -j "$USERPROFILE/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar" \
  "assets/minecraft/textures/entity/bee/bee.png" -d .
```

### Keeping it honest

The tool hardcodes what the mod accepts, because a static file cannot ask the mod. If any of
these change, the table at the top of the `<script>` block has to change with it:

| Source | Table |
| --- | --- |
| `bee/BeeTemplate.java` and the seven `*Allele` enums | `ALLELES` |
| `registry/MelliferaBeeSpecies.java` | `SPECIES` |
| `registry/MelliferaCombTypes.java` | `COMBS` |
| `registry/MelliferaBeeBranches.java` | `BRANCHES` |
| `config/CustomBeeDefinition.java`, `config/CustomCombDefinition.java` | `DEFAULTS`, `MAX_COMB_TICKS` |

Two things it cannot check, both by nature: whether an item id in a Centrifuge output exists at
runtime (a missing one is dropped with a warning, see `CustomCombDefinition.toRecipe`), and
whether a parent species defined in another config file is present.

A file in this folder is read at registration time, so the game needs a restart to pick up a new
bee. See the note in `config/CustomBees.java`.
