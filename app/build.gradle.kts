import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionName = "1.3.0"

android {
    namespace = "com.momentoestoico"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.momentoestoico"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = appVersionName
    }

    // Idiomas de la app: aparecen en Ajustes › Idioma de la app (Android 13+)
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("es", "fr")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Cada assemble deja una copia del APK con nombre y versión en <proyecto>/dist/
androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val variantName = variant.name
        val copy = tasks.register<Copy>("dist${cap}Apk") {
            from(variant.artifacts.get(SingleArtifact.APK)) { include("*.apk") }
            into(rootProject.layout.projectDirectory.dir("dist"))
            rename { "MomentoEstoico-$appVersionName-$variantName.apk" }
        }
        tasks.configureEach { if (name == "assemble$cap") finalizedBy(copy) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
