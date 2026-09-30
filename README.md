# 🟨 Pogo Pocket

**Your [Pogo](https://github.com/dvher/pogo) sticky notes on your phone's home screen.**

Pogo Pocket is a home-screen widget that shows one of your notes. Tick tasks off right on the
widget, tap the folded corner to flip to the next note, and tap the text to edit it. Notes sync
with your desktop through **[Pogo Pad](https://github.com/dvher/pogo_pad)**, the small server you
host yourself, with optional end-to-end encryption.

Android is available now. iOS is planned (see [iOS](#ios)).

## The widget

```
┌──────────────────────┐
│ Groceries            │  tap a task → crossed off, synced everywhere
│ [ ] milk             │  tap the text → open the note to edit
│ [x] eggs             │
│                      │
│ +   2 / 5         ◢──┘  + → new note      folded corner → next note
└──────────────────┘
```

- **Pick a note when you add the widget** from the launcher's widget picker. Widgets added from the
  app start on your first note. Long-press a widget → **Reconfigure** to choose again.
  You can add as many widgets as you like, each showing its own note.
- **No notes?** The widget shows an empty clipboard until you write one or sync some in.
- **Locked?** If your Pogo Pad uses end-to-end encryption and the phone has no notes yet, the
  widget asks you to open the app and enter the passphrase once.
- Pogo Pocket syncs in the background every 15 minutes (Android's minimum), shortly after
  every edit, and every sync interval while the app is open.

To add the widget, use your launcher's widget picker, or the app's **⋮ → Add widget to home screen**.

## Sync with Pogo Pad

1. On the server, create a token for the phone:
   ```sh
   docker compose exec pogo-pad pogo-pad token create --name phone
   ```
2. In Pogo Pocket, open **⚙ Sync**. Enter the server's IP address or hostname, its port and the
   token, then tap **Test connection**, turn on **Enable sync** and tap **Save**.
3. If your other devices use end-to-end encryption, enter the same passphrase under
   *End-to-end encryption*.

Only note text and color sync, the same as on the desktop.

## Privacy

- **On the phone:** notes and the sync token are encrypted in the app's database with AES-256-GCM.
  The key is kept wrapped by the Android Keystore, so the database file alone is unreadable. App data
  is excluded from Android backups.
- **With end-to-end encryption:** notes are encrypted before they leave the phone, exactly as Pogo
  does on the desktop, and Pogo Pad only stores unreadable data.
- **Plain HTTP** works for servers on your home network. Use HTTPS for anything reachable from outside it.

## How it's built

The note store, sync and encryption are the same Go code as the desktop app (Pogo's
`pkg/pogosync`, `pkg/store` and `pkg/secure`), compiled for the phone with gomobile. On top sits a
thin native layer: a Jetpack Glance widget and a small Compose app for editing and settings.

| Piece | Where |
|---|---|
| Go core: notes, widget state and view model, sync, E2E (gomobile API) | `core/` |
| Widget, its actions, and the note picker | `android/app/src/main/java/dev/pogo/pocket/widget/` |
| App screens: notes, editor, sync settings | `android/app/src/main/java/dev/pogo/pocket/ui/` |
| Keystore-wrapped database key | `android/app/src/main/java/dev/pogo/pocket/KeyVault.kt` |

On phones, SQLite runs through [ncruces/go-sqlite3](https://github.com/ncruces/go-sqlite3), which is
WebAssembly running in Go. The desktop's modernc.org/sqlite makes legacy system calls that Android's
seccomp filter blocks.

## Build

With Nix:

```sh
nix develop            # Go, gomobile's needs (SDK + NDK), JDK 17, Gradle, Task
task test              # Go core tests (the sync test runs Pogo Pad from ../server)
task apk               # → android/app/build/outputs/apk/debug/app-debug.apk
task install           # adb install on a connected phone
```

Without Nix, you need:
- Go 1.26+
- JDK 17
- the Android SDK (platform 35, build-tools 35.0.0) and NDK 27, with `ANDROID_HOME` and `ANDROID_NDK_HOME` set
- [Task](https://taskfile.dev)

**Memory.** Heavy steps (gomobile, Gradle, the emulator) run through `scripts/capped`, which puts
them in one memory-capped systemd slice (`POGO_DEV_MEM`, default `6G`). If they run out of room,
the kernel stops the build instead of freezing the machine.

**Emulator.** `nix develop .#emulator` adds the emulator and an x86_64 Android 15 image. Create an
AVD named `pogo`, then run `task emulator` (headless, 2 GB RAM, inside the memory cap).

**Changing Pogo's shared code.** `core/` depends on a tagged release of `github.com/dvher/pogo`.
To work on both at once, put the repos side by side and add a `go.work` with `use (./desktop ./mobile/core)`.

## iOS

The Go core has no Android-specific code: the data folder and key come from the host, and widget
state is keyed by an id the host chooses. An iOS version would add:
- `gomobile bind -target=ios` → `Pogocore.xcframework`
- a WidgetKit extension (iOS 17+): the folded corner as `Button(intent:)`, tasks as
  `Toggle(intent:)`, and the note picker as an `AppIntentConfiguration`
- the database in an App Group container, with its key in a shared Keychain access group

Building and testing iOS needs a Mac, so it isn't set up yet.

## License

[MIT](LICENSE)
