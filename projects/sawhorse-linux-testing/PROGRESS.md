# Sawhorse on Linux: test campaign progress

Purpose: verify that [sawhorse3d/sawhorse](https://github.com/sawhorse3d/sawhorse) (free, open-source
push/pull CAD for woodworkers) builds, tests and runs on Linux, and turn anything Linux-specific into
bug reports the maintainer can act on. Started 2026-11-05 in a Claude cloud session; written so a local
session can resume. Upstream commit tested: `bf2c9f0` on `main` (2026-11-01); release tested: v0.1.0-alpha.4.

## How to resume locally

```bash
git clone https://github.com/sawhorse3d/sawhorse.git && cd sawhorse
# Toolchain (what worked here, Ubuntu 24.04): Rust stable (1.97) + wasm32 target, wasm-pack 0.15.0, Node 22 (README says 24+, 22.23 worked)
rustup target add wasm32-unknown-unknown
npm install -g wasm-pack@0.15.0
sudo apt-get install -y libwebkit2gtk-4.1-dev libayatana-appindicator3-dev librsvg2-dev patchelf libdbus-1-dev pkg-config build-essential
npm ci
SAWHORSE_NO_WSL=1 npm run wasm          # kernel to WebAssembly (4.5 min on 4 cores)
npm run build                           # dist/ (needs the wasm step)
bash scripts/cargo.sh test --workspace  # native kernel tests (6 min)
npx vitest run                          # front-end unit tests (35 s)
npm run corpus:skp                      # 203 generated .skp files the e2e suite imports
REQUIRE_KERNEL=1 REQUIRE_CORPUS=1 npx playwright test   # e2e; needs Playwright's Chromium (npx playwright install chromium)
(cd src-tauri && cargo build --locked)  # desktop shell from source -> src-tauri/target/debug/sawhorse-desktop
```

Desktop testing without a monitor: `harness/run-desktop.sh <binary> <run-name>` starts Xvfb, D-Bus and a
keyring and launches the app; `harness/shot.sh <run-name> <label>` screenshots; drive it with `xdotool`;
`harness/stop.sh <run-name>` cleans up. Needs `xvfb xdotool imagemagick dbus-x11 gnome-keyring`.

## Steps

