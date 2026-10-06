package io.github.bytex0.excel.example;

import io.github.bytex0.common.model.ApiResponse;
import io.github.bytex0.excel.example.LegacyExcelService.ImportReport;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 原 Excel 入口测试接口(LegacyExcelController)暴露完整处理器流程和真实资源清理状态。
 *
 * @author linshiqiang
 * @since 2026-10-06 11:23:54
 */
@RestController
@RequestMapping("/api/excel/legacy")
public class LegacyExcelController {

    /**
     * 原处理器的业务回调实现。
     */
    private final LegacyExcelService service;

    /**
     * 注入实际集成服务。
     *
     * @param service 服务
     */
    public LegacyExcelController(LegacyExcelService service) {
        this.service = service;
    }

    /**
     * 下载普通、多 Sheet 或 ZIP 工作簿。
     *
     * @param mode simple、multi 或 zip
     * @param count 合成数据量
     * @param pageSize 查询批次大小
     * @param rowsPerSheet 每 Sheet/文件数据行上限
     * @param response HTTP 响应
     * @throws Exception 导出失败时抛出
     */
    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "multi") String mode,
                       @RequestParam(defaultValue = "11") int count,
                       @RequestParam(defaultValue = "4") int pageSize,
                       @RequestParam(defaultValue = "5") int rowsPerSheet,
                       HttpServletResponse response) throws Exception {
        service.export(mode, count, pageSize, rowsPerSheet, response);
    }

    /**
     * 通过旧上下文执行普通或有界并发导入。
     *
     * @param file 实际上传文件
     * @param mode simple 或 large
     * @param sheet 零基 Sheet
     * @param batchSize 消费批次
     * @param transactional 是否启用批次事务
     * @param continueOnError 是否继续失败批次
     * @param failAt 用于验证回滚的合成编号
     * @return 实际导入报告
     * @throws Exception 导入失败时抛出
     */
    @PostMapping("/import")
    public ApiResponse<ImportReport> importFile(@RequestPart MultipartFile file,
                                               @RequestParam(defaultValue = "large") String mode,
                                               @RequestParam(defaultValue = "0") int sheet,
                                               @RequestParam(defaultValue = "2") int batchSize,
                                               @RequestParam(defaultValue = "true") boolean transactional,
                                               @RequestParam(defaultValue = "false") boolean continueOnError,
                                               @RequestParam(defaultValue = "-1") int failAt) throws Exception {
        return ApiResponse.ok(service.importFile(file, mode, sheet, batchSize, transactional, continueOnError, failAt));
    }

    /**
     * 返回独立测试环境中残留文件和行数。
     *
     * @return 清理状态
     * @throws IOException 目录读取失败时抛出
     */
    @GetMapping("/state")
    public ApiResponse<Map<String, Long>> state() throws IOException {
        return ApiResponse.ok(service.state());
    }

    /**
     * 失败时不返回 SQL、文件路径或原始行正文。
     *
     * @return 统一错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> invalid() {
        return ResponseEntity.badRequest().body(ApiResponse.failOfMessage("Excel参数或批次处理失败", 400));
    }
}
