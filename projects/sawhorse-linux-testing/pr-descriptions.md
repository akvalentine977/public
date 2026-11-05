# Pull request descriptions, ready to paste

Open each pull request from your fork's branch against `sawhorse3d/sawhorse` `main`. The branch names
below are the ones the patches create; `git am` keeps the commit messages. Each description mirrors the
project's own tone: what changed, why, how it is tested.

---

## PR 1: branch `fix/desktop-discard-question`

**Title:** fix(desktop): the discard question is waited for

In the desktop app (Windows, macOS, Linux), File > Open and File > New replaced a model that had unsaved
changes at once; "Discard the unsaved changes to this model?" was never seen.

**Why.** `tauri-plugin-dialog` injects an init script that replaces `window.confirm` with an asynchronous
function, so `confirmDiscard()`'s synchronous check saw a Promise (truthy) and went ahead. The shell had also
never granted `dialog:allow-message`, so the plugin's dialog could not have shown. The browser build was
never affected, which is why the e2e suite did not see it.

**What.** `confirmDiscard` is asynchronous and awaited at its three call sites (Open, New, an empty SketchUp
file opening as a new model). On the desktop it asks through the plugin (`askYesNo` in `src/io/files.ts`,
with the app's name on the dialog); in the browser through `window.confirm` as before.
`capabilities/default.json` grants `dialog:allow-message`.

**Tests.** `src/io/files.test.ts` (browser and desktop paths; the desktop one waits for the answer);
`e2e/files.spec.ts` stands in the desktop's asynchronous confirm and checks that Open and New over unsaved
changes wait for No and keep the model, and go ahead on Yes. The e2e test fails on `main` before the fix.

**Found** on Linux (0.1.0-alpha.4 .deb and AppImage, and `main`) by scripting the app under Xvfb; the
write-up with screenshots is in the issue.

---

## PR 2: branch `fix/save-as-extension`

**Title:** fix(files): a Save As name without its extension gets it

On Linux the desktop's Save dialog (GTK's file chooser) hands the name back as typed: `cabinet` was saved as
`cabinet`, a valid document that the Open dialog's "Supported files" filter then hid. Windows and macOS add
the filter's extension in the dialog itself, so only Linux saw it.

**What.** A name without one of the type's extensions gets the type's first (`withExtension` in
`src/io/files.ts`: `cabinet` → `cabinet.shz`, `cabinet.v2` → `cabinet.v2.shz`, `part` → `part.stl`;
`Cabinet.SHZ` stays). The shell's `save_file_atomic` accepts such a path as the chosen one less its added
extension (`without_added_extension` in `sawhorse-safe-save`) and then allows it by its full name, so the
saves and reads that follow go by it. The page can only ever write `<chosen>.<ext>` beside a file the person
named in a Save dialog.

**Tests.** `src/io/save.test.ts` (the dialog's name with and without the extension, in either case; a
cancelled dialog; a second save to the handle's path); `src-tauri/safe-save` (the chosen path less its added
extension; none to take off). `cargo clippy -D warnings` and `cargo fmt --check` on `src-tauri` pass.

---

## PR 3: branch `feat/back-left-bottom-views`

**Title:** feat(view): Back, Left and Bottom views on the View menu

The README's "not yet" list had Back and Bottom views. This adds Back (looking along -Y), Left (along +X)
and Bottom (looking up +Z with +Y up the screen: the plan seen through the floor, so +X runs left), each a
square parallel view framed like the others.

**Where they live.** On the View menu as "Back view", "Left view" and "Bottom view". The top bar keeps the
mock's five buttons (`BAR_VIEWS`); a menu view lights none of them. If you would rather have them on the bar
too, drop the `menuOnly` flags in `src/viewport/camera.ts`. If Bottom should mirror the other way (+X right,
+Y down), flip its `up` to `[0, -1, 0]`; the choice is a one-liner and documented in the title text.

**Tests.** `src/viewport/camera.test.ts` (the eight views and projections, the bar's five, every direction,
opposite pairs cancel, square views' up is square to their direction); `e2e/navigation.spec.ts` (each menu
view sets `data-view` and a parallel projection with no bar button pressed). README and the user guide
updated; changelog entry under Unreleased.

---

## Note on changelog conflicts

To try all three at once locally, apply `patches/stacked/*.patch` instead, where the overlap is already merged.

All three add a section at the top of `## [Unreleased]` in `CHANGELOG.md`. Whichever merges second will
conflict there; keep both sections, newest first.

---

## PR 4: branch `feat/cli-cutlist`

**Title:** feat(cli): the cut list or the nesting of a saved .shz as JSON, headless

Issue #14 asked for a way to read a cut list from a `.shz` as JSON without opening the app (the first of the
three things aramshaw listed). This is that, and the nesting, as a small new workspace crate, `crates/cli`.

**Use.** `sawhorse-cli cutlist Bookshelf.shz` prints what the Cut list pane reads: rows with finished sizes,
quantities, grain, stock, board feet or area, the totals and the hash. `sawhorse-cli nest Bookshelf.shz` prints
what the Nesting section reads. Options: `--visible-only`, `--rounding sixteenth|thirty-second|half-mm`
(cutlist), `--pretty`. `cargo run -p sawhorse-cli -- cutlist file.shz` works from a checkout.

**How.** `sawhorse_shz::read` then `Model::cut_list` / `Model::nest`, serialized with serde exactly as
`wasm-api` does for the app, so a file gives the same JSON here and in the app, hash included. No dependency
beyond the kernel, the reader and serde; argument parsing is a few lines of std; it reads and prints, never
writes. It builds for wasm32 like every workspace crate, so CI's day-1 spike still holds.

**Not in this.** Building or changing a model from the command line, and an MCP server (the other two things
in #14). The kernel's op log makes both possible later; this PR only gets the numbers out. If you would rather
have this live under a different name or as a `--headless` mode of something else, it is a 150-line crate and
easy to move.

**Tests.** `crates/cli/tests/cutlist.rs`: a saved board's cut list and nesting equal the kernel's for the same
model byte for byte; rounding and visible-only reach the kernel; a file that is not a document says so; the
command line end to end (every option, every refusal, a missing file). Smoke-tested on a `.shz` saved by the
0.1.0-alpha.4 desktop app. `cargo fmt --check`, `cargo clippy -D warnings` and the wasm32 check pass.
README and changelog updated.

Refs #14
