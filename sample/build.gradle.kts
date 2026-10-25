plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.compose)
}
android {
    namespace = "io.github.chinmaydash007.webviewinspector.sample"
    compileSdk = 36
    defaultConfig { applicationId = "io.github.chinmaydash007.webviewinspector.sample"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release { isMinifyEnabled = true; signingConfig = signingConfigs.getByName("debug"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) }
        create("diagnostic") { initWith(getByName("release")); applicationIdSuffix = ".diagnostic"; matchingFallbacks += "release" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    debugImplementation(project(":webview-inspector"))
    add("diagnosticImplementation", project(":webview-inspector"))
    releaseImplementation(project(":webview-inspector-no-op"))
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
}
