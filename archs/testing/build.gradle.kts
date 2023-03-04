dependencies {
    // TODO move to archs test module (failed to write test on test dependency)
    implementation(kotlin("test"))
    implementation(platform("org.junit:junit-bom:5.8.0"))
    implementation("org.junit.jupiter:junit-jupiter")
}
