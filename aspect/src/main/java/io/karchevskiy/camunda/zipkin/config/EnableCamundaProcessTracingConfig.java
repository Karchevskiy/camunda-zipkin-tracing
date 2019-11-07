package io.karchevskiy.camunda.zipkin.config;

import brave.Tracing;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.karchevskiy.camunda.zipkin.CamundaSlueuthContextInjectingAspect;

@Configuration
@ConditionalOnWebApplication
public class EnableCamundaProcessTracingConfig {

    @Bean
    public CamundaSlueuthContextInjectingAspect processSpanDefinitionAspect(Tracing tracing){
        return new CamundaSlueuthContextInjectingAspect(tracing);
    }

}
