// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.natives;

import java.util.List;
import java.util.Locale;

/// Where the natives live in the jar, shared with the Gradle build through build-logic.
public final class NoHornyNativeLookup {

    public static final List<String> PLATFORMS =
            List.of("linux-x86_64", "linux-aarch64", "windows-x86_64", "macos-aarch64");

    private NoHornyNativeLookup() {}

    public static String host() {
        final var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        final var arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        final String platformOs;
        if (os.contains("win")) {
            platformOs = "windows";
        } else if (os.contains("mac")) {
            platformOs = "macos";
        } else {
            platformOs = "linux";
        }
        final var platformArch = arch.equals("amd64") || arch.equals("x86_64") ? "x86_64" : arch;
        return platformOs + "-" + platformArch;
    }

    public static String directory(final String platform) {
        return "com/xpdustry/nohorny/natives/" + platform;
    }

    public static String resource(final String platform) {
        final String file;
        if (platform.startsWith("windows-")) {
            file = "nohorny.dll";
        } else if (platform.startsWith("macos-")) {
            file = "libnohorny.dylib";
        } else {
            file = "libnohorny.so";
        }
        return directory(platform) + "/" + file;
    }
}
