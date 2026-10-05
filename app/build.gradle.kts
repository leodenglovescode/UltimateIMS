plugins { id("com.android.application") }
android {
    namespace = "io.github.leodenglovescode.ultimateims"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.leodenglovescode.ultimateims"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}
dependencies { implementation(project(":core")) }
