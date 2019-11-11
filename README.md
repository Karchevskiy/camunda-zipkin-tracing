# Add tracing through camunda bpm. Propagate trace context (rest/kafka)

Alternative to https://github.com/berndruecker/camunda-zipkin-springboot-demo

Get all zipkin spans and brave(sleuth) context propagation without coding.

Used weaving aspects on 
DelegateExecution, 
ServiceTaskConnectorActivityBehavior, 
CallActivityBehavior
Look here:
https://www.eclipse.org/aspectj/doc/released/devguide/ltw-rules.html

## Local start
1) Run zipkin
2) Add aspect as dependency and kafka-clients (if kafka used as event source/ commands, creating processes, or if process send messages) 
3) Configure sleuth. (contains default configurations)
4) Start spring-boot-bpm application with jvm option -javaagent: aspectjweaver-1.9.4.jar

try on example project (all stuff configured, just start with javaagent and start zipkin server)