| # | Step | Status | Result |
|---|------|--------|--------|
| 1 | Clone upstream, read docs, CI config | done | CI already runs the browser e2e suite on Ubuntu with Chromium; the desktop shell is only compiled there, never run. The maintainer develops on Windows 11. |
| 2 | Download v0.1.0-alpha.4 Linux artifacts, verify SHA256SUMS | done | AppImage, .deb and .rpm all match the published checksums. |
| 3 | Inspect packaging (.deb/.rpm metadata, desktop entry, AppImage contents) | done | .deb depends only on libwebkit2gtk-4.1-0 and libgtk-3-0; .rpm requires the same two libraries. AppImage bundles WebKitGTK, GTK3, GStreamer and GLib (201 shared objects). No .shz MIME type or file association in the desktop entry (upstream says that lands in a later lane). |
| 4 | Install toolchain (wasm32 target, wasm-pack, WebKitGTK dev) | done | All installed from normal registries; Node 22 was enough despite README asking for 24. |
| 5 | Native Rust tests (`cargo test --workspace`) | done | 56 suites, 604 passed, 0 failed, 3 ignored, 5 min 55 s. See logs/cargo-test-summary.txt. |
| 6 | Kernel to WebAssembly, golden hashes, production build | done | wasm-pack build 4 min 32 s; WASM matches native goldens; `npm run build` OK with provenance and test-hook checks passing. |
| 7 | Front-end unit tests (vitest) | done | 77 files passed, 1 skipped; 789 tests passed, 2 skipped; 35 s. |
| 8 | Run the official .deb binary on Ubuntu 24.04 under Xvfb (software GL, llvmpipe) | done | Launches, kernel loads, WebGL2 viewport renders, keyboard modelling works (R, 36,12, P, 3/4 gives a watertight 324 in³ board). Only harmless stderr: AT-SPI bus warning, libEGL DRI3 warnings. |
| 9 | Save / Save As / re-save / reopen on the Linux desktop build | done | Save and Save As write a valid .shz (zip: mimetype, scene.json, geometry.glb, thumbnail.jpg). Re-save needs no dialog and leaves a .bak beside the file. **Bug found**: a name typed without .shz is saved with no extension and the Open dialog then hides it (bug-reports/01). |
| 10 | Open a file while the document has unsaved changes | done | Scripted experiment (harness/dirty-open-experiment.sh): Open on a clean document works ("Opened one.shz: 1 solid"). Open on a document with unsaved changes also goes straight through, with **no "Discard the unsaved changes?" prompt**, and the unsaved work is gone within a second. Cause found and written up in bug-reports/02: tauri-plugin-dialog overrides window.confirm with an async function, so the synchronous check sees a Promise (truthy); the app also lacks the dialog:allow-message capability, so no dialog appears. Affects all desktop platforms, not only Linux. Screenshots deb-alpha4-dirtyopen-*.png. Note: an earlier attempt looked like Open did nothing; that was GTK location-bar autocompletion garbling a typed path, fixed by pasting paths from the clipboard. |
| 11 | Full Playwright e2e suite under Linux Chromium (145 tests incl. 203-file .skp corpus) | done | 137 passed, 8 skipped, 0 failed, 4 min 41 s with 3 workers (logs/e2e-linux-chromium.log). The skips are the private-fixture and render-service specs that need files or a service not in the repo. |
| 12 | Build the Tauri desktop shell from source on Linux | done | `cargo build --locked` in src-tauri: 1 min 36 s after deps, binary 269 MB (debug). Not yet run. |
| 13 | Run the from-source desktop build of `main` under Xvfb | done | A plain `cargo build` (dev profile) loads Tauri's devUrl (http://localhost:5173), not the embedded dist, so it shows "Could not connect to localhost" unless `npx vite --port 5173` is running. With the dev server up, `main` behaves exactly like alpha.4: modelling, save and reopen work; Open on a dirty document skips the prompt (screenshots/srcbuild-main-dirtyopen-montage-chips.png). |
| 14 | Run the AppImage (extracted with unsquashfs, via AppRun) | done | Launches with its bundled WebKitGTK and GTK (confirmed from /proc/<pid>/maps), its own WebKitWebProcess, WebGL2 viewport renders, a board extrudes correctly. The kernel took noticeably longer to load than from the .deb (first keystrokes at 6 s landed on "Kernel loading…"; fine at 25 s). Screenshot appimage-alpha4-board.png. | Checks the bundled WebKitGTK path, which is what most users will double-click. |
| 15 | Write the findings up; file bug reports upstream | write-up done, filing left for you | REPORT.md has the findings; bug-reports/01 and 02 are ready to paste. Filing needs your GitHub login; the cloud session had no write access to upstream. |

## Findings so far

- Linux is in good shape: every automated suite passes natively on Linux, and the shipped .deb runs on a
  stock Ubuntu 24.04 with only its two declared dependencies.
- Confirmed bug (Linux-specific, usability): Save As without typing an extension (bug-reports/01).
- Confirmed bug (all desktop platforms, data loss): File > Open and New discard unsaved changes without waiting for the confirmation (bug-reports/02).
- Harmless noise a Linux user will see on the terminal: AT-SPI accessibility-bus warning, libEGL DRI3 warnings
  when there is no hardware acceleration.
- The GTK save dialog opens in the process's working directory, not the home folder (cosmetic).

## Environment used

Ubuntu 24.04.5, glibc 2.39, WebKitGTK 2.52.6, Mesa 25.2.8 llvmpipe under Xvfb 1440x900, 4 CPUs, 15 GB RAM,
Rust 1.97.0, Node 22.23.4, npm 10.9.9, wasm-pack 0.15.0, Playwright 1.63.0 (pinned with
`npm install --no-save @playwright/test@1.63.0 playwright@1.63.0` because the 1.64 browser download was blocked;
the lockfile was left untouched).

## Next steps for a local session

1. File the two issues at https://github.com/sawhorse3d/sawhorse/issues/new from `bug-reports/01-*.md` and
   `bug-reports/02-*.md` (paste as is; attach the screenshots named in each). Mention the upstream commit
   `bf2c9f0` for the `main` reproduction.
2. Optional, if you want a pull request instead of a report: both fixes are small and described in the drafts
   (`src/io/files.ts` for 01; `src/App.tsx` confirmDiscard plus `src-tauri/capabilities/default.json` for 02).
   Follow the project's conventions: a changelog entry under `## [Unreleased]`, tests, and messages that read as
   sentences (see docs/DEVELOPMENT.md and recent commits).
3. On your real desktop: install the .deb or run the AppImage, check hardware WebGL and Wayland, which the
   cloud box could not. If the window is blank, try `WEBKIT_DISABLE_DMABUF_RENDERER=1`.
4. Nothing from this session needs cleaning up upstream; everything lives in this folder.

## Phase 2: contributions (started 2026-11-05, after the testing)

