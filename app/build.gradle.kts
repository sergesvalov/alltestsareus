plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/** 1 January 2024, 00:00 UTC: versionCode растёт с каждым коммитом (как в проекте bikes). */
val VERSION_EPOCH = 1704067200L

val commitMinutes = try {
    val seconds = providers.exec { commandLine("git", "log", "-1", "--format=%ct") }
        .standardOutput.asText.get().trim().toLong()
    ((seconds - VERSION_EPOCH) / 60).toInt().coerceAtLeast(1)
} catch (e: Exception) {
    1
}

val defaultWebCatalogUrl = (project.findProperty("webCatalogUrl") as? String)
    ?: "http://192.168.0.225:8085/catalog.json"

val defaultSampleWebpageUrl = (project.findProperty("sampleWebpageUrl") as? String)
    ?: "http://192.168.0.225:8085/standalone-sample-test.html"

android {
    namespace = "com.alltestsareus.app"
    // Compiled against Android 14: системный ARM aapt2 в Jenkins Docker не читает таблицу ресурсов android-35+
    compileSdk = 34
    buildToolsVersion = "36.0.0"

    signingConfigs {
        create("release") {
            val customKeystore = System.getenv("APK_KEYSTORE_FILE")
                ?: (project.findProperty("apkKeystoreFile") as? String)
            val customPassword = System.getenv("APK_KEYSTORE_PASSWORD")
                ?: (project.findProperty("apkKeystorePassword") as? String)
            if (customKeystore != null && file(customKeystore).isFile && !customPassword.isNullOrEmpty()) {
                val alias = System.getenv("APK_KEY_ALIAS")
                    ?: (project.findProperty("apkKeyAlias") as? String)
                storeFile = file(customKeystore)
                storePassword = customPassword
                keyAlias = if (!alias.isNullOrEmpty()) alias else "release"
                keyPassword = System.getenv("APK_KEY_PASSWORD") ?: customPassword
            } else {
                storeFile = file("release.keystore")
                storePassword = "bikes123"
                keyAlias = "release"
                keyPassword = "bikes123"
            }
        }
    }

    defaultConfig {
        applicationId = "com.alltestsareus.app"
        minSdk = 26
        targetSdk = 35

        val buildNumber = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: commitMinutes
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        buildConfigField("String", "DEFAULT_WEB_CATALOG_URL", "\"$defaultWebCatalogUrl\"")
        buildConfigField("String", "DEFAULT_SAMPLE_WEBPAGE_URL", "\"$defaultSampleWebpageUrl\"")
    }

    // Разделение сборок из одной ветки кода (Product Flavors)
    flavorDimensions += "topic"
    productFlavors {
        create("allInOne") {
            dimension = "topic"
            resValue("string", "app_name", "Quiz Deck")
            buildConfigField("String", "FLAVOR_CATEGORY", "\"all\"")
            buildConfigField("String", "FLAVOR_TITLE", "\"Языки • Ориентирование • Рептилии\"")
        }
        create("orienteering") {
            dimension = "topic"
            applicationIdSuffix = ".orienteering"
            versionNameSuffix = "-orienteering"
            resValue("string", "app_name", "Ориентир-Профи: Знаки и КП")
            buildConfigField("String", "FLAVOR_CATEGORY", "\"orienteering\"")
            buildConfigField("String", "FLAVOR_TITLE", "\"Спортивное ориентирование\"")
        }
        create("reptiles") {
            dimension = "topic"
            applicationIdSuffix = ".reptiles"
            versionNameSuffix = "-reptiles"
            resValue("string", "app_name", "Герпетология: Мир Рептилий")
            buildConfigField("String", "FLAVOR_CATEGORY", "\"reptiles\"")
            buildConfigField("String", "FLAVOR_TITLE", "\"Рептилии и Герпетология\"")
        }
        create("languages") {
            dimension = "topic"
            applicationIdSuffix = ".languages"
            versionNameSuffix = "-languages"
            resValue("string", "app_name", "LinguaMemory: Языковые тесты")
            buildConfigField("String", "FLAVOR_CATEGORY", "\"languages\"")
            buildConfigField("String", "FLAVOR_TITLE", "\"Иностранные языки\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    lint {
        // Ускоряет release-сборку всех 4 flavors на ARM-агенте Jenkins
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

// Гарантируем, что транзитивные зависимости не потребуют compileSdk 35+ (как в bikes)
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.core" && (requested.name == "core" || requested.name == "core-ktx")) {
            useVersion("1.13.1")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Room (Offline-First БД)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // JSON парсинг и корутины
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
