-- ============================================
-- PostgreSQL Initialization Script
-- Creates separate databases for each service
-- ============================================

CREATE DATABASE legent_foundation;
CREATE DATABASE legent_audience;
CREATE DATABASE legent_content;
CREATE DATABASE legent_campaign;
CREATE DATABASE legent_delivery;
CREATE DATABASE legent_automation;
CREATE DATABASE legent_deliverability;
CREATE DATABASE legent_admin;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE legent_foundation TO legent;
GRANT ALL PRIVILEGES ON DATABASE legent_audience TO legent;
GRANT ALL PRIVILEGES ON DATABASE legent_content TO legent;
GRANT ALL PRIVILEGES ON DATABASE legent_campaign TO legent;
GRANT ALL PRIVILEGES ON DATABASE legent_delivery TO legent;
GRANT ALL PRIVILEGES ON DATABASE legent_automation TO legent;
GRANT ALL PRIVILEGES ON DATABASE legent_deliverability TO legent;
GRANT ALL PRIVILEGES ON DATABASE legent_admin TO legent;
