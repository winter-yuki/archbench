plugins {
    application
}

dependencies {
    implementation(project(":lib"))
    implementation(project(":archs"))
    implementation(project(":archs:blocking"))
    implementation(project(":archs:nonblocking"))
    implementation(project(":archs:async"))

    implementation("org.jetbrains.lets-plot:lets-plot-kotlin:4.2.0")
    implementation("org.jetbrains.lets-plot:lets-plot-image-export:3.0.0")
}

application {
    mainClass.set("com.github.winteryuki.archbench.runner.MainKt")
}
