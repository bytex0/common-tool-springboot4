package io.github.bytex0.redis;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;

/**
 * Protobuf 编码适配(ProtobufJackson3Codec)保留生成消息的 Protobuf 和 POJO 的 Protostuff 协议。
 * 不依赖 Redisson 原适配器中硬编码的 Jackson 2 工厂；JDK 标量显式使用 Jackson 3。
 * 应用模型须来自可信类型配置，Redis 数据也须受访问控制保护，不能把二进制解码当作不可信数据沙箱。
 *
 * @param <T> 固定值类型
 * @author linshiqiang
 * @since 2026-10-06 10:13:01
 */
public final class ProtobufJackson3Codec<T> extends BaseCodec {

    /**
     * 与原 Protostuff 适配器一致的初始缓冲区大小。
     */
    private static final int BUFFER_SIZE = 512;

    /**
     * 显式值类型。
     */
    private final Class<T> type;

    /**
     * 普通应用模型的结构，不用于标量或生成消息。
     */
    private final Schema<T> schema;

    /**
     * Google 生成消息的标准解析器，其他类型为 null。
     */
    private final Parser<? extends MessageLite> parser;

    /**
     * JDK 标量/集合的无任意类型 JSON 编码器，应用模型为 null。
     */
    private final Codec scalar;

    /**
     * 创建支持 Bucket 和字符串字段 Hash 的固定类型编码器。
     *
     * @param type 具体模型或生成消息类，不能为 Object 或 record
     */
    public ProtobufJackson3Codec(Class<T> type) {
        Assert.notNull(type, "PROTOBUF值类型不能为空");
        Assert.isTrue(type != Object.class && !type.isRecord() && !type.isPrimitive(),
                "PROTOBUF需要具体POJO、包装类型或生成消息类");
        this.type = type;
        if (MessageLite.class.isAssignableFrom(type)) {
            try {
                MessageLite instance = (MessageLite) type.getMethod("getDefaultInstance").invoke(null);
                this.parser = instance.getParserForType();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("PROTOBUF生成类型缺少默认实例", exception);
            }
            this.scalar = null;
            this.schema = null;
        } else if (type.getPackageName().startsWith("java.")) {
            this.scalar = new TypedJsonJackson3Codec(type);
            this.schema = null;
            this.parser = null;
        } else {
            this.schema = RuntimeSchema.getSchema(type);
            this.scalar = null;
            this.parser = null;
        }
    }

    /**
     * 获取固定值编码器。
     *
     * @return 值编码器
     */
    @Override
    public Encoder getValueEncoder() {
        return this::encode;
    }

    /**
     * 获取固定值解码器。
     *
     * @return 值解码器
     */
    @Override
    public Decoder<Object> getValueDecoder() {
        return this::decode;
    }

    /**
     * Hash 字段名使用字符串协议。
     *
     * @return 字段名编码器
     */
    @Override
    public Encoder getMapKeyEncoder() {
        return StringCodec.INSTANCE.getMapKeyEncoder();
    }

    /**
     * Hash 字段名还原为字符串。
     *
     * @return 字段名解码器
     */
    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return StringCodec.INSTANCE.getMapKeyDecoder();
    }

    /**
     * Hash 字段值使用与 Bucket 一致的固定模型协议。
     *
     * @return 字段值编码器
     */
    @Override
    public Encoder getMapValueEncoder() {
        return getValueEncoder();
    }

    /**
     * Hash 字段值使用与 Bucket 一致的固定模型协议。
     *
     * @return 字段值解码器
     */
    @Override
    public Decoder<Object> getMapValueDecoder() {
        return getValueDecoder();
    }

    /**
     * 编码一个明确类型的值，失败时释放已分配 Netty 缓冲区。
     *
     * @param value 值
     * @return 所有权交给 Redisson 的缓冲区
     * @throws IOException 序列化失败时抛出
     */
    private ByteBuf encode(Object value) throws IOException {
        Assert.isTrue(type.isInstance(value), "PROTOBUF值与配置类型不匹配");
        if (scalar != null) {
            return scalar.getValueEncoder().encode(value);
        }
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            if (parser != null) {
                buffer.writeBytes(((MessageLite) value).toByteArray());
            } else {
                LinkedBuffer scratch = LinkedBuffer.allocate(BUFFER_SIZE);
                try (ByteBufOutputStream output = new ByteBufOutputStream(buffer)) {
                    ProtostuffIOUtil.writeTo(output, type.cast(value), schema, scratch);
                } finally {
                    scratch.clear();
                }
            }
            return buffer;
        } catch (IOException | RuntimeException exception) {
            buffer.release();
            throw exception;
        }
    }

    /**
     * 使用固定解析器解码，输入缓冲区仍由 Redisson 管理。
     *
     * @param buffer 输入缓冲区
     * @param state Redisson 解码状态
     * @return 固定类型值
     * @throws IOException 无效二进制内容时抛出
     */
    private Object decode(ByteBuf buffer, State state) throws IOException {
        if (scalar != null) {
            return scalar.getValueDecoder().decode(buffer, state);
        }
        try (InputStream input = new ByteBufInputStream(buffer)) {
            if (parser != null) {
                return parser.parseFrom(input);
            }
            T value = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(input, value, schema);
            return value;
        }
    }
}
