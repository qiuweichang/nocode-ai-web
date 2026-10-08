package com.qwc.aiappgenerate.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前端可见的样式版本摘要，不暴露服务器文件系统绝对路径。
 */
@Data
@Builder
public class DesignRevisionVO {

    /** 本地版本号。 */
    private Integer number;

    /** 可由 iframe 直接加载的同源 HTML 预览路径。 */
    private String previewPath;

    /** 可由浏览器加载的截图路径，可为空。 */
    private String screenshotPath;

    /** 本版修改要求。 */
    private String prompt;

    /** 本版是否为当前已确认版本。 */
    private Boolean confirmed;

    /** 本版生成时间。 */
    private LocalDateTime createdAt;
}
