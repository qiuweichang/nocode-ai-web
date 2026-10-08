package com.qwc.aiappgenerate.integration.stitch;

import lombok.Builder;
import lombok.Data;

/**
 * Stitch 单次生成或修改返回的可落盘设计产物。
 * 真实模式使用签名下载地址；本地验收模式使用 inlineHtml，二者由工作流服务统一保存。
 */
@Data
@Builder
public class StitchScreenArtifact {

    /** Stitch 项目 ID。 */
    private String projectId;

    /** Stitch 屏幕 ID。 */
    private String screenId;

    /** Stitch 导出的 HTML 签名下载地址。 */
    private String htmlDownloadUrl;

    /** Stitch 导出的截图签名下载地址。 */
    private String screenshotDownloadUrl;

    /** 仅本地验收模式使用的内联 HTML。 */
    private String inlineHtml;

    /** 当前产物是否来自显式开启的本地验收模式。 */
    private boolean mock;
}
