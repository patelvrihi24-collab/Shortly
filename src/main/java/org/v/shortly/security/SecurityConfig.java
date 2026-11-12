package org.v.shortly.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration                                              // a class that defines beans
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                           StringRedisTemplate redis) throws Exception {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService);      // created by hand (see the @Component note)
        RateLimitFilter rateLimitFilter = new RateLimitFilter(redis);

        http
                .csrf(csrf -> csrf.disable())
                // CSRF attacks abuse cookies that browsers attach automatically. We use an Authorization
                // header the browser never adds by itself, so CSRF protection isn't needed.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Never create server-side sessions: the token is the only proof of identity.
                .httpBasic(b -> b.disable())                                 // no browser popup login
                .formLogin(f -> f.disable())                                 // no generated HTML login page
                .exceptionHandling(e -> e.authenticationEntryPoint(          // unauthenticated access to a protected URL
                        (req, res, ex) -> HttpErrorWriter.write(res, HttpStatus.UNAUTHORIZED, "Authentication required")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()  // register + login are public
                        .requestMatchers(HttpMethod.POST, "/api/v1/urls").permitAll()     // anonymous shortening allowed
                        .requestMatchers("/api/v1/**").authenticated()                     // everything else under the API needs a login
                        .anyRequest().permitAll())                                         // redirects /{code} and /error stay public
                // Order matters: the FIRST matching rule wins, so specific rules go above general ones.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)    // identify the user first...
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);                     // ...then rate-limit (needs to know who)

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();      // slow on purpose and salted, so stolen hashes are hard to crack
    }

    @Bean
    public UserDetailsService noUsers() {                  // Spring Boot only creates its default user when NO such bean exists
        return username -> {                               // UserDetailsService has one method, so a lambda works
            throw new UsernameNotFoundException("Username/password login is not used; this API authenticates with JWT only");
        };
    }
}