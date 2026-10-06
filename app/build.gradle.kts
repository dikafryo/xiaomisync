import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 배포용 서명 키는 저장소·웹 밖에 둔다(vm1: ~/.android-keys/xiaomisync/keystore.properties).
// 파일이 없으면(다른 PC) 릴리스 서명 없이 빌드되고, 디버그 빌드는 그대로 동작한다.
val releaseKeyFile = File(System.getProperty("user.home"), ".android-keys/xiaomisync/keystore.properties")
val releaseKey = Properties().apply { if (releaseKeyFile.exists()) releaseKeyFile.inputStream().use(::load) }

// 버전은 git 태그(v1.2.3)에서 받는다: ./gradlew assembleRelease -PappVersion=1.2.3
// versionCode 는 1.2.3 → 10203 으로 계산해 항상 증가하게 한다 (각 자리 0~99)
val appVersion = (findProperty("appVersion") as String?) ?: "1.0.0"
val appVersionCode = appVersion.split('.').let { (major, minor, patch) ->
    major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
}

android {
    namespace = "kr.xiaomisync"
    compileSdk = 34

    defaultConfig {
        applicationId = "kr.xiaomisync"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeyFile.exists()) {
            create("release") {
                storeFile = File(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
