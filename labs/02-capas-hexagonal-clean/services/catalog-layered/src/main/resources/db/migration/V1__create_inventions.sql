CREATE TABLE inventions (
    id          UUID PRIMARY KEY,
    inventor_id VARCHAR(100)   NOT NULL,
    name        VARCHAR(200)   NOT NULL,
    price       NUMERIC(12, 2) NOT NULL,
    image_url   VARCHAR(500),
    status      VARCHAR(20)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX inventions_inventor_idx ON inventions (inventor_id);
