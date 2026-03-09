package com.example.serial.service;

import com.example.serial.model.SerialActivity;
import com.example.serial.model.SerialDetail;
import com.example.serial.repository.SerialActivityRepository;
import com.example.serial.repository.SerialDetailRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 序號業務邏輯 Service
 * 對應 Laravel: app/Services/SerialService.php
 *
 * 架構說明：
 *   Laravel 的 DB::connection('sqlsrv_serial')->transaction() → Spring 的 @Transactional
 *   Laravel 的 DB::table()->lockForUpdate() → JPA 的 PESSIMISTIC_WRITE @Lock
 *   Laravel 的 DB::table()->insert($batchArray) → JPA 的 repository.saveAll()
 *   Laravel 的 DB::table()->insertGetId() → JPA 的 repository.save() + entity.getId()
 */
@Service
public class SerialService {

    @Autowired
    private SerialActivityRepository activityRepository;

    @Autowired
    private SerialDetailRepository detailRepository;

    /**
     * EntityManager 用於取得 JPA 代理參考（proxy reference）
     * 對應 Laravel 直接使用 $activityId 插入 FK 的方式
     * 使用 em.getReference() 可以避免重新載入整個 Entity（等同 Laravel 直接用 ID 插入）
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * TransactionTemplate 用於在 cancelSerials() 中逐筆獨立 Transaction
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel 使用 DB::transaction(Closure) 可以輕鬆嵌套在 foreach 中
     *   Java 的 @Transactional 是方法級別的，無法直接在迴圈中對每個元素獨立 Transaction
     *   因此改用 TransactionTemplate（程式化事務管理），行為與 Laravel 相同：
     *   每個序號各自有獨立 Transaction，失敗不影響其他序號
     */
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public SerialService(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW: 確保每次執行都是全新的獨立 Transaction
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Random RANDOM = new Random();

    // =========================================================================
    // 批次新增序號 & 批次追加序號
    // 對應 Laravel SerialService::createActivityWithSerials()
    // =========================================================================

    /**
     * 建立活動並產生序號（新增）或直接追加序號至現有活動
     *
     * @param activityName      活動名稱（新增時使用）
     * @param activityUniqueId  活動唯一 ID
     * @param startDate         序號開始時間字串
     * @param endDate           序號結束時間字串
     * @param quota             序號數量
     * @param note              備註（追加時使用，新增時為 null）
     * @param isAdditional      true = 追加序號，false = 新增活動並產生序號
     */
    @Transactional
    public Map<String, Object> createActivityWithSerials(
            String activityName,
            String activityUniqueId,
            String startDate,
            String endDate,
            Integer quota,
            String note,
            boolean isAdditional) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDt = LocalDateTime.parse(startDate, DATE_TIME_FORMATTER);
        LocalDateTime endDt = LocalDateTime.parse(endDate, DATE_TIME_FORMATTER);

        Long activityId;

        if (!isAdditional) {
            // ---- 批次新增序號：需建立新活動 ----
            // 對應 Laravel: DB::table('serial_activity')->insertGetId([...])
            SerialActivity activity = new SerialActivity();
            activity.setActivityName(activityName);
            activity.setActivityUniqueId(activityUniqueId);
            activity.setStartDate(startDt);
            activity.setEndDate(endDt);
            activity.setQuota(quota);
            activity.setCreatedAt(now);
            activity.setUpdatedAt(now); // 新增時 created_at 和 updated_at 同時設定

            activity = activityRepository.save(activity);
            activityId = activity.getId();

        } else {
            // ---- 批次追加序號：查詢現有活動 ID ----
            // 對應 Laravel:
            //   $activity = DB::table('serial_activity')->where('activity_unique_id', $id)->first();
            //   $activityId = $activity->id;
            SerialActivity activity = activityRepository.findByActivityUniqueId(activityUniqueId)
                    .orElseThrow(() -> new RuntimeException("活動不存在：" + activityUniqueId));
            activityId = activity.getId();
        }

        // 產生不重複的序號
        // 對應 Laravel: $uniqueSerials = $this->generateUniqueSerials($data['quota'])
        List<String> uniqueSerials = generateUniqueSerials(quota);

        // 準備批次寫入的序號資料
        // 對應 Laravel: foreach ($uniqueSerials as $code) { $insertData[] = [...] }
        List<SerialDetail> insertData = new ArrayList<>();
        String noteContent = isAdditional ? note : null;

        // 使用 EntityManager.getReference() 取得 Proxy，避免重新查詢 Activity 實體
        // 對應 Laravel 直接寫入 serial_activity_id 數字的方式
        SerialActivity activityRef = entityManager.getReference(SerialActivity.class, activityId);

        for (String code : uniqueSerials) {
            SerialDetail detail = new SerialDetail();
            detail.setActivity(activityRef);
            detail.setContent(code);
            detail.setStatus(0);                // 未核銷
            detail.setNote(noteContent);
            detail.setStartDate(startDt);
            detail.setEndDate(endDt);
            detail.setCreatedAt(now);
            detail.setUpdatedAt(null);          // 對應 Laravel: 'updated_at' => null
            insertData.add(detail);
        }

        // 批次寫入，對應 Laravel: DB::table('serial_detail')->insert($insertData)
        detailRepository.saveAll(insertData);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activity_id", activityId);
        result.put("total_generated", insertData.size());
        return result;
    }

