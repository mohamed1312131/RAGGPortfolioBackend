-- Quick Setup Script for Portfolio Analytics Database
-- Run this script to set up everything at once

-- Step 1: Create the database (uncomment if needed)
-- CREATE DATABASE portfolio_analytics;

-- Step 2: Connect to the database
-- \c portfolio_analytics

-- Step 3: Create tables (Hibernate will do this automatically, but you can run manually if needed)

-- Users/Sessions table
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255) UNIQUE NOT NULL,
    first_visit TIMESTAMP NOT NULL DEFAULT NOW(),
    last_visit TIMESTAMP NOT NULL DEFAULT NOW(),
    location VARCHAR(100),
    total_page_views INTEGER DEFAULT 0,
    total_questions INTEGER DEFAULT 0,
    total_link_clicks INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Events table
CREATE TABLE IF NOT EXISTS tracking_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    location VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Questions table
CREATE TABLE IF NOT EXISTS questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255) NOT NULL,
    question TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    location VARCHAR(100),
    tokens_used INTEGER DEFAULT 0,
    session_duration INTEGER,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Daily stats table
CREATE TABLE IF NOT EXISTS daily_stats (
    date DATE PRIMARY KEY,
    user_count INTEGER DEFAULT 0,
    question_count INTEGER DEFAULT 0,
    page_view_count INTEGER DEFAULT 0,
    link_click_count INTEGER DEFAULT 0,
    avg_session_duration INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_tracking_events_session ON tracking_events(session_id);
CREATE INDEX IF NOT EXISTS idx_tracking_events_type ON tracking_events(event_type);
CREATE INDEX IF NOT EXISTS idx_tracking_events_timestamp ON tracking_events(timestamp);
CREATE INDEX IF NOT EXISTS idx_questions_session ON questions(session_id);
CREATE INDEX IF NOT EXISTS idx_questions_timestamp ON questions(timestamp);
CREATE INDEX IF NOT EXISTS idx_user_sessions_session_id ON user_sessions(session_id);

-- Verify tables were created
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- Done!
SELECT 'Database setup complete!' as status;
