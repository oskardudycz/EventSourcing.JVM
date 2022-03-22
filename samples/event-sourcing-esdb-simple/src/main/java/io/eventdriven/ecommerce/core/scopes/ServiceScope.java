package io.eventdriven.ecommerce.core.scopes;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class ServiceScope implements ApplicationContextAware {
  private static ApplicationContext context;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    context = applicationContext;
  }

  public <T> T getBean(Class<T> beanClass) {
    return Objects.nonNull(context) ? context.getBean(beanClass) : null;
  }

  public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
    return Objects.nonNull(context) ? context.getBeansOfType(type) : null;
  }

  public <Result> Result run(Function<ServiceScope, Result> handle) {
    RequestContextHolder.setRequestAttributes(new InMemoryRequestAttributes());
    try {
      return handle.apply(this);
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }

  public void run(Consumer<ServiceScope> handle) {
    RequestContextHolder.setRequestAttributes(new InMemoryRequestAttributes());
    try {
      handle.accept(this);
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }

}
