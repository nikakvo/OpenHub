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
        JsonObject database = loadSchema(DbSchema.VERSION);
        assertEquals(DbSchema.VERSION, database.get("version").getAsInt());

        Map<String, JsonObject> entities = new HashMap<>();
        for (JsonElement e : database.getAsJsonArray("entities")) {
            JsonObject entity = e.getAsJsonObject();
            entities.put(entity.get("tableName").getAsString(), entity);
        }
        assertEquals("every Room table is in DbSchema.TABLES",
                entities.size(), DbSchema.TABLES.length);

        for (DbSchema.Table table : DbSchema.TABLES) {
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
    }
}
