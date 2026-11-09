plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.compose)
    alias(libs.plugins.publishing)
    alias(libs.plugins.dokka)
}
android {
    namespace = providers.gradleProperty("INSPECTOR_PACKAGE").get()
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        targetSdk = 35
        animationsDisabled = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    // Only test hosts use Compose; no Compose dependency is published.
    compileOnly(platform(libs.compose.bom))
    compileOnly(libs.compose.runtime)
    implementation(libs.core)
    implementation(libs.material)
    implementation(libs.webkit)
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.runtime)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.activity.compose)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.foundation)
    androidTestImplementation(libs.recycler)
}
