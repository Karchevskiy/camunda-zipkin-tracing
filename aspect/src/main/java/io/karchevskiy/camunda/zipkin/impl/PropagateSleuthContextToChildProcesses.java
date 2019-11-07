package io.karchevskiy.camunda.zipkin.impl;

import brave.Span;
import brave.Tracing;
import org.aspectj.lang.ProceedingJoinPoint;
import org.camunda.bpm.engine.impl.bpmn.behavior.CallActivityBehavior;
import org.camunda.bpm.engine.impl.core.model.CallableElement;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;

import java.util.Map;

import static org.camunda.bpm.engine.impl.util.CallableElementUtil.getProcessDefinitionToCall;
import static io.karchevskiy.camunda.zipkin.CamundaSlueuthContextInjectingAspect.*;

public class PropagateSleuthContextToChildProcesses {

    /**
     * Method propagates parent process sleuth context for child processes
     */
    public static Object propagateSleuthContextToChildProcesses(ProceedingJoinPoint pjp, Tracing tracing) throws Throwable {
        if (tracing == null) {
            return pjp.proceed();
        }
        if (!(pjp.getArgs()[0] instanceof ExecutionEntity) || pjp.getArgs()[0] == null) {
            return pjp.proceed();
        }
        if (!(pjp.getArgs()[1] instanceof VariableMap) || pjp.getArgs()[1] == null) {
            return pjp.proceed();
        }

        ExecutionEntity execution = (ExecutionEntity) pjp.getArgs()[0];

        String childProcessName = defineChildProcessName(pjp, execution);

        final VariableMapImpl sourceVariables = execution.getProcessInstance().getVariables();
        if (sourceVariables == null) {
            return pjp.proceed();
        }
        Map<String, String> sleuthTraceSerialized = extractSerializedContext(sourceVariables.get(X_SLEUTH_TRACE_CONTEXT));
        if (sleuthTraceSerialized.isEmpty()) {
            return pjp.proceed();
        }

        VariableMap targetVariables = (VariableMap) pjp.getArgs()[1];
        final Object sleuthTraceId = sourceVariables.get(X_SLEUTH_TRACE_ID);
        if (sleuthTraceId != null) {
            targetVariables.put(X_SLEUTH_TRACE_ID, sleuthTraceId);
        } else {
            return pjp.proceed();
        }

        Span parentSpan = restoreTracingContext(sleuthTraceSerialized);
        tracing.tracer().withSpanInScope(parentSpan);

        Span processStartedSpan = tracing.tracer().nextSpan().name("CPS:" + childProcessName);
        processStartedSpan.annotate("Child Process Started");
        tracing.tracer().withSpanInScope(processStartedSpan);
        processStartedSpan.start().flush();
        serializeAndInjectTracingContext(targetVariables, processStartedSpan, X_SLEUTH_TRACE_CONTEXT);

        Span processDurationSpan = tracing.tracer().nextSpan().name("CPD:" + childProcessName);
        tracing.tracer().withSpanInScope(processDurationSpan);
        processDurationSpan.annotate("Child Process Duration");
        processDurationSpan.start();
        serializeAndInjectTracingContext(targetVariables, processDurationSpan, X_SLEUTH_TRACE_DURATION);

        tracing.tracer().withSpanInScope(processStartedSpan);
        return pjp.proceed();

    }

    private static String defineChildProcessName(ProceedingJoinPoint pjp, ExecutionEntity execution){
        CallActivityBehavior behavior = (CallActivityBehavior) pjp.getTarget();
        final CallableElement callableElement = behavior.getCallableElement();
        final ProcessDefinitionImpl processDefinitionToCall = getProcessDefinitionToCall(execution, callableElement);
        if(processDefinitionToCall != null){
            return processDefinitionToCall.getName();
        }
        return "Unknown Process";
    }
}
