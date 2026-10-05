package io.github.bytex0.oss;

import io.github.bytex0.oss.client.OssClient;
import io.github.bytex0.oss.client.S3OssClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 对象存储(OssConfiguration)自动配置
 *
 * @author bytex0
 * @since 2026-10-05 14:42:56
 */
@AutoConfiguration
@ConditionalOnClass(S3Client.class)
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {

    /**
     * 默认 S3 实现(DefaultClientConfiguration)配置，业务自定义 OssClient 时整体退让
     *
     * @author bytex0
     * @since 2026-10-05 14:42:56
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(OssClient.class)
    static class DefaultClientConfiguration {

        /**
         * 创建由 Spring 关闭的同步客户端，不在构造时连接 S3。
         *
         * @param properties 连接配置
         * @param credentialsProvider 可选外部凭据提供器
         * @return SDK 客户端
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(S3Client.class)
        S3Client s3Client(OssProperties properties, ObjectProvider<AwsCredentialsProvider> credentialsProvider) {
            properties.validateConnection();
            return S3Client.builder()
                    .endpointOverride(properties.getEndpoint())
                    .region(Region.of(properties.getRegion()))
                    .credentialsProvider(credentials(properties, credentialsProvider))
                    .serviceConfiguration(serviceConfiguration(properties))
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                    .httpClientBuilder(ApacheHttpClient.builder()
                            .maxConnections(properties.getMaxConnections())
                            .connectionTimeout(properties.getConnectionTimeout())
                            .socketTimeout(properties.getSocketTimeout())
                            .connectionAcquisitionTimeout(properties.getConnectionTimeout()))
                    .overrideConfiguration(builder -> builder.apiCallTimeout(properties.getApiCallTimeout()))
                    .build();
        }

        /**
         * 创建与上传相同端点和签名配置的签名器。
         *
         * @param properties 连接配置
         * @param credentialsProvider 可选凭据来源
         * @return 签名器
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(S3Presigner.class)
        S3Presigner s3Presigner(OssProperties properties, ObjectProvider<AwsCredentialsProvider> credentialsProvider) {
            properties.validateConnection();
            return S3Presigner.builder()
                    .endpointOverride(properties.getEndpoint())
                    .region(Region.of(properties.getRegion()))
                    .credentialsProvider(credentials(properties, credentialsProvider))
                    .serviceConfiguration(serviceConfiguration(properties))
                    .build();
        }

        /**
         * 注册包含大文件自动分片策略的客户端封装。
         *
         * @param s3Client 同步客户端
         * @param s3Presigner 签名器
         * @param properties 传输策略
         * @return 对象存储接口
         */
        @Bean
        OssClient ossClient(S3Client s3Client, S3Presigner s3Presigner, OssProperties properties) {
            properties.validateTransfers();
            return new S3OssClient(s3Client, s3Presigner,
                    properties.getMultipartThreshold().toBytes(), properties.getMultipartPartSize().toBytes());
        }

        /**
         * 优先使用业务凭据提供器，否则验证显式配置，不记录密钥。
         *
         * @param properties 连接配置
         * @param providers 业务凭据提供器
         * @return 当前凭据来源
         */
        private AwsCredentialsProvider credentials(OssProperties properties,
                                                    ObjectProvider<AwsCredentialsProvider> providers) {
            return providers.getIfAvailable(() -> {
                Assert.hasText(properties.getAccessKey(), "oss.access-key 不能为空");
                Assert.hasText(properties.getAccessSecret(), "oss.access-secret 不能为空");
                return StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getAccessSecret()));
            });
        }

        /**
         * 统一签名器与客户端的寻址和分块协议配置。
         *
         * @param properties 连接配置
         * @return S3 协议配置
         */
        private S3Configuration serviceConfiguration(OssProperties properties) {
            return S3Configuration.builder()
                    .pathStyleAccessEnabled(properties.isPathStyleAccess())
                    .chunkedEncodingEnabled(!properties.isChunkedEncodingDisabled())
                    .build();
        }
    }
}
