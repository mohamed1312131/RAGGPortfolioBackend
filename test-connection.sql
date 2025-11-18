-- Test PostgreSQL Connection and Setup
-- Run this in pgAdmin Query Tool or psql

-- 1. Check if database exists
SELECT datname FROM pg_database WHERE datname = 'portfolio_analytics';

-- 2. Check current user
SELECT current_user;

-- 3. Check PostgreSQL version
SELECT version();

-- 4. List all tables (run after Spring Boot starts)
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- 5. Check if tables have correct structure
SELECT 
    table_name,
    column_name,
    data_type 
FROM information_schema.columns 
WHERE table_schema = 'public' 
ORDER BY table_name, ordinal_position;

-- 6. Test insert (optional - Spring Boot will do this automatically)
-- INSERT INTO user_sessions (session_id, first_visit, last_visit, location)
-- VALUES ('test-session', NOW(), NOW(), 'Test Location');

-- 7. Check if data exists
SELECT 
    (SELECT COUNT(*) FROM user_sessions) as sessions,
    (SELECT COUNT(*) FROM tracking_events) as events,
    (SELECT COUNT(*) FROM questions) as questions,
    (SELECT COUNT(*) FROM daily_stats) as daily_stats;

-- Success message
SELECT 'PostgreSQL connection test completed!' as status;
