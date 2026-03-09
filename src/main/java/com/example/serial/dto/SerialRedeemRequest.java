package com.example.serial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 核銷序號 請求 DTO
 * 對應 Laravel SerialController::serials_redeem() 的 Validator::make() 規則
 */
@Data
public class SerialRedeemRequest {

    /**
     * 訂單編號（必填）
     * Laravel: 'orderno' => 'required|string'
     */
    @NotBlank(message = "訂單編號 為必填欄位")
    private String orderno;

    /**
     * 序號內容（必須恰好 8 碼）
     * Laravel: 'content' => 'required|string|size:8'
     */
    @NotBlank(message = "序號 為必填欄位")
    @Size(min = 8, max = 8, message = "序號 必須是 8 碼字元")
    private String content;
}
