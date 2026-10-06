package io.github.bytex0.excel;

import io.github.bytex0.excel.config.ExcelAutoConfiguration;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Excel(ExcelTemplateTest)边界拆分、批次导入与失败传播测试
 *
 * @author bytex0
 * @since 2026-10-05 15:36:55
 */
class ExcelTemplateTest {

    /**
     * 被测模板
     */
    private final ExcelTemplate template = new ExcelTemplate();

    /**
     * 默认模板、关闭开关及用户覆盖保持有效。
     */
    @Test
    void shouldConfigureDisableAndOverride() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class));
        runner.run(context -> assertThat(context).hasSingleBean(ExcelTemplate.class));
        runner.withPropertyValues("excel.enabled=false").run(context -> assertThat(context).doesNotHaveBean(ExcelTemplate.class));
        runner.withBean(ExcelTemplate.class, () -> template).run(context ->
                assertThat(context.getBean(ExcelTemplate.class)).isSameAs(template));
    }

    /**
     * 不整除批次在多个 Sheet 间拆分时不能丢失或重复行。
     *
     * @throws Exception 工作簿解析失败时抛出
     */
    @Test
    void shouldSplitInsideBatchesWithoutLostOrDuplicatedRows() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        template.write(output, Row.class, "invalid/name", rows(11), 5);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(5);
            assertThat(workbook.getSheetAt(1).getLastRowNum()).isEqualTo(5);
            assertThat(workbook.getSheetAt(2).getLastRowNum()).isEqualTo(1);
        }
        List<Integer> ids = new ArrayList<>();
        for (int sheet = 0; sheet < 3; sheet++) {
            template.read(new ByteArrayInputStream(output.toByteArray()), Row.class, sheet, 3,
                    batch -> batch.forEach(row -> ids.add(row.getId())), false);
        }
        assertThat(ids).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
    }

    /**
     * 空数据仍生成有效工作簿，ZIP 拆分保持行数上限。
     *
     * @throws Exception 工作簿或 ZIP 解析失败时抛出
     */
    @Test
    void shouldProduceValidEmptyWorkbookAndZip() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        template.write(output, Row.class, "Empty", List::of, 5);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
        }
        output.reset();
        template.writeZip(output, Row.class, rows(11), 5);
        List<Integer> counts = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            while (zip.getNextEntry() != null) {
                byte[] data = zip.readAllBytes();
                try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
                    counts.add(workbook.getSheetAt(0).getLastRowNum());
                }
            }
        }
        assertThat(counts).containsExactly(5, 5, 1);
    }

    /**
     * 数据来源失败和非法上限须立即传播，不挂起等待。
     */
    @Test
    void shouldPropagateProducerFailureWithoutQueueDeadlock() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(() -> template.write(new ByteArrayOutputStream(), Row.class, "Data", () -> {
                    throw new IllegalStateException("source failed");
                }, 5)).isInstanceOf(RuntimeException.class));
        assertThatThrownBy(() -> template.write(new ByteArrayOutputStream(), Row.class, "Data", List::of, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 继续模式统计整个失败批次，默认模式遇错终止。
     */
    @Test
    void shouldCountFailedBatchesAndStopByDefault() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        template.write(output, Row.class, "Data", rows(4), 10);
        ExcelTemplate.ImportResult result = template.read(new ByteArrayInputStream(output.toByteArray()),
                Row.class, 0, 2, batch -> {
                    throw new IllegalStateException("consumer failed");
                }, true);
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.failed()).isEqualTo(4);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(() -> template.read(new ByteArrayInputStream(output.toByteArray()),
                        Row.class, 0, 2, batch -> {
                            throw new IllegalStateException("consumer failed");
                        }, false))
                        .isInstanceOf(RuntimeException.class));
    }

    /**
     * 按四行批次提供合成数据。
     *
     * @param count 总行数
     * @return 顺序数据来源
     */
    private Supplier<List<Row>> rows(int count) {
        AtomicInteger current = new AtomicInteger(1);
        return () -> {
            List<Row> result = new ArrayList<>();
            for (int index = 0; index < 4 && current.get() <= count; index++) {
                Row row = new Row();
                row.setId(current.getAndIncrement());
                result.add(row);
            }
            return result;
        };
    }

    /**
     * 测试行(Row)数据模型
     *
     * @author bytex0
     * @since 2026-10-05 15:36:55
     */
    public static class Row {

        /**
         * 行编号
         */
        @ExcelProperty("ID")
        private Integer id;

        /**
         * 获取行号。
         *
         * @return 编号
         */
        public Integer getId() {
            return id;
        }

        /**
         * 设置行号。
         *
         * @param id 编号
         */
        public void setId(Integer id) {
            this.id = id;
        }
    }
}
