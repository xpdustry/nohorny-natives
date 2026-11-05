// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.natives;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

public final class NativeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NativeException(final String message, final @Nullable Throwable cause) {
        super(message, cause);
    }
}
