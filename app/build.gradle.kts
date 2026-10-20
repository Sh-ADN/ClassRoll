plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

run {
  val defaultJson = """
    {
      "project_info": {
        "project_number": "657750499614",
        "project_id": "gen-lang-client-0269184419",
        "storage_bucket": "gen-lang-client-0269184419.firebasestorage.app"
      },
      "client": [
        {
          "client_info": {
            "mobilesdk_app_id": "1:657750499614:android:867b393c7714dd5f1d690f",
            "android_client_info": {
              "package_name": "com.aistudio.classroll.jkmxlp"
            }
          },
          "oauth_client": [
            {
              "client_id": "657750499614-11qkk1egv33laah5eq40rlatjio3b538.apps.googleusercontent.com",
              "client_type": 1,
              "android_info": {
                "package_name": "com.aistudio.classroll.jkmxlp",
                "certificate_hash": "55a7157d505e08cbc325ea816ce7cf4ec278801d"
              }
            },
            {
              "client_id": "657750499614-goc2ljrqu9bt97gtt5do2brrttp9n6gj.apps.googleusercontent.com",
              "client_type": 3
            }
          ],
          "api_key": [
            {
              "current_key": "AIzaSyAR8ntGRu1L0HCc_ufey50p5vdWZIE_SkY"
            }
          ],
          "services": {
            "appinvite_service": {
              "other_platform_oauth_client": [
                {
                  "client_id": "657750499614-goc2ljrqu9bt97gtt5do2brrttp9n6gj.apps.googleusercontent.com",
                  "client_type": 3
                }
              ]
            }
          }
        }
      ],
      "configuration_version": "1"
    }
  """.trimIndent()

  val paths = listOf(
    file("google-services.json"),
    file("src/google-services.json"),
    file("src/main/google-services.json"),
    file("src/release/google-services.json")
  )
  val existing = paths.find { it.exists() }?.readText() ?: defaultJson
  paths.forEach { f ->
    if (!f.exists()) {
      f.parentFile?.mkdirs()
      f.writeText(existing)
    }
  }
}



android {
  namespace = "com.aistudio.classroll.jkmxlp"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  androidResources {
    localeFilters += listOf("en")
  }

  defaultConfig {
    applicationId = "com.aistudio.classroll.jkmxlp"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "1.0.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val customPath = System.getenv("KEYSTORE_PATH")
      val uploadKey = file("${rootDir}/my-upload-key.jks")
      val debugKey = file("${rootDir}/debug.keystore")

      if (customPath != null && file(customPath).exists()) {
        storeFile = file(customPath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else if (uploadKey.exists()) {
        storeFile = uploadKey
        storePassword = System.getenv("STORE_PASSWORD") ?: "android"
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
      } else if (debugKey.exists()) {
        storeFile = debugKey
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      val debugKey = file("${rootDir}/debug.keystore")
      if (debugKey.exists()) {
        storeFile = debugKey
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile?.exists() == true }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig").takeIf { it.storeFile?.exists() == true }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "/META-INF/DEPENDENCIES"
      excludes += "/META-INF/LICENSE*"
      excludes += "/META-INF/NOTICE*"
      excludes += "/META-INF/*.version"
      excludes += "/META-INF/INDEX.LIST"
      excludes += "/META-INF/*.kotlin_module"
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
