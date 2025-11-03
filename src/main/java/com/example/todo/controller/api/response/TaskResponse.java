package com.example.todo.controller.api.response;

import com.example.todo.service.task.TaskEntity;

public record TaskResponse (
        long id,
        String summary,
        String description,
        String status
){
    public static TaskResponse from(TaskEntity entity){
        return new TaskResponse(
                entity.id(),
                entity.summary(),
                entity.description(),
                entity.status().name()
        );
    }
}
