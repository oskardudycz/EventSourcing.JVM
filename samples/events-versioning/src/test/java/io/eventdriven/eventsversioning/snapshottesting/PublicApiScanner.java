package io.eventdriven.eventsversioning.snapshottesting;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PublicApiScanner {
  private final String packageName;
  private Predicate<Method> methodFilter = _ -> true;
  private boolean excludeConstructors;

  private PublicApiScanner(String packageName) {
    this.packageName = packageName;
  }

  public static PublicApiScanner forPackage(String packageName) {
    return new PublicApiScanner(packageName);
  }

  public PublicApiScanner excludingMethods(Predicate<Method> filter) {
    this.methodFilter = this.methodFilter.and(filter.negate());
    return this;
  }

  public PublicApiScanner excludeConstructors() {
    this.excludeConstructors = true;
    return this;
  }

  public PublicApiScanner onlyGettersAndFields() {
    return excludeConstructors()
      .excludeStandardObjectMethods()
      .excludingMethods(m -> m.getParameterCount() > 0 || m.getReturnType() == void.class);
  }

  public PublicApiScanner excludeStandardObjectMethods() {
    return excludingMethods(m ->
      (m.getName().equals("equals") && m.getParameterCount() == 1) ||
      (m.getName().equals("hashCode") && m.getParameterCount() == 0) ||
      (m.getName().equals("toString") && m.getParameterCount() == 0)
    );
  }

  public String generate() {
    try (var scanResult = new ClassGraph()
      .enableAllInfo()
      .acceptPackages(packageName)
      .scan()) {

      return scanResult.getAllClasses().stream()
        .map(ClassInfo::loadClass)
        .filter(c -> Modifier.isPublic(c.getModifiers()))
        .sorted(Comparator.comparing(Class::getName))
        .map(this::describeType)
        .collect(Collectors.joining("\n\n"));
    }
  }

  private String describeType(Class<?> clazz) {
    var sb = new StringBuilder();
    sb.append(typeDeclaration(clazz)).append(" {\n");

    Arrays.stream(clazz.getDeclaredFields())
      .filter(f -> Modifier.isPublic(f.getModifiers()) && !f.isSynthetic())
      .sorted(Comparator.comparing(Field::getName))
      .forEach(f -> sb.append("  ").append(f.toGenericString()).append(";\n"));

    if (!excludeConstructors) {
      Arrays.stream(clazz.getDeclaredConstructors())
        .filter(c -> Modifier.isPublic(c.getModifiers()))
        .sorted(Comparator.comparing(Constructor::toGenericString))
        .forEach(c -> sb.append("  ").append(formatConstructor(c)).append(";\n"));
    }

    Arrays.stream(clazz.getDeclaredMethods())
      .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
      .filter(methodFilter)
      .sorted(Comparator.comparing(Method::getName).thenComparing(Method::toGenericString))
      .forEach(m -> sb.append("  ").append(formatMethod(m)).append(";\n"));

    sb.append("}");
    return sb.toString();
  }

  private static String formatConstructor(Constructor<?> c) {
    var params = Arrays.stream(c.getGenericParameterTypes())
      .map(Type::getTypeName)
      .collect(Collectors.joining(", "));
    return Modifier.toString(c.getModifiers()) + " " + c.getDeclaringClass().getSimpleName() + "(" + params + ")";
  }

  private static String formatMethod(Method m) {
    var params = Arrays.stream(m.getGenericParameterTypes())
      .map(Type::getTypeName)
      .collect(Collectors.joining(", "));
    return Modifier.toString(m.getModifiers()) + " " + m.getGenericReturnType().getTypeName() + " " + m.getName() + "(" + params + ")";
  }

  private static String typeDeclaration(Class<?> clazz) {
    var parts = new StringBuilder();

    if (Modifier.isPublic(clazz.getModifiers())) parts.append("public ");
    if (Modifier.isStatic(clazz.getModifiers())) parts.append("static ");
    if (Modifier.isFinal(clazz.getModifiers()) && !clazz.isEnum()) parts.append("final ");

    if (clazz.isRecord()) parts.append("record");
    else if (clazz.isInterface()) parts.append("interface");
    else if (clazz.isEnum()) parts.append("enum");
    else parts.append("class");

    parts.append(" ").append(clazz.getName());

    var interfaces = clazz.getInterfaces();
    if (interfaces.length > 0) {
      var keyword = clazz.isInterface() ? " extends " : " implements ";
      parts.append(keyword).append(
        Arrays.stream(interfaces)
          .map(Class::getName)
          .collect(Collectors.joining(", "))
      );
    }

    return parts.toString();
  }
}
