# Auram application branding

The client mod sets Minecraft's initial and subsequent window title to `Auram`
and replaces vanilla's application/window icon with the pack's purple unicorn.
The icon is bundled in the mod so ordinary launches work without extra files.
Client mixins are excluded from dedicated servers.

Forge's loading window exists before client mods load. The separate Java 17
startup agent changes GLFW's title argument before window creation, and keeps
later GLFW title assignments consistent. It has its own relocated ASM dependency;
it does not depend on Forge, load Minecraft classes, initialize AWT, or call native
code during `premain`. Keep it outside `mods/`.

## Launcher contract

Install the contents of `build/distributions/auram-branding-<version>.zip` into
`<game-directory>/branding/`. The descriptor is:

```json
{"name":"Auram","icon":"icon.png","javaAgent":"auram-bootstrap.jar"}
```

Resolve the icon and agent relative to the descriptor's directory. Add these
individual JVM arguments before the Minecraft main class (paths are absolute):

```text
-Dauram.branding.name=Auram
-Dauram.branding.icon=/absolute/game-directory/branding/icon.png
-javaagent:/absolute/game-directory/branding/auram-bootstrap.jar
```

Use normal process argument/Java argfile escaping, including paths with spaces.
Keep `-XstartOnFirstThread` on macOS. Do not add the agent to server, installer or
other utility JVMs. A launcher without this descriptor can still start normally;
AuramCore supplies the later title and bundled icon by itself.

On macOS the earliest menu-bar/Dock **name** and icon come from the game's native
application bundle. The launcher must start the game through its branded
`Auram.app`/JLI helper using the same unicorn icon, bundle display/name `Auram`,
and its managed Java runtime. The agent cannot replace that process identity.
AuramCore uses Minecraft's Cocoa icon API to keep vanilla from overwriting the
helper's icon during initialization. Other platforms use GLFW window icons.

## Build and direct validation

`./gradlew :bootstrap:build brandingZip reobfJarJar -x deployToModpack` builds
the agent, its executable bytecode test, the branding archive and production mod.
The agent is also built by the normal full build.

`tools/branding_client_smoke.py --java /path/to/java17 --output /private/qa/path`
starts Java directly with cached JVM-only launch arguments and assets under
`--runtime` (default `~/Library/Application Support/AuramLauncherDev`). It does
not invoke the launcher. It copies the pack into the output directory and tests:

1. Agent enabled, Forge early window enabled, external descriptor icon/name flags.
2. Agent enabled, Forge early window disabled, the same flags.
3. Core alone, early window disabled, bundled icon and default name.

All cases read the actual Cocoa window title and application-icon pixels, reload
resources, and repeat. Agent cases also attempt a later title reset. The smoke
mod/agent use `auram.smoke.hiddenWindow=true` to suppress window showing/focus
while testing; normal launches do not set this property. Results and logs remain
in the output directory; live saves and settings are untouched. This macOS test
does not validate a launcher's native helper or menu/Dock process name.
