package com.coreclash.webbackend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.FirebaseDatabase;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfig {

  @Bean
  FirebaseApp firebaseApp(@Value("${coreclash.firebase.database-url}") String databaseUrl) throws IOException {
    if (FirebaseApp.getApps().isEmpty()) {
      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.getApplicationDefault())
          .setDatabaseUrl(databaseUrl)
          .build();
      return FirebaseApp.initializeApp(options);
    }
    return FirebaseApp.getInstance();
  }

  @Bean
  FirebaseDatabase firebaseDatabase(FirebaseApp app) {
    return FirebaseDatabase.getInstance(app);
  }
}
