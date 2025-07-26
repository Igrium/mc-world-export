package com.igrium.worldexport.util;

import lombok.Lombok;

public class ExceptionUtils {

    /**
     * Re-implementation of <code>Lombok.sneakyThrow</code> to call without Lombok at runtime.
     */
    public static RuntimeException sneakyThrow(Throwable t) {
        if (t == null) throw new NullPointerException("t");
        return ExceptionUtils.sneakyThrow0(t);
    }

    @SuppressWarnings("unchecked") // Intentionally abusing heap pollution to not check the exception
    private static <T extends Throwable> T sneakyThrow0(Throwable t) throws T {
        throw (T)t;
    }
}
