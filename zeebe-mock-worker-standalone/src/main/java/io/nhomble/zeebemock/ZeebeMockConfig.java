package io.nhomble.zeebemock;

import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
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
