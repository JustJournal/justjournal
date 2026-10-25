package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Converts every table to utf8mb4, so text outside latin1 (and, for the old utf8 tables, emoji)
 * can be stored. Saving such text into the latin1 tables failed with "Incorrect string value".
 *
 * <p>The production schema predates Flyway (it was baselined), so the tables to convert are found
 * from information_schema rather than listed here. Converting keeps every character as it reads
 * today. Before anything is changed, each unique key on text is checked for values that are
 * distinct now but would be equal under the new collation, which would make the conversion fail
 * halfway.
 *
 * <p>MySQL can't roll back DDL, so a table converted before a failure stays converted; after
 * {@code flyway repair} the migration picks up the remaining tables.
 */
@Slf4j
public class V1_18__unicode_tables extends BaseJavaMigration {

  static final String CHARSET = "utf8mb4";
  static final String COLLATION = "utf8mb4_unicode_ci";
  static final String BINARY_COLLATION = "utf8mb4_bin";

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(final Context context) throws Exception {
    final Connection connection = context.getConnection();
    if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
      log.info("Not MySQL, nothing to convert");
      return;
    }

    final Map<String, String> tables =
        tablesToConvert(connection, context.getConfiguration().getTable());
    log.info("Converting {} tables to {}: {}", tables.size(), CHARSET, tables.keySet());

    final List<String> clashes = new ArrayList<>();
    for (final Map.Entry<String, String> table : tables.entrySet()) {
      clashes.addAll(uniqueKeyClashes(connection, table.getKey(), table.getValue()));
    }
    if (!clashes.isEmpty()) {
      throw new IllegalStateException(
          "Unique keys would have duplicate values after conversion, nothing was changed: "
              + clashes);
    }

    try (Statement statement = connection.createStatement()) {
      // the old tables have zero dates ('0000-00-00') as defaults and values, which the default
      // strict sql_mode refuses when a table is rebuilt; keep them as they are
      final String sqlMode;
      try (ResultSet rs = statement.executeQuery("SELECT @@SESSION.sql_mode")) {
        rs.next();
        sqlMode = rs.getString(1);
      }
      statement.execute("SET SESSION sql_mode = ''");
      try {
        convert(statement, tables);
      } finally {
        statement.execute("SET SESSION sql_mode = '" + sqlMode.replace("'", "''") + "'");
      }
    }
  }

  private static void convert(final Statement statement, final Map<String, String> tables)
      throws SQLException {
    statement.execute("ALTER DATABASE CHARACTER SET " + CHARSET + " COLLATE " + COLLATION);

    for (final Map.Entry<String, String> table : tables.entrySet()) {
      final long start = System.currentTimeMillis();
      statement.execute(
          "ALTER TABLE `"
              + table.getKey()
              + "` CONVERT TO CHARACTER SET "
              + CHARSET
              + " COLLATE "
              + table.getValue());
      log.info(
          "Converted {} to {} in {} ms",
          table.getKey(),
          table.getValue(),
          System.currentTimeMillis() - start);
    }
  }

  /** Tables with any table or column character set other than the target, and their collation. */
  static Map<String, String> tablesToConvert(final Connection connection, final String flywayTable)
      throws SQLException {
    final Map<String, String> tables = new LinkedHashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT t.TABLE_NAME, t.TABLE_COLLATION,"
                    + " (SELECT COUNT(*) FROM information_schema.COLUMNS c"
                    + "   WHERE c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME"
                    + "   AND c.CHARACTER_SET_NAME IS NOT NULL"
                    + "   AND c.CHARACTER_SET_NAME <> '"
                    + CHARSET
                    + "') AS other_columns"
                    + " FROM information_schema.TABLES t"
                    + " WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_TYPE = 'BASE TABLE'"
                    + " ORDER BY t.TABLE_NAME")) {
      while (rs.next()) {
        final String name = rs.getString(1);
        final String collation = rs.getString(2);
        if (name.equalsIgnoreCase(flywayTable)) continue;
        if (collation != null && collation.startsWith(CHARSET + "_") && rs.getInt(3) == 0)
          continue;
        // keep binary (case sensitive) tables binary
        tables.put(
            name, collation != null && collation.endsWith("_bin") ? BINARY_COLLATION : COLLATION);
      }
    }
    return tables;
  }

  /** A column of a unique key, and what to group by to compare it under the new collation. */
  record KeyPart(String column, String expression) {}

  /**
   * Unique keys on text in {@code table} with values that would compare equal under {@code
   * collation}, described for the error message.
   */
  static List<String> uniqueKeyClashes(
      final Connection connection, final String table, final String collation)
      throws SQLException {
    final Map<String, List<KeyPart>> keys = new LinkedHashMap<>();
    final Map<String, Boolean> hasText = new LinkedHashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT s.INDEX_NAME, s.COLUMN_NAME, s.SUB_PART, c.CHARACTER_SET_NAME"
                    + " FROM information_schema.STATISTICS s"
                    + " JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA = s.TABLE_SCHEMA"
                    + "  AND c.TABLE_NAME = s.TABLE_NAME AND c.COLUMN_NAME = s.COLUMN_NAME"
                    + " WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = '"
                    + table.replace("'", "''")
                    + "' AND s.NON_UNIQUE = 0"
                    + " ORDER BY s.INDEX_NAME, s.SEQ_IN_INDEX")) {
      while (rs.next()) {
        final String index = rs.getString(1);
        final String column = "`" + rs.getString(2) + "`";
        final int prefix = rs.getInt(3);
        final boolean text = rs.getString(4) != null;
        String expression = column;
        if (text) {
          expression =
              "CONVERT(" + column + " USING " + CHARSET + ") COLLATE " + collation;
          if (prefix > 0) expression = "LEFT(" + expression + ", " + prefix + ")";
        }
        keys.computeIfAbsent(index, k -> new ArrayList<>()).add(new KeyPart(column, expression));
        hasText.merge(index, text, Boolean::logicalOr);
      }
    }

    final List<String> clashes = new ArrayList<>();
    for (final Map.Entry<String, List<KeyPart>> key : keys.entrySet()) {
      if (!hasText.get(key.getKey())) continue;
      final List<String> group = new ArrayList<>();
      final List<String> notNull = new ArrayList<>();
      for (final KeyPart part : key.getValue()) {
        group.add(part.expression());
        notNull.add(part.column() + " IS NOT NULL");
      }
      final String sql =
          "SELECT COUNT(*) FROM (SELECT 1 FROM `"
              + table
              + "` WHERE "
              + String.join(" AND ", notNull)
              + " GROUP BY "
              + String.join(", ", group)
              + " HAVING COUNT(*) > 1) d";
      try (Statement statement = connection.createStatement();
          ResultSet rs = statement.executeQuery(sql)) {
        rs.next();
        final int duplicates = rs.getInt(1);
        if (duplicates > 0) {
          clashes.add(table + "." + key.getKey() + " (" + duplicates + " values)");
        }
      }
    }
    return clashes;
  }
}
