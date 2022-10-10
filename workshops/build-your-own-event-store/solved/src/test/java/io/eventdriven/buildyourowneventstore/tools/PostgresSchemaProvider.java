package io.eventdriven.buildyourowneventstore.tools;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static io.eventdriven.buildyourowneventstore.tools.SqlInvoker.*;

public class PostgresSchemaProvider {

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
        var columns =
            querySql(
                dbConnection,
                getTableColumnsSql,
                st -> setStringParam(st, 1, tableName),
                rs -> new Column(
                    getString(rs, "name"),
                    getString(rs, "type")
                )
            );
        return !columns.isEmpty() ? Optional.of(new Table(tableName, columns)) : Optional.empty();
    }

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
        public static final String uuidType = "uuid";
        public static final String bigintType = "bigint";
        public static final String textType = "text";
        public static final String jsonbType = "jsonb";
        public static final String timestampWithTimeZone = "timestamp with time zone";

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
