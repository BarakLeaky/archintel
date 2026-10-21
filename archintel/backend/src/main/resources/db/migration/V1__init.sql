CREATE TABLE users (
  id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL, timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE TABLE notification_preferences (
  id UUID PRIMARY KEY, user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  whatsapp_number VARCHAR(20), whatsapp_opt_in BOOLEAN NOT NULL DEFAULT false,
  email_enabled BOOLEAN NOT NULL DEFAULT true, whatsapp_enabled BOOLEAN NOT NULL DEFAULT false,
  in_app_enabled BOOLEAN NOT NULL DEFAULT true, timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE TABLE sources (
  id UUID PRIMARY KEY, source_type VARCHAR(20) NOT NULL, title TEXT, url TEXT NOT NULL,
  canonical_url TEXT NOT NULL, domain VARCHAR(255), external_id VARCHAR(255),
  author VARCHAR(255), publisher VARCHAR(255), published_at TIMESTAMPTZ, updated_at TIMESTAMPTZ,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  content_hash CHAR(64), excerpt TEXT, metadata JSONB);
CREATE UNIQUE INDEX ux_sources_canonical ON sources(canonical_url);
CREATE INDEX ix_sources_hash ON sources(content_hash);
CREATE INDEX ix_sources_published ON sources(published_at);

CREATE TABLE research_projects (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL, description TEXT NOT NULL, target_users TEXT, requirements TEXT,
  visibility VARCHAR(15) NOT NULL DEFAULT 'PRIVATE', share_token VARCHAR(64) UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE TABLE subscriptions (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(160) NOT NULL, query TEXT, keywords TEXT[] NOT NULL DEFAULT '{}',
  categories TEXT[] NOT NULL DEFAULT '{}', sources TEXT[] NOT NULL DEFAULT '{}',
  frequency VARCHAR(15) NOT NULL, delivery_time TIME, timezone VARCHAR(64) NOT NULL,
  importance_threshold VARCHAR(10) NOT NULL DEFAULT 'HIGH', enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX ix_subs_enabled ON subscriptions(enabled);

CREATE TABLE research_runs (
  id UUID PRIMARY KEY, project_id UUID REFERENCES research_projects(id) ON DELETE CASCADE,
  subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
  status VARCHAR(12) NOT NULL, started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
  sources_found INT NOT NULL DEFAULT 0, new_items INT NOT NULL DEFAULT 0,
  important_items INT NOT NULL DEFAULT 0, error_message TEXT);
CREATE INDEX ix_runs_status ON research_runs(status);

CREATE TABLE discoveries (
  id UUID PRIMARY KEY, source_id UUID NOT NULL REFERENCES sources(id),
  category VARCHAR(30) NOT NULL, title TEXT NOT NULL, why_it_matters TEXT,
  repository_created_at TIMESTAMPTZ, published_at TIMESTAMPTZ, repository_updated_at TIMESTAMPTZ,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  newness TEXT[] NOT NULL DEFAULT '{}', relevance_score REAL, freshness_score REAL,
  source_quality_score REAL, novelty_score REAL);
CREATE UNIQUE INDEX ux_discoveries_source ON discoveries(source_id);

CREATE TABLE opportunities (
  id UUID PRIMARY KEY, title TEXT NOT NULL, organization TEXT, description TEXT,
  source_url TEXT NOT NULL, source_name VARCHAR(120), location TEXT, remote BOOLEAN,
  compensation TEXT, published_at TIMESTAMPTZ, deadline TIMESTAMPTZ,
  detected_at TIMESTAMPTZ NOT NULL DEFAULT now(), language VARCHAR(40), category VARCHAR(60),
  relevance_score REAL, content_hash CHAR(64) NOT NULL);
CREATE UNIQUE INDEX ux_opp_hash ON opportunities(content_hash);

CREATE TABLE notifications (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
  discovery_id UUID REFERENCES discoveries(id), title TEXT NOT NULL, summary TEXT,
  url TEXT NOT NULL, importance VARCHAR(10) NOT NULL, read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now());
-- Dedup: a user is never notified twice about the same discovery.
CREATE UNIQUE INDEX ux_notif_user_discovery ON notifications(user_id, discovery_id);

CREATE TABLE notification_deliveries (
  id UUID PRIMARY KEY, notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  channel VARCHAR(12) NOT NULL, status VARCHAR(8) NOT NULL,
  provider_message_id VARCHAR(255), sent_at TIMESTAMPTZ, failure_reason TEXT);
CREATE INDEX ix_deliv_status ON notification_deliveries(status);
