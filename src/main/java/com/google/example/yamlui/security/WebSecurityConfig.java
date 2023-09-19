// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.example.yamlui.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Enables username/password authentication for the application.
 *
 * <p>Also sets up sample users for demo purposes.
 *
 * <p><a
 * href="https://docs.spring.io/spring-security/reference/6.1/servlet/configuration/java.html">Reference</a>.
 */
@Configuration
@EnableWebSecurity
class WebSecurityConfig {

  @Bean
  UserDetailsService userDetailsService() {
    InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
    manager.createUser(userBuilder().username("alice").password("password").roles("USER").build());
    manager.createUser(userBuilder().username("bob").password("password").roles("USER").build());
    manager.createUser(
        userBuilder().username("charlie").password("password").roles("USER").build());
    manager.createUser(
        userBuilder().username("admin").password("password").roles("USER,ADMIN").build());
    return manager;
  }

  /** <code>withDefaultPasswordEncoder()</code> is used since this is a sample application. */
  @SuppressWarnings("deprecation")
  private UserBuilder userBuilder() {
    return org.springframework.security.core.userdetails.User.withDefaultPasswordEncoder();
  }

  /**
   * Configure authentication and authorization for the application using Spring Security.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Actuator endpoints are unauthenticated, on the assumption that these endpoints are only
   *       available on a port that is <em>not</em> exposed externally.
   *   <li>Schema update requests are authenticated and require the <code>ADMIN</code> role.
   *   <li>All other requests (except for the login page and logout endpoint) are authenticated and
   *       require the <code>USER</code> role.
   * </ul>
   *
   * <p>The method enables form-based login for web browser users, and HTTP Basic authentication for
   * programmatic usage. For production use, HTTP Basic authentication should be combined with
   * authenticated transport using HTTPS.
   */
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/schema/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/schema/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .hasRole("USER"))
        .formLogin(Customizer.withDefaults())
        .httpBasic(Customizer.withDefaults())
        // .rememberMe(Customizer.withDefaults())
        .build();
  }
}
