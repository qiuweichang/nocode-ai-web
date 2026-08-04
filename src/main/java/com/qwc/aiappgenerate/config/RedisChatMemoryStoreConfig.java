package com.qwc.aiappgenerate.config;

import cn.hutool.core.util.StrUtil;
import com.qwc.aiappgenerate.store.PasswordCompatibleRedisChatMemoryStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 持久化对话记忆配置。
 * 该配置统一创建项目内自定义的 ChatMemoryStore，以兼容仅密码认证和 ACL 认证两种 Redis 部署模式。
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {

    /**
     * Redis 主机地址。
     */
    private String host;

    /**
     * Redis 端口。
     */
    private int port;

    /**
     * Redis ACL 用户名。
     * 仅当 Redis 服务端显式开启 ACL 并要求用户名认证时才需要配置。
     */
    private String username;

    /**
     * Redis 数据库编号。
     */
    private int database;

    /**
     * Redis 认证密码。
     */
    private String password;

    /**
     * 聊天记忆在 Redis 中的过期时间，单位为秒。
     */
    private long ttl;

    /**
     * 创建 Redis 对话记忆存储 Bean。
     * 这里不再直接复用三方 RedisChatMemoryStore，而是通过 JedisClientConfig 显式传递密码、
     * 用户名和 database，确保不同 Redis 认证模式下都能稳定连接。
     *
     * @return Redis 持久化对话记忆存储实例
     */
    @Bean
    public ChatMemoryStore redisChatMemoryStore() {
        return new PasswordCompatibleRedisChatMemoryStore(
                host,
                port,
                database,
                StrUtil.blankToDefault(username, null),
                StrUtil.blankToDefault(password, null),
                "",
                ttl
        );
    }
}
