package com.example.serial.filter;

import com.example.serial.model.SerialLog;
import com.example.serial.repository.SerialLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * API 請求日誌 Filter
 * 對應 Laravel: app/Http/Middleware/ApiLogger.php
 *
 * 功能：記錄所有 /api/* 請求的請求與回應內容至 serial_log 資料表
 *
 * 注意（Java vs Laravel 差異）：
 *   Laravel Middleware 透過 ->middleware('api.logger') 逐路由套用
 *   Java Filter 透過 URL Pattern 過濾，僅記錄 /api/ 開頭的請求（行為等同）
 *
 *   Laravel 可直接讀取 $response->getContent() 取得回應內容
 *   Java 的 HttpServletResponse 輸出流一旦寫入就無法重讀，
 *   因此必須使用 ContentCachingResponseWrapper 包裝，在 Filter Chain 執行後讀取快取內容
 *   最後必須呼叫 responseWrapper.copyBodyToResponse() 將內容實際輸出給客戶端
 *
 *   Laravel route name（如 "批次新增序號"）透過靜態 Map 對應 URI 取得
 */
@Component
public class ApiLoggerFilter extends OncePerRequestFilter {

    @Autowired
    private SerialLogRepository serialLogRepository;

    /**
     * URI 到 API 名稱的對應表
     * 對應 Laravel: $request->route()->getName()（路由命名）
     */
    private static final Map<String, String> API_NAME_MAP = Map.of(
            "/api/serials_insert",             "批次新增序號",
            "/api/serials_additional_insert",  "批次追加序號",
            "/api/serials_redeem",             "核銷序號",
            "/api/serials_cancel",             "批次註銷序號"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 1. 記錄請求開始時間（對應 Laravel: $requestAt = now()）
        LocalDateTime requestAt = LocalDateTime.now();

        // 2. 包裝 Request 和 Response 以支援內容快取
        //    對應 Laravel 直接讀取 $request->all() 和 $response->getContent()
        // Spring Boot 4.x (Spring 7) 的 ContentCachingRequestWrapper 需要第二個參數 contentCacheLimit
        // 1MB 上限，足以涵蓋所有 API 請求的 JSON body
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 1024 * 1024);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        // 3. 執行後續 Filter Chain（即執行 Controller 邏輯）
        //    對應 Laravel: $response = $next($request)
        filterChain.doFilter(requestWrapper, responseWrapper);

        // 4. 記錄回應結束時間（對應 Laravel: $responseAt = now()）
        LocalDateTime responseAt = LocalDateTime.now();

        // 5. 讀取請求內容（JSON body）
        //    對應 Laravel: json_encode($request->all(), JSON_UNESCAPED_UNICODE)
        //    說明：ContentCachingRequestWrapper 在 Controller 讀取 @RequestBody 後，
        //          快取已填充，此時可以安全讀取
        byte[] requestBody = requestWrapper.getContentAsByteArray();
        String requestJson = requestBody.length > 0
                ? new String(requestBody, StandardCharsets.UTF_8)
                : "{}";

        // 6. 讀取回應內容（JSON）
        //    對應 Laravel: json_encode(json_decode($response->getContent(), true))
        byte[] responseBody = responseWrapper.getContentAsByteArray();
        String responseJson = responseBody.length > 0
                ? new String(responseBody, StandardCharsets.UTF_8)
                : "{}";

        // 7. 取得 API 名稱（對應 Laravel: $request->route()->getName()）
        String requestUri = request.getRequestURI();
        String apiName = API_NAME_MAP.getOrDefault(requestUri, "未定義 API");

        // 8. 取得完整 URL（對應 Laravel: $request->fullUrl()）
        String fullUrl = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            fullUrl += "?" + queryString;
        }

        // 9. 寫入資料庫（對應 Laravel: DB::table('serial_log')->insert([...])）
        try {
            SerialLog log = new SerialLog();
            log.setApiName(apiName);
            log.setHost(request.getRemoteAddr());   // 對應 Laravel: $request->ip()
            log.setApi(fullUrl);                    // 對應 Laravel: $request->fullUrl()
            log.setRequest(requestJson);            // 對應 Laravel: json_encode($request->all())
            log.setRequestAt(requestAt);
            log.setResponse(responseJson);
            log.setResponseAt(responseAt);
            log.setCreatedAt(requestAt);            // 對應 Laravel: 'created_at' => $requestAt

            serialLogRepository.save(log);
        } catch (Exception e) {
            // 日誌寫入失敗不應影響 API 回應（靜默失敗）
            // 對應 Laravel 的 Middleware 行為（若 insert 失敗，Laravel 會拋出 500）
            // 這裡選擇靜默處理，避免日誌問題影響核心業務
            logger.error("ApiLogger 寫入失敗: " + e.getMessage(), e);
        }

        // 10. 關鍵步驟：將快取的回應內容實際輸出給客戶端
        //     若省略此步驟，客戶端會收到空回應
        //     對應 Laravel 的 return $response（框架自動輸出）
        responseWrapper.copyBodyToResponse();
    }

    /**
     * 僅攔截 /api/ 路徑下的請求
     * 對應 Laravel routes/api.php 中套用 middleware('api.logger') 的路由範圍
     *
     * shouldNotFilter() 返回 true 表示「不過濾此請求」（即不記錄日誌）
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
