package com.example.serial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 序號明細表 Entity
 * 對應 Laravel Model: app/Models/SerialDetail.php
 * 對應資料表: serial_detail
 *
 * 與 SerialActivity 的關聯：
 * Laravel: $this->belongsTo(SerialActivity::class, 'serial_activity_id', 'id')
 * Java:    @ManyToOne + @JoinColumn(name = "serial_activity_id")
 */
@Entity
@Table(name = "serial_detail")
@Getter
@Setter
@NoArgsConstructor
public class SerialDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 多對一關聯：每筆序號屬於一個活動
     * 對應 Laravel: public function activity(): BelongsTo
     *
     * 注意：使用 FetchType.EAGER，因為後台列表頁面每行都需要顯示活動名稱
     * （等同 Laravel 的 SerialDetail::with('activity')）
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "serial_activity_id", referencedColumnName = "id")
    private SerialActivity activity;

    // 序號內容（格式：1 個大寫英文字母 + 7 位數字，共 8 碼）
    private String content;

    // 狀態：0=未核銷, 1=已核銷, 2=已註銷
    private Integer status;

    // 備註（追加序號時填入說明；核銷時為空；註銷時填入原因）
    private String note;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    // 建立時間（insert 時設定，之後不變）
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新時間
     * 注意：insert 時為 null，僅在 redeem/cancel 操作時才更新
     * 對應 Laravel 行為：'updated_at' => null（insert 時）
     * 與 @UpdateTimestamp 不同，此欄位由業務邏輯手動管理
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
