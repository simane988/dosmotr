plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

// Detekt runs from the root on every source set at once (`./gradlew detekt`), so a rule
// violation cannot hide in androidTest just because the app module was analysed alone.
detekt {
    source.setFrom("app/src")
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    // Rules the codebase already satisfies are enforced; everything it does not is in the
    // baseline, so the job fails on new findings only.
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    parallel = false // gradle.properties keeps this build single-threaded on purpose
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        sarif.required.set(true) // uploaded to the Security tab by ci.yml
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}
