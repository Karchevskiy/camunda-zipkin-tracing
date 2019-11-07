package example.adapter;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.logging.Logger;


@Component
public class ServiceBDelegate implements JavaDelegate {

  private static final int timeout = 8000;
  private Logger log = Logger.getLogger(ServiceBDelegate.class.getName());

  public void execute(DelegateExecution ctx) throws Exception {

    log.info("play with time param for different process execution results");
    log.info("current ServiceBDelegate delay:" + timeout + "ms");
    Thread.sleep(timeout);

    RestTemplate restTemplate = new RestTemplate();
    String fooResourceUrl
            = "https://google.com";
    try {
      restTemplate.getForEntity(fooResourceUrl, String.class);
    }catch (Exception e){
      e.printStackTrace();
    }

  }

}
