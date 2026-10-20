pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

// Ensure google-services.json exists in all required locations before app project plugins evaluate
val googleServicesJsonDefault = """
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

val gservicesPaths = listOf(
  file("app/google-services.json"),
  file("app/src/google-services.json"),
  file("app/src/main/google-services.json"),
  file("app/src/release/google-services.json")
)
val existingGservices = gservicesPaths.find { it.exists() && it.length() > 0 }?.readText() ?: googleServicesJsonDefault
gservicesPaths.forEach { targetFile ->
  if (!targetFile.exists() || targetFile.length() == 0L) {
    targetFile.parentFile?.mkdirs()
    targetFile.writeText(existingGservices)
  }
}

rootProject.name = "ClassRoll"

include(":app")

