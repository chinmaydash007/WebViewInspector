plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.publishing)
    alias(libs.plugins.dokka)
}
android {
    namespace = "com.visit.webviewinspector.noop"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
