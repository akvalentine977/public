# Sawhorse on Linux: findings (2026-11-05)

Scope: does [sawhorse3d/sawhorse](https://github.com/sawhorse3d/sawhorse) build, test and run on Linux, and
is anything Linux-specific broken? Tested: release v0.1.0-alpha.4 (the .deb, the AppImage) and `main` at
commit `bf2c9f0` (2026-11-01), on Ubuntu 24.04 with WebKitGTK 2.52.6, under Xvfb with Mesa software rendering.
Everything below is reproducible with the files in this folder; PROGRESS.md has the step-by-step record.

## Verdict

Linux is a first-class platform for Sawhorse in practice, even though the maintainer develops on Windows.
Every automated suite passes natively on Linux, the shipped .deb runs on a stock Ubuntu 24.04 with just its
two declared dependencies, and the AppImage runs on its own bundled WebKitGTK. The two bugs found are real
but neither is a Linux port problem: one is a GTK file-dialog difference the app does not compensate for,
the other is a desktop-shell bug on every platform that the maintainer's browser-based testing cannot see.

## What passed

| Check | Result |
|---|---|
| Release checksums (SHA256SUMS.txt) | AppImage, .deb, .rpm all match |
| .deb / .rpm dependencies | only libwebkit2gtk-4.1-0 and libgtk-3-0; `ldd` finds nothing else missing on Ubuntu 24.04 |
| Native kernel tests, `cargo test --workspace` | 56 suites, 604 passed, 0 failed, 3 ignored (5 min 55 s) |
| Kernel to WebAssembly + golden hashes | builds in 4 min 32 s; WASM output matches the native golden hashes |
| Production web build | OK, provenance and test-hook checks pass |
| Front-end unit tests (vitest) | 789 passed, 2 skipped (35 s) |
| Browser end-to-end suite (Playwright, Linux Chromium, 203-file .skp corpus) | 137 passed, 8 skipped, 0 failed (4 min 41 s, 3 workers) |
| Tauri desktop shell compiles from source on Linux | yes (`cargo build --locked`, 1 min 36 s after dependencies) |
| .deb binary launches, WebGL2 viewport renders, kernel loads | yes, even on software GL (llvmpipe) |
| Keyboard modelling in the desktop app | R, `36, 12`, P, `3/4` gives a watertight 324 in³ board |
| Save, Save As, re-save, reopen (.shz) | valid archives; re-save leaves a `.bak`; Open restores the model |
| AppImage (extracted, run via AppRun) | uses its bundled WebKitGTK/GTK, renders, models; kernel load is slower than the .deb |

Node 22 was enough to build although the README asks for Node 24.

## Bugs found

1. **Linux: Save As without typing `.shz` writes an extensionless file, and the Open dialog then hides it.**
   GTK's file chooser never appends the filter's extension (Windows and macOS do), and the app uses the path as
   is. The Open dialog only offers a "Supported files" filter, so the file is invisible until you type its path.
   Draft: `bug-reports/01-linux-save-as-without-extension.md`.
2. **Desktop app (all platforms): Open and New throw away unsaved changes without waiting for the
   "Discard the unsaved changes?" answer.** `tauri-plugin-dialog` replaces `window.confirm` with an async
   function, so the app's synchronous check sees a Promise and proceeds; the app also never granted the plugin's
   message permission, so no dialog appears at all. Reproduced on the .deb, the AppImage and a from-source build
   of `main`. The browser build is unaffected. Draft: `bug-reports/02-desktop-open-discards-unsaved-changes-without-asking.md`.

Neither is covered by upstream's tests: the e2e suite opens files through a hidden `<input type=file>` and
never exercises the dirty-document prompt, and the desktop shell is only compiled in CI, never run.

## Fixes, and their verification

Both bugs above were fixed the same day (upstream PRs #28 and #29, with #30 adding Back, Left and Bottom views
and #33 a headless cut-list command for issue #14). The two fixes were then verified in a desktop build of the
stacked branch on this Linux box: the discard question appears as a native dialog, OK opens and Cancel keeps
the model; Save As without an extension writes `.shz` and re-saves keep working. See PROGRESS.md.

## Smaller observations

- Harmless terminal noise on Linux: an AT-SPI accessibility-bus warning and two libEGL DRI3 warnings when
  there is no hardware acceleration. Neither affects the app.
- The GTK save dialog opens in the process's working directory rather than the home folder.
- No `.shz` MIME type or file association is installed by the .deb/.rpm/AppImage (upstream's shell source says
  this lands in a later lane), so double-clicking a `.shz` in a file manager does nothing yet.
- A plain `cargo build` of `src-tauri` (dev profile) loads Tauri's `devUrl` and shows "Could not connect to
  localhost" unless `npx vite --port 5173` is running; use `cargo build --release` for a self-contained binary.
- The GTK file chooser's location bar autocompletes while you type, which garbled paths typed by `xdotool`
  during testing; pasting the path works. A human typing normally is not affected.

## Installing it on your own Linux machine

- **Debian/Ubuntu**: `sudo apt install ./Sawhorse_0.1.0-alpha.4_amd64.deb` pulls in WebKitGTK 4.1 and GTK 3.
  Fedora: `sudo dnf install ./Sawhorse-0.1.0-alpha.4-1.x86_64.rpm`. Both install `sawhorse-desktop` and a
  desktop entry. Verify first: `sha256sum -c --ignore-missing SHA256SUMS.txt`.
- **AppImage**: `chmod +x Sawhorse_0.1.0-alpha.4_amd64.AppImage` and run it; it needs FUSE 2 (`libfuse2t64` on
  Ubuntu 24.04) like every AppImage, or `--appimage-extract` to run without FUSE. It carries its own WebKitGTK,
  so it is the safer choice on distributions with an older WebKitGTK.
- If the window comes up blank on a real GPU desktop (a WebKitGTK symptom seen with some NVIDIA/Wayland
  setups, not reproduced here), the usual workaround is `WEBKIT_DISABLE_DMABUF_RENDERER=1 sawhorse-desktop`;
  on Wayland, `GDK_BACKEND=x11` is the second thing to try.
- Until bug 2 is fixed: save before you press Ctrl+O or File > New, because the app will not ask.
- Until bug 1 is fixed: type the `.shz` extension yourself in Save As.

## Not tested here

Hardware GPU rendering, Wayland, NVIDIA drivers, HiDPI scaling, Fedora (the .rpm was only inspected, not
installed), drag-and-drop of files onto the window, the plugin folder opener (xdg-open), and the keyring-backed
render-service key (the render service is not launched).

## Files in this folder

- `PROGRESS.md`: the step-by-step record and how to resume.
- `bug-reports/`: two issue drafts ready to paste into GitHub.
- `harness/`: Xvfb launch, screenshot, stop and scripted-experiment scripts, plus the bare WebKitGTK confirm test.
- `logs/`: summaries of every test run; `screenshots/`: evidence for each claim above.
