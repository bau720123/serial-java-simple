# 序號管理系統 API 技術手冊

本文件根據最新邏輯編寫，定義了活動建立、序號追加與核心核銷流程。

## 📋 基礎資訊
- **Base URL**：http://localhost:8080/api
- **資料格式**：application/json
- **全域 Middleware**：api.logger

## 📋 序號管理後台
- **Base URL**：http://localhost:8080/admin/serials

---

## 技術棧

| 項目 | 版本 / 技術 |
|------|------------|
| 框架 | **Spring Boot 4.0.2** |
| Java | **Java 25** |
| Web 容器 | Tomcat / Servlet 6.1（Jakarta EE 11）|
| ORM | Spring Data JPA + Hibernate（JPA 3.2）|
| 模板引擎 | Thymeleaf（後台介面）|
| 驗證 | Bean Validation 3.1（jakarta.validation）|
| JSON | Jackson 3 |
| 資料庫 | SQL Server（mssql-jdbc）|
| CSV 匯出 | Apache Commons CSV 1.12.0 |
| 程式碼簡化 | Lombok |
| 測試 | JUnit 5（JUnit Jupiter，暫時沒用到）|

---

### 環境需求
- Java 25+ ✅
- Maven 3.9+
- SQL Server 2019+

### 1. Clone 專案
```bash
git clone <YOUR_REPOSITORY_URL>
cd serial-java-simple
```

### 2. 設定資料庫連線
編輯 `src/main/resources/application.properties`，填入實際的資料庫資訊：
```properties
spring.datasource.url=jdbc:sqlserver://<HOST>:1433;databaseName=<DB>;encrypt=true;trustServerCertificate=true
spring.datasource.username=<USERNAME>
spring.datasource.password=<PASSWORD>
```

### 3. 編譯與啟動
```powershell
.\mvnw clean spring-boot:run
```

---

## 🛠 API 介面詳細定義

### 1. 批次新增序號
- **路由名稱（Route Name）**：批次新增序號
- **Endpoint**：/serials_insert
- **Method**：POST
- **說明**：初始化活動並產出第一批隨機唯一序號。

#### 📥 Request Parameters（JSON）
| 欄位名稱 | 型態 | 必填 | 說明 |
| :--- | :--- | :--- | :--- |
| activity_name | String | 是 | 活動名稱 |
| activity_unique_id | String | 是 | 活動唯一識別碼（需全系統唯一） |
| start_date | DateTime| 是 | 生效日（格式: YYYY-MM-DD HH:mm:ss） |
| end_date | DateTime| 是 | 失效日（需晚於現在時間且晚於生效日） |
| quota | Integer | 是 | 產出筆數（範圍: 1 ~ 100） |

```json
{
    "activity_name": "春節序號大放送_4",
    "activity_unique_id": "spring_004",
    "start_date": "2026-01-28 16:00:00",
    "end_date": "2026-02-15 23:59:59",
    "quota": 100
}
```

#### 📤 Response（201 Created）
```json
{
    "status": "success",
    "message": "活動與序號已成功產生",
    "data": {
        "activity_id": 4, // 會返回該活動的流水ID
        "total_generated": 100 // 會返回總共產生的組數
    }
}
```

#### ❌ 錯誤回應範例（422 Unprocessable Entity）
```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "activity_name": [
            "活動名稱 為必填欄位。"
        ],
        "activity_unique_id": [
            "活動唯一識別碼 為必填欄位"
        ],
        "start_date": [
            "活動開始時間 為必填欄位"
        ],
        "end_date": [
            "活動結束時間 為必填欄位"
        ],
        "quota": [
            "序號配額 為必填欄位"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "start_date": [
            "活動開始時間 格式必須為 yyyy-MM-dd HH:mm:ss"
        ],
        "end_date": [
            "活動結束時間 格式必須為 yyyy-MM-dd HH:mm:ss"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "activity_unique_id": [
            "活動唯一識別碼 已存在，請勿重複新增"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "end_date": [
            "活動結束時間 必須晚於或等於 開始日期",
            "活動結束時間 不能早於當前時間，否則序號將立即過期"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "quota": [
            "序號配額 最小值為 1",
            "序號配額 最大值為 100"
        ]
    }
}
```

