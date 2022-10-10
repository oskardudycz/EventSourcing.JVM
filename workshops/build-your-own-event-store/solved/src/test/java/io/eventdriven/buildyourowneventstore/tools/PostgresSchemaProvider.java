package io.eventdriven.buildyourowneventstore.tools;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostgresSchemaProvider {
    private final Connection dbConnection;

    private final String getTableColumnsSql =
        """
            SELECT column_name AS name, data_type AS type
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE
                table_name = ?
                -- get only tables from current schema named as current test class
                AND table_schema in (select schemas[1] from (select current_schemas(false) as schemas) as currentschema)
            """;

    private final String functionExistsSql =
        "select exists(select * from pg_proc where proname = ?);";

    public PostgresSchemaProvider(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Returns schema information about specific table
     *
     * @param tableName table name
     * @return
     */
    public Optional<Table> getTable(String tableName) {
        try (var st = dbConnection.prepareStatement(getTableColumnsSql)) {
            st.setString(1, tableName);
            var columns = new ArrayList<Column>();
            try (var rs = st.executeQuery()) {
                while (rs.next()) {
                    columns.add(
                        new Column(
                            rs.getString("name"),
                            rs.getString("type")
                        )
                    );
                }
            }
            return !columns.isEmpty() ? Optional.of(new Table(tableName, columns)) : Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Describes basic information about database table
     */
    public class Table {
        /**
         * Table Name
         */
        private final String name;
        /**
         * Table Columns
         */
        private final List<Column> columns;

        public Table(String name, List<Column> columns) {
            this.name = name;
            this.columns = columns;
        }

        public Optional<Column> getColumn(String columnName) {
            return columns.stream()
                .filter(column -> column.getName().equals(columnName))
                .findAny();
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Describes basic information about database column
     */
    public class Column {
        public static final String guidType = "uuid";
        public static final String longType = "bigint";
        public static final String stringType = "text";
        public static final String jsonType = "jsonb";
        public static final String dateTimeType = "timestamp with time zone";

        /**
         * Column Name
         */
        private String name;
        /**
         * Column Type
         */
        private String type;

        public Column(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

}
