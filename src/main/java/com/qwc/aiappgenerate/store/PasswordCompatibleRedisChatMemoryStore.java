package com.qwc.aiappgenerate.store;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

/**
 * 兼容密码认证模式的 Redis 对话记忆存储。
 * 之所以不直接使用 langchain4j-community-redis 提供的 RedisChatMemoryStore，
 * 是因为当前版本在未传 user 时会退化为无认证连接，导致仅密码认证的 Redis 无法使用。
 * 该实现直接基于 JedisClientConfig 组装认证参数，从而同时兼容：
 * 1. 仅密码认证
 * 2. ACL 用户名 + 密码认证
 * 3. 指定 Redis database 和 TTL
 */
public class PasswordCompatibleRedisChatMemoryStore implements ChatMemoryStore {

    /**
     * Redis 客户端连接池。
     * 该客户端在构造时即绑定认证、库编号等配置，后续对每个 memoryId 复用同一连接池。
     */
    private final JedisPooled jedisPooled;

    /**
     * Redis key 前缀。
     * 用于隔离聊天记忆和其他业务 key，避免不同模块之间发生 key 冲突。
     */
    private final String keyPrefix;

    /**
     * 聊天记忆过期时间，单位秒。
     * 小于等于 0 时表示不过期。
     */
    private final long ttlSeconds;

    /**
     * 创建 Redis 对话记忆存储实例。
     *
     * @param host Redis 主机地址
     * @param port Redis 端口
     * @param database Redis 库编号
     * @param username Redis ACL 用户名；仅密码认证时可为空
     * @param password Redis 密码；无密码时可为空
     * @param keyPrefix Redis key 前缀
     * @param ttlSeconds Redis key 过期时间，单位秒；小于等于 0 表示不过期
     */
    public PasswordCompatibleRedisChatMemoryStore(String host, int port, int database, String username,
                                                  String password, String keyPrefix, long ttlSeconds) {
        if (StrUtil.isBlank(host)) {
            throw new IllegalArgumentException("Redis host 不能为空");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("Redis port 必须大于 0");
        }
        if (database < 0) {
            throw new IllegalArgumentException("Redis database 不能小于 0");
        }
        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .database(database);
        if (StrUtil.isNotBlank(username)) {
            clientConfigBuilder.user(username);
        }
        if (StrUtil.isNotBlank(password)) {
            clientConfigBuilder.password(password);
        }
        this.jedisPooled = new JedisPooled(new HostAndPort(host, port), clientConfigBuilder.build());
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * 查询指定 memoryId 的历史消息。
     *
     * @param memoryId 对话记忆标识
     * @return 反序列化后的消息列表；不存在时返回空列表
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = jedisPooled.get(toRedisKey(memoryId));
        if (json == null) {
            return new ArrayList<>();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    /**
     * 覆盖写入指定 memoryId 的历史消息。
     * 当配置了 TTL 时，写入后会刷新该 key 的过期时间，保证最近活跃会话可持续保留。
     *
     * @param memoryId 对话记忆标识
     * @param messages 最新完整消息列表
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String redisKey = toRedisKey(memoryId);
        if (CollUtil.isEmpty(messages)) {
            jedisPooled.del(redisKey);
            return;
        }
        String messageJson
                = ChatMessageSerializer.messagesToJson(messages);
        if (ttlSeconds > 0) {
            jedisPooled.setex(redisKey, ttlSeconds, messageJson);
            return;
        }
        jedisPooled.set(redisKey, messageJson);
    }

    /**
     * 删除指定 memoryId 的历史消息。
     *
     * @param memoryId 对话记忆标识
     */
    @Override
    public void deleteMessages(Object memoryId) {
        jedisPooled.del(toRedisKey(memoryId));
    }

    /**
     * 将业务 memoryId 转换为 Redis key。
     * 统一在这里做空值校验和 key 拼装，避免调用方传入非法标识后写出脏数据。
     *
     * @param memoryId 对话记忆标识
     * @return Redis key
     */
    private String toRedisKey(Object memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("memoryId 不能为空");
        }
        String memoryIdText = memoryId.toString();
        if (StrUtil.isBlank(memoryIdText)) {
            throw new IllegalArgumentException("memoryId 不能为空字符串");
        }
        return keyPrefix + memoryIdText;
    }
}