The fork is `akvalentine977/sawhorse`. The cloud session could not push to it (Claude's GitHub App is not
installed for that repository), so every finished change is also exported as a patch in `patches/`, made with
`git format-patch` against upstream `main` at `bf2c9f0`. Pull request text for each is in `pr-descriptions.md`. To use a patch locally:

```bash
cd sawhorse && git fetch origin && git checkout -b <branch> origin/main
git am path/to/public/projects/sawhorse-linux-testing/patches/0001-*.patch
git push -u <your-fork> <branch>     # then open the pull request against sawhorse3d/sawhorse
```

| # | Change | Branch | Status |
|---|--------|--------|--------|
| P1 | Desktop app waits for the "Discard the unsaved changes?" answer (bug-reports/02) | fix/desktop-discard-question | done: commit 20a858a, patches/0001; typecheck, vitest and e2e files.spec green; the new e2e test fails without the fix |
| P2 | Save As appends the type's extension when the dialog returns none (bug-reports/01) | fix/save-as-extension | done: patches/0002; typecheck, vitest, safe-save tests, shell clippy and fmt green |
| P3 | Back, Left and Bottom standard views | feat/back-left-bottom-views | done: patches/0003; typecheck, full vitest (789) and e2e navigation.spec green |
| P4 | Issue #26, metric stock library and labels | | not started; hand-off notes below |

### The three changes together

`patches/stacked/0001..0003` are the same three commits rebased in sequence on `main` at `bf2c9f0`, with the
one overlap (each adds a section at the top of `## [Unreleased]` in CHANGELOG.md) already merged, newest
section first. On that stack every check passes on Linux: typecheck, vitest (798 tests, 9 of them new), the
`sawhorse-safe-save` tests (5), `cargo clippy -D warnings` and `cargo fmt --check` on `src-tauri`, and the
`files` and `navigation` e2e specs (10 tests, 2 new). Use the stacked series to try everything at once
(`git am patches/stacked/*.patch`); use `patches/0001`, `0002`, `0003` for three separate pull-request
branches off `main` (the maintainer will see a trivial changelog conflict on whichever merges second).

### Hand-off for issue #26 (metric workshops), if you take it on

Not started here because it needs design choices the maintainer should see first. What I found:

- The status chip's volume is hard-coded to cubic inches: `src/App.tsx` around line 2397 formats
  `s.volume_m3 / M_PER_IN ** 3` with "in³" whatever the units.
- The starting stock library is `StockLibrary::builtin()` in `crates/kernel/src/stock.rs` (around line 341):
  4 x 8 sheets and 4/4 to 12/4 boards, all imperial; nominal names like "4/4" are mapped at line ~216.
- Nesting labels ("4/4 x 4 x 96", "Kerf 3.17 mm") and the cut-list tiles (board feet, 4 x 8 sheets) are
  built from that library and from `crates/kernel/src/cutlist.rs` / `nesting.rs`; the panes are under
  `src/cutlist/` and `src/nesting/`.
- The reporter's spec (issue #26): dressed boards 19 x 42, 66, 90, 140, 190 mm in 0.3 m steps from 1.8 to
  6.0 m; sheets 2400 x 1200 (also 1800 and 3600 long) in 3, 6, 7, 9, 12, 16, 18, 25 mm; labels and totals that
  follow the unit setting; a warning when a part is placed on stock thinner than itself.
- Open question to settle with the maintainer first: does a metric document get a metric starting library
  (and how is "metric" decided: display units, or a per-document stock choice), given that thickness matching
  is "within 1/16 in" and 18 mm and 3/4 in would both match a 19 mm part. ADR-0007 is the nesting decision
  record to extend.

### What is left for you

1. The branches are on the fork (pushed 2026-11-05 after the GitHub App was installed and GitHub reconnected):
   `fix/desktop-discard-question`, `fix/save-as-extension`, `feat/back-left-bottom-views`, and `stack/all-three`.
   Open the three pull requests with the text in `pr-descriptions.md`:
   - https://github.com/sawhorse3d/sawhorse/compare/main...akvalentine977:sawhorse:fix/desktop-discard-question?expand=1
   - https://github.com/sawhorse3d/sawhorse/compare/main...akvalentine977:sawhorse:fix/save-as-extension?expand=1
   - https://github.com/sawhorse3d/sawhorse/compare/main...akvalentine977:sawhorse:feat/back-left-bottom-views?expand=1
2. File the two issues from `bug-reports/` (PR 1 and PR 2 fix them; link each PR to its issue).
3. Optionally take on #26 with the notes above.
