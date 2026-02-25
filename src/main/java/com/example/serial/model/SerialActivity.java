package com.example.serial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 活動主表 Entity
 * 對應 Laravel Model: app/Models/SerialActivity.php
 * 對應資料表: serial_activity
 */
@Entity
@Table(name = "serial_activity")
@Getter
@Setter
@NoArgsConstructor
public class SerialActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_name")
    private String activityName;

    // 活動唯一識別碼（在 serials_insert 時需確保唯一性）
    @Column(name = "activity_unique_id", unique = true)
    private String activityUniqueId;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    // 活動序號配額（最小 1，最大 100）
    private Integer quota;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
