package io.eventdriven.core.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionCheckpointRepository extends JpaRepository<SubscriptionCheckpoint, String> {}
