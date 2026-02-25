package com.example.serial.repository;

import com.example.serial.model.SerialLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * API 日誌表 Repository
 * 對應 Laravel Middleware 中的:
 *   DB::connection('sqlsrv_serial')->table('serial_log')->insert([...])
 */
public interface SerialLogRepository extends JpaRepository<SerialLog, Long> {
    // 目前僅需要基本的 save() 方法，由 JpaRepository 提供
}
