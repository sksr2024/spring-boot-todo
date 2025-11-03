package com.example.todo.controller.api.request;

import com.example.todo.service.task.TaskEntity;
import com.example.todo.service.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaskUpdateRequest(
        @NotBlank(message = "概要は必須です")
        @Size(max = 256, message = "概要は256文字以内で入力してください")
        String summary,

        String description,

        @NotBlank(message = "ステータスは必須です")
        @Pattern(regexp = "TODO|DOING|DONE", message = "ステータスはTODO, DOING, DONEのいずれかを選択してください")
        String status
) {
    public TaskEntity toEntity(long id) {
        return new TaskEntity(
                id,
                summary,
                description,
                TaskStatus.valueOf(status)
        );
    }
}
