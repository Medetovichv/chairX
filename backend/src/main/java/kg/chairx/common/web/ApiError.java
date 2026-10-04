package kg.chairx.common.web;

import java.util.Map;

public record ApiError(String code, String message, Map<String, ?> details) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }
}
