# NC Viewer IntelliJ Port

This sub-project is a prototype port of the VS Code “NC Viewer” extension into an IntelliJ Platform plugin that targets IntelliJ IDEA (and other 2024.2+ IDEs). It embeds the existing Three.js-based web viewer inside a JCEF panel and mirrors the VS Code messaging contract so caret changes and file edits stay in sync with the 3D preview.

## Requirements
- **IntelliJ Platform:** 2024.2 Community Edition (or newer) since the Gradle IntelliJ plugin is pinned to that build.
- **JDK:** 17 (JetBrains Runtime ships with the IDE; point Gradle to it to skip toolchain downloads).
- **Gradle:** Wrapper is included (`gradlew` / `gradlew.bat`), so no global install required.

## Directory Layout
- `src/main/resources/ncviewer/media` — bundled Three.js viewer assets copied directly from the VS Code project.
- `src/main/kotlin/com/ncviewer/idea/ui/NcViewerPanel.kt` — hosts the viewer inside a `JBCefBrowser` and bridges messages.
- `src/main/kotlin/com/ncviewer/idea/actions/OpenNcViewerAction.kt` — editor action that opens the tool window for `.nc/.gcode/.cnc` files.
- `src/main/kotlin/com/ncviewer/idea/service/NcViewerProjectService.kt` — project-level service that wires actions and the tool window.
- `src/main/resources/META-INF/plugin.xml` — IntelliJ plugin metadata, action registrations, and tool window declaration.

## Build & Run
```bash
cd nc_viewer_idea
./gradlew build        # compiles Kotlin + packages plugin zip under build/distributions
./gradlew runIde       # launches a sandboxed IntelliJ IDEA with the plugin installed
```

> If you need Windows command prompts, substitute `gradlew.bat` for `./gradlew`.

### Common Gradle Tasks
- `./gradlew test` — runs Kotlin/JVM tests (currently placeholder).
- `./gradlew verifyPlugin` — JetBrains plugin validation (signatures, descriptors, etc.).
- `./gradlew clean buildPlugin` — produces the distributable archive you can install manually.

### Reuse Existing IntelliJ & JDK (faster builds)
By default the Gradle IntelliJ plugin downloads the target IDE + JetBrains Runtime. You can instead point the build to your already-installed IntelliJ IDEA and its bundled JBR:

1. Opt-in by editing `nc_viewer_idea/gradle.properties` (or `~/.gradle/gradle.properties`) and setting:
   ```
   idea.local.path=/path/to/idea-IC-242.23726.103/IntelliJ IDEA CE.app/Contents
   org.gradle.java.home=/path/to/idea-IC-242.23726.103/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home
   ```
   Use an IntelliJ build whose `Resources/product-info.json` reports the same major version (`2024.2`) and whose `lib/modules` directory contains version-suffixed jars (Toolbox installs auto-updated to 2025.x omit these and will fail to resolve dependencies).
2. Alternatively, pass the overrides per build:
   ```bash
   ./gradlew build -Didea.local.path="/path/to/idea-IC-242.23726.103/IntelliJ IDEA CE.app/Contents" \
                   -Dorg.gradle.java.home="/path/to/idea-IC-242.23726.103/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home"
   ```

If the properties are left empty or point to an incompatible install, the build automatically falls back to downloading the matching IntelliJ distribution.

## Development Notes
1. **Web Assets:** The viewer expects the same relative file paths as the VS Code extension. Copy updates from the root `media/` folder into `src/main/resources/ncviewer/media` to keep them in sync.
2. **Message Bridge:** The JCEF layer injects a lightweight `acquireVsCodeApi` shim so the existing `media/index.html` script works unchanged. Kotlin-side messages mirror the `loadGCode`, `cursorPositionChanged`, `contentChanged`, and `highlightLine` contract.
3. **Settings:** `NcViewerSettings` stores the `excludeCodes` array. A UI is not implemented yet; values can be tweaked via the `NcViewerSettings` service for now.
4. **IDE Compatibility:** Because the tool window uses `JBCefBrowser`, the target IDE must ship with JCEF (true for 2020.3+ builds). Stick to 2024.2+ until we broaden testing.

## Next Steps / TODO
- Add a proper Settings/Preferences page to edit exclude-code lists.
- Implement syntax highlighting and inspections for G-code files.
- Add integration tests with the IntelliJ UI Robot or headless message contracts.
- Wire up packaging/signing for JetBrains Marketplace distribution.
