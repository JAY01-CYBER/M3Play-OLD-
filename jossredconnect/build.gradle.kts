plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {

    // OkHttp (to JossRedClient)
    implementation(libs.okhttp) // o la versión más reciente

    // If you also need interceptors for logging (optional)
    implementation(libs.logging.interceptor)

}
