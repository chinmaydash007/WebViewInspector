plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin)
}

android {
    val samplePackage = providers.gradleProperty("INSPECTOR_PACKAGE").get() + ".sample"
    namespace = samplePackage
    compileSdk = 36
    defaultConfig {
        applicationId = samplePackage
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = project.version.toString()
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { viewBinding = true }
}
kotlin { jvmToolchain(17) }

dependencies {
    debugImplementation(project(":webview-inspector"))
    releaseImplementation(project(":webview-inspector-no-op"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.core)
    testImplementation(libs.junit)
}
