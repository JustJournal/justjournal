package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Drops user_pic.image. Avatars used to be stored in that column; they are in MinIO now, and the
 * row only records the file name and mime type.
 *
 * <p>An avatar that was never copied to MinIO has an image but no file name, and would be lost. If
 * there are any, nothing is changed and the migration fails, naming them.
 */
@Slf4j
public class V1_19__drop_avatar_blob extends BaseJavaMigration {

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(final Context context) throws Exception {
    final Connection connection = context.getConnection();
    if (!hasImageColumn(connection)) {
      log.info("user_pic.image is already gone");
      return;
    }

    final String notCopied = notCopiedToStorage(connection);
    if (!notCopied.isEmpty()) {
      throw new IllegalStateException(
          "Avatars not copied to MinIO yet, nothing was changed. user_pic ids: " + notCopied);
    }

    try (Statement statement = connection.createStatement()) {
      statement.execute("ALTER TABLE user_pic DROP COLUMN image");
    }
    log.info("Dropped user_pic.image");
  }

  static boolean hasImageColumn(final Connection connection) throws SQLException {
    try (ResultSet rs =
        connection
            .getMetaData()
            .getColumns(connection.getCatalog(), connection.getSchema(), "user_pic", "image")) {
      return rs.next();
    }
  }

  /** Ids of avatars with an image in the database and no file in storage, comma separated. */
  static String notCopiedToStorage(final Connection connection) throws SQLException {
    final StringBuilder ids = new StringBuilder();
    try (Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT id FROM user_pic WHERE filename IS NULL AND image IS NOT NULL ORDER BY id")) {
      while (rs.next()) {
        if (!ids.isEmpty()) ids.append(", ");
        ids.append(rs.getInt(1));
      }
    }
    return ids.toString();
  }
}