    // =========================================================================
    // 核銷序號
    // 對應 Laravel SerialService::redeemSerial()
    // =========================================================================

    /**
     * 核銷單一序號
     *
     * @param orderno 訂單編號（會自動 trim）
     * @param content 序號（會自動 trim + toUpperCase）
     */
    @Transactional
    public Map<String, Object> redeemSerial(String orderno, String content) {
        // 去前後空白
        // 對應 Laravel: $orderno = trim($orderno)
        orderno = orderno.trim();
        // 去前後空白 + 轉大寫
        // 對應 Laravel: $content = strtoupper(trim($content))
        content = content.trim().toUpperCase();

        LocalDateTime now = LocalDateTime.now();

        // 查找訂單編號並鎖定（悲觀鎖），確認是否已被其他序號核銷使用
        // 對應 Laravel: DB::table('serial_detail')->where('orderno', $orderno)->where('status', 1)->lockForUpdate()->first()
        boolean ordernoUsed = detailRepository.findByOrdernoAndStatusWithLock(orderno, 1).isPresent();
        if (ordernoUsed) {
            throw new RuntimeException("該訂單編號已被其它序號所核銷使用，請勿重複使用");
        }

        // 查找序號並鎖定該行（悲觀鎖）
        // 對應 Laravel: DB::table('serial_detail')->where('content', $content)->lockForUpdate()->first()
        SerialDetail serial = detailRepository.findByContentWithLock(content)
                .orElseThrow(() -> new RuntimeException("此序號不存在"));

        // ---- 業務邏輯條件檢查（對應 Laravel 的 if 系列判斷）----
        if (serial.getStatus() == 1) {
            throw new RuntimeException("此序號已被核銷，請勿重複核銷");
        }
        if (serial.getStatus() == 2) {
            throw new RuntimeException("此序號已被註銷，無法進行核銷");
        }
        if (now.isBefore(serial.getStartDate())) {
            throw new RuntimeException("此序號活動尚未開始 (開放時間：" + serial.getStartDate().format(DATE_TIME_FORMATTER) + ")");
        }
        if (now.isAfter(serial.getEndDate())) {
            throw new RuntimeException("此序號已過期失效 (到期時間：" + serial.getEndDate().format(DATE_TIME_FORMATTER) + ")");
        }

        // 執行核銷（status = 1）
        // 對應 Laravel: DB::table('serial_detail')->where('id', $serial->id)->update([...])
        serial.setOrderno(orderno);
        serial.setStatus(1);
        serial.setUpdatedAt(now);   // 紀錄真正的核銷時點
        detailRepository.save(serial);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serial_orderno", orderno);
        result.put("serial_content", serial.getContent());
        result.put("redeemed_at", now.format(DATE_TIME_FORMATTER));
        return result;
    }

    // =========================================================================
    // 批次註銷序號
    // 對應 Laravel SerialService::cancelSerials()
    // =========================================================================

