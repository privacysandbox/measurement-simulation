load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")

http_archive(
    name = "rules_python",
    sha256 = "ca2671529884e3ecb5b79d6a5608c7373a82078c3553b1fa53206e6b9dddab34",
    strip_prefix = "rules_python-0.38.0",
    url = "https://github.com/bazelbuild/rules_python/releases/download/0.38.0/rules_python-0.38.0.tar.gz",
)

load("@rules_python//python:repositories.bzl", "py_repositories")

py_repositories()

load("@rules_python//python:pip.bzl", "pip_parse")

pip_parse(
    name = "deps",
    requirements_lock = "//python:requirements.txt",
)

load("@deps//:requirements.bzl", "install_deps")

install_deps()

http_archive(
    name = "rules_proto",
    sha256 = "80d3a4ec17354cccc898bfe32118edd934f851b03029d63ef3fc7c8663a7415c",
    strip_prefix = "rules_proto-5.3.0-21.5",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/refs/tags/5.3.0-21.5.tar.gz",
    ],
)

http_archive(
    name = "rules_proto_grpc",
    sha256 = "5f0f2fc0199810c65a2de148a52ba0aff14d631d4e8202f41aff6a9d590a471b",
    strip_prefix = "rules_proto_grpc-1.0.2",
    urls = ["https://github.com/rules-proto-grpc/rules_proto_grpc/archive/1.0.2.tar.gz"],
)

# buildifier is written in Go and hence needs rules_go to be built.
# See https://github.com/bazelbuild/rules_go for the up to date setup instructions.
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "b6828eb2d03bb5ef76f2077f8670b211fe792e77ddb83450ea9f887df04db9c7",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.44.1/rules_go-v0.44.1.zip",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.44.1/rules_go-v0.44.1.zip",
    ],
)

http_archive(
    name = "bazel_gazelle",
    sha256 = "de69a09dc70417580aabf20a28619bb3ef60d038470c7cf8442fafcf627c21cb",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.24.0/bazel-gazelle-v0.24.0.tar.gz",
        "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.24.0/bazel-gazelle-v0.24.0.tar.gz",
    ],
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "3bd7828aa5af4b13b99c191e8b1e884ebfa9ad371b0ce264605d347f135d2568",
    strip_prefix = "protobuf-3.19.4",
    urls = [
        "https://github.com/protocolbuffers/protobuf/archive/v3.19.4.tar.gz",
    ],
)

http_archive(
    name = "com_github_bazelbuild_buildtools",
    sha256 = "ae34c344514e08c23e90da0e2d6cb700fcd28e80c02e23e4d5715dddcb42f7b3",
    strip_prefix = "buildtools-4.2.2",
    urls = [
        "https://github.com/bazelbuild/buildtools/archive/refs/tags/4.2.2.tar.gz",
    ],
)

# Fetches all go rules dependencies according to (https://github.com/bazelbuild/buildtools/blob/master/buildifier/README.md)
load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

# Fetches go rules dependencies
go_rules_dependencies()

go_register_toolchains(version = "1.17.12")

# Configures gazelle according to instructions for buildfier (https://github.com/bazelbuild/buildtools/blob/master/buildifier/README.md)
load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies", "go_repository")

go_repository(
    name = "org_golang_google_grpc",
    importpath = "google.golang.org/grpc",
    sum = "h1:9n77onPX5F3qfFCqjy9dhn8PbNQsIKeVU04J9G7umt8=",
    version = "v1.47.0",
)

gazelle_dependencies()

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

# google-java-format for presubmit checks of format and unused imports
http_file(
    name = "google_java_format",
    downloaded_file_path = "google-java-format.jar",
    urls = ["https://github.com/google/google-java-format/releases/download/v1.27.0/google-java-format-1.27.0-all-deps.jar"],
)

http_archive(
    name = "rules_java",
    sha256 = "a9690bc00c538246880d5c83c233e4deb83fe885f54c21bb445eb8116a180b83",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/7.12.2/rules_java-7.12.2.tar.gz",
    ],
)

load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")

rules_java_dependencies()

rules_java_toolchains()

RULES_JVM_EXTERNAL_TAG = "6.4"

RULES_JVM_EXTERNAL_SHA = "85776be6d8fe64abf26f463a8e12cd4c15be927348397180a01693610da7ec90"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazel-contrib/rules_jvm_external/releases/download/%s/rules_jvm_external-%s.tar.gz" % (RULES_JVM_EXTERNAL_TAG, RULES_JVM_EXTERNAL_TAG),
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

APACHE_AVRO_VERSION = "1.4.1"

APACHE_BEAM_VERSION = "2.62.0"

APACHE_BEAM_VENDOR_GUAVA_VERSION = "0.1"

GUAVA_VERSION = "31.1-jre"

GUAVA_ANNOTATIONS_VERSION = "r03"

HAMCREST_VERSION = "1.3"

MOCKITO_VERSION = "5.8.0"

MOCKITO_INLINE_VERSION = "5.2.0"

SIMPLE_JSON_VERSION = "1.1.1"

COMMONS_IO_VERSION = "2.11.0"

JUNIT_VERSION = "4.13.2"

CBOR_VERSION = "0.7"

GSON_VERSION = "2.8.6"

SQLITE_VERSION = "3.45.0.0"

AUTO_VALUE_VERSION = "1.7.4"

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "org.apache.avro:avro:" + APACHE_AVRO_VERSION,
        "org.apache.beam:beam-sdks-java-core:" + APACHE_BEAM_VERSION,
        "org.apache.beam:beam-model-pipeline:" + APACHE_BEAM_VERSION,
        "org.apache.beam:beam-runners-direct-java:" + APACHE_BEAM_VERSION,
        "commons-io:commons-io:" + COMMONS_IO_VERSION,
        "org.mockito:mockito-core:" + MOCKITO_VERSION,
        "org.mockito:mockito-inline:" + MOCKITO_INLINE_VERSION,
        "com.googlecode.json-simple:json-simple:" + SIMPLE_JSON_VERSION,
        "com.google.truth:truth:1.4.4",
        "junit:junit:" + JUNIT_VERSION,
        "org.apache.beam:beam-vendor-guava-26_0-jre:" + APACHE_BEAM_VENDOR_GUAVA_VERSION,
        "com.google.guava:guava:" + GUAVA_VERSION,
        "com.google.guava:guava-annotations:" + GUAVA_ANNOTATIONS_VERSION,
        "org.hamcrest:hamcrest-core:" + HAMCREST_VERSION,
        "org.hamcrest:hamcrest-library:" + HAMCREST_VERSION,
        "co.nstant.in:cbor:" + CBOR_VERSION,
        "com.google.code.gson:gson:" + GSON_VERSION,
        "org.xerial:sqlite-jdbc:" + SQLITE_VERSION,
        "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
        "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
        "com.google.errorprone:error_prone_annotations:2.+",
        "org.powermock:powermock-module-junit4:2.0.9",
        "org.powermock:powermock-api-mockito2:2.0.9",
    ],
    generate_compat_repositories = True,
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)

load("@maven//:compat.bzl", "compat_repositories")

compat_repositories()
