# The virtualization dead end

Before the bubble became a Private Space, two experiments tried to build it on the Android Virtualization Framework (AVF) and its Microdroid VMs. Both are kept here as evidence of why that road was closed.

## Experiment 1: protected execution

A test APK used the AVF APIs by reflection. It created a protected, non-debuggable Microdroid VM with 256 MiB, ran a C library inside, derived an instance secret twice, compared and erased it without exporting it, and exited with 0 in about 2.1 s. It needed `MANAGE_VIRTUAL_MACHINE` granted over adb, and APK signature scheme v3.

**What it shows:** experimental execution according to AVF, not absolute isolation or independence from the host: Microdroid comes from the phone's own system. At most, a protected VM could keep secrets. It cannot host apps.

## Experiment 2: running ART inside Microdroid

Goal: run a simple DEX inside the VM by bringing Android's runtime (ART) into it, following how CompOS uses ART modules inside Microdroid.

Failures, in order:

1. `USE_CUSTOM_VIRTUAL_MACHINE` was granted, but the APK could not reach `setPayloadConfigPath` by reflection: `NoSuchMethodException`.
2. The official `/apex/com.android.virt/bin/vm run-app` from adb **did** start the custom configuration, but that depends on adb, not on an app.
3. Executing `dalvikvm64` in the guest failed with `exec_errno=13`; SELinux logged `execute_no_trans`.
4. Switched to loading ART as a library through `libnativehelper.so` and JNI.
5. ART failed for lack of `libstatspull.so`; added `com.android.os.statsd`, i18n and Conscrypt modules.
6. ART began to start but failed to reserve memory; killed by timeout.
7. Heap limited to `-Xms8m -Xmx32m`, VM at 256 MiB.

Last run:

```
payload_exit_code: 34
dex_execution_reported: false
F nativeloader: Error preloading public library libandroid.so: dlopen failed: library "libandroid.so" not found
F microdroid_launcher: runtime.cc:769] Runtime aborting...
```

## Conclusion

The DEX never ran. A missing `libandroid.so` is not a one-off: an ordinary app needs the whole framework (ActivityManager, PackageManager, graphics, input, Binder). Bringing that into Microdroid means building a complete Android inside the VM, and an ordinary APK cannot even start custom VMs. The result would be heavy, not a light app. **Closed.** Android already ships a real, light isolation unit: the user profile.

These runs used `debug: full`; they are not evidence about isolation either way.
