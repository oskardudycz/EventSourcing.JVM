package io.eventdriven.eventsversioning.snapshottesting;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

public class PackageSnapshotTests {

  @Test
  public void v1_package_hasNoPublicApiChanges() {
    var api = PublicApiScanner.forPackage("io.eventdriven.eventsversioning.v1")
      .generate();

    Approvals.verify(api);
  }
}
