import java.util.Properties

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
        if (override != null) {
            storeFile = rootProject.file(override).also {
                require(it.isFile) { "M2V_DEBUG_KEYSTORE must point to an existing development keystore" }
            }
        } else {
            // Existing contributors retain their development identity. Fresh checkouts
            // use AGP's standard, automatically generated debug keystore.
            val localDebugKey = rootProject.file("build/debug.jks")
            if (localDebugKey.isFile) storeFile = localDebugKey
        }
    }
    val signedRelease = providers.gradleProperty("m2vReleaseSigning").orNull == "true"
    if (signedRelease) {
        val local = Properties()
        val config = rootProject.file("release-signing.properties")
        if (config.isFile) config.inputStream().use { local.load(it) }
        fun secret(env: String, key: String): String = providers.environmentVariable(env).orNull
            ?: local.getProperty(key) ?: error("Missing release signing setting: $env / $key")
        signingConfigs.create("production") {
            val keyFile = rootProject.file(secret("M2V_RELEASE_KEYSTORE", "storeFile"))
            require(keyFile.isFile) { "Release keystore is not a readable file" }
            storeFile = keyFile
            storePassword = secret("M2V_RELEASE_STORE_PASSWORD", "storePassword")
            keyAlias = secret("M2V_RELEASE_KEY_ALIAS", "keyAlias")
            keyPassword = secret("M2V_RELEASE_KEY_PASSWORD", "keyPassword")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        getByName("debug") { isDebuggable = true }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (signedRelease) signingConfigs.getByName("production") else null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    // Already present at runtime via lifecycle-runtime; explicit compile visibility
    // lets lint resolve the receiver whose exported=false override we retain.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}

val syncStudio by tasks.registering(Copy::class) {
    from("../ui/Motion2Video-Studio.html")
    into("src/main/assets")
    rename { "studio.html" }
}
tasks.named("preBuild") { dependsOn(syncStudio) }

// Copy the actual release runtime graph for the isolated OWASP scanner. No app code runs.
tasks.register<Sync>("copySecurityDependencies") {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(configurations.named("releaseRuntimeClasspath"))
    into(rootProject.layout.buildDirectory.dir("security-dependencies"))
}
