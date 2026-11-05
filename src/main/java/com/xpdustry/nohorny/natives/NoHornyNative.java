// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.natives;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandleProxies;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.jspecify.annotations.Nullable;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

final class NoHornyNative {

    private static final @Nullable Bindings BINDINGS;
    private static final @Nullable Throwable FAILURE;

    static {
        Bindings bindings = null;
        Throwable failure = null;
        try {
            bindings = Bindings.create(load());
        } catch (final IOException | RuntimeException | LinkageError e) {
            failure = e;
        }
        BINDINGS = bindings;
        FAILURE = failure;
    }

    private NoHornyNative() {}

    static Bindings bindings() {
        if (BINDINGS == null) {
            throw new NativeException(
                    "The nohorny native library is unavailable for " + NoHornyNativeLookup.host(), FAILURE);
        }
        return BINDINGS;
    }

    static NativeException takeLastError() {
        final var bindings = bindings();
        final var error = bindings.errorGet().call();
        final var message = error.equals(MemorySegment.NULL)
                ? "Unknown native error"
                : error.reinterpret(Long.MAX_VALUE).getString(0);
        bindings.errorClear().call();
        return new NativeException(message, null);
    }

    private static SymbolLookup load() throws IOException {
        final var resource = NoHornyNativeLookup.resource(NoHornyNativeLookup.host());
        try (final var stream = NoHornyNative.class.getResourceAsStream("/" + resource)) {
            if (stream == null) {
                throw new UnsatisfiedLinkError("No native library bundled for " + NoHornyNativeLookup.host());
            }
            final var file =
                    Files.createTempFile("nohorny-", "-" + Path.of(resource).getFileName());
            file.toFile().deleteOnExit();
            Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
            return SymbolLookup.libraryLookup(file, Arena.global());
        }
    }

    // The downcall handles are hidden behind typed interfaces since they declare Throwable, even though
    //   downcalls only ever throw unchecked exceptions. MethodHandleProxies requires the interfaces to be public.

    @FunctionalInterface
    public interface ClassifierCreate {
        MemorySegment call(MemorySegment modelPath);
    }

    @FunctionalInterface
    public interface ClassifierClassify {
        int call(
                MemorySegment classifier,
                MemorySegment argbPixels,
                int width,
                int height,
                MemorySegment scores,
                int capacity);
    }

    @FunctionalInterface
    public interface ClassifierClose {
        void call(MemorySegment classifier);
    }

    @FunctionalInterface
    public interface ErrorGet {
        MemorySegment call();
    }

    @FunctionalInterface
    public interface ErrorClear {
        void call();
    }

    record Bindings(
            ClassifierCreate classifierCreate,
            ClassifierClassify classifierClassify,
            ClassifierClose classifierClose,
            ErrorGet errorGet,
            ErrorClear errorClear) {

        static Bindings create(final SymbolLookup lookup) {
            return new Bindings(
                    bind(
                            lookup,
                            "nh_classifier_create",
                            ClassifierCreate.class,
                            FunctionDescriptor.of(ADDRESS, ADDRESS)),
                    bind(
                            lookup,
                            "nh_classifier_classify",
                            ClassifierClassify.class,
                            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT)),
                    bind(lookup, "nh_classifier_close", ClassifierClose.class, FunctionDescriptor.ofVoid(ADDRESS)),
                    bind(lookup, "nh_error_get", ErrorGet.class, FunctionDescriptor.of(ADDRESS)),
                    bind(lookup, "nh_error_clear", ErrorClear.class, FunctionDescriptor.ofVoid()));
        }

        private static <T> T bind(
                final SymbolLookup lookup,
                final String symbol,
                final Class<T> type,
                final FunctionDescriptor descriptor) {
            final var handle = Linker.nativeLinker().downcallHandle(lookup.findOrThrow(symbol), descriptor);
            return MethodHandleProxies.asInterfaceInstance(type, handle);
        }
    }
}
