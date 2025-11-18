-- Create Database Script
-- Run this in pgAdmin or psql to create the database

-- Create the database
CREATE DATABASE portfolio_analytics
    WITH 
    OWNER = admin
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Connect to the database
\c portfolio_analytics

-- Grant all privileges to admin user
GRANT ALL PRIVILEGES ON DATABASE portfolio_analytics TO admin;

-- Verify connection
SELECT 'Database created successfully!' as status;
