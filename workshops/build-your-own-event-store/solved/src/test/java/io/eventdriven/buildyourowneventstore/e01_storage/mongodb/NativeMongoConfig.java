package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

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
  public static CodecRegistry codecRegistry = fromRegistries(
    MongoClientSettings.getDefaultCodecRegistry(),
    fromProviders(PojoCodecProvider.builder().automatic(true).build())
  );

  public static MongoClient createClient() {
    CodecRegistry codecRegistry = fromRegistries(
      CodecRegistries.fromCodecs(new UuidCodec(UuidRepresentation.STANDARD)), // Explicitly add UUIDCodec
      MongoClientSettings.getDefaultCodecRegistry(),
      fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    MongoClientSettings settings = MongoClientSettings.builder()
      .applyConnectionString(new ConnectionString("mongodb://localhost:27017/test?replicaSet=rs0&directConnection=true"))
      .uuidRepresentation(UuidRepresentation.STANDARD)
      .codecRegistry(codecRegistry)
      .build();


    return MongoClients.create(settings);
  }
}
