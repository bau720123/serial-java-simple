package com.example.serial.controller.api;

import com.example.serial.dto.*;
import com.example.serial.repository.SerialActivityRepository;
import com.example.serial.service.SerialService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API Controller - 序號管理
 * 對應 Laravel: app/Http/Controllers/Api/SerialController.php
 *
 * 路由對應：
 *   POST /api/serials_insert           → serials_insert()
 *   POST /api/serials_additional_insert → serials_additional_insert()
 *   POST /api/serials_redeem           → serials_redeem()
 *   POST /api/serials_cancel           → serials_cancel()
 *
 * 架構說明（Java vs Laravel 差異）：
 *   Laravel 使用 Validator::make() 手動建立 Validator，失敗時回傳 422
 *   Java 使用 @Valid 注解 + BindingResult 手動檢查，行為等同
 *   Laravel 的 'unique' 和 'exists' 規則需要查詢 DB，Java 改在 Controller 補充驗證
 *   Laravel 的 'date_format', 'after', 'after_or_equal' 規則在 Java 以手動判斷實作
 */
@RestController
@RequestMapping("/api")
public class SerialController {

    @Autowired
    private SerialService serialService;

    @Autowired
    private SerialActivityRepository activityRepository;

    // =========================================================================
    // POST /api/serials_insert - 批次新增序號
    // 對應 Laravel SerialController::serials_insert()
    // =========================================================================

    @PostMapping("/serials_insert")
    public ResponseEntity<Map<String, Object>> serials_insert(
            @Valid @RequestBody SerialInsertRequest request,
            BindingResult bindingResult) {

        // 基本格式驗證（@NotBlank, @Min, @Max 等）
        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(buildValidationError(bindingResult));
        }

        // 補充驗證（需查詢 DB 或跨欄位比較）
        // 對應 Laravel 的 'unique', 'date_format', 'after', 'after_or_equal' 規則
        Map<String, List<String>> extraErrors = new LinkedHashMap<>();

        // 驗證日期格式
        LocalDateTime startDt = null, endDt = null;
        try {
            startDt = SerialService.parseDateTime(request.getStartDate());
        } catch (DateTimeParseException e) {
            extraErrors.computeIfAbsent("start_date", k -> new ArrayList<>())
                    .add("活動開始時間 格式必須為 yyyy-MM-dd HH:mm:ss");
        }
        try {
            endDt = SerialService.parseDateTime(request.getEndDate());
        } catch (DateTimeParseException e) {
            extraErrors.computeIfAbsent("end_date", k -> new ArrayList<>())
                    .add("活動結束時間 格式必須為 yyyy-MM-dd HH:mm:ss");
        }

        if (startDt != null && endDt != null) {
            // end_date 必須晚於或等於 start_date（對應 Laravel: after_or_equal:start_date）
            if (endDt.isBefore(startDt)) {
                extraErrors.computeIfAbsent("end_date", k -> new ArrayList<>())
                        .add("活動結束時間 必須晚於或等於 開始日期");
            }
            // end_date 必須晚於現在（對應 Laravel: after:now）
            if (!endDt.isAfter(LocalDateTime.now())) {
                extraErrors.computeIfAbsent("end_date", k -> new ArrayList<>())
                        .add("活動結束時間 不能早於當前時間，否則序號將立即過期");
            }
        }

        // 驗證 activity_unique_id 唯一性（對應 Laravel: unique:SerialActivity,activity_unique_id）
        if (activityRepository.existsByActivityUniqueId(request.getActivityUniqueId())) {
            extraErrors.computeIfAbsent("activity_unique_id", k -> new ArrayList<>())
                    .add("活動唯一識別碼 已存在，請勿重複新增");
        }

