#!/usr/bin/env bash
A="${ADB:-adb} -s ${SERIAL:-emulator-5554}"
# Restore the emulator "clean" snapshot (saved right after first boot) and wait for boot.
$A emu avd snapshot load clean >/dev/null; sleep 5
timeout 120 bash -c "until [ \"\$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')\" = 1 ]; do sleep 2; done"
$A shell pm list users | grep -c UserInfo
