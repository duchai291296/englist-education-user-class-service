package com.english.education.security.config;

import com.english.education.security.exception.AccessDenied;
import com.english.education.security.exception.JwtEntryPoint;
import com.english.education.security.jwt.JwtTokenFilter;
import com.english.education.security.jwt.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {
    private final AccessDenied accessDenied;
    private final JwtEntryPoint jwtEntryPoint;
    private final JwtTokenFilter jwtTokenFilter;
    private final TraceIdFilter traceIdFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cf -> cf.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174"));
                    config.setAllowedMethods(List.of("*"));
                    config.setAllowCredentials(true);
                    config.setAllowedHeaders(List.of("*"));
                    config.setExposedHeaders(List.of("*"));
                    return config;
                }))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(url -> url
//                        .requestMatchers("/api/v1/admin/**").hasAuthority(RoleName.ROLE_ADMIN.name())
//                        .requestMatchers("/api/v1/moderator/**").hasAuthority(RoleName.ROLE_MODERATOR.name())
//                        .requestMatchers("/api/v1/user/**").hasAuthority(RoleName.ROLE_USER.name())
                        .anyRequest().permitAll())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtEntryPoint).accessDeniedHandler(accessDenied))
                .addFilterBefore(traceIdFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
