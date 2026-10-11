-- V21__project_health_and_profitability.sql
-- Adds detailed cost breakdown to projects for Profitability & Health Intelligence

ALTER TABLE projects
    ADD COLUMN material_cost numeric(14,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN labour_cost numeric(14,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN vendor_cost numeric(14,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN travel_cost numeric(14,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN other_expenses numeric(14,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN expected_cost numeric(14,2) NOT NULL DEFAULT 0.00;
