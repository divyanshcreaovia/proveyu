package com.proveyu.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.proveyu.shared.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] COLLEGE_ROLES = {"COLLEGE_ADMIN", "COLLEGE_COORDINATOR", "COLLEGE_VIEWER"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final ObjectMapper jsonMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> xss.headerValue(org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .contentTypeOptions(contentType -> {})
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                        "Authentication required or session expired", "UNAUTHORIZED"))
                .accessDeniedHandler((req, res, e) -> writeError(res, HttpServletResponse.SC_FORBIDDEN,
                        "You do not have permission to perform this action", "FORBIDDEN"))
            )
            .authorizeHttpRequests(auth -> auth
                // ---- public ----
                .requestMatchers("/api/v1/auth/logout").authenticated()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/college/auth/register", "/api/v1/college/auth/accept-invite",
                        "/api/v1/college/auth/student-signup", "/api/v1/college/auth/recruiter-accept").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/college/auth/invite/**", "/api/v1/college/auth/recruiter-invite/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/domains/**").permitAll()
                .requestMatchers("/api/v1/candidates/resume/download/**").permitAll()
                .requestMatchers("/api/v1/admit-cards/download/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/exams/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // WebSocket handshake authenticates itself via WebSocketAuthInterceptor (?token=)
                .requestMatchers("/ws/**").permitAll()
                // ---- role based ----
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/college/student/**").hasRole("CANDIDATE")
                .requestMatchers("/api/v1/college/**").hasAnyRole(COLLEGE_ROLES)
                // candidates read / answer their own job invitations through the recruiter jobs API
                .requestMatchers("/api/v1/recruiter/jobs/candidate/**").hasRole("CANDIDATE")
                .requestMatchers(HttpMethod.PUT, "/api/v1/recruiter/jobs/invitations/*/accept",
                        "/api/v1/recruiter/jobs/invitations/*/decline").hasRole("CANDIDATE")
                .requestMatchers("/api/v1/recruiter/**", "/api/v1/recruiters/**").hasAnyRole("RECRUITER", "ADMIN")
                .requestMatchers("/api/v1/test/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse res, int status, String message, String code) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(message, code);
        body.setStatus(status);
        res.getWriter().write(jsonMapper.writeValueAsString(body));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if ("*".equals(allowedOrigins)) {
            configuration.addAllowedOriginPattern("*");
        } else {
            configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        }
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setExposedHeaders(Arrays.asList("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
