-- La versión INGENUA, para comparar: solo el estado actual. La historia se pierde en cada UPDATE.
CREATE TABLE naive_wallets (
    id      TEXT          PRIMARY KEY,
    owner   TEXT          NOT NULL,
    balance NUMERIC(12,2) NOT NULL
);
