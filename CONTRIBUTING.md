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

Release builds must be signed with your own key; never commit keystores (`*.jks` and `*.keystore` are ignored). A local release build reads its key from `BUBBLE_KEYSTORE`, `BUBBLE_KEYSTORE_PASSWORD`, `BUBBLE_KEY_ALIAS` and `BUBBLE_KEY_PASSWORD`, and its version from `BUBBLE_VERSION`; without them it stays unsigned.

## Releasing (maintainers)

Every push to `main` runs [`release.yml`](.github/workflows/release.yml). No manual tag or version edit is needed: the workflow selects the next patch version, builds the APK, signs it with the existing repository secrets (`BUBBLE_KEYSTORE_BASE64`, `BUBBLE_KEYSTORE_PASSWORD`, `BUBBLE_KEY_ALIAS`, `BUBBLE_KEY_PASSWORD`), verifies the signature, tags the exact commit and publishes a GitHub release with the APK and its SHA-256. The website's download button points to the latest release.

Release runs are queued to avoid version collisions. Retrying a tagged commit reuses its version and leaves an already published release unchanged; a partial draft can be completed. `versionCode` remains `MAJOR×10000 + MINOR×100 + PATCH`. Patch and minor components roll over at 100 to keep that code increasing (for example, `0.1.99` becomes `0.2.0`).

```sh
git push origin main
```

The signing key must never change: Android refuses to update an app signed with a different key, and uninstalling Bubble deletes nothing but would force users to set it up again.

## Website

`site/` holds the home page; [`tools/build-site.py`](tools/build-site.py) adds the manual (English and Spanish) as HTML pages. [`pages.yml`](.github/workflows/pages.yml) builds and publishes it to GitHub Pages when any of them change. To preview: `python3 tools/build-site.py` and open `_site/index.html`.

The Spanish manual in `docs/manual/es/` mirrors the English one chapter by chapter (same numbers); update both when behaviour changes.

## Checking a change

- `./gradlew :bubble-app:lint` and a compile must pass.
- Describe what you tested, on what (emulator image or phone, Android version). See [testing](docs/testing.md) for the emulator setup and the on-device bridge check.

## Commits and pull requests

- One logical change per commit, in English, imperative mood ("Add per-network rules to the Meta space").
- Pull requests explain the problem, the change, and how it was verified.
- Update the [manual](docs/manual/README.md), in English and Spanish, when user-visible behaviour changes.

## Reporting bugs

Open an issue with your phone, Android version, Bubble version, what you did, what you expected, what happened, and Bubble's **technical log** (Settings → Technical log), with anything personal removed. Security problems: see [SECURITY.md](SECURITY.md).
