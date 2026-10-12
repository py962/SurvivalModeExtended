# Release checklist: 1.0.0-beta.1 (public beta on Modrinth and CurseForge)

## 1. Must be done before uploading

- [ ] **Auroral copyright line.** MIT requires the original copyright notice. Open Auroral's LICENSE
      (github.com/breakinblocks/auroral) and paste its "Copyright (c) ..." line into `CREDITS.md`
      where the TODO is. (`build.gradle` now packs `CREDITS.md` and `LICENSE` into the jar.)
- [ ] **Make the GitHub repository public** (Settings > General > Danger Zone > Change visibility)
      before submitting to either site. The README, store description and `neoforge.mods.toml` link to
      `github.com/py962/survival-mode-extended` for issues, the licence and `CREDITS.md`; while the
      repository is private those links show players a 404, and CC BY 4.0 requires the credits to be
      visible.
- [ ] **Icon.** 512 x 512 PNG. Upload it to both sites. To show it in the in-game mod list too, put it at
      `src/main/resources/survival_mode_extended_icon.png` and add `logoFile="survival_mode_extended_icon.png"`
      under `[[mods]]` in `neoforge.mods.toml`.
- [ ] **Pickaxe tag.** `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json` lists
      `terrainlab:celestial_forge` (otherwise the forge drops nothing when mined).
- [ ] **Delete files the mod doesn't use:**
      `src/main/java/com/terrainlab/client/renderer/MarshMistRenderer.java` (empty placeholder),
      `src/main/java/com/terrainlab/event/AmbientSoundHandler.java`,
      `src/main/java/com/terrainlab/celestial/BreathConduitHooks-1.java`,
      the three switched-off recipes `celestial_amulet_craft.json`, `celestial_conduit_craft.json` and
      `suspicious_stew_flower.json` in `src/main/resources/data/terrainlab/recipe/`,
      `src/main/resources/data/terrainlab/worldgen/template_pool/outlaws_hideout.json`,
      `TEMPLATE_LICENSE.txt`, `livingentity_bytecode.txt`, `verify_trapper_pillager.png`, and the NeoForge
      files extracted into the project root (`assets`, `data`, `net`, `META-INF`, `mcplogo.png`,
      `neoforged_logo.png`, `url.png`, `pack.mcmeta`, `neoforge.mixins.json`).
- [ ] **No debug left on.** Run configurations used for the release build must not have
      `-Dterrainlab.debug=true` (it only affects your IntelliJ runs, not the jar, but check the log
      of a final test run is quiet).

## 2. Build and test the release jar

- [ ] Gradle > Tasks > build > **clean**, then **build**. The jar is
      `build/libs/survival-mode-extended-neoforge-1.21.11-1.0.0-beta.1.jar`.
- [ ] Test the **built jar**, not the IntelliJ run: a clean instance (CurseForge app or Prism Launcher)
      with NeoForge 21.11.42+, Curios API 14.0.0+ and only this jar. Create a new world.
- [ ] Check the log has no errors from `terrainlab` at startup (recipe, tag or mixin errors).
- [ ] Join a dedicated server too (the NeoForge server installer + the same mods) to confirm
      nothing client-only crashes the server.
- [ ] Quick play-test: temperature HUD, a village and its trades, a guide, the Celestial Forge,
      `/locate structure terrainlab:outlaws_hideout` and `terrainlab:trappers_lodge`.

## 3. Modrinth

1. modrinth.com > sign in > **Create a project**: name "Survival Mode Extended", slug
   `survival-mode-extended`, visibility Draft.
2. Fill in summary, description (`docs/store_description.md`), icon, categories, client/server
   sides, licence and links from `docs/store_page.md`. Add the gallery images.
3. **Versions > Create a version**: upload the jar, version number `1.0.0-beta.1`, channel Beta,
   loader NeoForge, game version 1.21.11, dependency Curios API (Required), changelog from
   `CHANGELOG.md`.
4. **Submit for review.** It goes public once approved.

## 4. CurseForge

1. authors.curseforge.com > **Create project**: Minecraft > Mods. Name, summary, description
   (editor set to Markdown), logo, categories and licence from `docs/store_page.md`.
2. **Files > Upload file**: the jar, release type Beta, game versions 1.21.11 + NeoForge + Java 21
   + Client + Server, related project Curios API as **Required Dependency**, changelog from
   `CHANGELOG.md`.
3. Wait for moderation. The project becomes public when the first file is approved.

## 5. After release

- [ ] Tag the release in git: `v1.0.0-beta.1`.
- [ ] For the next beta: raise `mod_version` in `gradle.properties` (e.g. `1.0.0-beta.2`) and add a
      new section at the top of `CHANGELOG.md` before building.
