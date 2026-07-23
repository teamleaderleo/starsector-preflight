# Asset Quality Track (exploratory)

Status: **exploratory / not yet evidence-gated.** This document records a proposed
second track distinct from the measured speed-first program in
[optimization-north-star.md](optimization-north-star.md). Nothing here is committed
work or a release claim. It exists so the idea and its concrete asset facts are not
lost, and so the open questions can be researched (including by external tools).

## Framing: two opposite vectors on the same resources

Everything under consideration lands on one of two axes that pull against each other on
VRAM, GPU upload bandwidth, and decode time:

- **Speed track** (the existing program): make assets *cheaper* — pre-decoded textures,
  persisted Janino bytecode, prepared audio, AppCDS, bounded scheduling. Less work and
  less memory pressure at load.
- **Quality track** (this document): make selected assets *richer* — higher-resolution,
  crisper. This *costs* load time, VRAM, and upload.

They can coexist, but only as **separate, independently switchable opt-in modes**, and
the quality track must never silently regress the speed-track measurements. This matches
the project's exact-gated, fail-open discipline: a quality transformation is applied only
when it passes its gate, otherwise the original bytes are used.

Measured repeat-launch CPU attribution (from the north star) sets the guardrail the
quality track must respect: audio decoding ~24–28% and Janino ~25–27% of samples, with
texture preparation the third dominant domain. Any quality feature is validated against
the existing JFR per-subsystem attribution before and after, so a fidelity gain is never
confused with a load-time regression.

## Not frame generation

The relevant technique is **offline single-image super-resolution baked into assets
once**, not runtime temporal frame interpolation. Given fixed model weights and a fixed
input it is deterministic and therefore reproducible and content-addressable — it slots
into `prepare` as a new transformation type alongside the existing `IDENTITY` path, with
its own faithfulness gate. Model cost is paid once, offline; it does not touch the render
loop.

## Sub-proposal A: crisper fonts (best quality-per-risk; candidate standalone mod)

The most-felt, lowest-risk fidelity win, and a clean candidate to also ship as an
isolated drop-in mod. Fonts are UI-space: no gameplay geometry is coupled to them.

### Concrete asset facts (reviewed 0.98a-RC8 install)

- Location: `Contents/Resources/Java/graphics/fonts/` (this is the core resource root on
  macOS; note `stelnet.log` sits at the root of the same tree, which is why runtime logs
  are now excluded from the resource index — see [resource-index.md](resource-index.md)).
- 51 `*.fnt` files, ~2.1 MB total including atlases.
- Format: **AngelCode BMFont, plain ASCII text (CRLF)**. Each font is a pair:
  `name.fnt` + one or more `name_<page>.png` greyscale/alpha atlases.
- Example header (`insignia15LTaa.fnt`):
  ```text
  info face="InsigniaLT" size=15 bold=0 italic=0 ... stretchH=100 smooth=1 aa=4 ...
  common lineHeight=15 base=12 scaleW=256 scaleH=256 pages=1 packed=0 alphaChnl=1 ...
  page id=0 file="insignia15LTaa_0.png"
  char id=32 x=254 y=39 width=1 height=1 xoffset=0 yoffset=12 xadvance=4 page=0 chnl=15
  ```
- Field roles:
  - **Atlas-space (scale with resolution):** `char x, y, width, height`; `common scaleW, scaleH`.
  - **Layout-space (screen px):** `char xoffset, yoffset, xadvance`; `common lineHeight, base`; `info size`.

### The central design question (research this before building)

There are two very different outcomes, and they must not be conflated:

1. **"Bigger text"** — scale *every* number (atlas-space *and* layout-space) by 2×,
   upscale the atlas 2×. Text renders twice as large. Only useful as an accessibility
   option; it changes UI layout and may break fixed-size panels.
2. **"Same size, sharper" (the goal)** — keep layout-space metrics unchanged so text
   occupies the same screen space, but sample glyphs from a higher-resolution atlas so
   each glyph is supersampled/anti-aliased at its native on-screen size.

Outcome 2 is the desirable one and the harder one: standard BMFont rendering maps atlas
source pixels to destination pixels 1:1, so a higher-res atlas normally implies bigger
text. Getting supersampling-in-place requires the renderer to know an atlas→screen scale
factor. Open questions:

- Does Starsector's (closed-source) font renderer honor `smooth`/bilinear sampling such
  that serving a higher-res atlas + proportionally-scaled `x/y/width/height/scaleW/scaleH`
  while halving the on-screen quad size is even expressible? Likely not without renderer
  cooperation.
- Alternative: does the game already render at a higher internal resolution / support UI
  scaling (0.96+) such that a higher-res atlas is sampled above 1:1 in practice?
