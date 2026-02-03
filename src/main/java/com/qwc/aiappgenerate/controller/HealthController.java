package com.qwc.aiappgenerate.controller;

import com.qwc.aiappgenerate.common.BaseResponse;
import com.qwc.aiappgenerate.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 *
 * @author qiuwc
 * @since 2026/2/3 14:07
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> healthCheck() {
        return ResultUtils.success("ok");
    }
}
