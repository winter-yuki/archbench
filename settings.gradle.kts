rootProject.name = "archtest"
include(":lib")
include(":archs", ":archs:testing", ":archs:async", ":archs:blocking", ":archs:nonblocking")
include(":runner", ":app")
