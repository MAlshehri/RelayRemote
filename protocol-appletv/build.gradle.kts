plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.coreback.relayremote.protocol"
    compileSdk = 36

    defaultConfig {
        minSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncycastle)
    implementation(libs.dd.plist)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
