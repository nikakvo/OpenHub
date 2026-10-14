package com.thirtydegreesray.openhub.dao;

/**
 * The tables of the Room database (version {@link #VERSION}), written the way Room creates
 * them. The migration from the old greenDAO database (version 5) rebuilds every table with
 * exactly this SQL and copies the rows over, so Room's schema check always passes.
 *
 * Plain Java on purpose: the unit tests compare it with the schema Room exports at build
 * time (app/schemas), so a changed entity without a changed migration fails the tests.
 */
public final class DbSchema {

    private DbSchema() {
    }

    /** Room schema version. greenDAO used 1-5. */
    public static final int VERSION = 6;

    /** The last greenDAO schema version. */
    public static final int GREENDAO_VERSION = 5;

    /** Placeholder Room uses in the exported createSql. */
    public static final String TABLE_NAME = "${TABLE_NAME}";

    /** One table: its name, Room's CREATE statement (with {@link #TABLE_NAME}) and columns. */
    public static final class Table {
        public final String name;
        public final String createSql;
        public final String[] columns;

        Table(String name, String createSql, String... columns) {
            this.name = name;
            this.createSql = createSql;
            this.columns = columns;
        }

        /** The CREATE statement for a table with the given name. */
        public String createSql(String tableName) {
            return createSql.replace(TABLE_NAME, tableName);
        }

        /** The column list, quoted, for INSERT ... SELECT. */
        public String columnList() {
            StringBuilder sb = new StringBuilder();
            for (String column : columns) {
                if (sb.length() > 0) sb.append(", ");
                sb.append('`').append(column).append('`');
            }
            return sb.toString();
        }
    }

    public static final Table AUTH_USER = new Table("AUTH_USER",
            "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`ACCESS_TOKEN` TEXT NOT NULL, "
                    + "`AUTH_TIME` INTEGER NOT NULL, `EXPIRE_IN` INTEGER NOT NULL, "
                    + "`SCOPE` TEXT NOT NULL, `SELECTED` INTEGER NOT NULL, "
                    + "`LOGIN_ID` TEXT NOT NULL, `NAME` TEXT, `AVATAR` TEXT, "
                    + "PRIMARY KEY(`ACCESS_TOKEN`))",
            "ACCESS_TOKEN", "AUTH_TIME", "EXPIRE_IN", "SCOPE", "SELECTED", "LOGIN_ID",
            "NAME", "AVATAR");

    public static final Table LOCAL_USER = new Table("LOCAL_USER",
            "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`LOGIN` TEXT NOT NULL, "
                    + "`NAME` TEXT, `AVATAR_URL` TEXT, `FOLLOWERS` INTEGER, "
                    + "`FOLLOWING` INTEGER, PRIMARY KEY(`LOGIN`))",
            "LOGIN", "NAME", "AVATAR_URL", "FOLLOWERS", "FOLLOWING");

    public static final Table LOCAL_REPO = new Table("LOCAL_REPO",
            "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`ID` INTEGER NOT NULL, "
                    + "`NAME` TEXT NOT NULL, `DESCRIPTION` TEXT, `LANGUAGE` TEXT, "
                    + "`STARGAZERS_COUNT` INTEGER, `WATCHERS_COUNT` INTEGER, "
                    + "`FORKS_COUNT` INTEGER, `FORK` INTEGER, `OWNER_LOGIN` TEXT, "
                    + "`OWNER_AVATAR_URL` TEXT, PRIMARY KEY(`ID`))",
            "ID", "NAME", "DESCRIPTION", "LANGUAGE", "STARGAZERS_COUNT", "WATCHERS_COUNT",
            "FORKS_COUNT", "FORK", "OWNER_LOGIN", "OWNER_AVATAR_URL");

    public static final Table TRACE = new Table("TRACE",
            "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`ID` TEXT NOT NULL, `TYPE` TEXT, "
                    + "`USER_ID` TEXT, `REPO_ID` INTEGER, `START_TIME` INTEGER, "
                    + "`LATEST_TIME` INTEGER, `TRACE_NUM` INTEGER, PRIMARY KEY(`ID`))",
            "ID", "TYPE", "USER_ID", "REPO_ID", "START_TIME", "LATEST_TIME", "TRACE_NUM");

    public static final Table BOOKMARK = new Table("BOOKMARK",
            "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`ID` TEXT NOT NULL, "
                    + "`TYPE` TEXT NOT NULL, `USER_ID` TEXT, `REPO_ID` INTEGER, "
                    + "`MARK_TIME` INTEGER, PRIMARY KEY(`ID`))",
            "ID", "TYPE", "USER_ID", "REPO_ID", "MARK_TIME");

    public static final Table MY_TRENDING_LANGUAGE = new Table("MY_TRENDING_LANGUAGE",
            "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`SLUG` TEXT NOT NULL, "
                    + "`NAME` TEXT NOT NULL, `ORDER` INTEGER NOT NULL, PRIMARY KEY(`SLUG`))",
            "SLUG", "NAME", "ORDER");

    public static final Table[] TABLES = {
            AUTH_USER, LOCAL_USER, LOCAL_REPO, TRACE, BOOKMARK, MY_TRENDING_LANGUAGE
    };

    /** greenDAO tables of schema 2-3, emptied and dropped by its 3 -> 4 upgrade. */
    public static final String[] LEGACY_TABLES = {
            "TRACE_USER", "TRACE_REPO", "BOOK_MARK_USER", "BOOK_MARK_REPO"
    };
}
