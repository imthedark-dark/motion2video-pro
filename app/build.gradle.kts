plugins { id("com.android.application") }
android {
    namespace = "dev.motion2video.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.motion2video.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

val syncStudio by tasks.registering(Copy::class) {
    from("../ui/Motion2Video-Studio.html")
    into("src/main/assets")
    rename { "studio.html" }
}
tasks.named("preBuild") { dependsOn(syncStudio) }
