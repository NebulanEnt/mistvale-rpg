<h1 align="center">MistvaleRPG</h1>

<p align="center">
  <i>A cozy, story-driven RPG layer for Minecraft — one journey that ties many mods together.</i>
</p>

<p align="center">
  <img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square">
  <img alt="NeoForge 21.1.x" src="https://img.shields.io/badge/NeoForge-21.1.x-E68C2E?style=flat-square">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-5382A1?style=flat-square">
  <img alt="Status: early development" src="https://img.shields.io/badge/status-early%20development-7FE6D6?style=flat-square">
  <img alt="License: All Rights Reserved" src="https://img.shields.io/badge/license-All%20Rights%20Reserved-lightgrey?style=flat-square">
</p>

---

## What is Mistvale?

Mistvale is a world wrapped in mist, and something inside it is waiting to be woken. **MistvaleRPG** is the mod that carries the story of the Mistvale modpack: it adds the souls, the rifts, the Spirit that guides you, and (soon) the Quest Book that turns a pile of independent mods into a single adventure with a beginning, a middle and an end.

The goal is not to add more content for its own sake. The goal is that exploring a dungeon from one mod, cooking with another and fighting a boss from a third all feel like chapters of the same tale.

## Features so far

- **Soul Shards and nine souls.** Collect shards, feed them to caged souls and set free the spirits of Mistvale.
- **Six custom rarities.** Gear is graded in six tiers, each with its own colour and presence.
- **Animated Caged Soul Block.** A GeckoLib-animated cage that reacts to every shard you give it, glows in the colour of the soul inside, and breaks open on the third.
- **Charged Soul Block and the soul platform.** Arrange nine different souls in a 3×3 ring and a ritual begins.
- **Rifts.** Six rift variants (blue, orange, corrupted, white, end and deep) with their own opening, closing and crossing animations, sounds and screen effects.
- **The Spirit of Mistvale.** A hovering guide with its own model, voice and appearances.

## Coming next

- The first dialogue scene with the Spirit
- The Quest Book: an Armory, Advancements and a Bestiary first, then the storylines
- Real destinations behind the remaining rift variants

## Requirements

| Requirement | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |
| [GeckoLib](https://github.com/bernie-g/geckolib) | 4.9.3 or newer 4.x for 1.21.1 |

## Commands (for testing)

These need operator permission (level 2).

| Command | What it does |
| --- | --- |
| `/mistvale rift <variant>` | Opens a rift a few blocks ahead of you. It closes on its own after two minutes. |
| `/mistvale spirit` | Summons the Spirit of Mistvale near you for a short visit. |

## Building from source

You need JDK 21.

```bash
git clone https://github.com/NebulanEnt/mistvale-rpg.git
cd mistvale-rpg
./gradlew build          # jar ends up in build/libs
./gradlew runClient      # start a development client
```

On Windows use `gradlew.bat` instead of `./gradlew`. If your IDE is missing libraries, run `./gradlew --refresh-dependencies`.

## Feedback and community

- Found a bug? Open an [issue](https://github.com/NebulanEnt/mistvale-rpg/issues/new/choose).
- Have an idea, a question, or just want to talk about the story? Start a [discussion](https://github.com/NebulanEnt/mistvale-rpg/discussions).
- Please read the [Code of Conduct](CODE_OF_CONDUCT.md) and [Contributing](CONTRIBUTING.md) notes first.
- Security issues: see [SECURITY.md](SECURITY.md).

## Supporting the project

If you enjoy Mistvale and want to help it grow, the **Sponsor** button at the top of this page lists the ways to do it. It is never expected, and the mod will always be playable without it.

## License

Copyright (c) 2026 NebulanEnt. **All rights reserved.** You are welcome to read the source, play the mod and build it for your own use, but you may not copy, redistribute or modify it without written permission. See [LICENSE](LICENSE) for the full text.

## Credits

- Built on [NeoForge](https://neoforged.net/) for Minecraft by Mojang Studios.
- Animations are powered by [GeckoLib](https://github.com/bernie-g/geckolib).
- Minecraft is a trademark of Mojang Studios. This project is not affiliated with or endorsed by Mojang or Microsoft.
