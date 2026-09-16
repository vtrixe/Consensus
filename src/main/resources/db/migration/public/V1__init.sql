CREATE TABLE IF NOT EXISTS public.tenants (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) UNIQUE  NOT NULL,
    schema_name VARCHAR(255) UNIQUE  NOT NULL,
    api_key     VARCHAR(255) UNIQUE  NOT NULL,
    created_at  TIMESTAMP            NOT NULL DEFAULT now()
);
