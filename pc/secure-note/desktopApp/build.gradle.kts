import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    implementation("org.json:json:20240303")
    implementation("com.google.zxing:core:3.5.3")
}

compose.desktop {
    application {
        mainClass = "org.cursiveerror.securenote.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "SecureNote Desktop"
            packageVersion = "1.0.0"
            description = "SecureNote Desktop LAN Sync"
            copyright = "© 2026 CursiveError"
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        }
    }
}