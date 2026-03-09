package com.example.serial.controller.admin;

import com.example.serial.model.SerialActivity;
import com.example.serial.model.SerialDetail;
import com.example.serial.repository.SerialDetailRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.persistence.criteria.*;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 後台管理 Controller - 序號列表與匯出
 * 對應 Laravel: app/Http/Controllers/Admin/SerialAdminController.php
 *
 * 路由對應：
 *   GET /admin/serials              → index()
 *   GET /admin/serials/export       → export()
 *   GET /admin/serials/export-ajax  → export()（同一方法，支援 AJAX 匯出方式）
 */
@Controller
@RequestMapping("/admin/serials")
public class SerialAdminController {

    @Autowired
    private SerialDetailRepository detailRepository;

    private static final int PAGE_SIZE = 15;    // 對應 Laravel: paginate(15)
    private static final int CHUNK_SIZE = 1000; // 對應 Laravel: chunk(1000, ...)

    // 日期時間格式：對應 Laravel Carbon 的預設輸出格式 "Y-m-d H:i:s"（無毫秒、空格分隔）
    private static final DateTimeFormatter DT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // =========================================================================
    // 共用查詢條件建構（對應 Laravel SerialAdminController::common_data()）
    // =========================================================================

    /**
     * 動態建構查詢 Specification（等同 Laravel 的 $query Builder 邏輯）
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel 使用 Eloquent Builder 鏈式方法動態組合查詢
     *   Java 使用 JPA Specification 模式（等同 Criteria API），達到相同的動態查詢效果
     *
     * @param keyword     關鍵字（搜尋活動名稱或活動唯一 ID）
     * @param content     序號
     * @param status      狀態（0/1/2）
     * @param dateStart   開始日期（格式：yyyy-MM-dd）
     * @param dateEnd     結束日期（格式：yyyy-MM-dd）
     */
    private Specification<SerialDetail> buildSpec(
            String keyword, String orderno, String content, Integer status,
            String dateStart, String dateEnd) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ---- 關鍵字搜尋（搜尋活動名稱或活動唯一 ID）----
            // 對應 Laravel:
            //   $query->whereHas('activity', function($q) use ($keyword) {
            //       $q->where('activity_name', 'like', "%{$keyword}%")
            //         ->orWhere('activity_unique_id', 'like', "%{$keyword}%");
            //   });
            if (keyword != null && !keyword.trim().isEmpty()) {
                String k = "%" + keyword.trim() + "%";
                Join<SerialDetail, SerialActivity> activityJoin = root.join("activity", JoinType.INNER);
                predicates.add(cb.or(
                        cb.like(activityJoin.get("activityName"), k),
                        cb.like(activityJoin.get("activityUniqueId"), k)
                ));
            }

