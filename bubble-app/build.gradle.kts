plugins {
    id("com.android.application")
}

android {
    namespace = "org.bubble.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.bubble.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "org.bubble.app.ProfileBridgeInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
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
