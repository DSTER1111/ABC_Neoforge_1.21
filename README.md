# Better Circuits

*A.C.E.S: Better Circuits.*

A NeoForge 1.21 mod focused entirely on redstone: logic gates, wiring, and utility blocks.

## About this document

This README documents every block and item the mod adds, organized by category. Each entry is
tagged with the version it was introduced in.

When a feature's behavior changes in a later version, the **original entry is left in place**
and a new note is appended below it describing the new behavior and the version it shipped in —
entries are never overwritten, so this file doubles as a per-feature changelog.

---

## Table of Contents

- [Blocks](#blocks)
  - [Redstone Logic Gates](#redstone-logic-gates)
  - [Lightweight Redstone Gate Family](#lightweight-redstone-gate-family)
  - [Redstone Wiring & Rails](#redstone-wiring--rails)
  - [Machines & Utility](#machines--utility)
  - [Ores, Raw Materials & Building Blocks](#ores-raw-materials--building-blocks)
  - [Aluminum Fixtures](#aluminum-fixtures)
- [Items](#items)
- [World Generation](#world-generation)
- [Vanilla Tweaks & Gameplay Changes](#vanilla-tweaks--gameplay-changes)

---

## Blocks

### Redstone Logic Gates

Floor-mounted, horizontal-facing gates built on vanilla's `DiodeBlock` (like the
Repeater/Comparator) — instant to break, stone breaking sound, 2-tick signal delay unless noted.

#### AND Gate
*Added in Version 1.0.0*

A true 2-input AND gate. Unlike vanilla diodes it reads two inputs from its **left/right sides** 
(relative to the direction it faces); output of 15 when both sides are powered. 
`LEFT_POWERED`/`RIGHT_POWERED` are separate cosmetic properties that light up each side's torch. 
It can be locked like a repeater, but the lock signal comes from directly **behind** the gate — 
the one side it doesn't already have input/output.

#### XOR Gate
*Added in Version 1.0.0*

Same 2-side-input shape as the AND Gate, but outputs 15 only when **exactly one** side is
powered. Can be locked with a signal **behind** the gate (the unused side).

#### Redstone Inverter
*Added in Version 1.0.0*

Follows standard vanilla input/output sides. Outputs 15 when input is 0, and 0 when input is >0.
Can be locked from either side (like a repeater).

#### Redstone Threshold
*Added in Version 1.0.0*

Single input/output diode with a right-click GUI for setting a mode and a threshold value
(1–15), backed by its own block entity and menu. Three modes compare the incoming analog signal
against the threshold: **Less Than**, **Equals**, and **Greater Than** (Less Than additionally
requires the signal be above 0, so a bare "no power" input doesn't count as "less than"). While
the condition is true, the block's output matches the input signal rather than a flat 15. 
Can be locked from either side — while locked, its GUI cannot be opened and its output is frozen
at whatever it last was, regardless of further input changes.

#### Redstone RS Latch
*Added in Version 1.0.0*

A true bistable memory gate — unlike every other gate in this list, which is purely combinational
(output depends only on the current input). It uses both ends of both axes: the direction it
faces and the opposite direction are its two outputs, and the two sides are its two inputs.
A pulse on the currently-active input flips which state is active, swapping which side
outputs. A 20-tick cooldown after each flip prevents runaway flip storms if an input is held.

#### Timer
*Added in Version 1.0.0*

Diode with a block entity, a right-click GUI, and a custom animated 3D spinning pointer on top of
the block. While powered, it counts up every tick toward a configurable target — anywhere from 1
second to 1 hour, editable in either ticks or seconds via the GUI's integer input box (click it
and type the exact number, or slide the selector bar). On reaching the target it fires a 1-tick
output pulse and resets; losing input resets its progress to 0 immediately. Reading it from the
side with a comparator emits a continuous **analog output** proportional to `progress / target`.
It can also be locked like the other gates — while locked, its GUI cannot be opened and counting
is frozen entirely.

#### Redstone Capacitor
*Added in Version 1.0.0*

Single input/output diode that increments by 1 its output strength every time it receives an input pulse.
It wraps back to 0 after 15. Lockable; unlocking resets the count back to 0. 

#### Randomizer
*Added in Version 1.0.0*

Single input, three outputs (left/middle/right relative to facing). On a rising edge it randomly
picks one of the three directions and asserts output there for exactly as long as the input remains;
releasing input stops that output.

#### Rain Detector
*Added in Version 1.0.0*

A line-for-line mirror of vanilla's Daylight Detector, but reads rain exposure instead of sky
light: output is 0–15 scaled by the current rain intensity, and only counts at all when the
position is actually being rained on (clear sky above, and the biome allows rain). Right-click
cycles an inverted mode exactly like the Daylight Detector.

#### Heat Detector
*Added in Version 1.0.0*

Another mirror of the Daylight Detector that scans outward along each of the 6 axis
directions — up, down, and the 4 horizontal directions — looking for the closest unobstructed
"hot" block: lava, lava cauldrons, fire, soul fire, torches (regular and soul), and lit
campfires, soul campfires, furnaces, smokers, and blast furnaces (a furnace-family block only
counts while actually lit, not just present). A solid block in the way stops that direction's
search entirely, same as a wall blocking line of sight. Output is 15 if the closest hot block is
directly adjacent, 10 if one block away, 5 if two blocks away (three-block range), and 0 if
nothing qualifying is found within range. In the Nether (or any other ultrawarm dimension),
ambient heat alone keeps the reading at a floor of 5 and the range shrinks to two blocks, though a
genuinely close source can still push it up to 10 or 15. Can be inverted to detect "cold" blocks instead:
Ice, Packed Ice, Blue Ice, Frosted Ice, Snow, or Snow Blocks. The Inverted version gives off that same
floor of 5 in the End instead (no range change). Short of being in either dimension, a sufficiently hot
Overworld biome (one with no rain at all, like a desert) grants the Nether's own floor of 5 while not
inverted, and a sufficiently cold one (one that snows) grants the End's own floor of 5 while inverted.
Recipe: glass, snowballs, wooden slabs (same shape as the Rain Detector's own recipe, with a
snowball in place of the slime ball).

### Lightweight Redstone Gate Family
*Added in Version 1.0.0*

A 'lightweight' counterpart to the vanilla repeater/comparator, and every diode block added above.
Mountable on any of a block's 6 faces (24 total orientations) instead of only the floor. 
Functionally identical to their heavy counterparts; each one crafts with Redstone Cable + Aluminum Ingot
in place of the heavy version's redstone dust + stone.

Included: Lightweight Redstone Inverter, Lightweight Threshold (with the same lock mechanic as
its heavy counterpart), Lightweight AND Gate, Lightweight XOR Gate, Lightweight Repeater
(any-surface vanilla repeater), Lightweight RS Latch, Lightweight Timer (its own block
entity/renderer, sharing the heavy Timer's GUI and lock mechanic), Lightweight Comparator
(any-surface vanilla comparator), Lightweight Capacitor, and Lightweight Randomizer.

### Redstone Wiring & Rails

#### Redstone Cable
*Added in Version 1.0.0*

A wire that transmits redstone signal and mounts on any block face — up to 6 independent faces per
block position, stored per-face rather than as a blockstate. Connects to same-plane cables, sibling
faces on the same block, diagonal "wrap-around" cables sharing a support corner, and ordinary redstone
components, decaying by 1 per 'hop' like redstone dust. Breaking removes a single face at a time via a 
precise raycast, without affecting any other face on the same block.

#### Insulated Redstone Cable
*Added in Version 1.0.0*

Sixteen dye-colored variants of Redstone Cable. A colored face never connects to (or powers) a
differently-colored or plain cable face, even sharing the same block position — letting several
independent signal runs share one block space. Made by combining a Redstone Cable with a
matching colored carpet, or by right-clicking a bare cable face with one to convert it in place.
Never loses signal strength.

#### Bundled Redstone Cable
*Added in Version 1.0.0*

A third cable type that relays all 16 Insulated colors through one physical wire via
per-color flood-fill propagation. Connects to itself and to any Insulated Cable, but never to a
plain Redstone Cable or a generic redstone component. Never loses signal strength.

#### Aluminum Cable Frame
*Added in Version 1.0.0*

Shares Redstone Cable's own block, so it can occupy the same block position as up to 6 cable
faces at once. Unlike every cable face, which stays instant to break regardless of what's held, a
frame requires a pickaxe and takes real, tool-scaled mining time — and only drops when broken
with a pickaxe. A simplified hitbox plus one per connected arm makes it easy to target even though
its actual rendered geometry is a hollow lattice. Frames connect to directly adjacent frames 
(never diagonally), extending a visible arm between them. Its main purpose, though, is a 7th 
"center" slot: right-clicking a frame with a Bundled or Insulated Cable (never plain Redstone Cable)
sets it on the frame, where it connects to any color-compatible face on the same block and to a 
neighboring block's own center cable — center-to-center only — extending both a cable arm and a
frame arm toward each connection.

#### Comparator Rail
*Added in Version 1.0.0*

A rail that reads a passing chest or hopper minecart's inventory and outputs an analog signal the
same way a comparator reads a real container block; non-container minecarts (plain, furnace,
TNT) read as 0.

#### Gold Button
*Added in Version 1.0.0*

A plain vanilla-style button (mountable on any of a block's 6 faces, just like the wood and stone
buttons) with a much shorter pulse than either: 10 ticks, versus wood's 30 and stone's 20. Recipe:
gold nuggets ×4 (2×2).

#### Iron Button
*Added in Version 1.0.0*

The mirror image of the Gold Button: same plain vanilla-style button, but with a much longer
pulse instead — 40 ticks, versus wood's 30 and stone's 20. Recipe: iron nuggets ×4 (2×2).

### Machines & Utility

#### Filtered Hopper
*Added in Version 1.0.0*

Visually a hopper with an item-frame display in its funnel opening. Right-clicking with an
item sets it as the hopper's filter (consuming one); once set, the hopper only sucks up items
matching that exact item and its relevant data (enchantments, potion effects, custom names,
trims, etc. — but not durability or shulker/bundle contents). Attacking the frame, or dispensing
shears at it, ejects the current filter. Every other part of the hopper (5-slot inventory, item
transfer, comparator output) is unmodified vanilla behavior.

#### Filtered Hopper Minecart
*Added in Version 1.0.0*

Minecart version of the Filtered Hopper, with the same filtering rule.

#### Blower
*Added in Version 1.0.0*

Dispenser-shaped block that, while powered, continuously pushes entities within 8 blocks of its
front face — the push weakens with distance — and emits a visible wind particle stream.

#### Vacuum
*Added in Version 1.0.0*

The mirror image of the Blower: pulls entities toward it instead of pushing them away, using the
same range/falloff.

### Ores, Raw Materials & Building Blocks
*Added in Version 1.0.0*

- **Aluminum Ore** / **Deepslate Aluminum Ore** — mined with a pickaxe, drop 2–4 Raw Aluminum
  (Fortune-scaled).
- **Block of Aluminum** / **Block of Raw Aluminum** — standard 9-ingot/9-raw storage blocks. The
  raw block can additionally be smelted or blasted directly into a Block of Aluminum, which
  vanilla doesn't allow for its own equivalent compressed raw blocks.

### Aluminum Fixtures
*Added in Version 1.0.0*

Aluminum Lantern, Aluminum Torch/Wall Torch (with its own particle flame color), Aluminum Chain,
Aluminum Bars, Aluminum Door, Aluminum Trapdoor (both hand-openable like Copper's, unlike Iron's),
and Aluminum Grate (a Copper Grate clone). All craft from Aluminum Ingot/Nugget, mirroring
vanilla's Iron/Copper equivalents.

---

## Items

Standalone items not already covered as a block's placement item above.

#### Screwdriver
*Added in Version 1.0.0*

Shift-right-click cycles the orientation of any block with a recognized
facing/rail-shape/orientation property — a generic mechanism rather than a hardcoded block list,
so it covers every gate in this mod as well as vanilla dispensers, observers, pistons, rails,
hoppers, crafters, and more. Consumes 1 durability (238 max, like Shears) per use; enchantable
with Unbreaking and Mending. Recipe: aluminum ingots + redstone.

#### Aluminum material chain
*Added in Version 1.0.0*

Raw Aluminum, Aluminum Ingot, and Aluminum Nugget — the base material chain (9 nuggets → 1 ingot,
9 ingots → 1 block, with "9 from 1" un-crafting recipes in both directions).

---

## World Generation

#### Aluminum Ore generation
*Added in Version 1.0.0*

Generates throughout the Overworld in stone- and deepslate-replaceable blocks, vein size 9,
common ore placement with 12 attempts per chunk, Y −64 to 80 (uniform distribution), in every
applicable biome.

---

## Vanilla Tweaks & Gameplay Changes

*Added in Version 1.0.0*

- **Cauldron dispenser interactions** — Dispensers can now fill/empty cauldrons with water, lava,
  or powder-snow buckets, fill glass bottles from a water cauldron, and use potions to fill an
  empty or partial water cauldron — the same interactions a player can already do by hand, now
  also usable via dispenser (vanilla dispensers previously supported none of this). Unlike
  vanilla's own player-facing version, a bucket can only fill a cauldron that's genuinely empty —
  a non-empty cauldron (regardless of level or liquid type) can only be emptied, via an empty
  bucket, not overwritten with a different bucket.
- **Filtered Hopper dispenser interaction** — A dispenser facing a Filtered Hopper puts whatever
  it dispenses into the hopper's filter slot instead of its main inventory; dispensing Shears at
  an occupied filter empties it (pops the filtered item out) instead.
- **Farmland seed dispensing** — Dispensers can now plant vanilla seeds (Wheat, Beetroot, Carrot,
  Potato, Melon, Pumpkin, Torchflower, Pitcher) directly onto farmland in front of them, instead
  of just ejecting the item. No vanilla dispenser ever plants crops.
- **Nether Wart dispensing** — Dispensers now plant Nether Wart directly onto Soul Sand in front
  of them, the same way farmland seed dispensing works for regular crops — vanilla dispensers
  never did this either.
