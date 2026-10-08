package com.qwc.aiappgenerate.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用源码保存请求。
 * 用于把在线编辑器中的文本内容写回指定应用的生成目录。
 */
@Data
public class AppFileSaveRequest implements Serializable {

    /** 应用 ID，用于权限校验和定位对应的代码生成目录。 */
    private Long appId;

    /** 文件相对于应用生成目录的路径，禁止使用绝对路径或跳出项目目录。 */
    private String path;

    /** 编辑器提交的完整 UTF-8 文本内容。 */
    private String content;

    private static final long serialVersionUID = 1L;
}