            // ---- 訂單編號搜尋（精確搜尋）----
            // 對應 Laravel: $query->where('orderno', trim($request->orderno))
            if (orderno != null && !orderno.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("orderno"), orderno.trim()));
            }

            // ---- 序號搜尋（精確搜尋）----
            // 對應 Laravel: $query->where('content', trim($request->content))
            if (content != null && !content.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("content"), content.trim()));
            }

            // ---- 狀態篩選 ----
            // 對應 Laravel: $query->where('status', $request->status)
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // ---- 日期區間篩選 ----
            // 對應 Laravel:
            //   $query->where(function($q) use ($request) {
            //       if ($request->filled('date_start'))
            //           $q->where('start_date', '>=', $request->date_start . ' 00:00:00');
            //       if ($request->filled('date_end'))
            //           $q->where('end_date', '<=', $request->date_end . ' 23:59:59');
            //   });
            if ((dateStart != null && !dateStart.isEmpty()) ||
                    (dateEnd != null && !dateEnd.isEmpty())) {

                List<Predicate> datePredicates = new ArrayList<>();
                if (dateStart != null && !dateStart.isEmpty()) {
                    LocalDateTime startDt = LocalDate.parse(dateStart).atStartOfDay(); // 00:00:00
                    datePredicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDt));
                }
                if (dateEnd != null && !dateEnd.isEmpty()) {
                    LocalDateTime endDt = LocalDate.parse(dateEnd).atTime(23, 59, 59); // 23:59:59
                    datePredicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDt));
                }
                // 使用 and 組合日期條件（對應 Laravel 的 where closure）
                predicates.add(cb.and(datePredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // =========================================================================
    // GET /admin/serials - 後台序號列表
    // 對應 Laravel SerialAdminController::index()
    // =========================================================================

    /**
     * 後台序號管理列表
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel paginate(15) 使用 page=1 作為第一頁
     *   Spring Pageable 使用 0-indexed（page=0 為第一頁）
     *   Controller 接收 page 參數時預設為 1（符合 Laravel 行為），並轉換為 0-indexed 給 Spring
     */
    @GetMapping
    public String index(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderno,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Integer status,
            @RequestParam(name = "date_start", required = false) String dateStart,
            @RequestParam(name = "date_end", required = false) String dateEnd,
            @RequestParam(defaultValue = "1") int page,
            Model model) {

        Specification<SerialDetail> spec = buildSpec(keyword, orderno, content, status, dateStart, dateEnd);

        // 對應 Laravel: $query->paginate(15)->withQueryString()
        // page 參數為 1-indexed（Laravel 預設），轉為 0-indexed 給 Spring
        PageRequest pageable = PageRequest.of(
                Math.max(0, page - 1),  // 1-indexed → 0-indexed
                PAGE_SIZE,
                Sort.by("id").descending()  // 對應 Laravel: orderBy('id', 'desc')
        );

        Page<SerialDetail> list = detailRepository.findAll(spec, pageable);

        // 產生頁碼列表（對應 Laravel pagination::bootstrap-5 的頁碼生成）
        List<Integer> pageNumbers = IntStream.rangeClosed(1, list.getTotalPages())
                .boxed().toList();

        // 傳遞搜尋條件至模板（對應 Laravel: withQueryString() 保留 URL 參數）
        model.addAttribute("list", list);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("currentPage", page);

        // 傳遞搜尋條件（用於分頁連結和表單預填值）
        model.addAttribute("keyword", keyword);
        model.addAttribute("orderno", orderno);
        model.addAttribute("searchContent", content);   // 避免與 Thymeleaf 的 content 保留字衝突
        model.addAttribute("status", status);
        model.addAttribute("dateStart", dateStart);
        model.addAttribute("dateEnd", dateEnd);

        return "admin/serials/index";
    }

    // =========================================================================
    // GET /admin/serials/export - CSV 匯出（直接下載）
    // GET /admin/serials/export-ajax - CSV 匯出（AJAX 方式）
    // 對應 Laravel SerialAdminController::export()
    //
    // 注意：export 和 export-ajax 對應同一個方法
    // 前端 AJAX 按鈕呼叫 /admin/serials/export，JavaScript 接收 blob 並觸發下載
    // 行為與 Laravel StreamedResponse 相同
    // =========================================================================

    /**
     * CSV 匯出（串流方式，避免記憶體耗盡）
     *
     * 注意（Java vs Laravel 差異）：
     *   Laravel 使用 StreamedResponse + chunk(1000, ...) 串流輸出
     *   Java 直接寫入 HttpServletResponse.getOutputStream()，等同串流輸出
     *   Laravel chunk() 每次處理 1000 筆，Java 使用 Pageable 分頁循環達到相同效果
     *
     *   Laravel 設定 X-Suggested-Filename Header（非標準 Header）
     *   前端 JavaScript 讀取此 Header 作為下載檔名（同源請求，無 CORS 問題）
     */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderno,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Integer status,
            @RequestParam(name = "date_start", required = false) String dateStart,
            @RequestParam(name = "date_end", required = false) String dateEnd,
            HttpServletResponse response) throws IOException {

        Specification<SerialDetail> spec = buildSpec(keyword, orderno, content, status, dateStart, dateEnd);

        // 設定 Response Header（對應 Laravel $headers 設定）
        String fileName = "serial_export_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
        response.setContentType("text/csv");
        response.setHeader("X-Suggested-Filename", fileName);  // 對應 Laravel 的自訂 Header

        // 使用 UTF-8 + BOM 寫入，防止 Excel 開啟亂碼
        // 對應 Laravel: fprintf($handle, chr(0xEF).chr(0xBB).chr(0xBF))
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("\uFEFF"); // UTF-8 BOM

        // 使用 Apache Commons CSV（對應 Laravel fputcsv）
        // try-with-resources：離開區塊時自動呼叫 printer.close()，
        // close() 內部會先 flush() 再釋放資源，等同 PHP 的 fclose($handle)
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            // 寫入標題列（對應 Laravel: fputcsv($handle, ['活動名稱', ...])）
            printer.printRecord("活動名稱", "活動唯一ID", "訂單編號", "序號", "狀態", "更新時間",
                    "有效期限（起）", "有效期限（迄）", "備註說明", "新增時間");

            // 批次處理資料（對應 Laravel: $query->chunk(1000, function ($serials) use ($handle) {...})）
            // 使用 Pageable 分頁循環，每次處理 CHUNK_SIZE 筆
            int pageNum = 0;
            Page<SerialDetail> chunk;
            do {
                PageRequest pageable = PageRequest.of(pageNum, CHUNK_SIZE, Sort.by("id").descending());
                chunk = detailRepository.findAll(spec, pageable);

                for (SerialDetail row : chunk.getContent()) {
                    // 狀態文字轉換（對應 Laravel: match ($row->status) {...}）
                    String statusText = switch (row.getStatus()) {
                        case 0 -> "未核銷";
                        case 1 -> "已核銷";
                        case 2 -> "已註銷";
                        default -> "未設定";
                    };

                    SerialActivity activity = row.getActivity();
                    // 日期格式化：LocalDateTime 預設 toString() 輸出 "2026-02-25T10:47:27.600"（含 T 和毫秒）
                    // 使用 DT_FORMATTER 轉換為 "2026-02-25 10:47:27"（空格分隔、無毫秒）
                    // 對應 Laravel 的 $row->updated_at（Carbon 物件自動格式化為 Y-m-d H:i:s）
                    printer.printRecord(
                            activity != null ? activity.getActivityName() : "N/A",
                            activity != null ? activity.getActivityUniqueId() : "-",
                            row.getOrderno() != null ? row.getOrderno() : "-",
                            row.getContent(),
                            statusText,
                            row.getUpdatedAt() != null ? row.getUpdatedAt().format(DT_FORMATTER) : "--",
                            row.getStartDate() != null ? row.getStartDate().format(DT_FORMATTER) : "",
                            row.getEndDate() != null ? row.getEndDate().format(DT_FORMATTER) : "",
                            row.getNote() != null ? row.getNote() : "",
                            row.getCreatedAt() != null ? row.getCreatedAt().format(DT_FORMATTER) : ""
                    );
                }

                pageNum++;
            } while (chunk.hasNext()); // 對應 Laravel while 的繼續條件

        } // printer.close() 自動執行：flush → 釋放資源
    }
}
