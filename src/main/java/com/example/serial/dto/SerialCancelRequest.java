package com.example.serial.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 批次註銷序號 請求 DTO
 * 對應 Laravel SerialController::serials_cancel() 的 Validator::make() 規則
 *
 * 注意（Java vs Laravel 差異）：
 *   Laravel 對 content 陣列元素逐一驗證並使用 custom Replacer 顯示出錯的序號值
 *   Java 的 @Size 驗證失敗時，錯誤訊息中會包含欄位路徑（如 content[2]）
 *   在 Controller 層組裝錯誤訊息時，會一併呈現出錯的序號值，行為等同 Laravel
 */
@Data
public class SerialCancelRequest {

    /**
     * 序號清單（1～1000 筆，每筆必須恰好 8 碼）
     * Laravel:
     *   'content' => 'required|array|min:1|max:1000'
     *   'content.*' => 'required|string|size:8'
     */
    @NotNull(message = "序號 為必填欄位")
    @Size(min = 1, max = 1000, message = "序號內容 一次最多只能處理 1000 筆")
    private List<@NotBlank(message = "序號不得為空")
                 @Size(min = 8, max = 8, message = "序號必須是 8 碼字元") String> content;

    /**
     * 註銷原因備註（必填，最多 255 字）
     * Laravel: 'note' => 'required|string|max:255'
     */
    @NotBlank(message = "註銷原因 為必填欄位")
    @Size(max = 255, message = "註銷原因 最多 255 字元")
    private String note;
}
