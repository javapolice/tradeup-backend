package com.tradeup.backend.product.support;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

/** Invalid JSON values shared by the DTO and HTTP boundary tests. */
public final class ProductPatchCases {
    private ProductPatchCases() {}

    public static Stream<Arguments> invalidValues() {
        return Stream.of(
                "{}", "null", "[]", "[{}]", "1", "1.5", "\"text\"", "true",
                "{\"title\":null}", "{\"title\":\"\"}", "{\"title\":\"  \\t\\n\"}",
                "{\"title\":\"" + "가".repeat(256) + "\"}",
                "{\"title\":123}", "{\"title\":false}", "{\"title\":[]}", "{\"title\":{}}",
                "{\"description\":\"" + "나".repeat(256) + "\"}",
                "{\"description\":123}", "{\"description\":true}",
                "{\"description\":[]}", "{\"description\":{}}",
                "{\"price\":null}", "{\"price\":-1}", "{\"price\":1.5}", "{\"price\":1.0}",
                "{\"price\":1e3}", "{\"price\":\"1000\"}", "{\"price\":true}",
                "{\"price\":[]}", "{\"price\":{}}",
                "{\"price\":9223372036854775808}", "{\"price\":-9223372036854775809}",
                "{\"unknown\":1}", "{\"title\":\"changed\",\"sellerId\":1}",
                "{\"title\":\"changed\",\"status\":\"SOLD\"}",
                "{\"title\":\"changed\",\"description\":null,\"price\":-1}"
        ).map(Arguments::of);
    }
}
