package com.example.todo.controller.api.response;

import java.util.List;

public record ErrorResponse(
        String errorCode,
        String message,
        List<FieldError> details
) {
    public record FieldError(
            String field,
            String message
    ){}
}
