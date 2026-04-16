package com.example.serial.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 批次新增序號 請求 DTO
 * 對應 Laravel SerialController::serials_insert() 的 Validator::make() 規則
 *
 * JSON key 使用 snake_case（@JsonProperty），對應 Laravel $request->all() 的欄位名稱
 */
@Data
public class ActivityInsertRequest {

    /**
     * 活動名稱
     * Laravel: 'activity_name' => 'required|string'
     */
    @JsonProperty("activity_name")
    @NotBlank(message = "活動名稱 為必填欄位")
    private String activityName;

    /**
     * 活動唯一識別碼
     * Laravel: 'activity_unique_id' => 'required|string|unique:SerialActivity,activity_unique_id'
     * 注意：unique 驗證在 Service 層執行（需查詢資料庫）
     */
    @JsonProperty("activity_unique_id")
    @NotBlank(message = "活動唯一識別碼 為必填欄位")
    private String activityUniqueId;

    /**
     * 活動開始時間（格式：yyyy-MM-dd HH:mm:ss）
     * Laravel: 'start_date' => 'required|date_format:Y-m-d H:i:s'
     * 注意：日期格式與業務邏輯驗證在 Controller 層執行
     */
    @JsonProperty("start_date")
    @NotBlank(message = "活動開始時間 為必填欄位")
    private String startDate;

    /**
     * 活動結束時間（格式：yyyy-MM-dd HH:mm:ss）
     * Laravel: 'end_date' => 'required|date_format:Y-m-d H:i:s|after_or_equal:start_date|after:now'
     */
    @JsonProperty("end_date")
    @NotBlank(message = "活動結束時間 為必填欄位")
    private String endDate;
}
