load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")

RULES_PYTHON_TAG = "0.8.1"

RULES_PYTHON_SHA = "cdf6b84084aad8f10bf20b46b77cb48d83c319ebe6458a18e9d2cebf57807cdd"

http_archive(
    name = "rules_python",
    sha256 = RULES_PYTHON_SHA,
    strip_prefix = "rules_python-%s" % RULES_PYTHON_TAG,
    url = "https://github.com/bazelbuild/rules_python/archive/refs/tags/%s.tar.gz" % RULES_PYTHON_TAG,
)

load("@rules_python//python:pip.bzl", "pip_install")

pip_install(
    name = "deps",
    requirements = "//python:requirements.txt",
)

RULES_JVM_EXTERNAL_TAG = "4.2"

RULES_JVM_EXTERNAL_SHA = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

APACHE_AVRO_VERSION = "1.4.1"

APACHE_BEAM_VERSION = "2.36.0"

APACHE_BEAM_VENDOR_GUAVA_VERSION = "0.1"

GUAVA_VERSION = "31.1-jre"

GUAVA_ANNOTATIONS_VERSION = "r03"

HAMCREST_VERSION = "1.3"

MOCKITO_VERSION = "4.5.1"

SIMPLE_JSON_VERSION = "1.1.1"

PY4J_VERSION = "0.10.9.5"

COMMONS_IO_VERSION = "2.11.0"

JUNIT_VERSION = "4.13.2"

CBOR_VERSION = "0.7"

GSON_VERSION = "2.8.6"

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "org.apache.avro:avro:" + APACHE_AVRO_VERSION,
        "org.apache.beam:beam-sdks-java-core:" + APACHE_BEAM_VERSION,
        "org.apache.beam:beam-model-pipeline:" + APACHE_BEAM_VERSION,
        "org.apache.beam:beam-runners-direct-java:" + APACHE_BEAM_VERSION,
        "commons-io:commons-io:" + COMMONS_IO_VERSION,
        "org.mockito:mockito-core:" + MOCKITO_VERSION,
        "org.mockito:mockito-inline:" + MOCKITO_VERSION,
        "com.googlecode.json-simple:json-simple:" + SIMPLE_JSON_VERSION,
        "junit:junit:" + JUNIT_VERSION,
        "org.apache.beam:beam-vendor-guava-26_0-jre:" + APACHE_BEAM_VENDOR_GUAVA_VERSION,
        "com.google.guava:guava:" + GUAVA_VERSION,
        "com.google.guava:guava-annotations:" + GUAVA_ANNOTATIONS_VERSION,
        "org.hamcrest:hamcrest-core:" + HAMCREST_VERSION,
        "org.hamcrest:hamcrest-library:" + HAMCREST_VERSION,
        "net.sf.py4j:py4j:" + PY4J_VERSION,
        "co.nstant.in:cbor:" + CBOR_VERSION,
        "com.google.code.gson:gson:" + GSON_VERSION,
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)

###########################
# Binary Dev Dependencies #
###########################

http_archive(
    name = "com_google_protobuf",
    sha256 = "a79d19dcdf9139fa4b81206e318e33d245c4c9da1ffed21c87288ed4380426f9",
    strip_prefix = "protobuf-3.11.4",
    # latest, as of 2020-02-21
    urls = [
        "https://mirror.bazel.build/github.com/protocolbuffers/protobuf/archive/v3.11.4.tar.gz",
        "https://github.com/protocolbuffers/protobuf/archive/v3.11.4.tar.gz",
    ],
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

http_archive(
    name = "io_bazel_rules_go",
    sha256 = "f2dcd210c7095febe54b804bb1cd3a58fe8435a909db2ec04e31542631cf715c",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.31.0/rules_go-v0.31.0.zip",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.31.0/rules_go-v0.31.0.zip",
    ],
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains(version = "1.18")

http_file(
    name = "google_java_format",
    downloaded_file_path = "google-java-format.jar",
    sha256 = "a356bb0236b29c57a3ab678f17a7b027aad603b0960c183a18f1fe322e4f38ea",
    urls = ["https://github.com/google/google-java-format/releases/download/v1.15.0/google-java-format-1.15.0-all-deps.jar"],
)

http_archive(
    name = "bazel_skylib",
    sha256 = "f7be3474d42aae265405a592bb7da8e171919d74c16f082a5457840f06054728",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.2.1/bazel-skylib-1.2.1.tar.gz",
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.2.1/bazel-skylib-1.2.1.tar.gz",
    ],
)

http_archive(
    name = "com_github_bazelbuild_buildtools",
    sha256 = "e3bb0dc8b0274ea1aca75f1f8c0c835adbe589708ea89bf698069d0790701ea3",
    strip_prefix = "buildtools-5.1.0",
    urls = ["https://github.com/bazelbuild/buildtools/archive/5.1.0.tar.gz"],
)
