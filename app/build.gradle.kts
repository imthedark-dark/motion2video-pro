plugins { id("com.android.application") }
android {
    namespace = "dev.motion2video.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "dev.motion2video.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.4.1"
    }
    signingConfigs.getByName("debug") {
        val override = providers.environmentVariable("M2V_DEBUG_KEYSTORE").orNull
        storeFile = if (override == null) rootProject.file("build/debug.jks") else rootProject.file(override).also {
            require(it.isFile) { "M2V_DEBUG_KEYSTORE must point to an existing development keystore" }
        }
    }
    buildTypes {
        getByName("debug") { isDebuggable = true }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = null // Production signing is a separate Phase 4 change.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies { implementation("androidx.core:core:1.13.1") }

val syncStudio by tasks.registering(Copy::class) {
    from("../ui/Motion2Video-Studio.html")
    into("src/main/assets")
    rename { "studio.html" }
}
tasks.named("preBuild") { dependsOn(syncStudio) }
