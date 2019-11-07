package io.karchevskiy.camunda.zipkin;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import io.karchevskiy.camunda.zipkin.impl.ConnectorCallExecutionSpanDefinition;
import io.karchevskiy.camunda.zipkin.impl.ProcessSpanDefinition;
import io.karchevskiy.camunda.zipkin.impl.PropagateSleuthContextToChildProcesses;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.karchevskiy.camunda.zipkin.impl.JavaDelegateSpanDefinition;

import java.util.HashMap;
import java.util.Map;


@SuppressWarnings("AroundAdviceStyleInspection")
@Component
@Aspect
public class CamundaSlueuthContextInjectingAspect {

    private static Tracing tracing;

    public static final String X_SLEUTH_TRACE_CONTEXT = "X-SLEUTH-TRACE-CONTEXT";
    public static final String X_SLEUTH_TRACE_DURATION = "X-SLEUTH-TRACE-DURATION";
    public static final String X_SLEUTH_TRACE_ID = "X-B3-TraceId";

    /**
     * Default constructor for LTW aspect
     * */
    @SuppressWarnings("unused")
    public CamundaSlueuthContextInjectingAspect() {
    }

    /**
     * Constructor for bean instance. Propagate Tracing configurations from spring context to LTW weaved classes
     * trough static field sounds dangerous.
     *                                  If you know better way - please report - geisterkirche@gmail.com
     * */
    @Autowired
    public CamundaSlueuthContextInjectingAspect(Tracing tracing) {
        CamundaSlueuthContextInjectingAspect.tracing = tracing;
    }

    @Around("execution(* org.camunda.bpm.engine.impl.bpmn.behavior.CallActivityBehavior.startInstance(..))")
    public Object propagateSleuthContextToChildProcesses(ProceedingJoinPoint pjp) throws Throwable {
        return PropagateSleuthContextToChildProcesses.propagateSleuthContextToChildProcesses(pjp, tracing);
    }

    @Around("execution(* org.camunda.bpm.engine.impl.RuntimeServiceImpl.*(..))")
    public Object startProcessSpan(ProceedingJoinPoint pjp) throws Throwable {
        return ProcessSpanDefinition.startProcessSpan(pjp, tracing);
    }

    @Before("execution(public void org.camunda.bpm.engine.impl.pvm.runtime.PvmExecutionImpl.end(..))")
    public void endProcessSpan(JoinPoint jp) {
        ProcessSpanDefinition.endProcessSpan(jp, tracing);
    }

    @Around("execution(public void org.camunda.bpm.engine.delegate.JavaDelegate.execute(" +
            "org.camunda.bpm.engine.delegate.DelegateExecution))")
    public void javaDelegateSpan(ProceedingJoinPoint pjp) throws Throwable {
        JavaDelegateSpanDefinition.javaDelegateSpan(pjp, tracing);
    }

    @Around("execution(void org.camunda.connect.plugin.impl.ServiceTaskConnectorActivityBehavior.execute(..))")
    public Object propagateTracingFromCamundaSerializedContextInAnyConnector(ProceedingJoinPoint pjp) throws Throwable {
        return ConnectorCallExecutionSpanDefinition.
                propagateTracingFromCamundaSerializedContextInAnyConnector(pjp, tracing);
    }

    public static Span restoreTracingContext(Map<String, String> tracingContextSerialized) {
        TraceContext.Extractor<Map<String, String>> extractor = tracing
                .propagation()
                .extractor(Map<String, String>::get);
        TraceContext context = extractor.extract(tracingContextSerialized).context();
        return tracing.tracer().toSpan(context);
    }

    public static Map<String, String> serializeAndInjectTracingContext(Map<String, Object> targetVariables,
                                                                       Span span,
                                                                       String key){
        HashMap<String, String> sleuthTraceSerialized = new HashMap<>();
        TraceContext.Injector<Map<String, String>> injector = tracing.propagation().injector(Map<String, String>::put);
        injector.inject(span.context(), sleuthTraceSerialized);
        targetVariables.put(key, sleuthTraceSerialized);
        return sleuthTraceSerialized;
    }

    public static Map<String,String> extractSerializedContext(Object sleuthContext){
        if(!(sleuthContext instanceof Map)){
            return new HashMap<>();
        }
        return (Map<String, String>) sleuthContext;
    }
}
