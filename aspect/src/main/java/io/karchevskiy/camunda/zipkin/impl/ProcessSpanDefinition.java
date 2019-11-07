package io.karchevskiy.camunda.zipkin.impl;

import brave.Span;
import brave.Tracing;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.camunda.bpm.engine.impl.RuntimeServiceImpl;
import org.camunda.bpm.engine.impl.pvm.runtime.PvmExecutionImpl;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static io.karchevskiy.camunda.zipkin.CamundaSlueuthContextInjectingAspect.*;

public class ProcessSpanDefinition {

    /**
     * There 6 overloads for 2 methods for process creation in {@link RuntimeServiceImpl}.
     * We should propagate sleuth context and call that one, which expect Map with parameters for initialization
     */
    public static Object startProcessSpan(ProceedingJoinPoint pjp, Tracing tracing) throws Throwable {
        //Check sleuth context configured
        if (tracing == null) {
            return pjp.proceed(pjp.getArgs());
        }
        //Check called method signature
        if (!pjp.getSignature().getName().startsWith("startProcessInstance")) {
            return pjp.proceed(pjp.getArgs());
        }

        //Define Map existence as last argument
        Object[] args = pjp.getArgs();
        int argsLength = args.length;
        if (args[argsLength - 1] instanceof Map) {
            argsLength--;
        }

        //Define sleuth context existence
        Map<String, Object> internalProcessVariables = extractOrDefineParamMap(args);
        //If sleuth context configured - execute method as is (success way or second pass)
        if (internalProcessVariables.containsKey(X_SLEUTH_TRACE_ID)) {
            return pjp.proceed(pjp.getArgs());
        }

        //In another way restore sleuth context, if exists as process creation call param
        if (internalProcessVariables.containsKey(X_SLEUTH_TRACE_CONTEXT)) {
            final Map<String, String> parentTrace =
                    extractSerializedContext(internalProcessVariables.get(X_SLEUTH_TRACE_CONTEXT));
            Span parentSpan = restoreTracingContext(parentTrace);
            tracing.tracer().withSpanInScope(parentSpan);
        } else {
            Object[] argsCache = new Object[argsLength + 1];
            System.arraycopy(args, 0, argsCache, 0, argsLength);
            argsCache[argsLength] = internalProcessVariables;
            args = argsCache;
        }

        //Define root process span name (bpmn-schema name)
        String processName = "unknownProcess";
        if (args[0] instanceof String) {
            processName = (String) args[0];
        }

        Span span = tracing.tracer().nextSpan().name("RPS: " + processName);
        tracing.tracer().withSpanInScope(span);
        span.annotate("Root Process Started");
        span.start();
        span.flush();
        //should not be finished
        final Map<String, String> tracingContext =
                serializeAndInjectTracingContext(internalProcessVariables, span, X_SLEUTH_TRACE_CONTEXT);

        //Save TraceId for search purposes
        internalProcessVariables.put(X_SLEUTH_TRACE_ID, tracingContext.get(X_SLEUTH_TRACE_ID));

        //Save span for process lifetime duration tracking
        Span spanDuration = tracing.tracer().nextSpan().name("RPD: " + processName);
        tracing.tracer().withSpanInScope(spanDuration);
        spanDuration.annotate("Root Process Duration");
        spanDuration.start();
        serializeAndInjectTracingContext(internalProcessVariables, spanDuration, X_SLEUTH_TRACE_DURATION);

        Class[] signTypes = calculateSignTypes(args);
        String methodName = pjp.getSignature().getName();
        Method declaredMethod = pjp.getTarget().getClass().getMethod(methodName, signTypes);
        if(declaredMethod == null){
            tracing.tracer().withSpanInScope(span);
            return pjp.proceed(pjp.getArgs());
        }
        //!!!Execute overloaded method
        return declaredMethod.invoke(pjp.getTarget(), args);
    }

    /**
     * Intercept process execution ended
     */
    public static void endProcessSpan(JoinPoint pjp, Tracing tracing) {
        if (tracing == null) {
            return;
        }
        if (!(pjp.getTarget() instanceof PvmExecutionImpl)) {
            return;
        }
        PvmExecutionImpl target = (PvmExecutionImpl) pjp.getTarget();
        Object sleuthContext = target.getVariable(X_SLEUTH_TRACE_DURATION);

        if (sleuthContext == null) {
            return;
        }

        Map<String, String> tracingContextSerialized = extractSerializedContext(sleuthContext);
        if (tracingContextSerialized.isEmpty()) {
            return;
        }

        Span span = restoreTracingContext(tracingContextSerialized);
        tracing.tracer().withSpanInScope(span);
        span.tag("finishTime", LocalDateTime.now().toString());
        span.finish();
        span.tag("afterFinishSpan", "Surprize span");
        span.flush();
    }

    private static Class[] calculateSignTypes(Object[] args) {
        Class[] signTypes;
        int length = args.length;
        if (args[length - 1] instanceof Map) {
            signTypes = new Class[length];
        } else {
            signTypes = new Class[length + 1];
        }

        for (int i = 0; i < length; i++) {
            signTypes[i] = args[i].getClass();
        }

        signTypes[signTypes.length - 1] = Map.class;
        return signTypes;
    }

    private static Map<String, Object> extractOrDefineParamMap(Object[] args) {
        Map<String, Object> internalProcessVariables = new HashMap<>();
        for (Object arg : args) {
            if (arg instanceof Map) {
                internalProcessVariables = (Map<String, Object>) arg;
            }
        }
        return internalProcessVariables;
    }
}