---

### 2. 批次追加序號
- **路由名稱（Route Name）**：批次追加序號
- **Endpoint**：/serials_additional_insert
- **Method**：POST
- **說明**：針對現有活動增發序號，並同步設定新的序號效期。

#### 📥 Request Parameters（JSON）
| 欄位名稱 | 型態 | 必填 | 說明 |
| :--- | :--- | :--- | :--- |
| activity_unique_id | String | 是 | 欲追加的活動識別碼（必須存在於資料庫） |
| start_date | DateTime| 是 | 更新後的生效日（YYYY-MM-DD HH:mm:ss） |
| end_date | DateTime| 是 | 更新後的失效日（YYYY-MM-DD HH:mm:ss） |
| quota | Integer | 是 | 本次額外追加的筆數（範圍: 1 ~ 100） |
| note | String | 是 | 追加備註說明 |

```json
{
    "activity_unique_id": "spring_001",
    "start_date": "2026-01-20 16:00:00",
    "end_date": "2026-02-28 23:59:59",
    "quota": 2,
    "note": "這邊要填寫追加的原因"
}
```

#### 📤 Response（201 Created）
```json
{
    "status": "success",
    "message": "序號已成功產生",
    "data": {
        "activity_id": 4, // 會返回該活動的ID
        "total_generated": 100 // 會返回總共產生的組數
    }
}
```

