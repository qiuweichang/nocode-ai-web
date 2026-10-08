package com.qwc.aiappgenerate.service;

import com.qwc.aiappgenerate.model.dto.design.DesignReviseRequest;
import com.qwc.aiappgenerate.model.entity.User;
import com.qwc.aiappgenerate.model.vo.DesignWorkflowVO;

/**
 * 页面样式设计工作流服务。
 * 负责 Stitch 生成、局部修改、版本确认和为代码模型提供已确认设计上下文。
 */
public interface DesignWorkflowService {

    /**
     * 读取当前用户拥有应用的设计状态。
     *
     * @param appId 应用 ID
     * @param loginUser 当前登录用户
     * @return 设计工作流视图
     */
    DesignWorkflowVO getDesign(Long appId, User loginUser);

    /**
     * 根据应用初始需求生成首版 Stitch 桌面样式。
     *
     * @param appId 应用 ID
     * @param prompt 用户页面需求
     * @param loginUser 当前登录用户
     * @return 已落盘的设计状态
     */
    DesignWorkflowVO generateInitialDesign(Long appId, String prompt, User loginUser);

    /**
     * 基于当前版本和可选元素上下文生成下一版样式。
     *
     * @param request 样式修改请求
     * @param loginUser 当前登录用户
     * @return 修改后的设计状态
     */
    DesignWorkflowVO reviseDesign(DesignReviseRequest request, User loginUser);

    /**
     * 确认用户当前看到的样式版本，确认后才允许首轮代码生成。
     *
     * @param appId 应用 ID
     * @param revisionNumber 待确认版本号
     * @param loginUser 当前登录用户
     * @return 确认后的设计状态
     */
    DesignWorkflowVO confirmDesign(Long appId, Integer revisionNumber, User loginUser);

    /**
     * 判断应用是否已确认设计。
     *
     * @param appId 应用 ID
     * @return 已确认返回 true
     */
    boolean isDesignConfirmed(Long appId);

    /**
     * 把已确认 Stitch HTML 追加到首轮代码生成提示词中。
     *
     * @param appId 应用 ID
     * @param userMessage 用户原始需求
     * @return 携带已确认设计源码的完整生成指令
     */
    String buildConfirmedDesignContext(Long appId, String userMessage);
}
