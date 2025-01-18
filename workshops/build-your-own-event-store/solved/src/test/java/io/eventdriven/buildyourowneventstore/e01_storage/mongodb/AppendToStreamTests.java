package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import bankaccounts.BankAccount;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import io.eventdriven.buildyourowneventstore.e01_storage.mongodb.events.EventDataCodec;
import io.eventdriven.buildyourowneventstore.e01_storage.mongodb.events.EventEnvelope;
import io.eventdriven.buildyourowneventstore.e01_storage.mongodb.events.EventMetadata;
import io.eventdriven.buildyourowneventstore.e01_storage.mongodb.events.EventTypeMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static bankaccounts.BankAccount.Event.BankAccountOpened;
import static com.mongodb.client.model.Filters.eq;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AppendToStreamTests {
  private MongoClient nativeClient;
  private EventDataCodec codec;

  @BeforeAll
  public void setup() {
    nativeClient = NativeMongoConfig.createClient();
    codec = new EventDataCodec(
      nativeClient.getCodecRegistry(),
      EventTypeMapper.DEFAULT
    );
  }

  @AfterAll
  void teardown() {
    if (nativeClient != null) {
      nativeClient.close();
    }
  }

  @Test
  void testNativeWrite_thenSpringRead() {
    // Insert with NATIVE POJO
    MongoCollection<EventStream> collection =
      nativeClient
        .getDatabase("test_db")
        .getCollection("event_streams", EventStream.class);

    var eventStream = createSampleEventStream("native-write");
    collection.insertOne(eventStream);

    // Check the raw document to confirm _class is present
    EventStream rawDoc = collection.find(eq("id", eventStream.id())).first();

    assert rawDoc != null;

    var events = rawDoc.events();

    var e = events.get(0).getEvent(codec);

    System.out.println("Raw from DB => " + rawDoc);
//
//    assertThat(rawDoc).containsKey("_class");  // verify the field is there
//
//    // Retrieve with SPRING
//    @SuppressWarnings("unchecked")
//    List<EventStream<String, MongoDBReadModelMetadata>> all =
//      springTemplate.findAll((Class) EventStream.class, "event_streams");
//    assertThat(all).hasSize(1);
//
//    EventStream<String, MongoDBReadModelMetadata> fromSpring = all.get(0);
//    assertThat(fromSpring.getStreamName()).isEqualTo("native-write");
//    assertThat(fromSpring.getMessages()).hasSize(1);
  }

  private EventStream createSampleEventStream(String streamName) {
    var bankAccountId = UUID.randomUUID();
    var accountNumber = "PL61 1090 1014 0000 0712 1981 2874";
    var clientId = UUID.randomUUID();
    var currencyISOCOde = "PLN";

    var event = new BankAccountOpened(
      bankAccountId,
      accountNumber,
      clientId,
      currencyISOCOde,
      LocalDateTime.now(),
      1
    );
    var events = List.of(
      EventEnvelope.of(
        BankAccount.Event.class,
        event,
        new EventMetadata(
          UUID.randomUUID().toString(),
          "bank-account-event",
          1L,
          streamName
        ),
        codec
      )
    );

    var now = LocalDateTime.now();

    var streamMetadata = new StreamMetadata(
      streamName,
      "BankAccount",
      1L,
      now,
      now
    );

    return new EventStream(ObjectId.get(), streamName, events, streamMetadata);//, events, streamMetadata);
  }
}
