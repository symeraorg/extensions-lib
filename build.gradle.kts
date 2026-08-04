plugins {
    alias(libs.plugins.android.library) apply false
}

tasks.register<Delete>("clean") {
    description = "Deletes build outputs for extensions-lib modules."
    delete(rootProject.layout.buildDirectory)
}
