package com.lms.identity.security;

import com.lms.identity.config.LmsProperties;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter, RateLimitFilter rateLimit) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API, no cookies
            .cors(c -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/identity/auth/register", "/api/v1/identity/auth/login",
                    "/api/v1/identity/auth/refresh", "/api/v1/identity/auth/logout",
                    "/api/v1/identity/auth/verify-email", "/api/v1/identity/auth/verify-mobile",
                    "/api/v1/identity/auth/resend-verification", "/api/v1/identity/auth/forgot-password",
                    "/api/v1/identity/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/commerce/plans", "/api/v1/commerce/courses").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/commerce/webhooks/razorpay").permitAll() // verified by HMAC signature
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, "UNAUTHENTICATED", "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> writeError(res, 403, "FORBIDDEN", "You do not have access to this resource")))
            .addFilterBefore(rateLimit, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Boot would also register these filters in the servlet chain, where they run before Spring Security
    // and their authentication gets discarded. They must only run inside the security filter chain.
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter f) {
        FilterRegistrationBean<JwtAuthFilter> r = new FilterRegistrationBean<>(f);
        r.setEnabled(false);
        return r;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter f) {
        FilterRegistrationBean<RateLimitFilter> r = new FilterRegistrationBean<>(f);
        r.setEnabled(false);
        return r;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(LmsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(props.corsOrigins().split(",")).map(String::trim).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Device-Id", "X-Device-Name"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse res, int status, String code, String msg)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"code\":\"" + code + "\",\"message\":\"" + msg + "\"}");
    }
}
