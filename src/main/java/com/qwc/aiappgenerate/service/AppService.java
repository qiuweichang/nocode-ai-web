package com.qwc.aiappgenerate.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.qwc.aiappgenerate.model.dto.app.AppQueryRequest;
import com.qwc.aiappgenerate.model.entity.App;
import com.qwc.aiappgenerate.model.entity.User;
import com.qwc.aiappgenerate.model.vo.AppVO;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 */
public interface AppService extends IService<App> {

    /**
     * 校验数据
     *
     * @param app
     * @param add
     */
    void validApp(App app, boolean add);

    /**
     * 获取查询条件
     *
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取应用 VO
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * @apiNote
     * @author qiuweichang
     * @since 2026/2/10 17:50
     **/
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * @apiNote TODO
     * @author qiuweichang
     * @since 2026/2/10 18:01 
     **/
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * @apiNote 部署应用
     * @author qiuweichang
     * @since 2026/3/18 16:05
     **/
    String deployApp(Long appId, User loginUser);
}
