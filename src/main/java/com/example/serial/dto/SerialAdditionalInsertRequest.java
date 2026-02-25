package com.example.serial.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 批次追加序號 請求 DTO
 * 對應 Laravel SerialController::serials_additional_insert() 的 Validator::make() 規則
 */
@Data
public class SerialAdditionalInsertRequest {

    /**
     * 活動唯一識別碼（必須已存在）
     * Laravel: 'activity_unique_id' => 'required|string|exists:SerialActivity,activity_unique_id'
     * 注意：exists 驗證在 Service 層執行（需查詢資料庫）
     */
    @JsonProperty("activity_unique_id")
    @NotBlank(message = "活動唯一識別碼 為必填欄位")
    private String activityUniqueId;

    /**
     * 追加序號的開始時間
     * Laravel: 'start_date' => 'required|date_format:Y-m-d H:i:s'
     */
    @JsonProperty("start_date")
    @NotBlank(message = "序號開始時間 為必填欄位")
    private String startDate;

    /**
     * 追加序號的結束時間
     * Laravel: 'end_date' => 'required|date_format:Y-m-d H:i:s|after_or_equal:start_date|after:now'
     */
    @JsonProperty("end_date")
    @NotBlank(message = "序號結束時間 為必填欄位")
    private String endDate;

    /**
     * 追加序號數量（1～100）
     * Laravel: 'quota' => 'required|integer|min:1|max:100'
     */
    @NotNull(message = "追加序號數量 為必填欄位")
    @Min(value = 1, message = "追加序號數量 最小值為 1")
    @Max(value = 100, message = "追加序號數量 最大值為 100")
    private Integer quota;

    /**
     * 追加說明備註
     * Laravel: 'note' => 'required|string'
     */
    @NotBlank(message = "追加說明備註 為必填欄位")
    private String note;
}
