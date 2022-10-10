package io.eventdriven.buildyourowneventstore.tools;

import java.sql.*;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
        return querySql(dbConnection, sql, ps -> {
        }, map);
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

    public static void runInTransaction(
        Connection dbConnection,
        Consumer<Connection> callback
    ) {
        try {
            dbConnection.setAutoCommit(false);
            callback.accept(dbConnection);
            dbConnection.commit();
        } catch (SQLException e) {
            try {
                dbConnection.rollback();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
    }

    public static <Result> Result querySingleSql(
        Connection dbConnection,
        String sql,
        Function<ResultSet, Result> map
    ) {
        return querySingleSql(dbConnection, sql, ps -> {
        }, map);
    }

    public static <Result> Result querySingleSql(
        Connection dbConnection,
        String sql,
        Consumer<PreparedStatement> setParams,
        Function<ResultSet, Result> map
    ) {
        try (var st = dbConnection.prepareStatement(sql)) {
            setParams.accept(st);
            try (var rs = st.executeQuery()) {
                rs.next();
                return map.apply(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Consumer<PreparedStatement> setStringParam(String value) {
        return ps -> setStringParam(ps, 1, value);
    }

    public static void setStringParam(PreparedStatement ps, int index, String value) {
        try {
            ps.setString(index, value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setLong(PreparedStatement ps, int index, Long value) {
        try {
            if (value == null) {
                ps.setNull(index, Types.BIGINT);
                return;
            }

            ps.setLong(index, value);
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

    public static boolean getBoolean(ResultSet resultSet, String columnName) {
        try {
            return resultSet.getBoolean(columnName);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
