import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// keystore.properties (git-ignored, document 27): storeFile, storePassword, keyAlias, keyPassword. Null when absent.
val releaseKeys: Properties? = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "dev.knowledge82.swiftyproteins"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.knowledge82.swiftyproteins"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ONE debug signing key for every machine (document 11). By default each computer signs debug builds with its own
    // ~/.android/debug.keystore, so installing over a build made on ANOTHER machine fails with
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE. This key is for debugging only (standard public password "android");
    // it must never sign a release.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The RELEASE key (document 27): its passwords and its path live in keystore.properties at the project root,
        // which git ignores, and the key file itself lives OUTSIDE the repository. Without that file the release is
        // built UNSIGNED (it cannot be installed): the secret never has to be in the repository for the build to work.
        if (releaseKeys != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeys.getProperty("storeFile"))
                storePassword = releaseKeys.getProperty("storePassword")
                keyAlias = releaseKeys.getProperty("keyAlias")
                keyPassword = releaseKeys.getProperty("keyPassword")
            }
        }
    }

    testOptions {
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
            }
        }
    }

    buildTypes {
        release {
            // R8 (shrinking and obfuscation) stays OFF (document 27): Filament and SceneView reach their classes through
            // JNI and reflection, and keep rules written without testing on the phone could break the 3D in release
            // only. The cost is a bigger APK; the speed comes from the release build itself (no debuggable runtime).
            optimization {
                enable = false
            }
            if (releaseKeys != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    // Bouncy Castle ships as a multi-release jar; this OSGi file is metadata only. Excluded so that, if another
    // Bouncy Castle jar ever arrives through a dependency, packaging does not fail on the duplicate (document 05, 14).
    packaging {
        resources {
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Sign-in (document 05): ProcessLifecycleOwner (lock when the app goes to the background), the system biometric
    // dialog, and Argon2id for the passwords.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.sceneview)
}
