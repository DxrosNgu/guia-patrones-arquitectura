package com.mercadoinventos.gateway;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

/**
 * Gateway Offloading: lo que antes repetía cada servicio (validar el token, CORS) se hace UNA vez aquí.
 * La validación del JWT (firma, vencimiento, emisor) la configura Spring con application.yaml.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)              // API con tokens, sin cookies de sesión
            .cors(cors -> {})                                         // usa el CorsConfigurationSource de abajo
            .authorizeExchange(rules -> rules
                .pathMatchers("/actuator/health/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()   // el catálogo es público
                .pathMatchers(HttpMethod.POST, "/api/orders/**").hasRole("buyer")
                .pathMatchers("/api/orders/**").authenticated()
                .pathMatchers("/actuator/gateway/**").permitAll()   // ⚠️ solo para el lab: muestra las rutas
                .anyExchange().denyAll())                             // lo que no está en la lista, no pasa
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRoles())))
            .build();
    }

    /** Keycloak pone los roles en realm_access.roles; Spring los espera como ROLE_xxx. */
    static Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");     // la clave del rate limit por usuario
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoles);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @SuppressWarnings("unchecked")
    static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return ((List<String>) roles).stream()
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
    }

    /** CORS en un solo lugar: los servicios de atrás ya no necesitan configurarlo. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${gateway.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("X-RateLimit-Remaining"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
