plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Байткод 17 при любой JDK от 17, на которой запущен Gradle: и JDK 17 из Homebrew,
// и встроенная Java Android Studio собирают одинаково.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}
