package com.example.migration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * 安全配置類
 * 配置 Spring Security 相關設定
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.admin.username:admin}")
    private String adminUsername;

    @Value("${security.admin.password:admin123}")
    private String adminPassword;

    @Value("${security.viewer.username:viewer}")
    private String viewerUsername;

    @Value("${security.viewer.password:viewer123}")
    private String viewerPassword;

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    /**
     * 密碼編碼器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * 用戶詳細信息服務
     */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder().encode(adminPassword))
                .roles("ADMIN", "USER")
                .build();

        UserDetails viewer = User.builder()
                .username(viewerUsername)
                .password(passwordEncoder().encode(viewerPassword))
                .roles("VIEWER")
                .build();

        return new InMemoryUserDetailsManager(admin, viewer);
    }

    /**
     * 安全過濾器鏈配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf().disable()
                    .build();
        }

        http
            // CSRF 配置
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/batch/**", "/actuator/**")
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            
            // 會話管理
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(10)
                .maxSessionsPreventsLogin(false)
            )
            
            // 認證配置
            .authorizeHttpRequests(auth -> auth
                // 公開端點
                .requestMatchers("/", "/login", "/logout", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                
                // 管理員端點
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/batch/start", "/api/batch/stop", "/api/batch/restart").hasRole("ADMIN")
                .requestMatchers("/api/config/**").hasRole("ADMIN")
                
                // 一般用戶端點
                .requestMatchers("/api/batch/status", "/api/batch/history").hasAnyRole("ADMIN", "USER", "VIEWER")
                .requestMatchers("/api/monitoring/**").hasAnyRole("ADMIN", "USER", "VIEWER")
                
                // 監控端點
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // 其他請求需要認證
                .anyRequest().authenticated()
            )
            
            // 登入配置
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )
            
            // 登出配置
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessUrl("/login?logout=true")
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            
            // 記住我功能
            .rememberMe(remember -> remember
                .key("migration-remember-me")
                .tokenValiditySeconds(86400) // 24小時
                .userDetailsService(userDetailsService())
            )
            
            // 安全標頭
            .headers(headers -> headers
                .frameOptions().sameOrigin()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .includeSubdomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            )
            
            // 異常處理
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/error/403")
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.sendError(401, "Unauthorized");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
            );

        return http.build();
    }
}