package io.eventdriven.buildyourowneventstore.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SqlInvoker {
    public static void exectuteSql(Connection dbConnection, String sql) {
        try (var statement = dbConnection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static <Result> ArrayList<Result> querySql(
        Connection dbConnection,
        String sql,
        Function<ResultSet, Result> map
    ) {
        return querySql(dbConnection, sql, ps -> {}, map);
    }

    public static <Result> ArrayList<Result> querySql(
        Connection dbConnection,
        String sql,
        Consumer<PreparedStatement> setParams,
        Function<ResultSet, Result> map
    ) {
        try (var st = dbConnection.prepareStatement(sql)) {
            setParams.accept(st);
            var results = new ArrayList<Result>();
            try (var rs = st.executeQuery()) {
                while (rs.next()) {
                    results.add(map.apply(rs));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setStringParam(PreparedStatement ps, int index, String value) {
        try {
            ps.setString(index, value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getString(ResultSet resultSet, String columnName) {
        try {
            return resultSet.getString(columnName);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
