package io.eventdriven.eventdrivenarchitecture.e04_idempotency.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class DatabaseTest {
    public record Note(String text) {}

    @Test
    public void committedTransaction_ChangesAreVisibleThroughCollectionCreatedBeforeIt() {
        // Given
        var database = new Database();
        var notes = database.collection(Note.class);
        var id = UUID.randomUUID();

        // When
        database.transaction(db -> db.collection(Note.class).store(id, new Note("stored")));

        // Then
        assertThat(notes.get(id).orElseThrow()).isEqualTo(new Note("stored"));
    }

    @Test
    public void transactionThatThrows_AppliesNothing() {
        // Given
        var database = new Database();
        var notes = database.collection(Note.class);
        var id = UUID.randomUUID();

        // When
        var printed = new ByteArrayOutputStream();
        var originalOut = System.out;
        System.setOut(new PrintStream(printed));
        IllegalStateException exception;
        try {
            exception = assertThrows(
                    IllegalStateException.class,
                    () -> database.transaction(db -> {
                        db.collection(Note.class).store(id, new Note("stored"));
                        throw new IllegalStateException("failed");
                    }));
        } finally {
            System.setOut(originalOut);
        }

        // Then
        assertThat(exception.getMessage()).isEqualTo("failed");
        assertThat(printed.toString()).isEmpty();
        assertThat(notes.get(id)).isEmpty();
    }

    @Test
    public void twoTransactionsAtTheSameTimeOnDifferentDocuments_BothCommit() throws Exception {
        // Given
        var database = new Database();
        var sharedId = UUID.randomUUID();
        var newId = UUID.randomUUID();
        database.collection(Note.class).store(sharedId, new Note("original"));
        // And
        var secondTransactionOpen = new CountDownLatch(1);
        var firstTransactionCommitted = new CountDownLatch(1);

        // When
        var second = CompletableFuture.runAsync(() -> database.transaction(db -> {
            secondTransactionOpen.countDown();
            await(firstTransactionCommitted);
            db.collection(Note.class).store(newId, new Note("from second"));
        }));
        await(secondTransactionOpen);
        database.transaction(db -> db.collection(Note.class).store(sharedId, new Note("from first")));
        firstTransactionCommitted.countDown();
        second.get(5, TimeUnit.SECONDS);

        // Then
        var notes = database.collection(Note.class);
        assertThat(notes.get(sharedId).orElseThrow()).isEqualTo(new Note("from first"));
        assertThat(notes.get(newId).orElseThrow()).isEqualTo(new Note("from second"));
    }

    @Test
    public void deletedDocument_IsGoneAfterTheTransactionCommits() {
        // Given
        var database = new Database();
        var notes = database.collection(Note.class);
        var id = UUID.randomUUID();
        notes.store(id, new Note("stored"));

        // When
        database.transaction(db -> db.collection(Note.class).delete(id));

        // Then
        assertThat(notes.get(id)).isEmpty();
    }

    @Test
    public void transaction_SeesItsOwnChangesBeforeCommit() {
        // Given
        var database = new Database();
        var notes = database.collection(Note.class);
        var storedId = UUID.randomUUID();
        var deletedId = UUID.randomUUID();
        var newId = UUID.randomUUID();
        notes.store(storedId, new Note("stored"));
        notes.store(deletedId, new Note("deleted"));

        // When
        database.transaction(db -> {
            var transactionalNotes = db.collection(Note.class);
            transactionalNotes.store(storedId, new Note("changed"));
            transactionalNotes.store(newId, new Note("new"));
            transactionalNotes.delete(deletedId);

            // Then
            assertThat(transactionalNotes.get(storedId).orElseThrow()).isEqualTo(new Note("changed"));
            assertThat(transactionalNotes.get(deletedId)).isEmpty();
            assertThat(transactionalNotes.findAll()).containsExactlyInAnyOrder(new Note("changed"), new Note("new"));
            assertThat(notes.get(storedId).orElseThrow()).isEqualTo(new Note("stored"));
        });
    }

    @Test
    public void transactionThatReadDocumentAnotherTransactionChanged_FailsAndAppliesNothing() throws Exception {
        // Given
        var database = new Database();
        var sharedId = UUID.randomUUID();
        var newId = UUID.randomUUID();
        database.collection(Note.class).store(sharedId, new Note("original"));
        // And
        var secondTransactionRead = new CountDownLatch(1);
        var firstTransactionCommitted = new CountDownLatch(1);

        // When
        var second = CompletableFuture.runAsync(() -> database.transaction(db -> {
            db.collection(Note.class).get(sharedId);
            secondTransactionRead.countDown();
            await(firstTransactionCommitted);
            db.collection(Note.class).store(newId, new Note("from second"));
        }));
        await(secondTransactionRead);
        database.transaction(db -> db.collection(Note.class).store(sharedId, new Note("from first")));
        firstTransactionCommitted.countDown();
        var exception = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));

        // Then
        assertThat(exception.getCause()).isInstanceOf(ConcurrencyException.class);
        var notes = database.collection(Note.class);
        assertThat(notes.get(sharedId).orElseThrow()).isEqualTo(new Note("from first"));
        assertThat(notes.get(newId)).isEmpty();
    }

    @Test
    public void transactionThatWroteDocumentAnotherTransactionChanged_Fails() throws Exception {
        // Given
        var database = new Database();
        var sharedId = UUID.randomUUID();
        database.collection(Note.class).store(sharedId, new Note("original"));
        // And
        var secondTransactionWrote = new CountDownLatch(1);
        var firstTransactionCommitted = new CountDownLatch(1);

        // When
        var second = CompletableFuture.runAsync(() -> database.transaction(db -> {
            db.collection(Note.class).store(sharedId, new Note("from second"));
            secondTransactionWrote.countDown();
            await(firstTransactionCommitted);
        }));
        await(secondTransactionWrote);
        database.transaction(db -> db.collection(Note.class).store(sharedId, new Note("from first")));
        firstTransactionCommitted.countDown();
        var exception = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));

        // Then
        assertThat(exception.getCause()).isInstanceOf(ConcurrencyException.class);
        var notes = database.collection(Note.class);
        assertThat(notes.get(sharedId).orElseThrow()).isEqualTo(new Note("from first"));
    }

    @Test
    public void transactionThatReadDocumentStoredAgainOutsideIt_FailsAndAppliesNothing() {
        // Given
        var database = new Database();
        var notes = database.collection(Note.class);
        var sharedId = UUID.randomUUID();
        var newId = UUID.randomUUID();
        notes.store(sharedId, new Note("original"));

        // When
        assertThrows(
                ConcurrencyException.class,
                () -> database.transaction(db -> {
                    db.collection(Note.class).get(sharedId);
                    notes.store(sharedId, new Note("stored again"));
                    db.collection(Note.class).store(newId, new Note("from transaction"));
                }));

        // Then
        assertThat(notes.get(sharedId).orElseThrow()).isEqualTo(new Note("stored again"));
        assertThat(notes.get(newId)).isEmpty();
    }

    @Test
    public void transactionThatReadDocumentDeletedAndStoredAgainOutsideIt_FailsAndAppliesNothing() {
        // Given
        var database = new Database();
        var notes = database.collection(Note.class);
        var sharedId = UUID.randomUUID();
        var newId = UUID.randomUUID();
        notes.store(sharedId, new Note("original"));

        // When
        assertThrows(
                ConcurrencyException.class,
                () -> database.transaction(db -> {
                    db.collection(Note.class).get(sharedId);
                    notes.delete(sharedId);
                    notes.store(sharedId, new Note("original"));
                    db.collection(Note.class).store(newId, new Note("from transaction"));
                }));

        // Then
        assertThat(notes.get(newId)).isEmpty();
    }

    @Test
    public void transactionThatReadAllDocumentsOfTypeAnotherOneWasAddedTo_FailsAndAppliesNothing() {
        // Given
        var database = new Database();
        var notes = database.collection(Note.class);
        var newId = UUID.randomUUID();
        notes.store(UUID.randomUUID(), new Note("original"));

        // When
        assertThrows(
                ConcurrencyException.class,
                () -> database.transaction(db -> {
                    db.collection(Note.class).findAll();
                    notes.store(UUID.randomUUID(), new Note("added outside"));
                    db.collection(Note.class).store(newId, new Note("from transaction"));
                }));

        // Then
        assertThat(notes.get(newId)).isEmpty();
    }

    @Test
    public void transactionStartedInsideTransaction_Fails() {
        // Given
        var database = new Database();
        var notes = database.collection(Note.class);
        var id = UUID.randomUUID();

        // When
        var exception = assertThrows(
                UnsupportedOperationException.class,
                () -> database.transaction(db -> {
                    db.collection(Note.class).store(id, new Note("outer"));
                    db.transaction(inner -> {});
                }));

        // Then
        assertThat(exception.getMessage()).isEqualTo("Nested transactions are not supported");
        assertThat(notes.get(id)).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for the latch");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
