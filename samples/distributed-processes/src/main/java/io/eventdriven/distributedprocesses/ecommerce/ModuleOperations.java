package io.eventdriven.distributedprocesses.ecommerce;

import io.github.springwolf.asyncapi.v3.model.operation.Operation;
import io.github.springwolf.core.asyncapi.scanners.operations.OperationCustomizer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;

// Marks each operation with the module whose code sends or receives it, so each module's
// document can hold only its own operations.
@Component
public class ModuleOperations implements OperationCustomizer {
  public static final String module = "x-module";

  private static final String modulesPackage = ECommerceConfig.class.getPackageName() + ".";

  @Override
  public void customize(Operation operation, Method method) {
    var extensions = operation.getExtensionFields() != null
      ? new HashMap<>(operation.getExtensionFields())
      : new HashMap<String, Object>();
    extensions.put(module, moduleOf(method.getDeclaringClass()));
    operation.setExtensionFields(extensions);
  }

  static String moduleOf(Class<?> type) {
    var modulePackage = type.getPackageName().substring(modulesPackage.length()).split("\\.")[0];

    return modulePackage.equals("shoppingcarts") ? "shopping_carts" : modulePackage;
  }
}
