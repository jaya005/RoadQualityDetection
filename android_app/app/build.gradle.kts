plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.roadqualitysensor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.roadqualitysensor"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    testImplementation("junit:junit:4.13.2")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // CameraX
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // TensorFlow Lite 2.16.1 (Solves Manifest Namespace Merger Crash)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
    }

    // Guava
    implementation("com.google.guava:guava:31.1-android")
}

// --- GOOGLE SERVICES JSON INJECTION TASK ---
tasks.register("generateGoogleServicesJson") {
    doLast {
        // Navigate up from android_app/app/ to the root roadQualityDetection folder
        val envFile = file("../../.env")
        val envVars = mutableMapOf<String, String>()

        // Read the .env file if it exists
        if (envFile.exists()) {
            envFile.forEachLine { line ->
                if (line.contains("=") && !line.trimStart().startsWith("#")) {
                    val parts = line.split("=", limit = 2)
                    envVars[parts[0].trim()] = parts[1].trim()
                }
            }
        } else {
            logger.warn("No .env file found at ${envFile.absolutePath}")
        }

        // Locate the template inside the app module
        val templateFile = file("google-services-template.json")
        if (!templateFile.exists()) {
            throw GradleException("Missing google-services-template.json in the app module")
        }

        // Replace placeholder and write the actual JSON
        val templateContent = templateFile.readText()
        val apiKey = envVars["GOOGLE_API_KEY"] ?: "MISSING_KEY"
        val finalJson = templateContent.replace("GOOGLE_API_KEY", apiKey)

        file("google-services.json").writeText(finalJson)
    }
}

// Ensure the injection happens before the Google Services plugin runs
afterEvaluate {
    tasks.matching { it.name.matches(Regex("process.*GoogleServices")) }.configureEach {
        dependsOn("generateGoogleServicesJson")
    }
}