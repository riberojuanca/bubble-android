plugins {
    id("com.android.application")
}

// Release builds take their version from CI (BUBBLE_VERSION=0.1.0 → versionCode 100) and their
// signing key from the environment, so no secret lives in the repository. Without them, a local
// release build is simply left unsigned.
// Without the variable (F-Droid builds a release tag), the version is the vX.Y.Z tag on HEAD, so
// both builds carry the same version and F-Droid can reproduce the published APK.
fun headTagVersion(): String? = try {
    val git = ProcessBuilder("git", "describe", "--tags", "--exact-match", "--match", "v[0-9]*", "HEAD")
        .directory(rootDir).redirectErrorStream(true).start()
    val out = git.inputStream.bufferedReader().readText().trim()
    if (git.waitFor() == 0 && Regex("v\\d+\\.\\d+\\.\\d+").matches(out)) out.substring(1) else null
} catch (e: Exception) {
    null
}
val releaseVersion = System.getenv("BUBBLE_VERSION") ?: headTagVersion() ?: "0.1.0"
val releaseCode = releaseVersion.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    major * 10000 + minor * 100 + patch
}
val keystorePath: String? = System.getenv("BUBBLE_KEYSTORE")

android {
    namespace = "org.bubble.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.bubble.app"
        minSdk = 34
        targetSdk = 36
        versionCode = releaseCode
        versionName = releaseVersion
        testInstrumentationRunner = "org.bubble.app.ProfileBridgeInstrumentation"
    }
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("BUBBLE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BUBBLE_KEY_ALIAS")
                keyPassword = System.getenv("BUBBLE_KEY_PASSWORD")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}


// The privileged engine (bubble-shell) is compiled and dexed into the APK assets; the app pushes
// it to /data/local/tmp over its own adb channel and runs it with the shell UID.
val engineClasses = layout.buildDirectory.dir("engine/classes")
val engineAssets = layout.buildDirectory.dir("generated/engine-assets")
val compileEngine by tasks.registering(JavaCompile::class) {
    source(fileTree("../bubble-shell/src") { include("**/*.java") })
    classpath = files()
    destinationDirectory.set(engineClasses)
    options.release.set(8)
}
val dexEngine by tasks.registering(JavaExec::class) {
    dependsOn(compileEngine)
    classpath = files("${android.sdkDirectory}/build-tools/36.0.0/lib/d8.jar")
    mainClass.set("com.android.tools.r8.D8")
    inputs.dir(engineClasses)
    outputs.dir(engineAssets)
    doFirst {
        val out = engineAssets.get().asFile
        out.mkdirs()
        val classes = engineClasses.get().asFile.walkTopDown().filter { it.extension == "class" }
        args(listOf("--min-api", "34", "--output", File(out, "bubble-shell.jar").absolutePath) +
            classes.map { it.absolutePath }.toList())
    }
}
android.sourceSets.getByName("main").assets.srcDir(engineAssets)
tasks.named("preBuild") { dependsOn(dexEngine) }
