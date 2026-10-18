# The ceiling without root

What Bubble cannot change on a stock phone, why, and what breaking that ceiling would cost.

## What stays above the bubble

The main user and its system Google services sit above every profile:

- With `MANAGE_USERS`, they can know a private profile **exists** (id, type, creation date).
- With `INTERACT_ACROSS_USERS`, they can **list its packages**.
- With `READ_LOGS`, they can read the events buffer in the background without consent; Bubble keeps the bubble's events from being written ([system logs](system-logs.md)).

These cannot be removed without root because:

- the permissions are signature/privileged; `pm revoke` only changes runtime permissions;
- user enumeration lives in `UserManagerService`, inside `system_server`; changing its answers means injecting code;
- SELinux (enforcing) forbids `untrusted_app` from injecting into another domain.

In practice: Google can know "there is a locked room and what kind of furniture it has", as on millions of phones with Private Space, a Google feature. It does not see when the apps are used, their data, accounts or traffic.

## What root would take

Phone state: bootloader locked (`ro.boot.flash.locked=1`), verified boot green, `user` build.

- Root alone (uid 0) is not enough: SELinux confines root too. Intercepting `system_server` or Google's process needs a hooking framework (Zygisk/LSPosed), so a modified boot image, so **an unlocked bootloader**.
- Unlocking **wipes the phone**, verified boot is no longer green, and **Play Integrity fails**: banking and many other apps stop working.
- With that done, a module could hook `UserManagerService.getUsers()` and hide the bubble from Google. It works, but it costs exactly what Bubble set out to keep: a stock phone where everything still works.

## GrapheneOS

GrapheneOS achieves invisibility because it **is** the operating system: it decides that Google runs as an ordinary sandboxed app without those permissions. That power belongs to whoever builds the OS and cannot be extracted into an app. If hiding the bubble's existence matters to you more than keeping a stock phone, use GrapheneOS.
