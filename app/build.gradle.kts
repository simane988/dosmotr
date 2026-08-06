import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Secrets live in local.properties (never committed): the backend credentials and the
// release signing ones.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The app only ever talks to its own backend (dosmotr-backend). Which catalogue that
// backend reads from, and what key that needs, is not this build's business.
val backendUrl: String = localProps.getProperty("backend.url", "").trim().trimEnd('/')
val backendToken: String = localProps.getProperty("backend.token", "").trim()

if (backendUrl.isNotEmpty() && backendToken.isEmpty()) {
    // Would build fine and then 403 on every call, which is a confusing way to find out.
    error("backend.url is set but backend.token is empty — see the dosmotr-backend repo")
}

// version.properties is committed and CI rewrites it on a release branch, so the version
// in a build always matches a commit someone can point at.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

val releaseStoreFile: File? = localProps.getProperty("release.storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    // namespace is the Kotlin/R/BuildConfig package and stays put — renaming it would
    // touch every file for no user-visible gain. applicationId is what the system and
    // the store actually key on.
    namespace = "com.g3ck0.seriestracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.g3ck0.dosmotr"
        minSdk = 26
        targetSdk = 35
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Retrofit refuses to build on an empty base URL, so an unconfigured build gets a
        // placeholder it can parse. Nothing reaches it: a blank token means the app never
        // makes a call in the first place.
        val effectiveUrl = backendUrl.ifEmpty { "https://backend.invalid" }
        buildConfigField("String", "BACKEND_URL", "\"$effectiveUrl/\"")
        buildConfigField("String", "BACKEND_IMAGE_URL", "\"$effectiveUrl/img/\"")
        buildConfigField("String", "BACKEND_TOKEN", "\"$backendToken\"")
    }

    signingConfigs {
        // Only registered when the keystore is actually present, so a fresh clone
        // still builds (release just comes out unsigned).
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
        // Release-like build (R8 on, not debuggable) that can still be installed and
        // profiled locally. Use it for any performance measurement — a debug build
        // runs Compose unoptimised and its frame times mean nothing.
        create("profileable") {
            initWith(getByName("release"))
            applicationIdSuffix = ".profileable"
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
            // R8 off here on purpose: what makes debug frame times meaningless is
            // `debuggable`, not the missing shrinking — and R8 is the memory-hungry
            // step this machine cannot afford.
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // toUserError() logs the original exception through android.util.Log, which is an
        // unimplemented stub on the JVM and throws unless the stubs return defaults.
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        // MigrationTestHelper reads the exported schemas out of the test APK's assets, so
        // ksp's room.schemaLocation below has to be an androidTest asset directory too.
        // On androidTest only: the app itself never opens these files, and shipping them
        // in the release APK would be dead weight.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    lint {
        // CI runs `lintDebug` and must fail on it — a lint report nobody blocks on is a
        // report nobody reads. Findings the codebase already has live in lint-baseline.xml,
        // so only new ones break the build.
        abortOnError = true
        warningsAsErrors = true
        // checkDependencies stays off: analysing the AndroidX/Compose graph as well is the
        // step that pushes lint past what an 8 GB machine has (see "Memory budget").
        checkDependencies = false
        checkReleaseBuilds = false // the release job has no minutes to spare for a second pass
        baseline = file("lint-baseline.xml")
        sarifReport = true // uploaded to the Security tab by ci.yml
        htmlReport = true
        xmlReport = false
        // The app is Russian-only by design (see CLAUDE.md), so a missing translation and
        // an untranslated literal are not defects here.
        disable += setOf(
            // The app is Russian-only by design (see CLAUDE.md), so a missing translation
            // and an untranslated literal are not defects here.
            "MissingTranslation",
            "HardcodedText",
            // "a newer version exists" is a decision, not a defect — bumping AGP/Kotlin/
            // Compose is its own change with its own test run. CVEs in dependencies are
            // caught by the vulnerability-scan job instead.
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
            // Same reasoning, and it only fires on CI: the runner's SDK manager knows about
            // a newer platform than this machine has installed, so leaving it on means lint
            // passes locally and fails in the pipeline. Raising targetSdk is a change with
            // its own behaviour testing, not a lint fix.
            "OldTargetApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// connectedAndroidTest installs the app, runs, then uninstalls it again — which left
// the phone without a build to use. Reinstalling afterwards keeps the latest debug
// build on the device without also leaving the instrumentation APK behind (which the
// `leaveApksInstalledAfterRun` flag would). finalizedBy runs even when tests fail.
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    finalizedBy("installDebug")
}
