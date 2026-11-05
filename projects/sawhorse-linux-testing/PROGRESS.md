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
| 10 | Open a file while the document has unsaved changes | in progress | First attempt: Open dialog closed, document unchanged, no visible prompt. The app uses window.confirm() for "Discard the unsaved changes?"; checking whether WebKitGTK shows it. Screenshots dirty-open-*.png. |
| 11 | Full Playwright e2e suite under Linux Chromium (145 tests incl. 203-file .skp corpus) | in progress | Running; 72/145 done with 0 failures at last check. Result goes in logs/e2e.log. |
| 12 | Build the Tauri desktop shell from source on Linux | done | `cargo build --locked` in src-tauri: 1 min 36 s after deps, binary 269 MB (debug). Not yet run. |
| 13 | Run the from-source desktop build of `main` under Xvfb | not started | Repeat steps 8 to 10 against src-tauri/target/debug/sawhorse-desktop. |
| 14 | Run the AppImage (extracted with unsquashfs, via AppRun) | not started | Checks the bundled WebKitGTK path, which is what most users will double-click. |
| 15 | Write the findings up; file bug reports upstream | not started | Drafts live in bug-reports/. Filing needs a GitHub login; the cloud session had no write access to upstream. |

## Findings so far

- Linux is in good shape: every automated suite passes natively on Linux, and the shipped .deb runs on a
  stock Ubuntu 24.04 with only its two declared dependencies.
- Confirmed bug (Linux-specific, usability): Save As without typing an extension (bug-reports/01).
- Open-while-dirty behaviour on Linux is unverified (step 10).
- Harmless noise a Linux user will see on the terminal: AT-SPI accessibility-bus warning, libEGL DRI3 warnings
  when there is no hardware acceleration.
- The GTK save dialog opens in the process's working directory, not the home folder (cosmetic).

## Environment used

Ubuntu 24.04.5, glibc 2.39, WebKitGTK 2.52.6, Mesa 25.2.8 llvmpipe under Xvfb 1440x900, 4 CPUs, 15 GB RAM,
Rust 1.97.0, Node 22.23.4, npm 10.9.9, wasm-pack 0.15.0, Playwright 1.63.0 (pinned with
`npm install --no-save @playwright/test@1.63.0 playwright@1.63.0` because the 1.64 browser download was blocked;
the lockfile was left untouched).
