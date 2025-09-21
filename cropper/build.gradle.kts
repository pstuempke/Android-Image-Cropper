plugins {
  id("org.jetbrains.dokka")
  id("org.jetbrains.kotlin.android")
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.parcelize")
  id("com.vanniktech.maven.publish")
  id("app.cash.licensee")
  id("app.cash.paparazzi")
  id("org.jetbrains.kotlin.plugin.compose")
}

licensee {
  allow("Apache-2.0")
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

android {
  namespace = "com.canhub.cropper"

  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
  }

  buildFeatures {
    viewBinding = true
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.14"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
}

dependencies {
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.exifinterface)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Compose dependencies
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.preview)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.coil.compose)

  debugImplementation(libs.compose.ui.tooling)
}

dependencies {
  testImplementation(libs.androidx.fragment.testing)
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.junit)
  testImplementation(libs.mock)
  testImplementation(libs.robolectric)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Workaround https://github.com/cashapp/paparazzi/issues/1231
plugins.withId("app.cash.paparazzi") {
  // Defer until afterEvaluate so that testImplementation is created by Android plugin.
  afterEvaluate {
    dependencies.constraints {
      add("testImplementation", "com.google.guava:guava") {
        attributes {
          attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM),
          )
        }
        because(
          "LayoutLib and sdk-common depend on Guava's -jre published variant." +
            "See https://github.com/cashapp/paparazzi/issues/906.",
        )
      }
    }
  }
}
