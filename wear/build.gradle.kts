import org.gradle.api.tasks.compile.JavaCompile

plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.plugin.compose")
}
android {
	namespace = "com.ziacik.pilltap"
	compileSdk = 37
	defaultConfig {
		applicationId = "com.ziacik.pilltap"
		minSdk = 30
		targetSdk = 37
		versionCode = 1
		versionName = "0.1.0"
	}
	buildFeatures { compose = true }
	lint {
		warningsAsErrors = true
		abortOnError = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

kotlin {
	compilerOptions {
		allWarningsAsErrors.set(true)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
dependencies {
	implementation("androidx.core:core-ktx:1.19.0")
	implementation("androidx.activity:activity-compose:1.13.0")
	implementation(platform("androidx.compose:compose-bom:2026.08.00"))
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.foundation:foundation")
	implementation("androidx.wear.compose:compose-material3:1.5.0")
	implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
