package io.flowlab.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class PersistenceConfig {
  /**
   * Use the same JSON semantics for HTTP and JSONB; do not auto-discover unrelated classpath
   * modules.
   */
  @Bean
  HibernatePropertiesCustomizer jsonFormatMapper(ObjectMapper objectMapper) {
    return properties ->
        properties.put(
            "hibernate.type.json_format_mapper", new JacksonJsonFormatMapper(objectMapper));
  }
}
