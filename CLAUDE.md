# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案背景

Laravel 12 序號管理系統移植至 Spring Boot 4.0.2 + Java 25。原始 Laravel 專案位於 `C:\project\serial`，可參照對應邏輯。

## 常用指令

```powershell
# 啟動（含熱重載，Spring Boot DevTools 已啟用）
.\mvnw spring-boot:run

# 重新編譯後啟動
.\mvnw clean spring-boot:run

# 打包
.\mvnw clean package
```

目前無測試。Java 原始碼更動會觸發 DevTools 自動重啟；Thymeleaf 模板更動（`spring.thymeleaf.cache=false`）無需重啟，直接重新整理頁面即可。

## 架構概覽

```
filter/ApiLoggerFilter        → OncePerRequestFilter，攔截所有 /api/* 請求並寫入 serial_log
controller/api/SerialController     → 4 個 REST API 端點（POST）
controller/admin/SerialAdminController → 後台列表 + CSV 匯出
service/SerialService         → 核心業務邏輯
repository/                   → Spring Data JPA（SerialDetail 含 JpaSpecificationExecutor）
model/                        → 3 個 JPA Entity（serial_activity / serial_detail / serial_log）
dto/                          → 4 個請求 DTO（@Valid 驗證）
templates/admin/serials/      → index.html（主要）、index_test.html（RWD 客製化分頁）
```

## 關鍵設計決策

### Spring Boot 4.x / Spring Framework 7 特有注意事項
- Jackson 套件路徑為 `tools.jackson`（非 `com.fasterxml.jackson`），`ObjectMapper` 的 import 必須用 `tools.jackson.databind.ObjectMapper`
- HTTP 422 常數為 `HttpStatus.UNPROCESSABLE_CONTENT`（`UNPROCESSABLE_ENTITY` 已在 Spring 7 廢棄）
- `ContentCachingRequestWrapper` 建構子需第二個參數 `contentCacheLimit`：`new ContentCachingRequestWrapper(request, 1024 * 1024)`
- Lombok 需在 `maven-compiler-plugin` 中明確設定 `<annotationProcessorPaths>`，否則 Java 25 下 getter/setter 不會生成

### 業務邏輯
- **核銷悲觀鎖**：`SerialDetailRepository.findByContentWithLock()` 使用 `@Lock(PESSIMISTIC_WRITE)`，必須在 `@Transactional` 方法內呼叫
- **批次註銷逐筆 Transaction**：`SerialService.cancelSerials()` 使用 `TransactionTemplate`（PROPAGATION_REQUIRES_NEW），每筆序號各自獨立，失敗不連帶影響其他筆
- **序號生成去重**：`generateUniqueSerials()` 批次向 DB 確認（`findExistingContents`），非逐筆查詢

### Controller 層
- **分頁索引轉換**：Spring Page 為 0-indexed，Controller 接收 1-indexed 的 `page` 參數後以 `Math.max(0, page - 1)` 轉換
- **驗證錯誤排序**：`buildValidationError()` 透過 `getDeclaredFields()` Reflection 取得 DTO 欄位宣告順序，確保 `errors` 輸出順序一致
- **補充驗證**（日期格式、DB unique/exists 等）在 `@Valid` 通過後，於 Controller 手動執行並回傳 422

### 前端模板
- 分頁連結使用 `data-page` + JavaScript（`getCleanParamsArray`），而非 Thymeleaf `@{...}` URL，確保切換分頁時 URL 不帶空值參數
- 日期顯示使用 `#temporals.format(item.updatedAt, 'yyyy-MM-dd HH:mm:ss')`；CSV 匯出使用 `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")`

### 其他
- 時區在 `SerialApplication.@PostConstruct` 設定為 `Asia/Taipei`
- `ApiLoggerFilter` 將本機 IPv6 loopback（`::1` / `0:0:0:0:0:0:0:1`）正規化為 `127.0.0.1`
- JSON 全域 SNAKE_CASE：`spring.jackson.property-naming-strategy=SNAKE_CASE`（`application.properties`）
- `SerialDetail.updatedAt` 由業務邏輯手動管理：insert 時為 `null`，核銷/註銷時才設值
