package example;

import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
public class SampleRestController {

  private final ProcessEngine camunda;

  public SampleRestController(ProcessEngine camunda) {
    this.camunda = camunda;
  }

  @RequestMapping(path = "/test", method = GET)
  public String placeOrder() {
    camunda.getRuntimeService().startProcessInstanceByKey("sample");
    return "process started";
  }


}