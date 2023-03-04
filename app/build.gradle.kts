plugins {
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":runner"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.github.winteryuki.archbench.app.MainKt"
    }
}
