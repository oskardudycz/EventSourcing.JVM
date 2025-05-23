package io.eventdriven.buildyourowneventstore.tools.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.UuidRepresentation;
import org.bson.codecs.UuidCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class NativeMongoConfig {
  public static MongoClient createClient() {
    return createClient("mongodb://localhost:27017/test?replicaSet=rs0&directConnection=true");
  }

  public static MongoClient createClient(String connectionString) {
    CodecRegistry codecRegistry = fromRegistries(
      MongoClientSettings.getDefaultCodecRegistry(),
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
