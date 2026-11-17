package com.mercadoinventos.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTest {

    static final String ISSUER = "http://localhost:8180/realms/mercado";
    static final RSAKey KEY = generateKey();
    static final FakeBackend BACKEND = startBackend();
    static final GenericContainer<?> REDIS = startRedis();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("CATALOG_URL", BACKEND::url);
        registry.add("ORDERS_URL", BACKEND::url);
        registry.add("JWK_SET_URI", () -> BACKEND.url() + "/jwks");
        registry.add("JWT_ISSUER", () -> ISSUER);
        registry.add("REDIS_HOST", REDIS::getHost);
        registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
    }

    @Value("${local.server.port}")
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        BACKEND.received.clear();
    }

    @Test
    void theCatalogIsPublicAndRoutedToItsService() {
        client.get().uri("/api/catalog/inventions").exchange().expectStatus().isOk();

        assertThat(BACKEND.received).extracting(FakeBackend.Received::path).containsExactly("/api/catalog/inventions");
    }

    @Test
    void ordersWithoutTokenIs401AndNeverReachesTheService() {
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();

        assertThat(BACKEND.received).isEmpty();
    }

    @Test
    void buyingWithoutTheBuyerRoleIs403() {
        client.post().uri("/api/orders").header("Authorization", "Bearer " + token("beto", ISSUER))
            .bodyValue(Map.of("items", List.of())).exchange()
            .expectStatus().isForbidden();

        assertThat(BACKEND.received).isEmpty();
    }

    @Test
    void aBuyerReachesOrdersAndTheTokenTravelsWithTheRequest() {
        String token = token("ana", ISSUER, "buyer");

        client.post().uri("/api/orders").header("Authorization", "Bearer " + token)
            .bodyValue(Map.of("items", List.of())).exchange()
            .expectStatus().isCreated();

        // El gateway no "consume" el token: lo reenvía para que el servicio lo valide otra vez.
        assertThat(BACKEND.received).singleElement()
            .satisfies(r -> assertThat(r.authorization()).isEqualTo("Bearer " + token));
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() {
        client.get().uri("/api/orders").header("Authorization", "Bearer " + token("ana", "http://evil.example/realms/x", "buyer"))
            .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void eachUserHasHerOwnRateLimit() {
        String carla = token("carla", ISSUER, "buyer");
        List<HttpStatus> statuses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            statuses.add(HttpStatus.valueOf(client.get().uri("/api/orders").header("Authorization", "Bearer " + carla)
                .exchange().returnResult(String.class).getStatus().value()));
        }
        assertThat(statuses).contains(HttpStatus.TOO_MANY_REQUESTS).contains(HttpStatus.OK);

        // Otro usuario tiene su propio contador: no le afecta la ráfaga de carla.
        client.get().uri("/api/orders").header("Authorization", "Bearer " + token("dani", ISSUER, "buyer"))
            .exchange().expectStatus().isOk();
    }

    @Test
    void corsIsHandledOnceAtTheGateway() {
        client.options().uri("/api/orders")
            .header("Origin", "http://localhost:3000").header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "authorization,content-type")
            .exchange().expectStatus().isOk()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");

        client.options().uri("/api/orders")
            .header("Origin", "http://evil.example").header("Access-Control-Request-Method", "POST")
            .exchange().expectStatus().isForbidden();
    }

    // ── utilidades ──────────────────────────────────────────────────────────

    static String token(String user, String issuer, String... roles) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer).subject("id-" + user).claim("preferred_username", user)
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    static FakeBackend startBackend() {
        try {
            return new FakeBackend(KEY.toPublicJWK());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("resource")
    static GenericContainer<?> startRedis() {
        GenericContainer<?> redis = new GenericContainer<>("redis:8.10.3-alpine").withExposedPorts(6379);
        redis.start();
        return redis;
    }
}
