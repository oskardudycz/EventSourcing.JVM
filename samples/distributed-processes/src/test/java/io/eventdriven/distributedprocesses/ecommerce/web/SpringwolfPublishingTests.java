package io.eventdriven.distributedprocesses.ecommerce.web;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SpringwolfPublishingTests {
  @Autowired
  private MockMvc mvc;

  @Autowired
  private MessageBus messageBus;

  @Test
  public void theUiCanPublishOnTheInMemoryBus() throws Exception {
    mvc.perform(get("/springwolf/plugin/x-in-memory/publish")).andExpect(status().isOk());
  }

  @Test
  public void aMessagePublishedFromTheUiReachesTheBus() throws Exception {
    var shipmentId = ShipmentId.of(UUID.randomUUID());
    var published = new ArrayList<Object>();
    messageBus.use(published::add);

    mvc.perform(post("/springwolf/plugin/x-in-memory/publish")
        .param("topic", "shipments-internal")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "type": "io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent",
            "payload": "{\\"shipmentId\\": \\"%s\\", \\"carrier\\": \\"DHL\\", \\"trackingNumber\\": \\"123\\"}"
          }
          """.formatted(shipmentId)))
      .andExpect(status().isOk());

    assertThat(published).contains(new ConfirmPackageSent(shipmentId, "DHL", "123"));
  }
}
