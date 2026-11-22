package io.eventdriven.core.documentation;

import io.github.springwolf.asyncapi.v3.model.operation.Operation;
import io.github.springwolf.core.asyncapi.scanners.operations.OperationCustomizer;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

// Marks each operation with the module whose code sends or receives it, so each module's
// document can hold only its own operations.
public class ModuleOperations implements OperationCustomizer {
    public static final String module = "x-module";

    private final String modulesPackage;
    private final Map<String, String> moduleNames;

    public ModuleOperations(String modulesPackage, Map<String, String> moduleNames) {
        this.modulesPackage = modulesPackage + ".";
        this.moduleNames = moduleNames;
    }

    @Override
    public void customize(Operation operation, Method method) {
        var extensions = operation.getExtensionFields() != null
                ? new HashMap<>(operation.getExtensionFields())
                : new HashMap<String, Object>();
        extensions.put(module, moduleOf(method.getDeclaringClass()));
        operation.setExtensionFields(extensions);
    }

    private String moduleOf(Class<?> type) {
        var modulePackage =
                type.getPackageName().substring(modulesPackage.length()).split("\\.")[0];

        return moduleNames.getOrDefault(modulePackage, modulePackage);
    }
}
