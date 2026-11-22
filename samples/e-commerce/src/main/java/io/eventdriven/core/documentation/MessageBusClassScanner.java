package io.eventdriven.core.documentation;

import io.github.springwolf.core.asyncapi.scanners.beans.BeanMethodsScanner;
import io.github.springwolf.core.asyncapi.scanners.classes.SpringwolfClassScanner;
import io.github.springwolf.core.asyncapi.scanners.classes.spring.ComponentClassScanner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Springwolf finds annotated methods on Spring components and beans. The message bus is our own and
// its subscribers are wired by the modules' Config classes, so this tells Springwolf where they are.
public class MessageBusClassScanner extends SpringwolfClassScanner {
    private final List<Class<?>> onTheMessageBus;

    public MessageBusClassScanner(
            ComponentClassScanner componentClassScanner,
            BeanMethodsScanner beanMethodsScanner,
            List<Class<?>> onTheMessageBus) {
        super(componentClassScanner, beanMethodsScanner);
        this.onTheMessageBus = onTheMessageBus;
    }

    @Override
    public Set<Class<?>> scan() {
        var classes = new HashSet<>(super.scan());
        classes.addAll(onTheMessageBus);
        return classes;
    }
}