- Alternative: regenerate each font from its source TTF at 2× `size` with a proper glyph
  rasterizer (better than upscaling the baked atlas), then decide bigger-vs-sharper by
  which metric set is kept.

### What the community font evidence says (tier-4/5)

A 2024 report describes visible text blur at 140% UI scale; raising multisampling from 0
to 16 did little, and partial relief came only from a lower display resolution or by
selecting a larger existing font in the config. Others call the scaled UI blurry. Three
conclusions follow, and they narrow the design:

- **Launcher UI scaling resamples already-rasterized bitmap-font output** — it upscales
  finished text rather than supersampling the atlas, so a higher-density atlas does not
  automatically get sampled above 1:1 through UI scaling. This makes the naive "2× atlas +
  2× metrics" path unlikely to yield same-size sharpness on its own.
- **MSAA targets rendered geometry, not textured glyph interiors** — it offers little for
  an already-rasterized font atlas. Stop treating it as a font fix.
- **Selecting a larger font** improves readability through bigger glyphs but leaves the
  same-size/high-density problem unsolved.

**Refined recommendation — the practical asset-only solutions are:**

1. a **native-size hinted atlas**: re-rasterize each glyph from the source TTF at exactly
   its on-screen `size` with better hinting/AA (same metrics, same on-screen size, cleaner
   edges), and
2. an **offline supersampled-then-downsampled native atlas**: rasterize large, downsample
   to native size with a good filter, bake that into the atlas (same metrics/size, higher
   effective density per glyph).

Both keep on-screen size and layout identical and do **not** depend on undocumented
renderer scaling. The **2× atlas with proportional metric changes stays an experiment**
for probing whether the renderer ever samples above 1:1 — not the shipping approach.

### Font test matrix

```text
Display resolution:  native | one lower supported resolution
UI scale:            100% | 140% | 200%
Font source:         original atlas
                     hinted native-size atlas
                     offline-supersampled/downsampled native atlas
                     larger existing Starsector font
                     experimental 2x atlas with proportional metric changes
```

Capture lossless crops of identical text and measure: physical glyph height (screen px),
edge-spread width, local contrast, baseline position, character advance, fractional-position
blur, atlas bleed, clipping.

### Two delivery options

- **Standalone Starsector mod (preferred for the "extra mod" idea):** ship replacement
  `{name.fnt, name_<page>.png}` pairs that override `graphics/fonts/*`. Needs no agent.
  Verify that a mod can shadow *core* font paths by load order (the project's own
  winning-provider logic shows later roots win; confirm vanilla Starsector resolves core
  font resources the same way).
- **Folded into preflight:** a font atlas is just a texture through the existing
  `TextureLoader` hook; the `.fnt` is an indexed resource. The prepared-texture cache
  could serve enhanced atlases with the matching `.fnt` as a paired artifact under the
  same exact/faithfulness gate.

## Sub-proposal B: texture super-resolution (broader, higher risk)

Offline SR as a new `Transformation` type, opt-in, deterministic, cached like prepared
textures.

