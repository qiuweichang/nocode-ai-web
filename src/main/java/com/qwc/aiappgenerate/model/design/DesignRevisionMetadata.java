package com.qwc.aiappgenerate.model.design;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单个 Stitch 样式版本的持久化元数据。
 * HTML 和截图本体保存在 revision_{number} 目录，本对象只保存版本关系和可追溯提示词。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignRevisionMetadata {

    /** 从 1 开始递增的本地版本号。 */
    private Integer number;

    /** Stitch 返回的 screen ID，后续 edit_screens 以它为修改基线。 */
    private String screenId;

    /** 生成该版本时实际发送给 Stitch 的完整提示词。 */
    private String prompt;

    /** 选中元素的简短描述；整页修改时为空。 */
    private String selectedElementSummary;

    /** 相对设计根目录的 HTML 文件路径。 */
    private String htmlPath;

    /** 相对设计根目录的截图文件路径，供应商未返回截图时可为空。 */
    private String screenshotPath;

    /** 版本成功落盘的时间。 */
    private LocalDateTime createdAt;
}
