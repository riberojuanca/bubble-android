# Open source: shrink and optimize, but keep class and member names so the APK
# and its stack traces stay readable. Components come from the manifest; the
# engine (bubble-shell.jar) is dexed separately and never goes through R8.
-dontobfuscate
