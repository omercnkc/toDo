package com.kafein.todo.common.feign;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * OpenFeign kullanan <b>servlet</b> servislerine kullanıcı token'ını ileten interceptor'ı ekler.
 * Feign client'ları interceptor'ları uygulama context'inden de aldığı için tüm client'lara uygulanır.
 * Feign classpath'te değilse (auth-service, gateway) hiçbir şey yüklenmez.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class CommonFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeignAuthForwardingInterceptor feignAuthForwardingInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }
}
