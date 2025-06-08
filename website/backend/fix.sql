-- Fix price_predictions column types and constraints

-- Change columns to DOUBLE PRECISION
ALTER TABLE price_predictions
ALTER COLUMN predicted_high_price TYPE DOUBLE PRECISION USING predicted_high_price::DOUBLE PRECISION,
ALTER COLUMN predicted_low_price TYPE DOUBLE PRECISION USING predicted_low_price::DOUBLE PRECISION,
ALTER COLUMN actual_high_price TYPE DOUBLE PRECISION USING actual_high_price::DOUBLE PRECISION,
 ALTER COLUMN actual_low_price TYPE DOUBLE PRECISION USING actual_low_price::DOUBLE PRECISION;

-- Add UNIQUE constraint (if not present)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE tablename = 'price_predictions'
        AND indexname = 'price_predictions_item_id_prediction_type_key'
    ) THEN
        ALTER TABLE price_predictions
        ADD CONSTRAINT price_predictions_item_id_prediction_type_key UNIQUE (item_id, prediction_type);
    END IF;
END$$;
