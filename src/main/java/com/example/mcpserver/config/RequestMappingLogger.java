package com.example.mcpserver.config;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class RequestMappingLogger {

  private static final Logger log = LoggerFactory.getLogger(RequestMappingLogger.class);

  @Bean
  public ApplicationRunner logMcpMappings(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping
  ) {
    return args -> {
      for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
        String patterns = entry.getKey().getPatternValues().toString();
        if (patterns.contains("/mcp")) {
          log.info("MCP mapping: {} -> {}", patterns, entry.getValue());
        }
      }
    };
  }
}
