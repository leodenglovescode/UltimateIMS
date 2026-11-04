plugins { id("com.android.application") }
android {
    namespace = "io.github.leodenglovescode.ultimateims"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.leodenglovescode.ultimateims"
        minSdk = 33
        targetSdk = 37
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
        versionCode = 6
        versionName = "0.4.2-dev"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
    buildFeatures { aidl = true; buildConfig = true }
    sourceSets.getByName("main").jniLibs.directories.add(layout.buildDirectory.dir("pairing-jni").get().asFile.absolutePath)
}
val buildPairing = tasks.register<Exec>("buildPairing") {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/build_pairing.py")
    inputs.files(fileTree("src/main/cpp"), rootProject.file("scripts/build_pairing.py"))
    outputs.dir(layout.buildDirectory.dir("pairing-jni"))
}
tasks.named("preBuild").configure { dependsOn(buildPairing) }
dependencies {
    implementation(project(":core"))
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("org.conscrypt:conscrypt-android:2.7.0")
}
