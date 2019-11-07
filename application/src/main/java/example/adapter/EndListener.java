package example.adapter;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class EndListener implements JavaDelegate {

  public void execute(DelegateExecution ctx) {
    //do nothing
  }

}
