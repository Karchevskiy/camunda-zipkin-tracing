package example.adapter;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class ServiceADelegate implements JavaDelegate {

  private Logger log = Logger.getLogger(ServiceADelegate.class.getName());

  private final ProcessEngine camunda;

  public ServiceADelegate(ProcessEngine camunda) {
    this.camunda = camunda;
  }

  public void execute(DelegateExecution ctx) throws Exception {

    Thread.sleep(1000);

    log.info("starting child process ");
    camunda.getRuntimeService().startProcessInstanceByKey("child");


    log.info("service A executed");
  }

}
