# Andromeda
Obsidian-style second-brain app for game design. Android, Kotlin + Jetpack Compose, tablet-first, dark only. Repo: Imbluubyte/Andromeda.

## Rules
- Work ONLY on `main`. No branches, no PRs. Commit small, push to main.
- No README/usage guides unless asked.
- No Android SDK in sandbox: GitHub Actions (`.github/workflows/build.yml`) is the compile check. Read edits like a compiler; on failure use `gh run view --log-failed` (only `e: file:///` lines matter).
- Colours only from `Ad` (ui/Theme.kt). No hard-coded colours.
- All file IO via `data/Workspace.kt` (SAF), on Dispatchers.IO.
- Commit trailers: Co-Authored-By + Claude-Session from the session reminder.

## Token saving
- Use the map below; read only the file you need, with offset/limit. Never read the whole tree.
- Use Grep for symbols; Edit for small changes, don't rewrite files.
- Keep files small and single-purpose; update this map when adding/moving files.

## Map (app/src/main/java/com/andromeda/app/)
- MainActivity: folder picker launcher; Welcome vs Shell
- AppViewModel: all app state (root, projects, tree, tabs, dialogs), file ops
- data/Workspace: SAF file ops, Node, FileKind (md, atlas), attachments in `_attachments/`
- data/AtlasModel: .atlas JSON (items, strokes, shapes), AtlasJson
- data/LinkMeta: web link title/thumb (YouTube oEmbed, og tags), ImageCache
- ui/Theme: `Ad` palette + theme
- ui/Welcome: first-launch folder choice
- ui/Shell: ribbon, tab bar, layout; Sidebar: project switcher + file tree; Dialogs; Common (IconBtn)
- ui/Markdown (parser/preview, [[wiki links]]), MarkdownEditor (edit/preview, autosave)
- ui/AtlasEditor: canvas gestures, toolbar, ink drawing, dialogs
- ui/AtlasState: Atlas state, tools, undo, eraser; AtlasItems: item cards; ColorWheel

## Model
- Project = top-level folder in the chosen workspace folder. Atlas world units = dp.
