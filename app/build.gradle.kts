plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ru.vinteno.finni"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.vinteno.finni"
        minSdk = 26 // Android 8.0 — ТЗ 3.1
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            // R8 выкидывает неиспользуемые иконки библиотеки: без него APK вырастает на десятки мегабайт.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Сборка для показа детям и замеров: оптимизирована как выпускная, подписана отладочным
        // ключом Android Studio (он лежит вне репозитория). Ставится на любой телефон без среды.
        // Ключ подписи для сдачи — отдельно, его создаёт команда (build-plan, этап 0).
        create("prototype") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Графика: art/app целиком становится корнем ассетов, файлы — под своими именами. Новая
    // картинка, положенная в art/app, попадает в сборку без правки кода. Исходники art/source
    // в приложение не идут.
    sourceSets["main"].assets.srcDir("../art/app")

    // Скриншоты экранов на JVM (Robolectric + Roborazzi): агент проверяет вёрстку 360 dp
    // и шрифт ×2,0 без эмулятора. В APK ничего из этого не попадает.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.serialization.json)
    implementation(libs.compose.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
}