    /**
     * 批次註銷序號，每筆序號各自有獨立 Transaction
     *
     * @param contents 序號清單
     * @param note     註銷原因
     * @param now      操作時間
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel 使用 foreach + DB::transaction(Closure) 為每筆序號建立獨立 Transaction
     *   Java 使用 TransactionTemplate 達到相同效果（程式化事務管理）
     *   失敗的序號不會影響其他序號的 Transaction
     */
    public Map<String, List<String>> cancelSerials(List<String> contents, String note, LocalDateTime now) {
        List<String> successList = new ArrayList<>();
        List<String> failList = new ArrayList<>();

        // 先處理資料清洗（去重、去空白、轉大寫）
        // 對應 Laravel: array_unique(array_map(fn($item) => strtoupper(trim($item)), $contents))
        Set<String> seen = new LinkedHashSet<>();
        for (String c : contents) {
            seen.add(c.trim().toUpperCase());
        }

        for (String content : seen) {
            try {
                // 每個序號獨立 Transaction（對應 Laravel 的 DB::transaction() 在 foreach 中）
                transactionTemplate.execute(status -> {
                    // 鎖定單行並檢查
                    // 對應 Laravel: DB::table('serial_detail')->where('content', $content)->lockForUpdate()->first()
                    SerialDetail serial = detailRepository.findByContentWithLock(content)
                            .orElseThrow(() -> new RuntimeException("序號不存在"));

                    // 業務邏輯條件檢查
                    if (serial.getStatus() == 1) {
                        throw new RuntimeException("此序號已被核銷，無法進行註銷");
                    }
                    if (serial.getStatus() == 2) {
                        throw new RuntimeException("此序號已被註銷，請勿重複註銷");
                    }

                    // 執行註銷（status = 2）
                    // 對應 Laravel: DB::table('serial_detail')->where('id', $serial->id)->update([...])
                    serial.setStatus(2);
                    serial.setNote(note);           // 更新註銷原因
                    serial.setUpdatedAt(now);
                    detailRepository.save(serial);

                    return null;
                });

                successList.add(content);

            } catch (Exception e) {
                // 紀錄失敗原因，格式：序號 (原因)
                // 對應 Laravel: $results['fail'][] = "{$content} ({$e->getMessage()})"
                failList.add(content + " (" + e.getMessage() + ")");
            }
        }

        Map<String, List<String>> results = new LinkedHashMap<>();
        results.put("success", successList);
        results.put("fail", failList);
        return results;
    }

    // =========================================================================
    // 私有輔助方法
    // =========================================================================

    /**
     * 產生指定數量且保證不與資料庫重複的序號
     * 對應 Laravel SerialService::generateUniqueSerials()
     */
    private List<String> generateUniqueSerials(int quota) {
        List<String> finalSerials = new ArrayList<>();

        // 迴圈直到產滿為止（對應 Laravel 的 while (count($finalSerials) < $quota)）
        while (finalSerials.size() < quota) {
            int needed = quota - finalSerials.size();
            List<String> tempBatch = new ArrayList<>();

            // 產生一組候選序號
            for (int i = 0; i < needed; i++) {
                tempBatch.add(generateRandomString());
            }

            // 移除批次內重複項目（對應 Laravel: array_unique($tempBatch)）
            Set<String> uniqueBatch = new LinkedHashSet<>(tempBatch);

            // 批次查詢資料庫，找出已存在的序號
            // 對應 Laravel: DB::table('serial_detail')->whereIn('content', $tempBatch)->pluck('content')
            List<String> existing = detailRepository.findExistingContents(new ArrayList<>(uniqueBatch));
            Set<String> existingSet = new HashSet<>(existing);

            // 排除資料庫已存在的序號（對應 Laravel: array_diff($tempBatch, $existing)）
            for (String code : uniqueBatch) {
                if (!existingSet.contains(code) && finalSerials.size() < quota) {
                    finalSerials.add(code);
                }
            }
        }

        return finalSerials;
    }

    /**
     * 產生隨機序號（1 個大寫英文字母 + 7 位數字）
     * 對應 Laravel SerialService::generateRandomString()
     *
     * Laravel 版本：
     *   $letter = chr(rand(65, 90));                               // A-Z
     *   $numbers = str_pad(mt_rand(1, 9999999), 7, '0', STR_PAD_LEFT);
     *   return $letter . $numbers;
     *
     * Java 版本行為相同：1 個 A-Z 大寫字母 + 7 位數字（1～9999999，左補 0）
     */
    private String generateRandomString() {
        char letter = (char) ('A' + RANDOM.nextInt(26));        // A-Z
        int number = 1 + RANDOM.nextInt(9999999);               // 1～9999999
        String numbers = String.format("%07d", number);         // 左補 0 至 7 位
        return String.valueOf(letter) + numbers;
    }

    // =========================================================================
    // 工具方法（供 Controller 層使用）
    // =========================================================================

    /**
     * 解析日期字串
     * @throws DateTimeParseException 若格式不符合 yyyy-MM-dd HH:mm:ss
     */
    public static LocalDateTime parseDateTime(String dateStr) {
        return LocalDateTime.parse(dateStr, DATE_TIME_FORMATTER);
    }
}
