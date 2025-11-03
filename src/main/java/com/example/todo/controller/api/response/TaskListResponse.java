package com.example.todo.controller.api.response;

import java.util.List;

public record TaskListResponse (
        List<TaskResponse> tasks,
        int totalCount,
        String summary,
        List<String> status
){
}
