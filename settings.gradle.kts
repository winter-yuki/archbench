rootProject.name = "archbench"
include(":lib")
include(":archs", ":archs:testing", ":archs:async", ":archs:blocking", ":archs:nonblocking")
include(":runner", ":app")
