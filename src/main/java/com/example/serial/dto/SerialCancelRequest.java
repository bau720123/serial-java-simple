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
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel 的 'required|array|min:1' 對空陣列觸發 "required" 訊息
     *   Java 的 @Size(min=1, max=1000) 只有一條訊息，無法區分「空陣列」與「超過 1000 筆」
     *   解法：使用 @Size.List 拆成兩個獨立 @Size 約束，各自帶不同的錯誤訊息：
     *     - min=1 → 對應 Laravel 'required'（空陣列時顯示「為必填欄位」）
     *     - max=1000 → 對應 Laravel 'max:1000'（超量時顯示「最多 1000 筆」）
     */
    @NotNull(message = "序號 為必填欄位")
    @Size.List({
        @Size(min = 1, message = "序號 為必填欄位"),
        @Size(max = 1000, message = "序號內容 一次最多只能處理 1000 筆")
    })
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
