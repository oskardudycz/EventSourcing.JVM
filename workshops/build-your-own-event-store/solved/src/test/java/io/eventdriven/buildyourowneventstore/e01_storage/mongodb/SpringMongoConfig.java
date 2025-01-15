package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class SpringMongoConfig {

  @Bean
  public MongoTemplate mongoTemplate() {
    // no special typeMapper => default is _class
    var connectionString = new ConnectionString("mongodb://localhost:27017/?replicaSet=rs0&directConnection=true");
    return new MongoTemplate(MongoClients.create(connectionString), "test");
  }
}
