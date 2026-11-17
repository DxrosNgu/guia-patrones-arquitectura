package com.mercadoinventos.wallet;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Con un Postgres de verdad (Dev Services) y las migraciones del lab, incluidos los datos de ejemplo. */
@QuarkusTest
class WalletApiTest {

    @Inject
    AgroalDataSource dataSource;

    @AfterEach
    void resetKnobs() {
        given().contentType(ContentType.JSON).body("{\"ms\":0}").put("/admin/think-time").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"maxAttempts\":3}").put("/admin/retries").then().statusCode(200);
    }

    private String open(String owner, String initialDeposit) {
        String id = given().contentType(ContentType.JSON).body(Map.of("owner", owner))
            .post("/api/wallets").then().statusCode(201).extract().path("id");
        given().contentType(ContentType.JSON).body(Map.of("amount", initialDeposit, "channel", "YAPE"))
            .post("/api/wallets/" + id + "/deposits").then().statusCode(200);
        return id;
    }

    private Response withdraw(String path, String amount) {
        return given().contentType(ContentType.JSON).body(Map.of("amount", amount)).post(path);
    }

    @Test
    void deposit_and_withdraw_and_see_the_history() {
        String id = open("carla", "100.00");

        withdraw("/api/wallets/" + id + "/withdrawals", "30.50").then().statusCode(200).body("balance", equalTo(69.50f));

        given().get("/api/wallets/" + id).then().statusCode(200).body("balance", equalTo(69.50f)).body("version", equalTo(3));
        List<String> types = given().get("/api/wallets/" + id + "/events").then().extract().path("type");
        assertThat(types).containsExactly("WalletOpened", "FundsDeposited", "FundsWithdrawn");
    }

    @Test
    void a_rejected_withdrawal_is_a_422_and_stays_in_the_history() {
        String id = open("dani", "10.00");

        withdraw("/api/wallets/" + id + "/withdrawals", "50.00").then().statusCode(422)
            .body("events[0]", equalTo("WithdrawalRejected")).body("balance", equalTo(10.00f));

        List<String> types = given().get("/api/wallets/" + id + "/events").then().extract().path("type");
        assertThat(types).last().isEqualTo("WithdrawalRejected");
    }

    @Test
    void time_travel_gives_the_balance_at_a_past_date() {
        given().get("/api/wallets/wallet-ana").then().body("balance", equalTo(360.00f));
        given().get("/api/wallets/wallet-ana?at=2026-11-01").then().statusCode(200).body("balance", equalTo(580.00f));
        given().get("/api/wallets/wallet-ana?at=2026-10-02").then().body("balance", equalTo(0.00f));
        given().get("/api/wallets/wallet-ana?at=2026-09-01").then().statusCode(404);
    }

    @Test
    void old_v1_events_are_read_in_the_v2_format() {
        Response events = given().get("/api/wallets/wallet-ana/events");

        assertThat(events.<Integer>path("[1].storedSchemaVersion")).isEqualTo(1);
        assertThat(events.<Integer>path("[1].schemaVersion")).isEqualTo(2);
        assertThat(events.<Float>path("[1].data.amount")).isEqualTo(500.00f);
        assertThat(events.<String>path("[1].data.channel")).isEqualTo("DESCONOCIDO");
        assertThat(events.<String>path("[4].data.channel")).isEqualTo("YAPE");
    }

    @Test
    void two_concurrent_withdrawals_one_retries_and_is_rejected_with_the_new_balance() {
        String id = open("eva", "100.00");
        given().contentType(ContentType.JSON).body("{\"ms\":300}").put("/admin/think-time");

        List<Response> responses = concurrently(() -> withdraw("/api/wallets/" + id + "/withdrawals", "80.00"));

        assertThat(responses).extracting(Response::statusCode).containsExactlyInAnyOrder(200, 422);
        Response rejected = responses.stream().filter(r -> r.statusCode() == 422).findFirst().orElseThrow();
        assertThat(rejected.<Integer>path("attempts")).isEqualTo(2);
        given().get("/api/wallets/" + id).then().body("balance", equalTo(20.00f)).body("version", equalTo(4));
    }

    @Test
    void without_retries_the_loser_gets_a_409() {
        String id = open("fede", "100.00");
        given().contentType(ContentType.JSON).body("{\"ms\":300}").put("/admin/think-time");
        given().contentType(ContentType.JSON).body("{\"maxAttempts\":1}").put("/admin/retries");

        List<Response> responses = concurrently(() -> withdraw("/api/wallets/" + id + "/withdrawals", "10.00"));

        assertThat(responses).extracting(Response::statusCode).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void the_naive_version_loses_an_update() {
        String id = given().contentType(ContentType.JSON).body(Map.of("owner", "gabi", "balance", "100.00"))
            .post("/api/naive/wallets").then().statusCode(201).extract().path("id");
        given().contentType(ContentType.JSON).body("{\"ms\":300}").put("/admin/think-time");

        List<Response> responses = concurrently(() -> withdraw("/api/naive/wallets/" + id + "/withdrawals", "80.00"));

        assertThat(responses).extracting(Response::statusCode).containsExactly(200, 200);
        // Se retiraron 160 de 100 y el saldo dice 20: un retiro se perdió.
        given().get("/api/naive/wallets/" + id).then().body("balance", equalTo(20.00f));
    }

    @Test
    void after_n_events_the_state_is_loaded_from_a_snapshot() {
        String id = open("hugo", "1.00");
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).body(Map.of("amount", "1.00")).post("/api/wallets/" + id + "/deposits");
        }

        Response loaded = given().get("/api/wallets/" + id);

        assertThat(loaded.<Integer>path("version")).isEqualTo(7);
        assertThat(loaded.header("X-Snapshot-Version")).isEqualTo("5");
        assertThat(loaded.header("X-Events-Replayed")).isEqualTo("2");
        assertThat(loaded.<Float>path("balance")).isEqualTo(6.00f);
    }

    @Test
    void the_event_store_is_append_only() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            assertThat(catchSql(() -> s.executeUpdate("UPDATE events SET payload = '{}' WHERE stream_id = 'wallet-ana'")))
                .contains("append-only");
            assertThat(catchSql(() -> s.executeUpdate("DELETE FROM events"))).contains("append-only");
        }
    }

    @Test
    void the_projections_catch_up_and_can_be_rebuilt_from_scratch() throws Exception {
        String id = open("iris", "50.00");
        withdraw("/api/wallets/" + id + "/withdrawals", "20.00").then().statusCode(200);
        withdraw("/api/wallets/" + id + "/withdrawals", "99.00").then().statusCode(422);
        waitUntilProjected();
        List<Map<String, Object>> before = given().get("/api/balances").then().extract().path("$");
        assertThat(before).anySatisfy(row -> {
            assertThat(row.get("walletId")).isEqualTo(id);
            assertThat(((Number) row.get("balance")).doubleValue()).isEqualTo(30.0);
        });
        given().get("/api/wallets/wallet-ana/monthly").then().body("[0].month", equalTo("2026-10"))
            .body("[0].deposits", equalTo(700.00f)).body("[1].rejected", equalTo(1));

        given().post("/admin/projections/rebuild").then().statusCode(202);
        waitUntilProjected();

        List<Map<String, Object>> after = given().get("/api/balances").then().extract().path("$");
        assertThat(after).extracting(r -> r.get("walletId") + "=" + r.get("balance"))
            .containsExactlyElementsOf(before.stream().map(r -> r.get("walletId") + "=" + r.get("balance")).toList());
    }

    /**
     * La transacción A toma la posición 100 pero confirma DESPUÉS que la B (posición 101). Un proyector que
     * leyera "posición > checkpoint" vería la 101, avanzaría el checkpoint y se saltaría la 100 para siempre.
     */
    @Test
    void an_event_committed_late_is_not_skipped_by_the_projector() throws Exception {
        String slow = open("lento", "10.00");
        String fast = open("rapido", "10.00");
        waitUntilProjected();

        try (Connection a = dataSource.getConnection()) {
            a.setAutoCommit(false);
            try (Statement s = a.createStatement()) {
                s.executeUpdate("INSERT INTO events (stream_id, version, type, schema_version, payload) VALUES ('"
                    + slow + "', 3, 'FundsDeposited', 2, '{\"amount\": 5.00, \"currency\": \"PEN\", \"channel\": \"YAPE\"}')");
            }
            given().contentType(ContentType.JSON).body(Map.of("amount", "7.00"))
                .post("/api/wallets/" + fast + "/deposits").then().statusCode(200);
            Thread.sleep(1500);
            // El costo: mientras A siga abierta, el proyector ESPERA (no proyecta ni siquiera lo de B).
            assertThat(balanceInView(fast)).isEqualTo(10.0);
            a.commit();
        }
        waitUntilProjected();

        assertThat(balanceInView(slow)).isEqualTo(15.0);
        assertThat(balanceInView(fast)).isEqualTo(17.0);
    }

    private double balanceInView(String walletId) {
        List<Map<String, Object>> rows = given().get("/api/balances").then().extract().path("$");
        return rows.stream().filter(r -> walletId.equals(r.get("walletId")))
            .mapToDouble(r -> ((Number) r.get("balance")).doubleValue()).findFirst().orElseThrow();
    }

    private void waitUntilProjected() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Thread.sleep(200);
            if (given().get("/admin/projections").<Integer>path("pending") == 0) {
                return;
            }
        }
        throw new AssertionError("El proyector no se puso al día");
    }

    private static List<Response> concurrently(java.util.function.Supplier<Response> call) {
        CompletableFuture<Response> first = CompletableFuture.supplyAsync(call);
        CompletableFuture<Response> second = CompletableFuture.supplyAsync(call);
        return List.of(first.join(), second.join());
    }

    interface SqlAction {
        void run() throws SQLException;
    }

    private static String catchSql(SqlAction action) {
        try {
            action.run();
            return "sin error";
        } catch (SQLException e) {
            return e.getMessage();
        }
    }
}
