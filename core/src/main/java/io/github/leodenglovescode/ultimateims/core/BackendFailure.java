package io.github.leodenglovescode.ultimateims.core;

import java.util.concurrent.ExecutionException;

/** 仅公开固定阶段和异常类型，不输出异常中的订阅标识或配对密钥。 */
public final class BackendFailure {
    public static String describe(Throwable error) {
        while (error instanceof ExecutionException && error.getCause() != null) error = error.getCause();
        String message = error.getMessage();
        if (error instanceof IllegalStateException && message != null && message.matches(
                "Preparation\\[(client-lifetime|broker-initialization|delegation-preflight|instrumentation-connection|instrumentation-start|initial-read)\\]: (Framework operation failed: [A-Za-z]+|Permission denied or permission delegation occupied|Framework operation is unsupported on this build|Unsupported framework signature: [A-Za-z0-9_.$():, \\[\\]<>-]{1,256})")) return message;
        if (error instanceof NoSuchMethodException || error instanceof NoSuchFieldException || error instanceof ClassNotFoundException) {
            return "Unsupported framework signature: " + (message != null && message.matches("[A-Za-z0-9_.$():, \\[\\]<>-]{1,256}") ? message : error.getClass().getSimpleName());
        }
        if (error instanceof SecurityException) return "Permission denied or permission delegation occupied";
        if (error instanceof UnsupportedOperationException) return "Framework operation is unsupported on this build";
        return "Framework operation failed: " + error.getClass().getSimpleName();
    }
    private BackendFailure() { }
}
