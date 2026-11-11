package org.v.shortly.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration        // a class that configures Spring
@EnableScheduling     // switches on @Scheduled processing; without it @Scheduled methods are ignored
public class SchedulingConfig {}