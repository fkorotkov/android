load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "adt-ui-model",
    srcs = ["src/main/java"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:hamcrest[test]",
    ],
)
