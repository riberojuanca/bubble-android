# bubble-shell: the engine

A single Java program that builds and governs the bubble on a stock, unrooted phone. It runs with the `shell` UID, obtained through the phone's own wireless debugging, which holds the powers an ordinary app can never get.

| Power (confirmed on a Pixel 9a, Android 17) | Used for |
|---|---|
| `CREATE_USERS` | Creating the bubble and the Meta space |
| `INTERACT_ACROSS_USERS_FULL` | Managing packages and settings inside the profiles |
| `MANAGE_NETWORK_POLICY`, `NETWORK_SETTINGS` | The phone's firewall |
| `MANAGE_APP_OPS_MODES` | VPN consent inside the profiles; denying the usage-statistics dump |
| `WRITE_SECURE_SETTINGS` | Non-expiring key, wireless debugging after reboots, per-profile settings |

An ordinary app runs as `untrusted_app` and holds none of these; that is enforced by SELinux and by each permission's protection level. See [shell identity and adb](../docs/reverse-engineering/shell-identity-and-adb.md).

## How it runs

The app's Gradle build compiles `src/` with `--release 8`, dexes it with D8 and ships it in the APK assets as `bubble-shell.jar`. The app pushes it to `/data/local/tmp/bubble/` over its own adb channel when it changes, and runs it:

```sh
CLASSPATH=/data/local/tmp/bubble/bubble-shell.jar app_process /system/bin org.bubble.shell.BubbleEngine <command> [args]
```

It references no Android framework classes: it orchestrates the system's command-line tools (`pm`, `cmd`, `settings`, `appops`, `service call`). Running it without arguments prints the usage.

## Commands

**Bubble (Private Space)**
`bubble-create` · `bubble-adopt` · `bubble-recreate <id>` · `bubble-delete <id>` · `bubble-status` · `bubble-apps <id>` · `bubble-add <id> <pkg|apk>…` · `bubble-remove <id> <pkg>…` · `bubble-launch <id> <pkg> [bubble-pkg]` · `bubble-tools <id>` · `bubble-allow-installs <id> <pkg>` · `bubble-keyboard <id> …` · `bubble-firewall <id> <bubble-pkg> <rules>` · `bubble-smart-lock <id> <bubble-pkg>`

**Meta space (work profile)**
`meta-create` · `meta-status` · `meta-apps <id>` · `meta-add <id> <pkg>…` · `meta-remove <id> <pkg>…` · `meta-delete <id>` · `meta-firewall <id> <bubble-pkg> <rules>` · `meta-keyboard <id> …` · `meta-settings-off <id>`

**Phone (user 0, network rules only)**
`host-block <pkg>…` · `host-unblock <pkg>…` · `host-apply <rules-file>` · `host-bgdata-off <pkg>…` · `host-metered` · `host-status [rules-file]`. Rules file: one package per line, `#` comments.

**System and maintenance**
`notification-privacy` · `quiet-logs` · `undo-tweaks` · `launcher-icons` · `persist-access` · `watch <rules-file> [seconds] [bubble-user]` (the guard) · `status <id>` · `firewall-on <id>` / `firewall-off <id>` (legacy kernel-chain firewall for a profile)

## Safety rules in the code

- Profile commands refuse to operate on user 0. Host controls include chosen network rules, log/usage restrictions and notification access hardening. They never install, remove, disable or suspend host packages. Notification hardening affects host listeners and the assistant; see [scope and pending validation](../docs/reverse-engineering/notification-access.md).
- `bubble-create` builds a minimal Private Space: Google and every non-required system package are excluded at creation ([Private Space](../docs/reverse-engineering/private-space.md)). A profile it just created is removed at once if `system_server` or user 0's SystemUI restarts.
- If a Private Space already exists, `bubble-create` changes nothing and reports `bubble_decision=needed`. `bubble-adopt` uses it only if it has no Google services; `bubble-recreate <id>` deletes it (data lost) and creates a new one.
- Profile package maintenance only touches spaces Bubble manages. Android propagates notification-assistant revocation to enabled child profiles; the recovery journal preserves their approvals. This may affect an unrelated work profile and requires emulator validation.
