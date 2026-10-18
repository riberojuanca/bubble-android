# 0001. The bubble is a Private Space, not a virtual machine

- Date: 2026-10-16
- Status: Accepted

## Decision

The bubble is an Android Private Space profile, not an AVF/Microdroid virtual machine.

## Reasons

Hosting ordinary Android apps in a VM means rebuilding the Android framework inside it. Tested: the ART experiment aborted in `JNI_CreateJavaVM` because `libandroid.so` was missing (payload exit 34, DEX never ran), and an ordinary APK cannot start custom VMs (`NoSuchMethodException` on `setPayloadConfigPath`). A Private Space already gives kernel-level user isolation, is hidden from other apps, encrypts its data when locked, and is light.

Evidence: [the virtualization dead end](../reverse-engineering/avf-dead-end.md), [Private Space](../reverse-engineering/private-space.md).
