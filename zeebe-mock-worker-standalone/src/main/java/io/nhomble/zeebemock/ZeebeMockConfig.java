package io.nhomble.zeebemock;

import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ZeebeMockConfigurationProperties.class)
public class ZeebeMockConfig {

    @Bean
    public WorkerResolver wiremockWorkerResolver(ZeebeMockConfigurationProperties properties) {
        return new WiremockWorkerResolver(new HttpAdminClient(
                properties.getWiremockURI().getScheme(),
                properties.getWiremockURI().getHost(),
                properties.getWiremockURI().getPort())
        );
    }
}