        if (!extraErrors.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "error");
            body.put("message", "驗證失敗");
            body.put("errors", extraErrors);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
        }

        try {
            Map<String, Object> result = serialService.createActivityWithSerials(
                    request.getActivityName(),
                    request.getActivityUniqueId(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getQuota(),
                    null,   // note（新增時為 null）
                    false   // isAdditional = false（新增活動）
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success");
            body.put("message", "活動與序號已成功產生");
            body.put("data", result);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);

        } catch (Exception e) {
            // 對應 Laravel: catch (Exception $e) → 500 response
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "error");
            body.put("message", "系統處理失敗");
            body.put("debug", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // =========================================================================
    // POST /api/serials_additional_insert - 批次追加序號
    // 對應 Laravel SerialController::serials_additional_insert()
    // =========================================================================

    @PostMapping("/serials_additional_insert")
    public ResponseEntity<Map<String, Object>> serials_additional_insert(
            @Valid @RequestBody SerialAdditionalInsertRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(buildValidationError(bindingResult));
        }

        Map<String, List<String>> extraErrors = new LinkedHashMap<>();

        // 驗證日期格式
        LocalDateTime startDt = null, endDt = null;
        try {
            startDt = SerialService.parseDateTime(request.getStartDate());
        } catch (DateTimeParseException e) {
            extraErrors.computeIfAbsent("start_date", k -> new ArrayList<>())
                    .add("序號開始時間 格式必須為 yyyy-MM-dd HH:mm:ss");
        }
        try {
            endDt = SerialService.parseDateTime(request.getEndDate());
        } catch (DateTimeParseException e) {
            extraErrors.computeIfAbsent("end_date", k -> new ArrayList<>())
                    .add("序號結束時間 格式必須為 yyyy-MM-dd HH:mm:ss");
        }

        if (startDt != null && endDt != null) {
            if (endDt.isBefore(startDt)) {
                extraErrors.computeIfAbsent("end_date", k -> new ArrayList<>())
                        .add("序號結束時間 必須晚於或等於 開始日期");
            }
            if (!endDt.isAfter(LocalDateTime.now())) {
                extraErrors.computeIfAbsent("end_date", k -> new ArrayList<>())
                        .add("序號結束時間 不能早於當前時間，否則序號將立即過期");
            }
        }

        // 驗證 activity_unique_id 存在（對應 Laravel: exists:SerialActivity,activity_unique_id）
        if (!activityRepository.existsByActivityUniqueId(request.getActivityUniqueId())) {
            extraErrors.computeIfAbsent("activity_unique_id", k -> new ArrayList<>())
                    .add("所選擇的 活動唯一識別碼 無效（該活動不存在）");
        }

        if (!extraErrors.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "error");
            body.put("message", "驗證失敗");
            body.put("errors", extraErrors);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
        }

        try {
            // 對應 Laravel: $request->merge(['insert_serial_activity' => 0])
            // Java 改為 isAdditional = true 參數明確區分
            Map<String, Object> result = serialService.createActivityWithSerials(
                    null,   // activityName（追加時不需要）
                    request.getActivityUniqueId(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getQuota(),
                    request.getNote(),
                    true    // isAdditional = true（追加序號）
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success");
            body.put("message", "序號已成功產生");
            body.put("data", result);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);

        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "error");
            body.put("message", "系統處理失敗");
            body.put("debug", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // =========================================================================
    // POST /api/serials_redeem - 核銷序號
    // 對應 Laravel SerialController::serials_redeem()
    // =========================================================================

    @PostMapping("/serials_redeem")
    public ResponseEntity<Map<String, Object>> serials_redeem(
            @Valid @RequestBody SerialRedeemRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(buildValidationError(bindingResult));
        }

        try {
            Map<String, Object> result = serialService.redeemSerial(request.getOrderno(), request.getContent());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success");
            body.put("message", "核銷成功");
            body.put("data", result);
            return ResponseEntity.ok(body);

        } catch (Exception e) {
            // 對應 Laravel: catch (Exception $e) → 400（業務邏輯錯誤）
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "error");
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }

    // =========================================================================
    // POST /api/serials_cancel - 批次註銷序號
    // 對應 Laravel SerialController::serials_cancel()
    // =========================================================================

    @PostMapping("/serials_cancel")
    public ResponseEntity<Map<String, Object>> serials_cancel(
            @Valid @RequestBody SerialCancelRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            // 對應 Laravel 的 custom Replacer：對 content[N] 的 size 錯誤，顯示出錯的序號值
            // Java 的 BindingResult 錯誤訊息中包含 field path（如 content[2]），
            // buildCancelValidationError 會進一步附加實際的序號值
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(buildCancelValidationError(bindingResult, request));
        }

        LocalDateTime now = LocalDateTime.now();

        // 對應 Laravel: $now = now()->toDateTimeString()
        Map<String, List<String>> results = serialService.cancelSerials(
                request.getContent(),
                request.getNote(),
                now
        );

        int successCount = results.get("success").size();
        int failCount = results.get("fail").size();

        // 判斷最終 Message（對應 Laravel 的三元判斷）
        String message = "部分註銷成功";
        if (failCount == 0) message = "全部註銷成功";
        if (successCount == 0) message = "全部註銷失敗";

        // 對應 Laravel response 格式（逐字對應）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "success");
        body.put("message", message);
        body.put("cancel_at", now.toString().replace("T", " ").substring(0, 19));

        Map<String, String> successData = new LinkedHashMap<>();
        successData.put("serial_content", String.join(",", results.get("success")));
        body.put("success_data", successData);

        Map<String, String> failData = new LinkedHashMap<>();
        failData.put("serial_content", String.join(",", results.get("fail")));
        body.put("fail_data", failData);

        return ResponseEntity.ok(body);
    }

    // =========================================================================
    // 私有輔助方法
    // =========================================================================

    /**
     * 將 BindingResult 轉換為 Laravel 格式的驗證錯誤回應，且依 DTO 欄位宣告順序排列
     * 對應 Laravel: response()->json(['status' => 'error', 'errors' => $validator->errors()], 422)
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel 的 Validator::make() 錯誤依照 rules 陣列的定義順序輸出
     *   Spring 的 BindingResult.getFieldErrors() 順序不固定（依 Validator 實作而定）
     *   此處使用反射取得 DTO 的欄位宣告順序，再對錯誤清單排序，確保每次輸出順序一致
     */
    private Map<String, Object> buildValidationError(BindingResult bindingResult) {
        // 取得 DTO 欄位宣告順序（透過 Java Reflection）
        List<String> fieldOrder = getDeclaredFieldOrder(bindingResult.getTarget());

        // 依欄位宣告順序排序（Spring BindingResult 預設不保證順序）
        List<FieldError> sortedErrors = bindingResult.getFieldErrors().stream()
                .sorted(Comparator.comparingInt(error -> {
                    // 去除陣列索引（如 content[2] → content）再查找順序
                    String baseField = error.getField().replaceAll("\\[\\d+\\]", "");
                    int idx = fieldOrder.indexOf(baseField);
                    return idx >= 0 ? idx : Integer.MAX_VALUE;
                }))
                .collect(Collectors.toList());

        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError error : sortedErrors) {
            // 將 camelCase 欄位名稱轉換為 snake_case（對應 Laravel JSON key 格式）
            String fieldName = toSnakeCase(error.getField());
            errors.computeIfAbsent(fieldName, k -> new ArrayList<>())
                    .add(error.getDefaultMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", "驗證失敗");
        body.put("errors", errors);
        return body;
    }

    /**
     * 批次註銷序號的驗證錯誤格式化（含欄位宣告順序排序）
     * 特殊處理：對 content[N] 的 size 錯誤，附加實際的序號值
     * 對應 Laravel 的 custom Replacer:
     *   $message = str_replace(':value', "[{$value}]", $message)
     */
    private Map<String, Object> buildCancelValidationError(
            BindingResult bindingResult, SerialCancelRequest request) {

        List<String> fieldOrder = getDeclaredFieldOrder(request);

        // 依欄位宣告順序排序，陣列元素則進一步依索引 N 排序
        List<FieldError> sortedErrors = bindingResult.getFieldErrors().stream()
                .sorted(Comparator.comparingInt((FieldError error) -> {
                    String baseField = error.getField().replaceAll("\\[\\d+\\]", "");
                    int idx = fieldOrder.indexOf(baseField);
                    return idx >= 0 ? idx : Integer.MAX_VALUE;
                }).thenComparingInt(error -> {
                    // content[N] 的 N（確保陣列錯誤依序號索引排列）
                    String field = error.getField();
                    if (field.contains("[")) {
                        try {
                            int s = field.indexOf('[') + 1;
                            int e = field.indexOf(']');
                            return Integer.parseInt(field.substring(s, e));
                        } catch (NumberFormatException ignored) {}
                    }
                    return 0;
                }))
                .collect(Collectors.toList());

        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError error : sortedErrors) {
            String fieldName = error.getField();
            String message = error.getDefaultMessage();

            // 如果是 content 陣列元素的錯誤，附加出錯的序號值
            // 對應 Laravel: $message = str_replace(':value', "[{$value}]", $message)
            if (fieldName.startsWith("content[") && request.getContent() != null) {
                try {
                    int start = fieldName.indexOf('[') + 1;
                    int end = fieldName.indexOf(']');
                    int index = Integer.parseInt(fieldName.substring(start, end));
                    if (index < request.getContent().size()) {
                        String actualValue = request.getContent().get(index);
                        message = message + "（出錯的序號：[" + actualValue + "]）";
                    }
                } catch (NumberFormatException ignored) {}
            }

            String snakeCaseField = toSnakeCase(fieldName);
            errors.computeIfAbsent(snakeCaseField, k -> new ArrayList<>()).add(message);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", "驗證失敗");
        body.put("errors", errors);
        return body;
    }

    /**
     * 透過 Java Reflection 取得物件類別的欄位宣告順序（camelCase 欄位名稱列表）
     * 用於確保 errors 依 DTO 欄位定義順序輸出（對應 Laravel Validator 的 rules 陣列順序）
     *
     * getDeclaredFields() 在 JVM 中保證依原始碼宣告順序回傳（含 Lombok 生成前的順序）
     * isSynthetic() 過濾掉編譯器或 Lombok 插入的合成欄位
     */
    private List<String> getDeclaredFieldOrder(Object target) {
        if (target == null) return Collections.emptyList();
        return Arrays.stream(target.getClass().getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toList());
    }

    /**
     * 將 camelCase 或包含括號的欄位名稱轉換為 snake_case
     * 例如：activityName → activity_name
     *       content[0] → content[0]（陣列路徑保留）
     */
    private String toSnakeCase(String camelCase) {
        // 如果包含 [ 表示是陣列元素路徑，保持原樣
        if (camelCase.contains("[")) {
            return camelCase;
        }
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
