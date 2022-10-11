package bankaccounts;

import java.time.LocalDateTime;
import java.util.UUID;

import static bankaccounts.BankAccount.Event.*;

public record BankAccount(
    UUID id,
    BankAccountStatus status,
    double balance,
    long version
) {
    public static BankAccount evolve(BankAccount bankAccount, Event event) {
        return switch (event) {
            case BankAccountOpened bankAccountOpened -> create(bankAccountOpened);
            case DepositRecorded depositRecorded -> bankAccount.apply(depositRecorded);
            case CashWithdrawnFromATM cashWithdrawnFromATM -> bankAccount.apply(cashWithdrawnFromATM);
            case BankAccountClosed bankAccountClosed -> bankAccount.apply(bankAccountClosed);
        };
    }

    private static BankAccount create(BankAccountOpened event) {
        return new BankAccount(
            event.bankAccountId,
            BankAccountStatus.Opened,
            0,
            event.version
        );
    }

    private BankAccount apply(DepositRecorded event) {
        return new BankAccount(
            this.id,
            this.status(),
            this.balance() + event.amount(),
            event.version
        );
    }

    private BankAccount apply(CashWithdrawnFromATM event) {
        return new BankAccount(
            this.id,
            this.status(),
            this.balance() - event.amount(),
            event.version
        );
    }

    private BankAccount apply(BankAccountClosed event) {
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
            UUID bankAccountId,
            String accountNumber,
            UUID clientId,
            String currencyISOCode,
            LocalDateTime createdAt,
            long version
        ) implements Event {
        }

        record DepositRecorded(
            UUID bankAccountId,
            double amount,
            UUID cashierId,
            LocalDateTime recordedAt,
            long version
        ) implements Event {
        }

        record CashWithdrawnFromATM(
            UUID bankAccountId,
            double amount,
            UUID atmId,
            LocalDateTime recordedAt,
            long version
        ) implements Event {
        }

        record BankAccountClosed(
            UUID bankAccountId,
            String Reason,
            LocalDateTime closedAt,
            long version
        ) implements Event {
        }
    }
}
