# Contributing

Thanks for wanting to help. Bubble is small and deliberately simple; these notes keep it that way.

## Ground rules

- **English** for code, identifiers, comments, commit messages, issues and documentation. The app's interface is translated (English and Spanish today); new strings go in both `values/` and `values-es/`.
- **No third-party libraries** without discussing it first in an issue. Bubble's security argument rests on all privileged code being here.
- **Evidence over assumptions.** A claim about Android's behaviour should say how it was checked (command, source file, method). New findings belong in [docs/reverse-engineering](docs/reverse-engineering/README.md).
- **Emulator first.** Anything that changes how profiles are created, or what is excluded from them, is tested on an emulator before a real phone. One mistake here caused a boot loop and a factory reset ([why](docs/reverse-engineering/private-space.md#the-boot-loop)).
- **Never touch user 0** beyond network rules the user chooses.

## Project layout

| Path | What |
|---|---|
| `bubble-app/` | The Android app (Java, no libraries). Ships the engine in its assets |
| `bubble-shell/` | The engine, run as `shell` ([README](bubble-shell/README.md)) |
| `tools/` | Reference keep list, emulator harness and probe ([README](tools/README.md)) |
| `docs/` | Manual, architecture, privacy model, reverse engineering, decisions |

## Building

Requirements: JDK 17, Android SDK with platform 36 and build-tools 36.0.0.

```sh
./gradlew :bubble-app:assembleDebug
```

The build compiles and dexes the engine into the APK automatically. Install with `adb install -r bubble-app/build/outputs/apk/debug/bubble-app-debug.apk`.

Release builds must be signed with your own key; never commit keystores (`*.jks` and `*.keystore` are ignored).

## Checking a change

- `./gradlew :bubble-app:lint` and a compile must pass.
- Describe what you tested, on what (emulator image or phone, Android version). See [testing](docs/testing.md) for the emulator setup and the on-device bridge check.

## Commits and pull requests

- One logical change per commit, in English, imperative mood ("Add per-network rules to the Meta space").
- Pull requests explain the problem, the change, and how it was verified.
- Update the [manual](docs/manual/README.md) when user-visible behaviour changes.

## Reporting bugs

Open an issue with your phone, Android version, Bubble version, what you did, what you expected, what happened, and Bubble's **technical log** (Settings → Technical log), with anything personal removed. Security problems: see [SECURITY.md](SECURITY.md).
