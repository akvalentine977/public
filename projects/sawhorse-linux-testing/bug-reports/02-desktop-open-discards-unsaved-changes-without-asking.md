# Desktop app: Open and New discard unsaved changes without waiting for the "Discard the unsaved changes?" answer

**Version**: 0.1.0-alpha.4 Linux .deb and AppImage, reproduced on Ubuntu 24.04 (WebKitGTK 2.52.6). The cause
is in the desktop shell's JavaScript environment and is the same on Windows and macOS; the browser build is
not affected.

## Steps

1. Draw a board (R, `36, 12`, Enter, P, `3/4`, Enter) and save it as `one.shz`.
2. Draw a second board (R, `10, 10`, Enter, P, `1`, Enter). The document now has unsaved changes.
3. File > Open (Ctrl+O), choose `one.shz`, press Open.

## What happens

`one.shz` loads immediately ("Opened one.shz: 1 solid") and the unsaved second board is gone. No
"Discard the unsaved changes to this model?" question is shown, and nothing waits for an answer.
Screenshots: `deb-alpha4-dirtyopen-s6.png` (2 solids, unsaved) then `deb-alpha4-dirtyopen-open-dirty-now.png`
one second after pressing Open (the saved file, 1 solid, no prompt). Same result with the AppImage and with a
debug build of `main`.

## What I expected

The question, and the current model kept when the answer is Cancel. (It works that way in the browser build.)

## Cause

`src/App.tsx`:

```ts
const confirmDiscard = useCallback(() => !dirtyRef.current || window.confirm("Discard the unsaved changes to this model?"), []);
```

is used synchronously in three places (`takeFiles`, the empty-model branch, and `newDocument`). In the desktop
app `tauri-plugin-dialog` injects an init script into every window (`src/init-iife.js` in
`tauri-plugin-dialog` 2.8.2, registered from its `lib.rs`) that replaces the built-ins:

```js
window.alert = function (e) { n("plugin:dialog|message", { message: e.toString() }) };
window.confirm = async function (e) { return "Ok" === await n("plugin:dialog|message", { message: e.toString(), buttons: "OkCancel" }) };
```

So on the desktop `window.confirm(...)` returns a Promise, which is truthy, and `confirmDiscard()` returns it
without waiting. On top of that, `src-tauri/capabilities/default.json` grants only `dialog:allow-open` and
`dialog:allow-save`, not `dialog:allow-message`, so the asynchronous call is refused and no dialog ever appears
(nothing shows in the window list in the second after Open; the rejection surfaces only as an unhandled
promise rejection in the webview console).

A bare WebKitGTK web view without the plugin's override blocks on `window.confirm` as expected
(`harness/webkitgtk-confirm-test.py` in this folder shows the default handler blocking and an app-handled
signal returning in 1 ms), so this is not a WebKit limitation.

## Suggested fix

Make `confirmDiscard` async and await it at its three call sites. `await window.confirm(...)` works in both
builds (a boolean in the browser, a Promise of a boolean under the plugin), or use `confirm()` from
`@tauri-apps/plugin-dialog` on the desktop for a native dialog with the app's name on it. Either way add
`dialog:allow-message` (or `dialog:allow-confirm`) to `capabilities/default.json`, otherwise the plugin's
dialog is refused. Worth a Playwright check under the Tauri test hooks that Open on a dirty document waits.
