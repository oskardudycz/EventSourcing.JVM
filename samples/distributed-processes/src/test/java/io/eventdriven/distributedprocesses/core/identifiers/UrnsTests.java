package io.eventdriven.distributedprocesses.core.identifiers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UrnsTests {
  private static final UUID tail = UUID.fromString("9f2a3b7c-1d4e-4f60-8a91-2b3c4d5e6f70");

  private final String cart = Urns.of("ecommerce", "cart", tail);

  @Test
  public void ofBuildsTheUrnFromAUuidTail() {
    assertThat(cart).isEqualTo("urn:ecommerce:cart:9f2a3b7c-1d4e-4f60-8a91-2b3c4d5e6f70");
  }

  @Test
  public void ofBuildsTheUrnFromAStringTail() {
    assertThat(Urns.of("ecommerce", "cart", "the-tail")).isEqualTo("urn:ecommerce:cart:the-tail");
  }

  @Test
  public void deriveSwapsTheTypeAndKeepsTheNamespaceAndTail() {
    var order = Urns.derive(cart, "order");

    assertThat(order).isEqualTo("urn:ecommerce:order:9f2a3b7c-1d4e-4f60-8a91-2b3c4d5e6f70");
    assertThat(Urns.namespaceOf(order)).isEqualTo(Urns.namespaceOf(cart));
    assertThat(Urns.tailOf(order)).isEqualTo(Urns.tailOf(cart));
  }

  @Test
  public void deriveFromTheSameSourceTwiceGivesTheSameUrn() {
    assertThat(Urns.derive(cart, "payment")).isEqualTo(Urns.derive(cart, "payment"));
  }

  @Test
  public void deriveToTheSameTypeReturnsAnEqualUrn() {
    assertThat(Urns.derive(cart, "cart")).isEqualTo(cart);
  }

  @Test
  public void deriveChainedThroughTheProcessKeepsTheSameTail() {
    var payment = Urns.derive(Urns.derive(cart, "order"), "payment");

    assertThat(payment).isEqualTo("urn:ecommerce:payment:9f2a3b7c-1d4e-4f60-8a91-2b3c4d5e6f70");
  }

  @Test
  public void accessorsReturnTheMatchingSegments() {
    assertThat(Urns.namespaceOf(cart)).isEqualTo("ecommerce");
    assertThat(Urns.typeOf(cart)).isEqualTo("cart");
    assertThat(Urns.tailOf(cart)).isEqualTo(tail.toString());
  }

  @Test
  public void aTailContainingColonsRoundTrips() {
    var urn = Urns.of("ecommerce", "cart", "shard:7:%s".formatted(tail));

    assertThat(urn).isEqualTo("urn:ecommerce:cart:shard:7:%s".formatted(tail));
    assertThat(Urns.tailOf(urn)).isEqualTo("shard:7:%s".formatted(tail));
    assertThat(Urns.typeOf(urn)).isEqualTo("cart");
  }

  @Test
  public void nullUrnIsRejected() {
    assertThatThrownBy(() -> Urns.tailOf(null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void blankUrnIsRejected() {
    assertThatThrownBy(() -> Urns.tailOf("  "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("  ");
  }

  @Test
  public void aStringThatIsNotAUrnIsRejected() {
    assertThatThrownBy(() -> Urns.typeOf("not-a-urn"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not-a-urn");
  }

  @Test
  public void aUrnWithFewerThanFourSegmentsIsRejected() {
    assertThatThrownBy(() -> Urns.namespaceOf("urn:ecommerce"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("urn:ecommerce");
  }

  @Test
  public void aBlankTypePassedToDeriveIsRejected() {
    assertThatThrownBy(() -> Urns.derive(cart, " "))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void aBlankNamespacePassedToOfIsRejected() {
    assertThatThrownBy(() -> Urns.of(" ", "cart", tail))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void aBlankTailPassedToOfIsRejected() {
    assertThatThrownBy(() -> Urns.of("ecommerce", "cart", ""))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
