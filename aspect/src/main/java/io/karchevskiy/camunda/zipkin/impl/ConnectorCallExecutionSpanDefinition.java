package io.karchevskiy.camunda.zipkin.impl;

import brave.Span;
import brave.Tracing;
import org.aspectj.lang.ProceedingJoinPoint;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;

import java.util.Map;

import static io.karchevskiy.camunda.zipkin.CamundaSlueuthContextInjectingAspect.*;

public class ConnectorCallExecutionSpanDefinition {


    public static Object propagateTracingFromCamundaSerializedContextInAnyConnector(ProceedingJoinPoint pjp,
                                                                                    Tracing tracing) throws Throwable {
        if (tracing == null) {
            return pjp.proceed();
        }
        ExecutionEntity execution = (ExecutionEntity) pjp.getArgs()[0];
        final VariableMapImpl sourceVariables = execution.getProcessInstance().getVariables();
        Map<String, String> sleuthTraceSerialized =
                extractSerializedContext(sourceVariables.get(X_SLEUTH_TRACE_CONTEXT));

        if (sleuthTraceSerialized.isEmpty()) {
            return pjp.proceed();
        }

        final String currentTraceId = tracing.tracer().currentSpan().context().traceIdString();
        if (currentTraceId == null || !currentTraceId.toLowerCase().
                equals(sleuthTraceSerialized.get(X_SLEUTH_TRACE_ID).toLowerCase())) {
            final Span parentTraceContext = restoreTracingContext(sleuthTraceSerialized);
            tracing.tracer().withSpanInScope(parentTraceContext);
            final Span unknownEventSpan = tracing.tracer().nextSpan().name("sending unknown async event").start();
            final Object result = pjp.proceed();
            unknownEventSpan.finish();
            return result;
        }
        return pjp.proceed();
    }
}
