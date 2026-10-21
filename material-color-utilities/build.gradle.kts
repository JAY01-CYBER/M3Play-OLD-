plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    compileOnly(libs.errorprone.annotations)
    implementation(libs.annotation)
}
