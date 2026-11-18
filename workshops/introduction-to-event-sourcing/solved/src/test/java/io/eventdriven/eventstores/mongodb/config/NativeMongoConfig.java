package io.eventdriven.eventstores.mongodb.config;

import static org.bson.codecs.configuration.CodecRegistries.*;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.eventdriven.eventstores.mongodb.codecs.OffsetDateTimeCodec;
import io.eventdriven.eventstores.testing.tools.TestDatabases;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

public class NativeMongoConfig {
    public static MongoClient createClient() {
        return createClient(TestDatabases.mongoDBConnectionString("test"));
    }

    public static MongoClient createClient(String connectionString) {
        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromCodecs(new OffsetDateTimeCodec()),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .codecRegistry(codecRegistry)
                .build();

        return MongoClients.create(settings);
    }
}
