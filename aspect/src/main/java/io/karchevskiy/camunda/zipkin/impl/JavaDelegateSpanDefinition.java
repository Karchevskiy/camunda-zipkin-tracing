package io.karchevskiy.camunda.zipkin.impl;

import brave.Span;
import brave.Tracing;
import io.karchevskiy.camunda.zipkin.CamundaSlueuthContextInjectingAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

import java.util.Map;

public class JavaDelegateSpanDefinition {

    /**
     * Intercept call of {@link JavaDelegate} execution and wrapping it in corresponding span;
     * Java delegates are atomic;
     * JavaDelegate may produce messages (different ways);œœ
     * Base sleuth context for such messages -  JavaDelegate execution span;
     */
    public static void javaDelegateSpan(ProceedingJoinPoint pjp, Tracing tracing) throws Throwable {
        //assert context configured properly
        if (tracing == null) {
            pjp.proceed();
            return;
        }

        //LTW-around aspects can obtain values only in this way
        DelegateExecution execution = (DelegateExecution) pjp.getArgs()[0];
        Map<String, String> tracingContextSerialized =
                CamundaSlueuthContextInjectingAspect.extractSerializedContext(execution.getVariable(CamundaSlueuthContextInjectingAspect.X_SLEUTH_TRACE_CONTEXT));
        if (tracingContextSerialized.isEmpty()) {
            pjp.proceed();
            return;
        }

        Span parentSpan = CamundaSlueuthContextInjectingAspect.restoreTracingContext(tracingContextSerialized);
        tracing.tracer().withSpanInScope(parentSpan);
        String targetClassName = pjp.getTarget().getClass().getSimpleName();

        //wrap Java delegate execution with new span (JD = JavaDelegate)
        Span span = tracing.tracer().nextSpan().name("JD:" + targetClassName);
        tracing.tracer().withSpanInScope(span);
        span.start();
        pjp.proceed();
        span.finish();
    }
}
