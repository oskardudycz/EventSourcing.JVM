package bankaccounts;

import java.time.LocalDateTime;
import java.util.UUID;

public class BankAccount {
    public final class Events
    {
        public record BankAccountOpened(
            UUID BankAccountId,
            String AccountNumber,
            UUID ClientId,
            String CurrencyISOCode,
            LocalDateTime CreatedAt,
            long Version
        ){}

        public record DepositRecorded(
            UUID BankAccountId,
            double Amount,
            UUID CashierId,
            LocalDateTime RecordedAt,
            long Version
        ){}

        public record CashWithdrawnFromATM(
            UUID BankAccountId,
            double Amount,
            UUID ATMId,
            LocalDateTime RecordedAt,
            long Version
        ){}

        public record BankAccountClosed(
            UUID BankAccountId,
            String commandReason,
            LocalDateTime ClosedAt,
            long Version
        ){}
    }
}
