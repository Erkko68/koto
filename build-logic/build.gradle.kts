plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

// The plugins the modules apply, here so the convention plugins (koto-publish, koto-desktop-runtime) and the modules
// see one copy of each: a module names them without a version.
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    implementation("com.android.tools.build:gradle:9.4.1")
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.37.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.3.0")
    testImplementation(kotlin("test"))
}

// The API generator's fixture test runs clang; -Papigen.update rewrites the fixture's expected output.
tasks.test {
    useJUnitPlatform()
    inputs.dir("src/test/fixture").withPathSensitivity(PathSensitivity.RELATIVE)
    providers.gradleProperty("apigen.update").orNull?.let { systemProperty("apigen.update", it) }
}
