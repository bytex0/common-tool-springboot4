package io.github.bytex0.excel.example;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.excel.ExcelTemplate;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.fesod.sheet.exception.ExcelAnalysisException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Excel接口(ExcelController)验证分页、分Sheet、ZIP及导入
 *
 * @author bytex0
 * @since 2026-10-05 15:36:55
 */
@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    /**
     * Starter实际提供的模板
     */
    private final ExcelTemplate template;

    /**
     * 注入自动配置的流式模板。
     *
     * @param template Excel 模板
     */
    public ExcelController(ExcelTemplate template) {
        this.template = template;
    }

    /**
     * 下载合成数据，验证现有模板 API 未被兼容处理器替代。
     *
     * @param count 行数，范围 0 至 10000
     * @param rowsPerSheet 每 Sheet 数据行数
     * @param zip 是否为 ZIP
     * @param response 响应
     * @throws IOException 输出失败时抛出
     */
    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "11") int count,
                       @RequestParam(defaultValue = "5") int rowsPerSheet,
                       @RequestParam(defaultValue = "false") boolean zip, HttpServletResponse response) throws IOException {
        Assert.isTrue(count >= 0 && count <= 10000 && rowsPerSheet > 0 && rowsPerSheet <= 1000000, "示例行数不合法");
        response.setContentType(zip ? "application/zip"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", ContentDisposition.attachment()
                .filename(zip ? "数据.zip" : "数据.xlsx", StandardCharsets.UTF_8).build().toString());
        if (zip) {
            template.writeZip(response.getOutputStream(), ExcelRow.class, rows(count), rowsPerSheet);
        } else {
            template.write(response.getOutputStream(), ExcelRow.class, "数据", rows(count), rowsPerSheet);
        }
    }

    /**
     * 读取指定 Sheet 并返回实际行序。
     *
     * @param file 上传文件
     * @param sheet 零基 Sheet 序号
     * @return 行号及计数
     * @throws IOException 打开输入流失败时抛出
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importFile(@RequestPart MultipartFile file,
                                                       @RequestParam(defaultValue = "0") int sheet) throws IOException {
        List<Integer> ids = new ArrayList<>();
        try (InputStream input = file.getInputStream()) {
            ExcelTemplate.ImportResult result = template.read(input, ExcelRow.class, sheet, 3,
                    batch -> batch.forEach(row -> ids.add(row.getId())), false);
            return ApiResponse.ok(Map.of("ids", ids, "total", result.total(), "failed", result.failed()));
        }
    }

    /**
     * 隐藏底层文件或解析细节的错误响应。
     *
     * @return 参数错误
     */
    @ExceptionHandler({IllegalArgumentException.class, ExcelAnalysisException.class})
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("请求参数不合法", 400));
    }

    /**
     * 按四行一批产生确定性的中文示例数据。
     *
     * @param count 总行数
     * @return 批次来源
     */
    private Supplier<List<ExcelRow>> rows(int count) {
        AtomicInteger next = new AtomicInteger(1);
        return () -> {
            List<ExcelRow> batch = new ArrayList<>();
            for (int index = 0; index < 4 && next.get() <= count; index++) {
                ExcelRow row = new ExcelRow();
                row.setId(next.getAndIncrement());
                row.setName("名称" + row.getId());
                batch.add(row);
            }
            return batch;
        };
    }
}
