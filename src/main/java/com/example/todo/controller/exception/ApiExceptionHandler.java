package com.example.todo.controller.exception;

import com.example.todo.controller.api.response.ErrorResponse;
import com.example.todo.controller.task.TaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * バリデーションエラー
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(
            MethodArgumentNotValidException ex
    ){
        var errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> new ErrorResponse.FieldError(
                        e.getField(),
                        e.getDefaultMessage()
                ))
                .collect(Collectors.toList());

        var errorResponse = new ErrorResponse(
                "VALIDATION_ERROR",
                "入力値が不正です",
                errors
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * タスクが見つからない
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TaskNotFoundException ex){
        var errorResponse = new ErrorResponse(
                "TASK_NOT_FOUND",
                "タスクが見つかりません",
                null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * その他の予期しないエラー
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralError(Exception ex){
        var errorResponse = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "サーバーエラーが発生しました",
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
