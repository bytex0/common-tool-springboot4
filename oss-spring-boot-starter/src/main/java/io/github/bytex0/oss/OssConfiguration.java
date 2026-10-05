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
 * @author linshiqiang
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
     * @author linshiqiang
     * @since 2026-10-05 14:42:56
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(OssClient.class)
    static class DefaultClientConfiguration {

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

        @Bean
        OssClient ossClient(S3Client s3Client, S3Presigner s3Presigner) {
            return new S3OssClient(s3Client, s3Presigner);
        }

        private AwsCredentialsProvider credentials(OssProperties properties,
                                                    ObjectProvider<AwsCredentialsProvider> providers) {
            return providers.getIfAvailable(() -> {
                Assert.hasText(properties.getAccessKey(), "oss.access-key 不能为空");
                Assert.hasText(properties.getAccessSecret(), "oss.access-secret 不能为空");
                return StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getAccessSecret()));
            });
        }

        private S3Configuration serviceConfiguration(OssProperties properties) {
            return S3Configuration.builder()
                    .pathStyleAccessEnabled(properties.isPathStyleAccess())
                    .chunkedEncodingEnabled(!properties.isChunkedEncodingDisabled())
                    .build();
        }
    }
}
