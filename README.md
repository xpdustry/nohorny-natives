# nohorny-natives

<p align="center">
  <a href="https://nohorny.xpdustry.com"><img src=".github/nohorny-natives.svg" alt="nohorny-natives logo" width="740"></a>
</p>

The image classifier of [NoHorny](https://github.com/xpdustry/nohorny).
It runs ViT models in the ONNX format with OpenCV, and Java calls it through the Foreign Function and Memory API.

The jar includes the library for:

- `linux-x86_64` and `linux-aarch64`, with glibc 2.28 or later
- `windows-x86_64`
- `macos-aarch64`, with macOS 11 or later

## Usage

Requires Java 25 or later.

```kotlin
repositories {
    maven { url = uri("https://maven.xpdustry.com/releases") }
}

dependencies {
    implementation("com.xpdustry:nohorny-natives:0.1.0")
}
```

```java
try (final var classifier = NativeClassifier.create(Path.of("model.onnx"))) {
    final float[] probabilities = classifier.classify(image);
}
```

`classify` returns one probability per label, in the order of the model outputs.
A classifier is not thread-safe, so use one per thread.

Add `--enable-native-access=ALL-UNNAMED` to the JVM arguments, or `Enable-Native-Access: ALL-UNNAMED` to the manifest of an executable jar.
Otherwise the JVM prints a warning when it loads the library.

## Build

Requires Java 25, CMake 3.25 or later, and a C++20 compiler.

| Command                   | Does                                                               |
|---------------------------|--------------------------------------------------------------------|
| `./gradlew build`         | Builds the jar in `build/libs/`.                                   |
| `./gradlew cmakeBuild`    | Builds the library of your platform in `build/generated/natives/`. |
| `./gradlew spotlessApply` | Formats the code.                                                  |

The first build compiles OpenCV, which takes about 10 minutes.

A local jar only includes the library of your platform.
CI builds every platform on its own runner, then bundles them with `-Pprebuilt_natives=<dir>`.
Publishing fails if a platform is missing.

## Release

Run the **Release** workflow on `master`.
It builds every platform, publishes the jar to Maven, creates a GitHub release with generated notes, then bumps the minor version.
