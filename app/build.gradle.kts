plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// URL base del backend. Por defecto: debug usa el backend local (10.0.2.2 es el localhost de la máquina vista desde
// el emulador) y release un dominio de ejemplo. Para apuntar al borde público desplegado en AWS (la URL de CloudFront
// cambia cada vez que se recrea staging), sobrescríbela sin tocar el código:
//   ./gradlew installDebug -PsolventaBaseUrl=https://dxxxxxxxxxxxxx.cloudfront.net
// o fija `solventaBaseUrl=...` en ~/.gradle/gradle.properties.
fun baseUrl(porDefecto: String): String {
    val url = providers.gradleProperty("solventaBaseUrl").orNull?.trim().takeUnless { it.isNullOrEmpty() }
        ?: return porDefecto
    require(url.startsWith("http://") || url.startsWith("https://")) {
        "solventaBaseUrl debe empezar por http:// o https:// (recibido: $url)"
    }
    require('"' !in url && '\\' !in url) { "solventaBaseUrl contiene caracteres no permitidos" }
    return if (url.endsWith("/")) url else "$url/" // Retrofit exige la barra final
}

android {
    namespace = "com.solventa.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.solventa.mobile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Límite de espera de una cotización en el cliente, en segundos (HU37; el valor se afina con HA01).
        buildConfigField("int", "COTIZACION_TIMEOUT_S", "10")
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"${baseUrl("http://10.0.2.2:8000/")}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "BASE_URL", "\"${baseUrl("https://api.solventa.example.com/")}\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        // java.time en minSdk 24: la hora de fin del bloqueo se formatea según el idioma (HU03).
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDirs("src/androidTest/kotlin")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.datastore.preferences)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
