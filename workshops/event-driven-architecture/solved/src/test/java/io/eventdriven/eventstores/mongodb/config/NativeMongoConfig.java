package io.eventdriven.eventstores.mongodb.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.eventdriven.eventstores.mongodb.codecs.OffsetDateTimeCodec;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import static org.bson.codecs.configuration.CodecRegistries.*;

public class NativeMongoConfig {
  public static MongoClient createClient() {
    return createClient("mongodb://localhost:27017/test?replicaSet=rs0&directConnection=true");
  }

  public static MongoClient createClient(String connectionString) {
    CodecRegistry codecRegistry = fromRegistries(
      MongoClientSettings.getDefaultCodecRegistry(),
      fromCodecs(new OffsetDateTimeCodec()),
      fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    MongoClientSettings settings = MongoClientSettings.builder()
      .applyConnectionString(new ConnectionString(connectionString))
      .uuidRepresentation(UuidRepresentation.STANDARD)
      .codecRegistry(codecRegistry)
      .build();

    return MongoClients.create(settings);
  }
}