- **Faithfulness gate (in the project's spirit):** round-trip check — downscale the
  upscaled result back to native and compare to the source; if it drifts past a
  threshold, reject and retain original bytes. A *faithfulness* budget instead of a
  *byte-identity* budget.
- **Model spectrum:** classical scalers (xBRZ/Super-xBR/scaleFX) are deterministic but
  tuned for pixel art and look waxy on Starsector's painterly sprites; ML models
  (Real-ESRGAN and kin) suit painterly art and are still reproducible (fixed weights, no
  seed) but can hallucinate — favor conservative settings + the round-trip gate.
- **Load-bearing gotcha — verify first:** ship/weapon sprites have gameplay-coupled pixel
  geometry (bounds points, weapon-mount coordinates, render scale defined relative to
  native sprite pixels in `.ship`/`.variant` data). Naive rescaling can change world
  render size or desync mounts/bounds. **Safe targets:** fonts, UI chrome, backgrounds,
  planet/star/nebula sprites, effect fringes. **Unsafe without display-size handling:**
  hulls and weapons.

### VRAM cost is first-class — the Asset Lab enforces a budget

A quality overlay improves fidelity while increasing launch work, heap pressure, upload
traffic, and VRAM. Because combat-only slowdown often traces to VRAM exhaustion (see
[community-evidence.md](community-evidence.md)), the cost must be estimated up front, not
discovered in a battle. Add a decoded-texture / VRAM estimator to `doctor` and profile
reports, built from the resource census:

```text
Decoded texture bytes:  RGB = w × h × 3      RGBA = w × h × 4
Additional allocations: mip levels, normal/material/surface maps, generated effect
                        textures, framebuffer attachments, temporary upload buffers
Enhancement multiplier: 2× width&height = 4× pixels    4× width&height = 16× pixels
```

Report the pixel multiplier prominently for any enhancement, and generate estimates for:
current profile; proposed enhanced overlay; current + overlay; common UI/campaign working
set; representative combat working set; GraphicsLib-associated maps; and the largest
individual texture allocations.

- The **Asset Lab** (offline, opt-in overlay generator) must **reject or warn** on outputs
  exceeding a configurable memory budget.
- Enhanced assets live in a **separate cache namespace and manifest** from the speed-track
  prepared textures, so a fidelity overlay can never silently enter a speed measurement.

**Implemented (core + prepare report).** `TextureMemoryEstimator` /
`TextureMemoryEstimate` (in `preflight-core`) compute the exact base decoded bytes, RGB/RGBA
counts, largest allocations, the full-mip-chain upper bound, the 2×/4× enhancement
projections (overlay-only and combined-if-both-resident), and a `exceedsBudget(long)` check —
pure arithmetic over the texture manifest, no game launch. `prepare` now emits this under
`.stages.textures.details.memoryEstimate`. Still to do: surface it in `doctor` output, feed
the census image/working-set breakdowns (UI/campaign/combat/GraphicsLib maps), and wire the
budget verdict into the future Asset Lab.

## Explicitly out of scope: in-game FPS

Big-battle slowdown is a largely **single-threaded CPU bottleneck** (combat sim,
projectile physics, AI scripts). This project is deliberately non-invasive to the game's
sim/render loop, and pre-decoded textures are already resident in VRAM by then, so they do
not help steady-state FPS. The only adjacent, honest win is the *opposite* of upscaling:
texture **compression / shrinking oversized mod textures** to reduce VRAM thrashing and
its stutter on low-VRAM machines — a frametime-stability lever, not a raw-FPS one, and one
that trades against Sub-proposal B. A July 2026 community case (smooth campaign, severe
combat slowdown, resolved by cutting VRAM demand and GraphicsLib settings) corroborates
this, though it remains a single recent report — see [community-evidence.md](community-evidence.md).

## Audio quality: deprioritized

Audio super-resolution (bandwidth extension) mostly hallucinates high frequencies onto
source that is already at an adequate sample rate — low ROI, easy to worsen. The audio
subsystem's measured value is decode/load **speed** (~24–28% of samples); keep it there.

## Suggested next steps (measurement-first, matching project culture)

1. Confirm where a heavy-profile load actually spends time using the existing JFR
   attribution, so any quality feature is judged against real per-subsystem numbers.
2. Prototype the font atlas at 2× on a single font, resolve the bigger-vs-sharper
   question empirically, and test core-font shadowing by a standalone mod.
3. Only then consider Sub-proposal B, starting with backgrounds/planets behind a
   round-trip faithfulness gate — never hulls/weapons first.

## External references and research terms

Community load-time and profiling knowledge:

- Fractal forum — Faster Save/Load/Boot times (settings.json): <https://fractalsoftworks.com/forum/index.php?topic=23851.0>
- Starsector Wiki — Troubleshooting slowdown (recommends VisualVM sampler): <https://starsector.fandom.com/wiki/Troubleshooting_slowdown>
- Fractal forum — Performance Issue / CPU bottleneck: <https://fractalsoftworks.com/forum/index.php?topic=18689.0>

Texture super-resolution prior art / pipelines:

- No One Lives Forever ESRGAN x4 upscale pack (ModDB): <https://www.moddb.com/mods/no-one-lives-forever-esrgan-upscale-pack>
- LithTech-engine upscaling tutorial (ESRGAN workflow): <https://www.moddb.com/mods/no-one-lives-forever-esrgan-upscale-pack/tutorials/upscaling-lithtech-engine-games>
- ESRGAN tag index (many game upscale packs): <https://www.moddb.com/tags/esrgan>
- SWTOR Textures Upscaler (reference batch pipeline, GitHub): <https://github.com/ZeroGravitasIndeed/SWTOR-Textures-Upscaler>

Search terms to run where the crawler is blocked (Reddit/forum behind Cloudflare):

- `Starsector settings.json faster loading boot time`
- `Starsector reddit load time many mods GraphicsLib performance`
- `Starsector single threaded combat FPS CPU bottleneck`
- `Starsector mod override core graphics fonts load order`
- `Starsector font mod crisp high resolution UI scaling retina`
- `AngelCode BMFont regenerate atlas 2x metrics supersample`
- `Real-ESRGAN faithful game sprite upscale round-trip validation`
- `GraphicsLib LunaLib MagicLib load time performance`
