-- Evidence, research results, notifications, delivery log, GitHub repo analysis, duplicate provenance.

ALTER TABLE notification_preferences ADD COLUMN preferred_language VARCHAR(10) NOT NULL DEFAULT 'en';

ALTER TABLE research_projects
  ADD CONSTRAINT ck_projects_visibility CHECK (visibility IN ('PRIVATE','PUBLIC','SHARED_LINK'));

-- The same item seen under another URL/site is recorded here, not as a second source.
CREATE TABLE source_aliases (
  id UUID PRIMARY KEY,
  source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
  url TEXT NOT NULL,
  canonical_url TEXT NOT NULL,
  source_name VARCHAR(120),
  seen_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE UNIQUE INDEX ux_source_aliases_canonical ON source_aliases(canonical_url);
CREATE INDEX ix_source_aliases_source ON source_aliases(source_id);

CREATE TABLE research_results (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  project_id UUID REFERENCES research_projects(id) ON DELETE CASCADE,
  run_id UUID REFERENCES research_runs(id) ON DELETE SET NULL,
  title VARCHAR(255) NOT NULL,
  structured JSONB NOT NULL,
  visibility VARCHAR(15) NOT NULL DEFAULT 'PRIVATE',
  share_token VARCHAR(64) UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_results_visibility CHECK (visibility IN ('PRIVATE','PUBLIC','SHARED_LINK')));
CREATE INDEX ix_results_user ON research_results(user_id, created_at DESC);
CREATE INDEX ix_results_project ON research_results(project_id);

-- Every important claim points at a stored source. ref_code is the "SRC-001" style label shown to users.
CREATE TABLE evidence (
  id UUID PRIMARY KEY,
  result_id UUID NOT NULL REFERENCES research_results(id) ON DELETE CASCADE,
  source_id UUID NOT NULL REFERENCES sources(id),
  ref_code VARCHAR(16) NOT NULL,
  kind VARCHAR(20) NOT NULL,
  claim TEXT NOT NULL,
  excerpt TEXT,
  confidence REAL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ux_evidence_ref UNIQUE (result_id, ref_code),
  CONSTRAINT ck_evidence_kind CHECK (kind IN ('FACT','SOURCE_CLAIM','INFERENCE','RECOMMENDATION','UNCERTAINTY')));
CREATE INDEX ix_evidence_source ON evidence(source_id);

CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
  discovery_id UUID REFERENCES discoveries(id) ON DELETE SET NULL,
  result_id UUID REFERENCES research_results(id) ON DELETE SET NULL,
  title VARCHAR(255) NOT NULL,
  summary TEXT NOT NULL,
  url TEXT NOT NULL,
  importance VARCHAR(10) NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_notifications_importance CHECK (importance IN ('LOW','MEDIUM','HIGH')));
-- A user is never notified twice about the same discovery for the same subscription.
CREATE UNIQUE INDEX ux_notifications_dedup ON notifications(user_id, subscription_id, discovery_id)
  WHERE subscription_id IS NOT NULL AND discovery_id IS NOT NULL;
CREATE INDEX ix_notifications_user ON notifications(user_id, created_at DESC);

CREATE TABLE notification_deliveries (
  id UUID PRIMARY KEY,
  notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  channel VARCHAR(12) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  provider_message_id VARCHAR(255),
  sent_at TIMESTAMPTZ,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ux_delivery_channel UNIQUE (notification_id, channel),
  CONSTRAINT ck_deliveries_channel CHECK (channel IN ('IN_APP','EMAIL','WHATSAPP')),
  CONSTRAINT ck_deliveries_status CHECK (status IN ('PENDING','SENT','FAILED','SKIPPED')));
CREATE INDEX ix_deliveries_status ON notification_deliveries(status);

CREATE TABLE github_repositories (
  id UUID PRIMARY KEY,
  source_id UUID NOT NULL UNIQUE REFERENCES sources(id) ON DELETE CASCADE,
  github_id BIGINT NOT NULL UNIQUE,
  full_name VARCHAR(255) NOT NULL,
  description TEXT,
  primary_language VARCHAR(60),
  topics TEXT[] NOT NULL DEFAULT '{}',
  stars INT NOT NULL DEFAULT 0,
  forks INT NOT NULL DEFAULT 0,
  repo_created_at TIMESTAMPTZ,
  repo_pushed_at TIMESTAMPTZ,
  languages JSONB,
  manifests JSONB,
  readme_excerpt TEXT,
  analyzed_at TIMESTAMPTZ,
  last_checked_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX ix_github_repos_created ON github_repositories(repo_created_at);
