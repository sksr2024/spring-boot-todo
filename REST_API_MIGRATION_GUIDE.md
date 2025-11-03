# Spring Boot TODO アプリケーション REST API 化ガイド

このドキュメントでは、現在のThymeleafベースのTODOアプリケーションをREST API化し、React等のフロントエンドフレームワークから利用できるようにする方法を解説します。

## 目次

1. [現在のアーキテクチャ](#現在のアーキテクチャ)
2. [REST APIエンドポイント設計](#rest-apiエンドポイント設計)
3. [必要な変更点](#必要な変更点)
4. [追加推奨機能](#追加推奨機能)
5. [プロジェクト構造](#プロジェクト構造)
6. [フロントエンド連携](#フロントエンド連携)
7. [実装の優先順位](#実装の優先順位)
8. [段階的な移行戦略](#段階的な移行戦略)

---

## 現在のアーキテクチャ

現在のプロジェクト構成：

- **Controller**: `@Controller` + Thymeleaf（HTMLレスポンス）
- **Service**: ビジネスロジック層（**変更不要**）
- **Repository**: MyBatisデータアクセス層（**変更不要**）
- **DTO/Form**: Thymeleafバインディング用

### 変更が必要な箇所
- ✅ コントローラ層（`@RestController`に変更）
- ✅ DTO層（JSON用のRequest/Response作成）
- ✅ CORS設定
- ✅ エラーハンドリング

### 変更不要な箇所
- ✅ Service層（ビジネスロジック）
- ✅ Repository層（データアクセス）
- ✅ Entity/Enum（ドメインモデル）

---

## REST APIエンドポイント設計

### エンドポイント一覧

| HTTPメソッド | エンドポイント | 説明 | リクエストボディ | レスポンス |
|------------|---------------|------|----------------|-----------|
| `GET` | `/api/tasks` | タスク一覧取得（検索対応） | - | `TaskListResponse` |
| `GET` | `/api/tasks/{id}` | タスク詳細取得 | - | `TaskResponse` |
| `POST` | `/api/tasks` | タスク新規作成 | `TaskCreateRequest` | `TaskResponse` |
| `PUT` | `/api/tasks/{id}` | タスク更新 | `TaskUpdateRequest` | `TaskResponse` |
| `DELETE` | `/api/tasks/{id}` | タスク削除 | - | `204 No Content` |

### クエリパラメータ（GET /api/tasks）

- `?summary=keyword` - 概要検索（部分一致）
- `?status=TODO,DOING` - ステータスフィルター（カンマ区切りで複数指定可能）

### レスポンス例

#### タスク一覧取得

**リクエスト:**
```http
GET /api/tasks?summary=Spring&status=TODO,DOING
```

**レスポンス:**
```json
{
  "tasks": [
    {
      "id": 1,
      "summary": "Spring Bootを学ぶ",
      "description": "TODOアプリを作る",
      "status": "DONE"
    },
    {
      "id": 2,
      "summary": "Spring Securityを学ぶ",
      "description": "ログイン機能を作る",
      "status": "TODO"
    }
  ],
  "totalCount": 2,
  "searchCondition": {
    "summary": "Spring",
    "status": ["TODO", "DOING"]
  }
}
```

#### タスク作成

**リクエスト:**
```http
POST /api/tasks
Content-Type: application/json

{
  "summary": "REST APIを実装する",
  "description": "Reactから利用できるようにする",
  "status": "TODO"
}
```

**レスポンス:**
```json
{
  "id": 3,
  "summary": "REST APIを実装する",
  "description": "Reactから利用できるようにする",
  "status": "TODO"
}
```

#### エラーレスポンス

**バリデーションエラー:**
```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "入力値が不正です",
  "details": [
    {
      "field": "summary",
      "message": "256文字以内で入力してください"
    }
  ]
}
```

**タスクが見つからない:**
```json
{
  "errorCode": "TASK_NOT_FOUND",
  "message": "タスクが見つかりません",
  "details": null
}
```

---

## 必要な変更点

### 1. コントローラ層の変更

#### 現在（TaskController.java）

```java
@Controller
@RequestMapping("/tasks")
public class TaskController {
    @GetMapping
    public String list(TaskSearchForm searchForm, Model model) {
        // Thymeleafビューを返す
        return "tasks/list";
    }
}
```

#### 変更後（TaskRestController.java - 新規作成）

**ファイルパス:** `src/main/java/com/example/todo/controller/task/api/TaskRestController.java`

```java
package com.example.todo.controller.task.api;

import com.example.todo.controller.task.api.request.TaskCreateRequest;
import com.example.todo.controller.task.api.request.TaskUpdateRequest;
import com.example.todo.controller.task.api.response.TaskListResponse;
import com.example.todo.controller.task.api.response.TaskResponse;
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
@RequestMapping("/api/tasks")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@RequiredArgsConstructor
public class TaskRestController {

    private final TaskService taskService;

    /**
     * タスク一覧取得（検索対応）
     */
    @GetMapping
    public ResponseEntity<TaskListResponse> list(
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) List<String> status
    ) {
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
    public ResponseEntity<TaskResponse> detail(@PathVariable Long id) {
        var task = taskService.findById(id)
                .map(TaskResponse::from)
                .orElseThrow(TaskNotFoundException::new);

        return ResponseEntity.ok(task);
    }

    /**
     * タスク新規作成
     */
    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
        var entity = request.toEntity();
        taskService.create(entity);

        // 作成されたタスクを返す（IDは自動採番されるため再取得が必要な場合がある）
        var created = TaskResponse.from(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * タスク更新
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TaskUpdateRequest request
    ) {
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
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // タスクの存在確認
        taskService.findById(id)
                .orElseThrow(TaskNotFoundException::new);

        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

### 2. レスポンス/リクエストDTOの作成

#### TaskResponse.java（レスポンス用）

**ファイルパス:** `src/main/java/com/example/todo/controller/task/api/response/TaskResponse.java`

```java
package com.example.todo.controller.task.api.response;

import com.example.todo.service.task.TaskEntity;

public record TaskResponse(
        Long id,
        String summary,
        String description,
        String status
) {
    public static TaskResponse from(TaskEntity entity) {
        return new TaskResponse(
                entity.id(),
                entity.summary(),
                entity.description(),
                entity.status().name()
        );
    }
}
```

#### TaskListResponse.java（一覧レスポンス用）

**ファイルパス:** `src/main/java/com/example/todo/controller/task/api/response/TaskListResponse.java`

```java
package com.example.todo.controller.task.api.response;

import java.util.List;

public record TaskListResponse(
        List<TaskResponse> tasks,
        int totalCount,
        String summary,
        List<String> status
) {}
```

#### TaskCreateRequest.java（作成リクエスト用）

**ファイルパス:** `src/main/java/com/example/todo/controller/task/api/request/TaskCreateRequest.java`

```java
package com.example.todo.controller.task.api.request;

import com.example.todo.service.task.TaskEntity;
import com.example.todo.service.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaskCreateRequest(
        @NotBlank(message = "概要は必須です")
        @Size(max = 256, message = "概要は256文字以内で入力してください")
        String summary,

        String description,

        @NotBlank(message = "ステータスは必須です")
        @Pattern(regexp = "TODO|DOING|DONE", message = "ステータスはTODO, DOING, DONEのいずれかを選択してください")
        String status
) {
    public TaskEntity toEntity() {
        return new TaskEntity(
                null,
                summary,
                description,
                TaskStatus.valueOf(status)
        );
    }
}
```

#### TaskUpdateRequest.java（更新リクエスト用）

**ファイルパス:** `src/main/java/com/example/todo/controller/task/api/request/TaskUpdateRequest.java`

```java
package com.example.todo.controller.task.api.request;

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
    public TaskEntity toEntity(Long id) {
        return new TaskEntity(
                id,
                summary,
                description,
                TaskStatus.valueOf(status)
        );
    }
}
```

#### ErrorResponse.java（エラーレスポンス用）

**ファイルパス:** `src/main/java/com/example/todo/controller/task/api/response/ErrorResponse.java`

```java
package com.example.todo.controller.task.api.response;

import java.util.List;

public record ErrorResponse(
        String errorCode,
        String message,
        List<FieldError> details
) {
    public record FieldError(
            String field,
            String message
    ) {}
}
```

---

### 3. エラーハンドリング

#### ApiExceptionHandler.java

**ファイルパス:** `src/main/java/com/example/todo/controller/exception/ApiExceptionHandler.java`

```java
package com.example.todo.controller.exception;

import com.example.todo.controller.task.TaskNotFoundException;
import com.example.todo.controller.task.api.response.ErrorResponse;
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
    ) {
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

        return ResponseEntity
                .badRequest()
                .body(errorResponse);
    }

    /**
     * タスクが見つからない
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TaskNotFoundException ex) {
        var errorResponse = new ErrorResponse(
                "TASK_NOT_FOUND",
                "タスクが見つかりません",
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    /**
     * その他の予期しないエラー
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralError(Exception ex) {
        var errorResponse = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "サーバーエラーが発生しました",
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
}
```

#### TaskNotFoundException.java（修正）

既存のファイルはそのまま使用できます。

**ファイルパス:** `src/main/java/com/example/todo/controller/task/TaskNotFoundException.java`

```java
package com.example.todo.controller.task;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TaskNotFoundException extends RuntimeException {
}
```

---

### 4. CORS設定

#### WebConfig.java

**ファイルパス:** `src/main/java/com/example/todo/config/WebConfig.java`

```java
package com.example.todo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:3000",  // React開発サーバー
                        "http://localhost:5173"   // Vite開発サーバー
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

---

## 追加推奨機能

### 1. Swagger/OpenAPI（API仕様書自動生成）

#### build.gradleへの追加

```gradle
dependencies {
    // 既存の依存関係...

    // OpenAPI/Swagger
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
}
```

#### アクセスURL

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

### 2. ページネーション対応

#### ページネーション用のレスポンス

```java
public record PagedTaskResponse(
        List<TaskResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
```

#### コントローラの修正例

```java
@GetMapping
public ResponseEntity<PagedTaskResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String summary,
        @RequestParam(required = false) List<String> status
) {
    // ページネーション実装
    // MyBatisでLIMIT/OFFSETを使用
}
```

---

## プロジェクト構造

### 変更後のフォルダ構成

```
src/main/java/com/example/todo/
├── TodoApplication.java
├── config/                              # 新規
│   └── WebConfig.java                   # CORS設定
├── controller/
│   ├── IndexController.java             # 既存（残す場合）
│   ├── exception/                       # 新規
│   │   └── ApiExceptionHandler.java     # エラーハンドリング
│   └── task/
│       ├── api/                         # 新規: REST API用
│       │   ├── TaskRestController.java
│       │   ├── request/
│       │   │   ├── TaskCreateRequest.java
│       │   │   └── TaskUpdateRequest.java
│       │   └── response/
│       │       ├── TaskResponse.java
│       │       ├── TaskListResponse.java
│       │       └── ErrorResponse.java
│       ├── web/                         # 既存: Thymeleaf用（残す場合）
│       │   ├── TaskController.java
│       │   ├── TaskForm.java
│       │   ├── TaskDTO.java
│       │   ├── TaskSearchForm.java
│       │   └── TaskSearchDTO.java
│       └── TaskNotFoundException.java    # 既存（共通利用）
├── service/task/                        # 変更不要
│   ├── TaskService.java
│   ├── TaskEntity.java
│   ├── TaskSearchEntity.java
│   └── TaskStatus.java
└── repository/task/                     # 変更不要
    └── TaskRepository.java
```

---

## フロントエンド連携

### React + TypeScript の例

#### 1. APIクライアントの作成

**ファイル:** `src/api/client.ts`

```typescript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

// レスポンスインターセプター（エラーハンドリング）
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      // サーバーからのエラーレスポンス
      console.error('API Error:', error.response.data);
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

#### 2. タスクAPI関数

**ファイル:** `src/api/taskApi.ts`

```typescript
import apiClient from './client';

// 型定義
export interface Task {
  id: number;
  summary: string;
  description: string;
  status: 'TODO' | 'DOING' | 'DONE';
}

export interface TaskListResponse {
  tasks: Task[];
  totalCount: number;
  summary?: string;
  status?: string[];
}

export interface TaskCreateRequest {
  summary: string;
  description?: string;
  status: 'TODO' | 'DOING' | 'DONE';
}

export interface TaskUpdateRequest extends TaskCreateRequest {}

export interface SearchParams {
  summary?: string;
  status?: string[];
}

// API関数
export const taskApi = {
  // タスク一覧取得
  async fetchTasks(params?: SearchParams): Promise<TaskListResponse> {
    const queryParams = new URLSearchParams();
    if (params?.summary) {
      queryParams.append('summary', params.summary);
    }
    if (params?.status && params.status.length > 0) {
      queryParams.append('status', params.status.join(','));
    }

    const response = await apiClient.get<TaskListResponse>(
      `/tasks?${queryParams.toString()}`
    );
    return response.data;
  },

  // タスク詳細取得
  async fetchTask(id: number): Promise<Task> {
    const response = await apiClient.get<Task>(`/tasks/${id}`);
    return response.data;
  },

  // タスク作成
  async createTask(request: TaskCreateRequest): Promise<Task> {
    const response = await apiClient.post<Task>('/tasks', request);
    return response.data;
  },

  // タスク更新
  async updateTask(id: number, request: TaskUpdateRequest): Promise<Task> {
    const response = await apiClient.put<Task>(`/tasks/${id}`, request);
    return response.data;
  },

  // タスク削除
  async deleteTask(id: number): Promise<void> {
    await apiClient.delete(`/tasks/${id}`);
  },
};
```

#### 3. React Queryを使ったフック

**ファイル:** `src/hooks/useTasks.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { taskApi, SearchParams, TaskCreateRequest } from '../api/taskApi';

// タスク一覧取得
export const useTasks = (searchParams?: SearchParams) => {
  return useQuery({
    queryKey: ['tasks', searchParams],
    queryFn: () => taskApi.fetchTasks(searchParams),
  });
};

// タスク詳細取得
export const useTask = (id: number) => {
  return useQuery({
    queryKey: ['task', id],
    queryFn: () => taskApi.fetchTask(id),
  });
};

// タスク作成
export const useCreateTask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: taskApi.createTask,
    onSuccess: () => {
      // キャッシュを無効化して再取得
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
};

// タスク更新
export const useUpdateTask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: TaskCreateRequest }) =>
      taskApi.updateTask(id, request),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      queryClient.invalidateQueries({ queryKey: ['task', variables.id] });
    },
  });
};

// タスク削除
export const useDeleteTask = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: taskApi.deleteTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
};
```

#### 4. コンポーネント例

**ファイル:** `src/components/TaskList.tsx`

```typescript
import React, { useState } from 'react';
import { useTasks, useDeleteTask } from '../hooks/useTasks';

export const TaskList: React.FC = () => {
  const [searchSummary, setSearchSummary] = useState('');
  const [searchStatus, setSearchStatus] = useState<string[]>([]);

  const { data, isLoading, error } = useTasks({
    summary: searchSummary,
    status: searchStatus,
  });

  const deleteTask = useDeleteTask();

  const handleDelete = async (id: number) => {
    if (window.confirm('タスクを削除しますか？')) {
      await deleteTask.mutateAsync(id);
    }
  };

  if (isLoading) return <div>読み込み中...</div>;
  if (error) return <div>エラーが発生しました</div>;

  return (
    <div>
      <h1>タスク一覧</h1>

      {/* 検索フォーム */}
      <div>
        <input
          type="text"
          placeholder="概要で検索"
          value={searchSummary}
          onChange={(e) => setSearchSummary(e.target.value)}
        />
        {/* ステータスチェックボックス */}
      </div>

      {/* タスク一覧 */}
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>概要</th>
            <th>ステータス</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {data?.tasks.map((task) => (
            <tr key={task.id}>
              <td>{task.id}</td>
              <td>{task.summary}</td>
              <td>{task.status}</td>
              <td>
                <button onClick={() => handleDelete(task.id)}>削除</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
```

---

### Vue 3 + TypeScript の例

#### Pinia Store

**ファイル:** `src/stores/taskStore.ts`

```typescript
import { defineStore } from 'pinia';
import { ref } from 'vue';
import axios from 'axios';

const API_BASE = 'http://localhost:8080/api';

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<Task[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function fetchTasks(params?: SearchParams) {
    loading.value = true;
    error.value = null;

    try {
      const queryParams = new URLSearchParams();
      if (params?.summary) queryParams.append('summary', params.summary);
      if (params?.status) queryParams.append('status', params.status.join(','));

      const response = await axios.get(`${API_BASE}/tasks?${queryParams}`);
      tasks.value = response.data.tasks;
    } catch (e) {
      error.value = 'タスクの取得に失敗しました';
    } finally {
      loading.value = false;
    }
  }

  async function createTask(request: TaskCreateRequest) {
    const response = await axios.post(`${API_BASE}/tasks`, request);
    tasks.value.push(response.data);
    return response.data;
  }

  async function deleteTask(id: number) {
    await axios.delete(`${API_BASE}/tasks/${id}`);
    tasks.value = tasks.value.filter((t) => t.id !== id);
  }

  return { tasks, loading, error, fetchTasks, createTask, deleteTask };
});
```

---

## 実装の優先順位

| 優先度 | 実装内容 | 理由 |
|-------|---------|------|
| 🔴 **必須** | `@RestController`への変更 | REST API化の基本 |
| 🔴 **必須** | JSON用のRequest/ResponseDTO作成 | データ送受信 |
| 🔴 **必須** | CORS設定 | フロントエンドとの通信 |
| 🔴 **必須** | `ApiExceptionHandler`作成 | エラーハンドリング |
| 🟡 **推奨** | Swagger/OpenAPI導入 | API仕様書自動生成 |
| 🟡 **推奨** | ページネーション | パフォーマンス向上 |
| 🟢 **オプション** | Spring Security導入 | 認証・認可（本格運用時） |

---

## 段階的な移行戦略

### Phase 1: REST APIの並行稼働

- 既存の`TaskController`（Thymeleaf）は残す
- `/api/tasks`に新しい`TaskRestController`を追加
- 両方が同時に動作する状態を維持

**メリット:**
- 既存機能に影響を与えない
- 段階的にテストできる

### Phase 2: フロントエンド開発

- React/Vue等でフロントエンドを開発
- `http://localhost:8080/api`にアクセス
- REST APIの動作確認

### Phase 3: 完全移行

- フロントエンドが完成したらThymeleaf部分を削除
- 以下を削除可能:
  - `src/main/resources/templates/`
  - `TaskController.java`（Thymeleaf版）
  - `build.gradle`の`spring-boot-starter-thymeleaf`依存関係

---

## テスト方法

### cURLでのテスト

#### タスク一覧取得
```bash
curl -X GET "http://localhost:8080/api/tasks?summary=Spring&status=TODO"
```

#### タスク作成
```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "summary": "REST APIのテスト",
    "description": "cURLでテスト",
    "status": "TODO"
  }'
```

#### タスク更新
```bash
curl -X PUT http://localhost:8080/api/tasks/1 \
  -H "Content-Type: application/json" \
  -d '{
    "summary": "更新されたタスク",
    "description": "更新テスト",
    "status": "DOING"
  }'
```

#### タスク削除
```bash
curl -X DELETE http://localhost:8080/api/tasks/1
```

---

## まとめ

### 変更が必要な箇所

1. ✅ **コントローラ**: `@RestController`に変更、JSONレスポンス
2. ✅ **DTO**: Request/Response用の新しいRecord作成
3. ✅ **CORS設定**: フロントエンドからのアクセス許可
4. ✅ **エラーハンドリング**: JSON形式のエラーレスポンス

### 変更不要な箇所

- ✅ Service層（ビジネスロジック）
- ✅ Repository層（データアクセス）
- ✅ Entity/Enum（ドメインモデル）

### 追加推奨

- 📚 Swagger/OpenAPIでAPI仕様書自動生成
- 🔐 将来的にSpring Securityで認証・認可
- 📄 ページネーション対応
- 🧪 統合テスト（MockMvcやRestAssured）

---

この設計により、既存のビジネスロジックを保ちながら、モダンなフロントエンドフレームワークから利用可能なREST APIに移行できます。

**次のステップ:** 実装を始める場合は、まず`TaskRestController`と基本的なDTOクラスから作成することをお勧めします。
