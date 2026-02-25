package com.example.serial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * API 日誌表 Entity
 * 對應 Laravel Middleware: app/Http/Middleware/ApiLogger.php
 * 對應資料表: serial_log
 *
 * 所有 /api/* 請求的完整紀錄
 */
@Entity
@Table(name = "serial_log")
@Getter
@Setter
@NoArgsConstructor
public class SerialLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // API 名稱（對應 Laravel route name，如 "批次新增序號"）
    @Column(name = "api_name")
    private String apiName;

    // 呼叫端 IP（對應 Laravel $request->ip()）
    private String host;

    // 完整 URL（對應 Laravel $request->fullUrl()）
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String api;

    // 請求內容 JSON（對應 Laravel json_encode($request->all())）
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String request;

    // 請求開始時間
    @Column(name = "request_at")
    private LocalDateTime requestAt;

    // 回應內容 JSON
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String response;

    // 請求完成時間
    @Column(name = "response_at")
    private LocalDateTime responseAt;

    // 紀錄建立時間（等同 request_at）
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
