package com.example.eventlog.sqlite;

/**
 * Wrapper around Hibernate's community-provided SQLite dialect so it lives under our own package
 * and can be referenced via spring.jpa.hibernate.properties.hibernate.dialect.
 */
public class SQLiteDialect extends org.hibernate.community.dialect.SQLiteDialect {
    public SQLiteDialect() {
        super();
    }
}
