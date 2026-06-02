package com.waterquality.service.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AliyunSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsProvider.class);

    private final Client client;
    private final String signName;
    private final String templateCode;

    public AliyunSmsProvider(String accessKeyId, String accessKeySecret,
                             String signName, String templateCode, String regionId) {
        this.signName = signName;
        this.templateCode = templateCode;
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint("dysmsapi.aliyuncs.com");
            this.client = new Client(config);
        } catch (Exception e) {
            throw new RuntimeException("阿里云短信客户端初始化失败", e);
        }
    }

    @Override
    public String getName() {
        return "aliyun";
    }

    @Override
    public boolean send(String phone, String content, String signName) {
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName != null ? signName : this.signName)
                    .setTemplateCode(this.templateCode)
                    .setTemplateParam("{\"content\":\"" + escapeJson(content) + "\"}");

            SendSmsResponse response = client.sendSms(request);
            if ("OK".equals(response.getBody().getCode())) {
                log.info("阿里云短信发送成功: phone={}, bizId={}", phone, response.getBody().getBizId());
                return true;
            } else {
                log.warn("阿里云短信发送失败: code={}, message={}",
                        response.getBody().getCode(), response.getBody().getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("阿里云短信发送异常: phone={}", phone, e);
            return false;
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
