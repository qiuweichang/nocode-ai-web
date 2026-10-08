package com.qwc.aiappgenerate.model.dto.design;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改当前样式方案请求。
 * 选择器等字段来自同源设计预览 iframe，只作为设计服务精准理解修改范围的上下文，不直接执行 DOM 写入。
 */
@Data
public class DesignReviseRequest implements Serializable {

    /** 当前应用 ID。 */
    private Long appId;

    /** 用户对样式的修改要求。 */
    private String prompt;

    /**
     * 用户当前预览的基线版本号。
     * 允许从任意历史版本派生新版本；为空时由服务端兼容性回退到最新版本。
     */
    private Integer baseRevisionNumber;

    /** 被选元素所在的本地设计预览路径。 */
    private String pagePath;

    /** 被选元素标签名。 */
    private String tagName;

    /** 被选元素的稳定 CSS 选择器。 */
    private String selector;

    /** 被选元素 ID，可为空。 */
    private String elementId;

    /** 被选元素类名，可为空。 */
    private String className;

    /** 被选元素当前可见文本，可为空。 */
    private String textContent;
}