#### ❌ 錯誤回應範例（400 / 422）

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "activity_unique_id": [
            "活動唯一識別碼 為必填欄位"
        ],
        "start_date": [
            "序號開始時間 為必填欄位"
        ],
        "end_date": [
            "序號結束時間 為必填欄位"
        ],
        "quota": [
            "追加序號數量 為必填欄位"
        ],
        "note": [
            "追加說明備註 為必填欄位"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "activity_unique_id": [
            "所選擇的 活動唯一識別碼 無效（該活動不存在）"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "start_date": [
            "序號開始時間 格式必須為 yyyy-MM-dd HH:mm:ss"
        ],
        "end_date": [
            "序號結束時間 格式必須為 yyyy-MM-dd HH:mm:ss"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "end_date": [
            "序號結束時間 必須晚於或等於 開始日期",
            "序號結束時間 不能早於當前時間，否則序號將立即過期"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "quota": [
            "追加序號數量 最小值為 1",
            "追加序號數量 最大值為 100"
        ]
    }
}
```

---

### 3. 核銷序號
- **路由名稱（Route Name）**：核銷序號
- **Endpoint**：/serials_redeem
- **Method**：POST
- **說明**：終端使用者兌換。內建鎖機制防止重複核銷。

#### 📥 Request Parameters（JSON）
| 欄位名稱 | 型態 | 必填 | 說明 |
| :--- | :--- | :--- | :--- |
| content | String | 是 | 序號字串（必須為 8 碼） |

```json
{
    "orderno": "AB123456", // 要使用的訂單編號  
    "content": "B1172060" // 要核銷的序號
}
```

#### 📤 Response（200 OK）
```json
{
    "status": "success",
    "message": "核銷成功",
    "data": {
        "serial_orderno": "AB123456",
        "serial_content": "V8262397",
        "redeemed_at": "年-月-日 時:分:秒"
    }
}
```

#### ❌ 錯誤回應範例（400 / 422）
```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "orderno": [
            "訂單編號 欄位為必填。"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "content": [
            "序號 為必填欄位"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "content": [
            "序號 必須是 8 碼字元"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "此序號不存在"
}
```

```json
{
    "status": "error",
    "message": "此序號已經被核銷使用"
}
```

```json
{
    "status": "error",
    "message": "此序號已被註銷，無法核銷"
}
```

```json
{
    "status": "error",
    "message": "此序號活動尚未開始 (開放時間：年-月-日 時:分:秒)"
}
```

```json
{
    "status": "error",
    "message": "此序號已過期失效 (到期時間：年-月-日 時:分:秒)"
}
```

---

### 4. 批次註銷序號
- **路由名稱（Route Name）**：核銷序號
- **Endpoint**：/serials_cancel
- **Method**：POST
- **說明**：後端使用者註銷。防止將不再使用的序號做再次使用。

#### 📥 Request Parameters（JSON）
| 欄位名稱 | 型態 | 必填 | 說明 |
| :--- | :--- | :--- | :--- |
| content | String | 是 | 序號字串（必須為 8 碼） |

```json
{
    "content": [
        "B2725865",
        "B2725866",
        "B2725867"
    ],
    "note": "2026春節活動提早結束，剩餘序號批次註銷"
}
```

#### ❌ Response 全部註銷成功（200 OK）
```json
{
    "status": "success",
    "message": "全部註銷成功",
    "cancel_at": "年-月-日 時:分:秒",
    "success_data": {
        "serial_content": "M1474740,Q9416259,A2337698"
    },
    "fail_data": {
        "serial_content": ""
    }
}
```

#### ❌ Response 全部註銷失敗（200 OK）
```json
{
    "status": "success",
    "message": "全部註銷失敗",
    "cancel_at": "年-月-日 時:分:秒",
    "success_data": {
        "serial_content": ""
    },
    "fail_data": {
        "serial_content": "A1111111 (原因一),A2222222 (原因二),A3333333 (原因三)"
    }
}
```

#### ❌ Response 部分註銷成功（200 OK）
```json
{
    "status": "success",
    "message": "部分註銷成功",
    "cancel_at": "年-月-日 時:分:秒",
    "success_data": {
        "serial_content": "P8905479,M4220752"
    },
    "fail_data": {
        "serial_content": "A2337698 (此序號已被註銷，請勿重複註銷)"
    }
}
```

#### ❌ 錯誤回應範例（400 / 422）
```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "content[0]": [
            "序號必須是 8 碼字元（出錯的序號：[序號一]）"
        ],
        "content[1]": [
            "序號必須是 8 碼字元（出錯的序號：[序號一]）"
        ],
        "content[2]": [
            "序號必須是 8 碼字元（出錯的序號：[序號三]）"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "content": [
            "序號內容 一次最多只能處理 1000 筆。"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "content": [
            "序號 為必填欄位"
        ]
    }
}
```

```json
{
    "status": "error",
    "message": "驗證失敗",
    "errors": {
        "content": [
            "註銷原因 為必填欄位"
        ]
    }
}
```

---

## 🔒 技術規格重點

1. 成功狀態碼說明
   - 201（Created）：用於「批次新增」與「批次追加」介面，代表系統已成功建立新的資源紀錄。
   - 200（OK）：用於「核銷序號」介面，代表該核銷請求已成功處理完成。

2. 錯誤處理機制
   - 400（Bad Request）: 業務邏輯不符。例如序號「已使用」、「已過期」或「不存在」，由 Service 層拋出 Exception 觸發。
   - 422（Unprocessable Entity）: 參數驗證失敗。由 Controller 的 Validator 攔截，例如日期格式錯誤、ID 重複、或筆數超過限制（1~100）。
   - 500（Internal Server Error）: 非預期系統錯誤。例如資料庫連線中斷或程式執行異常。
#### ❌ 錯誤回應範例
```json
{
    "status": "error",
    "message": "系統處理失敗",
    "debug": "系統例外的錯誤訊息"
}
```

3. 核心邏輯機制
   - 併發防禦（核銷）：核銷流程採用 Pessimistic Locking 悲觀鎖，透過 Spring Data JPA 的 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 注解實作於 Repository 層，需搭配 `@Transactional` 方可生效。在 SQL Server 底層會產生 `WITH (UPDLOCK, HOLDLOCK)` 語法，與 Laravel 的 `lockForUpdate()` 行為等同，確保在極短時間內重複請求時，資料的一致性與唯一性。
   - 併發防禦（批次註銷）：批次註銷流程採用 Spring 的 `TransactionTemplate` 程式化事務管理，讓每筆序號各自擁有獨立 Transaction（等同 Laravel 在 foreach 中逐筆呼叫 `DB::transaction()`）。單筆序號失敗時，僅回滾該筆的操作，不影響其他序號的處理結果，避免全批次連帶失敗。
   - 資料自動清洗：序號 content 欄位在核銷與註銷前，後端會自動執行 `trim()` 去除前後空白，並執行 `toUpperCase()` 強制轉為大寫，增加使用者兌換的容錯率。
   - 全程紀錄：所有請求與回應皆由 `ApiLoggerFilter`（對應 Laravel 的 `api.logger` Middleware）寫入資料庫日誌表。

## 🛡 全域中間件說明（Middleware: api.logger）

本系統所有 API 路由皆掛載了自定義中間件 `api.logger`。該中間件負責將每一次的 API 交互完整儲存於資料庫中，提供比檔案日誌更易於檢索的追蹤機制。

### 1. 記錄機制 (Database Logging)
- **請求存證：** 自動擷取客戶端 IP、請求網址 (URL)、請求方法 (Method) 以及傳入的原始 Body 參數（如序號內容、活動設定）。
- **回應存證：** 記錄系統處理後的最終 JSON 回應內容以及 HTTP 狀態碼。
- **效能追蹤：** 紀錄請求與回應之間的時間差，用於監控 API 執行效率。

### 2. 日誌用途
- **串接對帳：** 當串接端對核銷結果有疑慮時，可直接查詢資料庫日誌表，確認當時傳入的原始資料。
- **責任歸屬：** 透過記錄 IP 與請求參數，可有效釐清是前端傳參錯誤 (422) 或是業務邏輯阻擋 (400)。
- **自動化監控：** 因為紀錄在資料庫，未來可輕鬆開發管理介面，即時統計核銷成功率與異常頻率。

### 3. SQL 語法參考

```sql
-- 建立活動主表
CREATE TABLE serial_activity (
    id                 INT IDENTITY(1,1) PRIMARY KEY, -- 自動遞增主鍵
    activity_name      NVARCHAR(255) NOT NULL,        -- 活動名稱
    activity_unique_id NVARCHAR(100) NOT NULL,        -- 活動唯一編號 (如: ACT202601)
    start_date         DATETIME NOT NULL,             -- 開始時間
    end_date           DATETIME NOT NULL,             -- 結束時間
    quota              INT NOT NULL,                  -- 預計產出總量
    created_at         DATETIME DEFAULT GETDATE(),    -- 建立時間
    updated_at         DATETIME DEFAULT GETDATE(),    -- 更新時間

    -- 建立唯一約束，防止活動編號重複
    CONSTRAINT UQ_ActivityUniqueID UNIQUE (activity_unique_id)
);

-- 針對活動編號建立索引，優化未來 API 查詢與驗證效能
CREATE INDEX IX_serial_activity_unique_id ON serial_activity(activity_unique_id);

-- 針對日期建立索引，方便未來查詢「進行中」或「已過期」的活動
CREATE INDEX IX_serial_activity_dates ON serial_activity(start_date, end_date);

-- 更新後的序號明細表
CREATE TABLE serial_detail (
    id                 INT IDENTITY(1,1) PRIMARY KEY, -- 自動遞增主鍵
    serial_activity_id INT NOT NULL,                  -- 關聯活動表 ID
    orderno            NVARCHAR(8) NULL,              -- 訂單編號
    content            NVARCHAR(8) NOT NULL,          -- 序號內容 (1碼大寫英文+7碼數字)
    status             INT NOT NULL DEFAULT 0,        -- 狀態: 0=未使用, 1=已使用
    note               NVARCHAR(MAX) NULL,            -- 手動新增備註
    start_date         DATETIME NOT NULL,             -- 開始時間
    end_date           DATETIME NOT NULL,             -- 結束時間
    created_at         DATETIME DEFAULT GETDATE(),    -- 建立時間
    updated_at         DATETIME NULL,                 -- 核銷/更新時間 (預設為空)

    -- 建立唯一約束，確保全系統序號不重複
    CONSTRAINT UQ_SerialContent UNIQUE (content),

    -- 建立外鍵約束，當活動被刪除時，對應的序號也會一併刪除 (依需求決定是否保留)
    CONSTRAINT FK_serial_detail_activity FOREIGN KEY (serial_activity_id)
    REFERENCES serial_activity(id) ON DELETE CASCADE
);

-- 針對外鍵建立索引，優化查詢特定活動下所有序號的效能
CREATE INDEX IX_serial_detail_activity_id ON serial_detail(serial_activity_id);

-- 針對訂單編號建立索引，優化查詢效能
-- 唯一性由應用層的 lockForUpdate + transaction 機制保證，不在 DB 層另設 UNIQUE 約束
CREATE INDEX IX_serial_detail_orderno ON serial_detail(orderno);

-- 針對狀態建立索引，優化未來「查詢未核銷序號」的效能
CREATE INDEX IX_serial_detail_status ON serial_detail(status);

-- 針對日期建立索引，方便未來查詢「進行中」或「已過期」的活動
CREATE INDEX IX_serial_detail_dates ON serial_detail(start_date, end_date);

-- 行為紀錄追蹤
CREATE TABLE serial_log (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    api_name    NVARCHAR(100) NOT NULL,           -- API 功能名稱
    host        NVARCHAR(50) NOT NULL,            -- 客戶端 IP
    api         NVARCHAR(255) NOT NULL,           -- 呼叫的網址
    request     NVARCHAR(MAX) NOT NULL,           -- 請求 JSON
    request_at  DATETIME NOT NULL,                -- 請求時間
    response    NVARCHAR(MAX) NULL,               -- 回應 JSON
    response_at DATETIME NULL,                    -- 回應時間
    created_at  DATETIME DEFAULT GETDATE()        -- 資料列建立時間
);

-- 建立索引以利後續依時間或功能篩選
CREATE INDEX IX_serial_log_request_at ON serial_log(request_at);
CREATE INDEX IX_serial_log_api_name ON serial_log(api_name);
```

---

## 🔒 壓力測試

1. Postman：
<img width="1909" height="1004" alt="螢幕擷取畫面 2026-01-30 151548" src="https://github.com/user-attachments/assets/2974f1c0-819b-4e1b-9e4d-18e990623389" />
<img width="1913" height="987" alt="螢幕擷取畫面 2026-01-30 151631" src="https://github.com/user-attachments/assets/283bcc3c-ae34-44a6-9aa0-349ac75ed977" />
<img width="1916" height="989" alt="螢幕擷取畫面 2026-01-30 151739" src="https://github.com/user-attachments/assets/15d81a76-2406-41e1-aba7-685828808b03" />
<img width="1916" height="981" alt="螢幕擷取畫面 2026-01-30 151805" src="https://github.com/user-attachments/assets/f33e008a-bb52-466e-83e0-c80c1af5c9cf" />

2. JMeter：
<img width="1523" height="856" alt="螢幕擷取畫面 2026-02-02 172241" src="https://github.com/user-attachments/assets/88b52610-eaef-4440-b548-6926b85f7f46" />
<img width="1520" height="856" alt="螢幕擷取畫面 2026-02-02 172407" src="https://github.com/user-attachments/assets/a4e0e7a5-8681-4f37-9e2e-48463ee25d6c" />
<img width="1517" height="853" alt="螢幕擷取畫面 2026-02-02 172422" src="https://github.com/user-attachments/assets/9e666a8d-4772-4b74-9660-29f0b725588a" />

## 🔒 Iframe 模擬
<img width="1908" height="994" alt="iframe" src="https://github.com/user-attachments/assets/e79f8541-1666-48dc-8af4-ba9fb63bd15c" />
