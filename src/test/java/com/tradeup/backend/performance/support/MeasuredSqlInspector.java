package com.tradeup.backend.performance.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/** Captures only an explicitly bounded, synchronous request on the current thread. */
public class MeasuredSqlInspector implements StatementInspector {

    private static final ThreadLocal<List<String>> STATEMENTS = new ThreadLocal<>();

    public static void start() {
        if (STATEMENTS.get() != null) {
            throw new IllegalStateException("SQL measurement already active");
        }
        STATEMENTS.set(new ArrayList<>());
    }

    public static List<String> snapshot() {
        return List.copyOf(STATEMENTS.get());
    }

    public static void stop() {
        STATEMENTS.remove();
    }

    @Override
    public String inspect(String sql) {
        List<String> statements = STATEMENTS.get();
        if (statements != null) {
            statements.add(sql);
        }
        return sql;
    }
}
