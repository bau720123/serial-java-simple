package com.example.serial.repository;

import com.example.serial.model.SerialActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 活動主表 Repository
 * 對應 Laravel Model: SerialActivity
 */
public interface SerialActivityRepository extends JpaRepository<SerialActivity, Long> {

    /**
     * 依 activity_unique_id 查詢活動
     * 對應 Laravel:
     *   DB::table('serial_activity')->where('activity_unique_id', $id)->first()
     */
    Optional<SerialActivity> findByActivityUniqueId(String activityUniqueId);

    /**
     * 確認 activity_unique_id 是否已存在（用於 unique 驗證）
     * 對應 Laravel validator rule: 'unique:SerialActivity,activity_unique_id'
     */
    boolean existsByActivityUniqueId(String activityUniqueId);
}
