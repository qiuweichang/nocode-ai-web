package com.qwc.aiappgenerate.model.vo;

import cn.hutool.core.bean.BeanUtil;
import com.qwc.aiappgenerate.model.entity.App;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用 VO
 */
@Data
public class AppVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    private String codeGenType;

    /**
     * 部署标识
     */
    private String deployKey;

    /**
     * 部署时间
     */
    private LocalDateTime deployedTime;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否已经存在可预览的生成代码。
     * 首页据此决定展示真实页面缩略预览还是空项目占位图，避免 iframe 请求不存在的目录。
     */
    private Boolean hasGeneratedCode;

    /**
     * 创建用户信息
     */
    private UserVO user;

    private static final long serialVersionUID = 1L;
}
