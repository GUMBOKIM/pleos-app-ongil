import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "kr.ongil.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.ongil.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.2"
        // 자랑 카드·그날 카드 QR이 여는 정적 페이지(서버 없음, GitHub Pages). 데이터는 URL 프래그먼트에만 있어 서버에 안 남는다
        buildConfigField("String", "SHARE_BASE", "\"https://gumbokim.github.io/pleos-app-ongil/web\"")
    }
    // 릴리스 서명: ongil/keystore.properties(저장소 제외)에 storeFile/storePassword/keyAlias/keyPassword. 없으면 서명 없이 빌드
    val ksProps = Properties().apply { val f = rootProject.file("keystore.properties"); if (f.exists()) f.inputStream().use { load(it) } }
    signingConfigs {
        if (ksProps.containsKey("storeFile")) create("release") {
            storeFile = file(ksProps["storeFile"] as String); storePassword = ksProps["storePassword"] as String
            keyAlias = ksProps["keyAlias"] as String; keyPassword = ksProps["keyPassword"] as String
        }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfigs.findByName("release")?.let { signingConfig = it } }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    compileOnly(files("${android.sdkDirectory}/platforms/android-35/optional/android.car.jar"))   // CarUxRestrictions (AAOS 시스템 라이브러리)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.fused)
    implementation(libs.pleos.navi)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.animation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
}
