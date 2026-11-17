-- PROYECCIONES: tablas derivadas de los eventos, pensadas para consultar. Se pueden borrar y rearmar.
CREATE TABLE balance_view (
    wallet_id  TEXT          PRIMARY KEY,
    owner      TEXT          NOT NULL,
    balance    NUMERIC(12,2) NOT NULL,
    movements  INT           NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL
);

CREATE TABLE monthly_movements (
    wallet_id   TEXT          NOT NULL,
    month       TEXT          NOT NULL,   -- '2026-11', en hora de Lima
    deposits    NUMERIC(12,2) NOT NULL DEFAULT 0,
    withdrawals NUMERIC(12,2) NOT NULL DEFAULT 0,
    rejected    INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (wallet_id, month)
);

-- Hasta dónde leyó el proyector. Se actualiza en la MISMA transacción que las vistas.
CREATE TABLE projection_checkpoints (
    name            TEXT   PRIMARY KEY,
    transaction_id  XID8   NOT NULL,
    global_position BIGINT NOT NULL
);
INSERT INTO projection_checkpoints VALUES ('wallet-views', '0', 0);
