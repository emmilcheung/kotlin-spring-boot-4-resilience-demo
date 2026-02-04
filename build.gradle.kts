plugins {
    // ...existing code...
    kotlin("jvm") version "1.9.25"  // Kotlin plugin
    kotlin("plugin.spring") version "1.9.25"  // Spring support
}

dependencies {
    // ...existing code...
    implementation("org.jetbrains.kotlin:kotlin-reflect")  // Kotlin runtime
    implementation("org.jetbrains.kotlin:kotlin-stdlib")   // Add if missing
    // ...existing code...
}
