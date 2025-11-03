# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.1.2 TODO管理アプリケーション（日本語プロジェクト）
- Java 17, Gradle 7.6.1
- MyBatis 3.0.2 (ORM)
- Thymeleaf + Bootstrap 5.2.3 (UI)
- H2 in-memory database

## Build and Run Commands

```bash
# Build
./gradlew clean build

# Run application (http://localhost:8080)
./gradlew bootRun

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests TodoApplicationTests
```

## Architecture

**3層アーキテクチャ:** Controller → Service → Repository → Database

### Key Layers

**Controller層** (`controller/task/`)
- `TaskController.java`: Thymeleaf MVCコントローラー（全CRUD操作）
- DTOs: `TaskForm`（作成/編集）, `TaskSearchForm`（検索条件）, `TaskDTO`（表示用）
- RESTful routing: `/tasks`, `/tasks/{id}`, `/tasks/creationForm`, `/tasks/{id}/editForm`

**Service層** (`service/task/`)
- `TaskService.java`: ビジネスロジック、`@Transactional`管理
- `TaskEntity.java`: ドメインモデル（Record型）
- `TaskStatus.java`: Enum（TODO, DOING, DONE）

**Repository層** (`repository/task/`)
- `TaskRepository.java`: MyBatisマッパー（`@Mapper`）
- 動的SQL: `<if>`タグで検索条件を動的に構築
- LIKE検索（概要の部分一致）+ IN句（ステータスフィルター）

### Data Flow Example

```
TaskForm → TaskController.create()
         → TaskService.create(TaskEntity)
         → TaskRepository.insert()
         → H2 Database
```

## Database

**H2インメモリDB（開発用）**
- 起動時に`schema.sql`→`data.sql`を自動実行
- H2コンソール: `http://localhost:8080/h2-console`
- テーブル: `tasks(id, summary, description, status)`

## Key Technologies & Patterns

### MyBatis Dynamic SQL
Repository層で動的SQL実装：
``` java
@Select("""
    <script>
      SELECT ... FROM tasks
      <where>
        <if test='condition.summary != null and !condition.summary.isBlank()'>
          summary LIKE CONCAT('%', #{condition.summary}, '%')
        </if>
        <if test='condition.status != null and !condition.status.isEmpty()'>
          AND status IN (<foreach ...>)
        </if>
      </where>
    </script>
""")
```

### Record-based DTOs
Java 17のRecordを積極活用（`TaskEntity`, `TaskForm`, `TaskDTO`等）

### Transaction Management
Service層の更新系メソッドに`@Transactional`
- `create()`, `update()`, `delete()`: トランザクション管理
- `find()`, `findById()`: 読み取り専用（トランザクション不要）

### Validation
Jakarta Validationアノテーション使用：
- `@NotBlank`, `@Size(max=256)`, `@Pattern(regexp="TODO|DOING|DONE")`
- Thymeleaf側で`th:errors`によるエラー表示

## REST API Migration

`REST_API_MIGRATION_GUIDE.md`に詳細なREST API化ガイドあり：
- `/api/tasks`エンドポイント設計
- `@RestController`実装例
- Request/Response DTO（JSON用）
- CORS設定、エラーハンドリング
- React/Vue連携サンプルコード

### 並行稼働戦略
既存Thymeleaf版とREST API版を並行稼働可能：
- Thymeleaf: `/tasks` → `TaskController`
- REST API: `/api/tasks` → `TaskRestController`（新規作成）

## Git Commit Rules

**日本語で記述、Prefix必須：**
- `feat:` 新しい機能
- `fix:` バグの修正
- `docs:` ドキュメントのみの変更
- `style:` 空白、フォーマット、セミコロン追加など
- `refactor:` 仕様に影響がないコード改善
- `perf:` パフォーマンス向上関連
- `test:` テスト関連
- `chore:` ビルド、補助ツール、ライブラリ関連

例: `feat: 検索内容を保持するように実装`

## Important Notes

### Language
- コードコメント、コミットメッセージ、ドキュメント: **日本語**
- Java識別子（変数名、メソッド名）: 英語

### No application.properties
Spring Bootのデフォルト設定を使用（設定ファイルなし）

### Hot Reload
Spring DevToolsが有効（`developmentOnly`依存関係）
- コード変更時に自動再起動

### Testing
現在は基本的なコンテキストロードテストのみ（`TodoApplicationTests.java`）
- 今後の拡張: Controller層の統合テスト、Service層の単体テスト推奨
