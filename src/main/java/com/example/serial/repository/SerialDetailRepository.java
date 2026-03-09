package com.example.serial.repository;

import com.example.serial.model.SerialDetail;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 序號明細表 Repository
 * 對應 Laravel Model: SerialDetail + DB::table('serial_detail') 操作
 */
public interface SerialDetailRepository extends JpaRepository<SerialDetail, Long>,
        JpaSpecificationExecutor<SerialDetail> {

    /**
     * 依訂單編號與狀態查詢並鎖定（悲觀鎖），用於防止同一訂單編號重複核銷
     * 對應 Laravel:
     *   DB::table('serial_detail')->where('orderno', $orderno)->where('status', 1)->lockForUpdate()->first()
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerialDetail s WHERE s.orderno = :orderno AND s.status = :status")
    Optional<SerialDetail> findByOrdernoAndStatusWithLock(
            @Param("orderno") String orderno, @Param("status") int status);

    /**
     * 依序號內容查詢並鎖定該行（悲觀鎖）
     * 對應 Laravel:
     *   DB::table('serial_detail')->where('content', $content)->lockForUpdate()->first()
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel 的 lockForUpdate() 在 SQL Server 產生 WITH(UPDLOCK, ROWLOCK)
     *   Spring JPA 的 PESSIMISTIC_WRITE 在 SQL Server 產生 WITH(UPDLOCK, HOLDLOCK)
     *   兩者效果等同，都能防止並發讀取/修改同一列
     *   此方法必須在 @Transactional 方法內呼叫才有效
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerialDetail s WHERE s.content = :content")
    Optional<SerialDetail> findByContentWithLock(@Param("content") String content);

    /**
     * 批次確認序號是否已存在（用於產生唯一序號時去重）
     * 對應 Laravel:
     *   DB::table('serial_detail')->whereIn('content', $tempBatch)->pluck('content')->toArray()
     */
    @Query("SELECT s.content FROM SerialDetail s WHERE s.content IN :contents")
    List<String> findExistingContents(@Param("contents") List<String> contents);

    /**
     * 後台分頁查詢（帶 Specification 動態條件）
     * 對應 Laravel: $query->paginate(15)
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel 的 paginate() 自動計算總筆數，Spring 的 findAll(Spec, Pageable) 同樣做法
     *   但 Spring 使用 0-indexed 頁碼，Controller 層會轉換為 1-indexed（對應 Laravel 預設）
     */
    Page<SerialDetail> findAll(Specification<SerialDetail> spec, Pageable pageable);
}
