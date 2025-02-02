package io.eventdriven.eventstores.testing.bankaccounts;

import java.time.LocalDateTime;

public record BankAccount(
  String id,
  BankAccountStatus status,
  double balance,
  long version
) {
  public static BankAccount evolve(BankAccount bankAccount, Event event) {
    return switch (event) {
      case Event.BankAccountOpened bankAccountOpened -> create(bankAccountOpened);
      case Event.DepositRecorded depositRecorded ->
        bankAccount.apply(depositRecorded);
      case Event.CashWithdrawnFromATM cashWithdrawnFromATM ->
        bankAccount.apply(cashWithdrawnFromATM);
      case Event.BankAccountClosed bankAccountClosed ->
        bankAccount.apply(bankAccountClosed);
    };
  }

  private static BankAccount create(Event.BankAccountOpened event) {
    return new BankAccount(
      event.bankAccountId,
      BankAccountStatus.Opened,
      0,
      event.version
    );
  }

  private BankAccount apply(Event.DepositRecorded event) {
    return new BankAccount(
      this.id,
      this.status(),
      this.balance() + event.amount(),
      event.version
    );
  }

  private BankAccount apply(Event.CashWithdrawnFromATM event) {
    return new BankAccount(
      this.id,
      this.status(),
      this.balance() - event.amount(),
      event.version
    );
  }

  private BankAccount apply(Event.BankAccountClosed event) {
    return new BankAccount(
      this.id,
      BankAccountStatus.Closed,
      this.balance(),
      event.version
    );
  }

  public enum BankAccountStatus {
    Opened,
    Closed
  }

  public sealed interface Event {
    record BankAccountOpened(
      String bankAccountId,
      String accountNumber,
      String clientId,
      String currencyISOCode,
      LocalDateTime createdAt,
      long version
    ) implements Event {
    }

    record DepositRecorded(
      String bankAccountId,
      double amount,
      String cashierId,
      LocalDateTime recordedAt,
      long version
    ) implements Event {
    }

    record CashWithdrawnFromATM(
      String bankAccountId,
      double amount,
      String atmId,
      LocalDateTime recordedAt,
      long version
    ) implements Event {
    }

    record BankAccountClosed(
      String bankAccountId,
      String Reason,
      LocalDateTime closedAt,
      long version
    ) implements Event {
    }
  }
}
