import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            optIn.add("androidx.media3.common.util.UnstableApi")
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
            implementation(compose.materialIconsExtended)
            implementation(libs.okhttp)
            implementation(libs.coil.compose)
            implementation(libs.androidx.palette.ktx)
            implementation(libs.compose.shimmer)
            implementation(libs.newpipeextractor)
            implementation(libs.ffmpeg.kit)
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
        versionCode = 5
        versionName = "1.0.4"
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
            // Release y F-Droid NO se tocan: ahí las cuatro ABIs son necesarias de verdad.
            //
            // Por defecto se dejan la del emulador (x86_64) y la de un móvil real (arm64-v8a).
            // Para bajar aún más, apuntando solo al emulador:
            //   ./gradlew installDebug -PdebugAbi=x86_64
            ndk {
                val requested = (project.findProperty("debugAbi") as String?)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                abiFilters += requested ?: listOf("x86_64", "arm64-v8a")
            }
        }
        getByName("release") {
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
