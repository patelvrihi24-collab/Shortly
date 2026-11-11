package org.v.shortly.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(                              // the title block at the top of the docs page
        title = "Shortly API", version = "v1",
        description = "URL shortener with Redis caching, JWT auth, rate limiting and click analytics"))
@SecurityScheme(name = "bearerAuth",                          // declares "this API accepts Bearer JWT tokens"
        type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {}                                 // no body: the annotations are the whole config