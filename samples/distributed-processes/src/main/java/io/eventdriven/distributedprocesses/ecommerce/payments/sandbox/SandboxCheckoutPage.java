package io.eventdriven.distributedprocesses.ecommerce.payments.sandbox;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/sandbox/checkout/{sessionId}")
public class SandboxCheckoutPage {
  private final SandboxPaymentGateway gateway;

  public SandboxCheckoutPage(SandboxPaymentGateway gateway) {
    this.gateway = gateway;
  }

  @GetMapping
  public String show(@PathVariable String sessionId, Model model) {
    var session = sessionOf(sessionId);
    model.addAttribute("checkout", session);
    model.addAttribute("expired", session.expired());
    return "sandbox/checkout";
  }

  @PostMapping
  public Object pay(@PathVariable String sessionId, @RequestParam String cardNumber, Model model) {
    var session = sessionOf(sessionId);

    return switch (gateway.pay(session, cardNumber)) {
      case Paid -> {
        var redirect = new RedirectView(session.successUrl().toString());
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        yield redirect;
      }
      case Declined -> page(session, model, "Your card was declined.");
      case UnknownCard -> page(session, model, "Use a test card: 4242 4242 4242 4242 pays, 4000 0000 0000 0002 is declined.");
      case Expired -> {
        model.addAttribute("checkout", session);
        model.addAttribute("expired", true);
        yield "sandbox/checkout";
      }
    };
  }

  private String page(SandboxPaymentGateway.Session session, Model model, String error) {
    model.addAttribute("checkout", session);
    model.addAttribute("expired", false);
    model.addAttribute("error", error);
    return "sandbox/checkout";
  }

  private SandboxPaymentGateway.Session sessionOf(String sessionId) {
    return gateway.session(sessionId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));
  }
}
