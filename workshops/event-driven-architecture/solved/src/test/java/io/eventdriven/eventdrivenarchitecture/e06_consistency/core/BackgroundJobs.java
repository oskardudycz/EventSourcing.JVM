package io.eventdriven.eventdrivenarchitecture.e06_consistency.core;

import java.util.ArrayList;
import java.util.List;

public class BackgroundJobs {
  private final List<Runnable> jobs = new ArrayList<>();

  public void register(Runnable job) {
    jobs.add(job);
  }

  public void run() {
    for (var job : jobs)
      job.run();
  }

  // Applications run background jobs all the time. Tests run them until they succeed and stop publishing new messages
  public void runUntilIdle(MessageCatcher publishedMessages) {
    for (var run = 0; run < 100; run++) {
      var publishedBefore = publishedMessages.published.size();

      try {
        run();
      } catch (TimeoutException exception) {
        continue;
      }

      if (publishedMessages.published.size() == publishedBefore)
        return;
    }

    throw new IllegalStateException("Background jobs keep failing or publishing messages");
  }
}
