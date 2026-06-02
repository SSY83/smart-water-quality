package com.waterquality.config;

import com.waterquality.service.sms.AliyunSmsProvider;
import com.waterquality.service.sms.MockSmsProvider;
import com.waterquality.service.sms.SmsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SmsConfig {

    @Value("${sms.aliyun.access-key-id:}")
    private String accessKeyId;

    @Value("${sms.aliyun.access-key-secret:}")
    private String accessKeySecret;

    @Value("${sms.aliyun.sign-name:水质监测}")
    private String signName;

    @Value("${sms.aliyun.template-code:SMS_123456789}")
    private String templateCode;

    @Value("${sms.aliyun.region-id:cn-hangzhou}")
    private String regionId;

    @Bean
    @ConditionalOnProperty(name = "sms.provider", havingValue = "aliyun")
    public SmsProvider aliyunSmsProvider() {
        return new AliyunSmsProvider(accessKeyId, accessKeySecret,
                signName, templateCode, regionId);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "sms.provider", havingValue = "mock", matchIfMissing = true)
    public SmsProvider mockSmsProvider() {
        return new MockSmsProvider();
    }
}
