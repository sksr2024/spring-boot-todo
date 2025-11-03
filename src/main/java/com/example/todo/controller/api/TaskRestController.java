package com.example.todo.controller.api;

import com.example.todo.controller.api.request.TaskCreateRequest;
import com.example.todo.controller.api.request.TaskUpdateRequest;
import com.example.todo.controller.api.response.TaskListResponse;
import com.example.todo.controller.api.response.TaskResponse;
import com.example.todo.controller.task.TaskNotFoundException;
import com.example.todo.service.task.TaskSearchEntity;
import com.example.todo.service.task.TaskService;
import com.example.todo.service.task.TaskStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("api/tasks")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost5173"})
@RequiredArgsConstructor
public class TaskRestController {
    private final TaskService taskService;

    /**
     * タスク一覧(検索対応)
     */
    @GetMapping
    public ResponseEntity<TaskListResponse> list(
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) List<String> status
    ){
        // ステータスリストをEnumに変換
        var statusList = Optional.ofNullable(status)
                .map(list -> list.stream()
                        .map(TaskStatus::valueOf)
                        .toList())
                .orElse(List.of());

        var searchEntity = new TaskSearchEntity(summary, statusList);
        var tasks = taskService.find(searchEntity);

        var taskResponses = tasks.stream()
                .map(TaskResponse::from)
                .toList();

        var response = new TaskListResponse(
                taskResponses,
                taskResponses.size(),
                summary,
                status
        );

        return ResponseEntity.ok(response);
    }

    /**
     * タスク詳細取得
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> detail(@PathVariable long id){
        var task = taskService.findById(id)
                .map(TaskResponse::from)
                .orElseThrow(TaskNotFoundException::new);

        return ResponseEntity.ok(task);
    }

    /**
     * タスク新規作成
     */
    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request){
        var entity = request.toEntity();
        taskService.create(entity);

        // 作成されたタスクを返す
        var created = TaskResponse.from(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(
            @PathVariable long id,
            @Valid @RequestBody TaskUpdateRequest request
            ){
        // タスクの存在確認
        taskService.findById(id)
                .orElseThrow(TaskNotFoundException::new);

        var entity = request.toEntity(id);
        taskService.update(entity);

        var updated = TaskResponse.from(entity);
        return ResponseEntity.ok(updated);
    }

    /**
     * タスク削除
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id){
        // タスクの存在確認
        taskService.findById(id)
                .orElseThrow(TaskNotFoundException::new);

        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
