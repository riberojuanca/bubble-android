# 0002. Power without root through the `shell` identity

- Date: 2026-10-16
- Status: Accepted

## Decision

Bubble gets the privileged permissions it needs by borrowing the `shell` identity through the phone's own wireless debugging, paired once by the user. No root, no modified operating system.

## Reasons

Creating the profile without Google, managing its packages and applying network policy need `CREATE_USERS`, `INTERACT_ACROSS_USERS_FULL`, `MANAGE_NETWORK_POLICY`, `NETWORK_SETTINGS` and similar signature/privileged permissions. An ordinary app cannot get them, and `pm grant` only works for the few with the `development` bit. The `shell` identity holds them all. This is Shizuku's model, extended with a self-contained engine and a built-in pairing and adb client.

## Consequences

- The user grants this power explicitly, once, with a pairing code.
- Bubble holds a lot of power in one app; see [SECURITY.md](../../SECURITY.md).

Evidence: [shell identity and adb](../reverse-engineering/shell-identity-and-adb.md).
