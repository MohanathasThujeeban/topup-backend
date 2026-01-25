package com.example.topup.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

@Configuration
public class MongoDBConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Override
    protected String getDatabaseName() {
        // Extract database name from the URI
        ConnectionString connString = new ConnectionString(mongoUri);
        return connString.getDatabase() != null ? connString.getDatabase() : "topup_db";
    }

    @Override
    public MongoClient mongoClient() {
        System.out.println("===== MongoDBConfig: Creating MongoClient with URI: " + hidePassword(mongoUri) + " =====");
        ConnectionString connectionString = new ConnectionString(mongoUri);
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        return MongoClients.create(mongoClientSettings);
    }

    private String hidePassword(String uri) {
        if (uri != null && uri.contains("@")) {
            return uri.replaceAll("://[^:]+:[^@]+@", "://***:***@");
        }
        return uri;
    }
}
