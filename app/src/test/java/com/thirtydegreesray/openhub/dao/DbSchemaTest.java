package com.thirtydegreesray.openhub.dao;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * The migration (DbSchema) must create exactly the tables Room expects. Room exports its
 * schema at build time to app/schemas; this test compares the two, so an entity change
 * without a matching migration fails here instead of on a phone.
 */
public class DbSchemaTest {

    private static final String SCHEMA_DIR = "schemas/" + AppDatabase.class.getName();

    private static JsonObject loadSchema(int version) throws Exception {
        File file = new File(SCHEMA_DIR, version + ".json");
        if (!file.exists()) file = new File("app/" + SCHEMA_DIR, version + ".json");
        assertTrue("Room schema " + file + " not found: compile the app once, Room exports it",
                file.exists());
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("database");
        }
    }

    @Test
    public void migrationCreatesRoomsTables() throws Exception {
        assertSchema(DbSchema.VERSION, DbSchema.TABLES);
    }

    @Test
    public void greenDaoUpgradeCreatesVersion6() throws Exception {
        assertSchema(DbSchema.FIRST_ROOM_VERSION, DbSchema.TABLES_V6);
    }

    /** Version 6 plus the added columns (ALTER TABLE adds them at the end) is version 7. */
    @Test
    public void version7OnlyAddsColumns() {
        assertEquals(7, DbSchema.VERSION);
        DbSchema.Table v6 = DbSchema.AUTH_USER_V6;
        DbSchema.Table v7 = DbSchema.AUTH_USER;
        assertEquals(v6.name, v7.name);

        String[] expected = new String[v6.columns.length + DbSchema.AUTH_USER_ADDED_7.length];
        System.arraycopy(v6.columns, 0, expected, 0, v6.columns.length);
        StringBuilder added = new StringBuilder();
        for (int i = 0; i < DbSchema.AUTH_USER_ADDED_7.length; i++) {
            String[] column = DbSchema.AUTH_USER_ADDED_7[i];
            expected[v6.columns.length + i] = column[0];
            added.append(", `").append(column[0]).append("` ").append(column[1]);
            // nullable, no default: old rows read as "does not expire"
            assertFalse(column[1].contains("NOT NULL"));
            assertFalse(column[1].contains("DEFAULT"));
        }
        assertArrayEquals(expected, v7.columns);
        String pk = ", PRIMARY KEY(";
        String v6Sql = v6.createSql;
        assertEquals(v6Sql.substring(0, v6Sql.indexOf(pk)) + added + v6Sql.substring(v6Sql.indexOf(pk)),
                v7.createSql);
        assertEquals("ALTER TABLE `AUTH_USER` ADD COLUMN `REFRESH_TOKEN` TEXT",
                DbSchema.addColumnSql("AUTH_USER", DbSchema.AUTH_USER_ADDED_7[0]));
    }

    private static void assertSchema(int version, DbSchema.Table[] tables) throws Exception {
        JsonObject database = loadSchema(version);
        assertEquals(version, database.get("version").getAsInt());

        Map<String, JsonObject> entities = new HashMap<>();
        for (JsonElement e : database.getAsJsonArray("entities")) {
            JsonObject entity = e.getAsJsonObject();
            entities.put(entity.get("tableName").getAsString(), entity);
        }
        assertEquals("every Room table of version " + version + " is in DbSchema",
                entities.size(), tables.length);

        for (DbSchema.Table table : tables) {
            JsonObject entity = entities.get(table.name);
            assertNotNull("Room has no table " + table.name, entity);
            assertEquals("CREATE statement of " + table.name,
                    entity.get("createSql").getAsString(), table.createSql);

            JsonArray fields = entity.getAsJsonArray("fields");
            String[] columns = new String[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                columns[i] = fields.get(i).getAsJsonObject().get("columnName").getAsString();
            }
            assertArrayEquals("columns copied for " + table.name, columns, table.columns);
        }
    }

    @Test
    public void tableSqlHelpers() {
        assertEquals("CREATE TABLE IF NOT EXISTS `X` (`SLUG` TEXT NOT NULL, "
                        + "`NAME` TEXT NOT NULL, `ORDER` INTEGER NOT NULL, PRIMARY KEY(`SLUG`))",
                DbSchema.MY_TRENDING_LANGUAGE.createSql("X"));
        assertEquals("`SLUG`, `NAME`, `ORDER`", DbSchema.MY_TRENDING_LANGUAGE.columnList());
        for (DbSchema.Table table : DbSchema.TABLES) {
            assertFalse(table.createSql(table.name).contains(DbSchema.TABLE_NAME));
        }
        for (DbSchema.Table table : DbSchema.TABLES_V6) {
            assertFalse(table.createSql(table.name).contains(DbSchema.TABLE_NAME));
        }
    }
}
