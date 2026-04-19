-- DMARC reporting schema
CREATE TABLE IF NOT EXISTS dmarc_reports (
    id SERIAL PRIMARY KEY,
    domain VARCHAR(255) NOT NULL,
    report_type VARCHAR(32) NOT NULL,
    xml_content TEXT,
    parsed_summary JSONB,
    received_at TIMESTAMPTZ
);
