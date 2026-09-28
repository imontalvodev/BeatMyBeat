import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.androidx.media)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.common)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.documentfile)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.okhttp)
            implementation(libs.coil.compose)
            implementation(libs.androidx.palette.ktx)
            implementation(libs.compose.shimmer)
            implementation(libs.reorderable)
            implementation(libs.newpipeextractor)
            implementation(libs.ffmpeg.kit)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kaml)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            // android.jar stubs org.json (lanza en runtime); esta dependencia da una implementación real para tests JVM.
            implementation(libs.json)
        }
    }
}

android {
    namespace = "com.imontalvodev.beatmybeat"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.imontalvodev.beatmybeat"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 8
        versionName = "1.3"
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // F-Droid reproducible builds: omit dependency metadata from APK/AAB.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildTypes {
        getByName("debug") {
            // Debug y release conviven como apps distintas en el mismo dispositivo. Sin esto,
            // instalar una build de debug sobre la release que se auto-actualizó falla con
            // INSTALL_FAILED_UPDATE_INCOMPATIBLE (firmas distintas) y hay que desinstalar,
            // perdiendo los datos de prueba. Con beta testers de por medio, eso pasa a menudo.
            //
            // Es seguro: la authority del FileProvider ya es "${applicationId}.fileprovider", y
            // los intents a los servicios son explícitos (Intent(context, X::class.java)), así que
            // las constantes de acción compartidas no se cruzan entre las dos instalaciones.
            //
            // Efecto secundario buscado: ApkUpdateInstaller rechaza un APK cuyo packageName no
            // coincide con el propio, así que una build de debug ya no intentará auto-actualizarse
            // a la release — cosa que de todos modos fallaría por firma.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // ffmpeg-kit trae libavcodec/libavformat/libavfilter compiladas para las cuatro ABIs:
            // ~90 MB de los ~136 MB del APK de debug. Al desarrollar solo se usa una, y un APK de
            // ese tamaño llega a no caber en el emulador
            // ("Requested internal only, but not enough space").
            //
            // Release (y F-Droid, que compila el mismo build type) lleva solo las dos ABIs ARM;
            // ver el bloque release.
            //
            // Por defecto se dejan la del emulador (x86_64) y la de un móvil real (arm64-v8a).
            // Para bajar aún más, apuntando solo al emulador:
            //   ./gradlew installDebug -PdebugAbi=x86_64
            //
            // Ojo: un móvil de 32 bits (armeabi-v7a) o un emulador x86 no pueden instalar este
            // APK (INSTALL_FAILED_NO_MATCHING_ABIS); para ellos: -PdebugAbi=armeabi-v7a, o
            // -PdebugAbi=all para no filtrar nada.
            ndk {
                val supportedAbis = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                // providers.gradleProperty (no project.findProperty): es la API compatible con
                // el configuration cache, que está activado en gradle.properties.
                val raw = providers.gradleProperty("debugAbi").orNull?.trim()
                val requested = raw
                    ?.split(",")
                    //noinspection WrongGradleMethod
                    ?.map { it.trim() }
                    //noinspection WrongGradleMethod
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() } // -PdebugAbi= vacío -> valor por defecto
                when {
                    raw.equals("all", ignoreCase = true) -> Unit
                    requested == null -> abiFilters += listOf("x86_64", "arm64-v8a")
                    else -> {
                        val unknown = requested - supportedAbis
                        require(unknown.isEmpty()) {
                            "debugAbi desconocida: $unknown. Valores válidos: $supportedAbis o all"
                        }
                        abiFilters += requested
                    }
                }
            }
        }
        getByName("release") {
            // Solo ARM: los móviles reales (Fairphone, LineageOS, etc.) son armeabi-v7a o
            // arm64-v8a. x86/x86_64 (emuladores, Android-x86, algún Chromebook) sumaban ~56 MB
            // de FFmpeg nativo a un APK de ~110 MB; esos equipos suelen traducir ARM
            // (houdini/libndk_translation) y siguen pudiendo instalarlo.
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// Ktor 3.6 trae OkHttp 5.5, que exige compileSdk 37 (AGP 8.13 llega a 36). Mismo major 5.x:
// se fija la versión del catálogo hasta subir AGP.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.squareup.okhttp3" && requested.name.startsWith("okhttp")) {
            useVersion(libs.versions.okhttp.get())
        }
    }
}
