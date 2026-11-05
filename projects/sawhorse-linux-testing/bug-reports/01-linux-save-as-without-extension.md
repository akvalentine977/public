# Linux: Save As without typing ".shz" writes a file with no extension, and the Open dialog then hides it

**Version**: 0.1.0-alpha.4, Linux .deb (`Sawhorse_0.1.0-alpha.4_amd64.deb`), Ubuntu 24.04, WebKitGTK 2.52.6.
Reproduced under Xvfb with software rendering; nothing about it depends on the GPU.

## Steps

1. Draw a board (R, `36, 12`, Enter, P, `3/4`, Enter).
2. File > Save As (Ctrl+Shift+S). The GTK dialog opens with `Untitled.shz` in the Name field.
3. Replace the whole name with `cabinet` (no extension) and press Save.
4. File > Open (Ctrl+O), go to the same folder.

## What happens

- Step 3 writes a file literally named `cabinet` (a valid .shz archive inside: `file cabinet` says Zip data
  with the sawhorse MIME type).
- Step 4: the Open dialog's only filter is "Supported files" (`.shz` and the import types), so `cabinet` is
  not listed and cannot be clicked. It opens only if you type its full path into the location bar.

## What I expected

Either the saved name to get `.shz` appended when the typed name has no extension (what the native Windows
and macOS dialogs do on their own), or an "All files" entry in the Open dialog's filter list.

## Why it is Linux-specific

`src/io/files.ts` (`saveFile`, Tauri branch) uses the path returned by `@tauri-apps/plugin-dialog`'s
`save()` as is. On Windows the native dialog appends the selected filter's extension itself; GTK's file chooser
never does, so on Linux the app is the only place where it could happen.

## Suggested fix

In `saveFile`, after `save()` returns on Tauri: if the path's extension is not one of `type.extensions`,
append `type.extensions[0]`. That matches the browser build, where `showSaveFilePicker` enforces the type.
Independently, adding an "All files" filter to `pickFile` lets people recover files saved this way by
older builds.

## Notes

- Re-saving with Ctrl+S after this works and keeps a `.bak`, so nothing is lost; the file is just invisible
  to the Open dialog.
- Replacing only the selected part of the name (GTK selects `Untitled`, not `.shz`) works fine; the bug needs
  the whole name replaced, which is what Ctrl+A or a double-click does.
