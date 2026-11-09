plugins { id("com.android.application") }
android {
    namespace = "example.consumer"
    compileSdk = 36
    defaultConfig {
        applicationId = "example.webviewinspector.consumer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName =
            "1"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        create("diagnostic") {
            initWith(getByName("release"))
            applicationIdSuffix = ".diagnostic"
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
val inspectorVersion = providers.gradleProperty("inspectorVersion").get()
dependencies {
    debugImplementation("io.github.chinmaydash007:webview-inspector:$inspectorVersion")
    add("diagnosticImplementation", "io.github.chinmaydash007:webview-inspector:$inspectorVersion")
    releaseImplementation("io.github.chinmaydash007:webview-inspector-no-op:$inspectorVersion")
}
