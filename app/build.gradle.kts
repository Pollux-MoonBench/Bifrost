import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.moonbench.bifrost"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.moonbench.bifrost"
        minSdk = 33
        targetSdk = 36
        versionCode = 11
        versionName = "1.2.0-beta-5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is externalized to a gitignored keystore.properties so no
    // credentials live in version control. Whoever has the keystore + properties
    // (e.g. the maintainer's local checkout) produces a signed release; everyone
    // else — including CI and other contributors — builds an unsigned release
    // and signs separately. Falls back gracefully when the file is absent.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val hasReleaseSigning = keystorePropsFile.exists()
    if (hasReleaseSigning) {
        val keystoreProps = Properties()
        keystorePropsFile.inputStream().use { keystoreProps.load(it) }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    // Real org.json on the JVM test classpath (Android's is a throwing stub),
    // so plugin-catalogue parsing can be unit-tested.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}