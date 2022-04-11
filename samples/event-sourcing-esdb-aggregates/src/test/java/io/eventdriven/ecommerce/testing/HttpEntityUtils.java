package io.eventdriven.ecommerce.testing;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public final class HttpEntityUtils {
  public static HttpEntity<String> toHttpEntity(org.json.JSONObject jsonBody){
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    return new HttpEntity<>(jsonBody.toString(), headers);
  }
}
