package io.eventdriven.ecommerce.core.scopes;

import org.springframework.web.context.request.AbstractRequestAttributes;

import java.util.HashMap;
import java.util.Map;

public class InMemoryRequestAttributes extends AbstractRequestAttributes {

  protected Map<String, Object> attributes = new HashMap<>();

  @Override
  public Object getAttribute(String name, int scope) {
    return attributes.get(name);
  }

  @Override
  public void setAttribute(String name, Object value, int scope) {
    attributes.put(name, value);
  }

  @Override
  public void removeAttribute(String name, int scope) {
    attributes.remove(name);
  }

  @Override
  public String[] getAttributeNames(int scope) {
    String[] result = new String[attributes.keySet().size()];
    attributes.keySet().toArray(result);
    return result;
  }

  @Override
  public void registerDestructionCallback(String name, Runnable callback, int scope) {
    synchronized (this.requestDestructionCallbacks) {
      this.requestDestructionCallbacks.put(name, callback);
    }

  }

  @Override
  public Object resolveReference(String key) {
    return attributes;
  }

  @Override
  public String getSessionId() {
    return null;
  }

  @Override
  public Object getSessionMutex() {
    return null;
  }

  @Override
  protected void updateAccessedSessionAttributes() {

  }

}
