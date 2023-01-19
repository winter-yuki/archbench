plugins {
    application
}

dependencies {
    implementation(project(":lib"))
    implementation(project(":archs"))
    implementation(project(":archs:blocking"))
}

application {
    mainClass.set("com.github.winteryuki.archtest.runner.MainKt")
}
