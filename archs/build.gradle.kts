allprojects {
    dependencies {
        implementation(project(":lib"))
    }
}

subprojects {
    dependencies {
        implementation(project(":archs"))
        testImplementation(project(":archs:testing"))
    }
}
