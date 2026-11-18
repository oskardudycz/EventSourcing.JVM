package io.eventdriven.ecommerce.core.subscriptions;

import io.kurrent.dbclient.SubscribeToAllOptions;
import io.kurrent.dbclient.SubscriptionFilter;

record KurrentDBSubscriptionToAllOptions(
        String subscriptionId, boolean ignoreDeserializationErrors, SubscribeToAllOptions subscribeToAllOptions) {
    static KurrentDBSubscriptionToAllOptions getDefault() {
        SubscriptionFilter filterOutSystemEvents = SubscriptionFilter.newBuilder()
                .withEventTypeRegularExpression("^[^\\$].*")
                .build();

        SubscribeToAllOptions options = SubscribeToAllOptions.get().fromStart().filter(filterOutSystemEvents);

        return new KurrentDBSubscriptionToAllOptions("default", true, options);
    }
}
