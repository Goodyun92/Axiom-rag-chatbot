package org.dyheo.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // CSRF 비활성화로 Ajax 통신 편의성 확보
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login").permitAll() // 로그인 페이지는 누구나 접근 가능
                .anyRequest().authenticated() // 그 외 모든 요청에 인증 요구
            )
            .formLogin(form -> form
                .loginPage("/login") // 커스텀 로그인 페이지 지정
                .defaultSuccessUrl("/documents", true) // 로그인 성공 시 이동할 기본 페이지
                .permitAll()
            );
        
        return http.build();
    }
}
