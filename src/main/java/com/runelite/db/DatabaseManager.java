package com.runelite.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the database connection and schema.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private static final String DB_NAME = "runescape_prices";
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "5432";
    private static final String POSTGRES_JDBC_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String H2_JDBC_URL = "jdbc:h2:./data/runescape_prices;MODE=PostgreSQL";
    private static final String JDBC_URL;
    private static final boolean USE_POSTGRES;

    // Static initializer to determine if PostgreSQL is available
    static {
        boolean postgresAvailable = isPostgresAvailable();
        USE_POSTGRES = postgresAvailable;
        if (postgresAvailable) {
            logger.info("Using PostgreSQL database");
            JDBC_URL = POSTGRES_JDBC_URL;
        } else {
            logger.info("PostgreSQL not available, falling back to H2 database");
            // Create the data directory for H2 if it doesn't exist
            File dataDir = new File("./data");
            if (!dataDir.exists()) {
                if (dataDir.mkdir()) {
                    logger.info("Created data directory for H2: {}", dataDir.getAbsolutePath());
                } else {
                    logger.error("Failed to create data directory for H2: {}", dataDir.getAbsolutePath());
                }
            }
            JDBC_URL = H2_JDBC_URL;
        }
    }

    /**
     * Checks if PostgreSQL is available by attempting to connect to the server.
     *
     * @return true if PostgreSQL is available, false otherwise
     */
    private static boolean isPostgresAvailable() {
        try (Socket socket = new Socket(DB_HOST, Integer.parseInt(DB_PORT))) {
            return true; // Connection successful
        } catch (Exception e) {
            logger.warn("Could not connect to PostgreSQL: {}", e.getMessage());
            return false; // Connection failed
        }
    }

    private static DatabaseManager instance;
    private final HikariDataSource dataSource;

    private DatabaseManager() {
        // Configure the connection pool
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);

        if (USE_POSTGRES) {
            // PostgreSQL configuration
            config.setUsername("admin");
            config.setPassword("elfe");

            // PostgreSQL specific properties
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("reWriteBatchedInserts", "true");
        } else {
            // H2 configuration
            config.setUsername("sa");
            config.setPassword("");
        }

        // Common connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(30000);

        // Add connection validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);

        // Add leak detection
        config.setLeakDetectionThreshold(60000); // 1 minute

        // Add automatic reconnect
        config.addDataSourceProperty("autoReconnect", "true");

        dataSource = new HikariDataSource(config);

        // Initialize the database schema
        try {
            initializeSchema();
        } catch (Exception e) {
            logger.error("Failed to initialize database schema: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Gets the singleton instance of the DatabaseManager.
     *
     * @return the singleton instance
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Gets a connection from the connection pool.
     *
     * @return a database connection
     * @throws SQLException if a database access error occurs
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    /**
     * Initializes the database schema.
     */
    private void initializeSchema() {
        logger.info("Initializing database schema");

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Create items table first, without any foreign key constraints
            stmt.execute("CREATE TABLE IF NOT EXISTS items (" +
                    "id INT PRIMARY KEY, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "description TEXT, " +
                    "icon_url VARCHAR(255), " +
                    "is_members_only BOOLEAN, " +
                    "low_alchemy_value INT, " +
                    "high_alchemy_value INT, " +
                    "shop_value INT, " +
                    "grand_exchange_limit INT, " +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS cleaned_timeseries (" +
                    "item_id INT, " +
                    "timestamp_seconds BIGINT, " +
                    "average_high_price DOUBLE PRECISION, " +
                    "average_low_price DOUBLE PRECISION, " +
                    "high_price_volume BIGINT, " +
                    "low_price_volume BIGINT, " +
                    "PRIMARY KEY (item_id, timestamp_seconds)" +
                    ")");
            logger.info("Cleaned_timeseries table created successfully");
            logger.info("Items table created successfully");
            stmt.execute("CREATE TABLE IF NOT EXISTS cleaned_timeseries (" +
                    "item_id INT, " +
                    "timestamp_seconds BIGINT, " +
                    "average_high_price DOUBLE PRECISION, " +
                    "average_low_price DOUBLE PRECISION, " +
                    "high_price_volume BIGINT, " +
                    "low_price_volume BIGINT, " +
                    "PRIMARY KEY (item_id, timestamp_seconds)" +
                    ")");
            logger.info("Cleaned_timeseries table created successfully");

            // Create latest_prices table
            stmt.execute("CREATE TABLE IF NOT EXISTS latest_prices (" +
                    "item_id INT, " +
                    "high_price BIGINT, " +
                    "high_price_time BIGINT, " +
                    "low_price BIGINT, " +
                    "low_price_time BIGINT, " +
                    "high_price_volume BIGINT, " +
                    "low_price_volume BIGINT, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (item_id, timestamp)" +
                    ")");

            logger.info("Latest_prices table created successfully");

            // Create timeseries table
            stmt.execute("CREATE TABLE IF NOT EXISTS timeseries (" +
                    "item_id INT, " +
                    "timestamp_seconds BIGINT, " +
                    "average_high_price BIGINT, " +
                    "average_low_price BIGINT, " +
                    "high_price_volume BIGINT, " +
                    "low_price_volume BIGINT, " +
                    "PRIMARY KEY (item_id, timestamp_seconds)" +
                    ")");

            logger.info("Timeseries table created successfully");
            stmt.execute("CREATE TABLE IF NOT EXISTS cleaned_timeseries (" +
                    "item_id INT, " +
                    "timestamp_seconds BIGINT, " +
                    "average_high_price DOUBLE PRECISION, " +
                    "average_low_price DOUBLE PRECISION, " +
                    "high_price_volume BIGINT, " +
                    "low_price_volume BIGINT, " +
                    "PRIMARY KEY (item_id, timestamp_seconds)" +
                    ")");
            logger.info("Cleaned_timeseries table created successfully");

            // Create price_predictions table for AI
            stmt.execute("CREATE TABLE IF NOT EXISTS price_predictions (" +
                    "id SERIAL PRIMARY KEY, " +
                    "item_id INT NOT NULL, " +
                    "prediction_type VARCHAR(10) NOT NULL, " +
                    "predicted_high_price DOUBLE PRECISION NOT NULL, " +
                    "predicted_low_price DOUBLE PRECISION NOT NULL, " +
                    "prediction_time TIMESTAMP NOT NULL, " +
                    "actual_high_price DOUBLE PRECISION, " +
                    "actual_low_price DOUBLE PRECISION, " +
                    "verification_time TIMESTAMP, " +
                    "accuracy_percent DOUBLE PRECISION, " +
                    "is_verified BOOLEAN DEFAULT FALSE" +
                    ")");
            logger.info("Price_predictions table created successfully");

            logger.info("Database schema initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize database schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    /**
     * Closes the connection pool.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
