-- Admin configs, branding, and integrations schema
CREATE TABLE IF NOT EXISTS admin_configs (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(255) NOT NULL UNIQUE,
    value TEXT
);

CREATE TABLE IF NOT EXISTS branding (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    logo_url TEXT NOT NULL,
    primary_color VARCHAR(32) NOT NULL,
    secondary_color VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS webhook_integrations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    event_type VARCHAR(64) NOT NULL
);
