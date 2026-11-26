package com.restogestion.config;

import com.restogestion.entity.Usuario;
import com.restogestion.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import com.restogestion.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UsuarioRepository usuarioRepository) {
        return username -> {
            Usuario usuario = usuarioRepository.findByUsername(username)
                    .filter(u -> "ACTIVO".equalsIgnoreCase(u.getEstado()))
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado o inactivo"));

            return User.withUsername(usuario.getUsername())
                    .password(usuario.getPasswordHash())
                    .roles(usuario.getRol().getNombre())
                    .build();
        };
    }

    @Bean
    AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) {
                response.sendRedirect("/");
            } else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CAJERO"))) {
                response.sendRedirect("/caja");
            } else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_COCINERO"))) {
                response.sendRedirect("/cocina");
            } else if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"))) {
                response.sendRedirect("/cliente");
            } else {
                response.sendRedirect("/mesas");
            }
        };
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                 JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                // Permite reutilizar la sesión del frontend Thymeleaf y también autenticar por Bearer JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/usuarios/**", "/api/roles/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/mesas/**").hasAnyRole("ADMINISTRADOR", "MESERO", "COCINERO")
                        .requestMatchers("/api/mesas/**", "/api/pedidos/**").hasAnyRole("ADMINISTRADOR", "MESERO")
                        .requestMatchers(HttpMethod.GET, "/api/productos/**", "/api/categorias/**").hasAnyRole("ADMINISTRADOR", "MESERO", "CLIENTE")
                        .requestMatchers("/api/productos/**", "/api/categorias/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/cocina/**").hasAnyRole("ADMINISTRADOR", "COCINERO")
                        .requestMatchers("/api/caja/**").hasAnyRole("ADMINISTRADOR", "CAJERO")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             AuthenticationSuccessHandler successHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/error").permitAll()
                        .requestMatchers("/menu/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/usuarios/**", "/reportes/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/cliente/**").hasRole("CLIENTE")
                        .requestMatchers("/mesas/nueva", "/mesas/editar/**", "/mesas/guardar", "/mesas/eliminar/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/mesas/**").hasAnyRole("ADMINISTRADOR", "MESERO", "COCINERO")
                        .requestMatchers("/mesas/**", "/pedidos/**").hasAnyRole("ADMINISTRADOR", "MESERO")
                        .requestMatchers("/cocina/**").hasAnyRole("ADMINISTRADOR", "COCINERO")
                        .requestMatchers("/caja/**").hasAnyRole("ADMINISTRADOR", "CAJERO")
                        .requestMatchers("/").hasRole("ADMINISTRADOR")
                        .requestMatchers("/panel").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                ;

        return http.build();
    }
}
