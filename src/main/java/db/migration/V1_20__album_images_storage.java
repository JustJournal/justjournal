package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Prepares user_images for pictures stored in MinIO:
 *
 * <ul>
 *   <li>filename, thumbnail: the image and its thumbnail in the image bucket
 *   <li>size: the image size in bytes
 *   <li>migrated: when an image that was stored in the database was copied to MinIO and checked.
 *       The image column is kept for now; rows with this set can have it cleared later.
 * </ul>
 *
 * The image column becomes nullable, as new pictures are only stored in MinIO.
 */
@Slf4j
public class V1_20__album_images_storage extends BaseJavaMigration {

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(final Context context) throws Exception {
    final Connection connection = context.getConnection();
    if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
      log.info("Not MySQL, nothing to change");
      return;
    }

    final List<String> changes = new ArrayList<>();
    // keep the column's type (it predates Flyway on the production server), only allow null
    final String imageType = columnType(connection, "image");
    if (imageType != null) changes.add("MODIFY COLUMN `image` " + imageType + " NULL");
    if (columnType(connection, "filename") == null)
      changes.add("ADD COLUMN `filename` VARCHAR(150) NULL");
    if (columnType(connection, "thumbnail") == null)
      changes.add("ADD COLUMN `thumbnail` VARCHAR(150) NULL");
    if (columnType(connection, "size") == null)
      changes.add("ADD COLUMN `size` BIGINT UNSIGNED NULL");
    if (columnType(connection, "migrated") == null)
      changes.add(
          "ADD COLUMN `migrated` DATETIME NULL COMMENT 'image copied to MinIO and checked; the"
              + " image column can be cleared'");

    try (Statement statement = connection.createStatement()) {
      // old rows can have zero dates, which the default strict sql_mode refuses on a rebuild
      final String sqlMode;
      try (ResultSet rs = statement.executeQuery("SELECT @@SESSION.sql_mode")) {
        rs.next();
        sqlMode = rs.getString(1);
      }
      statement.execute("SET SESSION sql_mode = ''");
      try {
        statement.execute("ALTER TABLE user_images " + String.join(", ", changes));
      } finally {
        statement.execute("SET SESSION sql_mode = '" + sqlMode.replace("'", "''") + "'");
      }
    }
    log.info("user_images: {}", changes);
  }

  /** The column's full type, e.g. mediumblob, or null if there is no such column. */
  static String columnType(final Connection connection, final String column) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA ="
                    + " DATABASE() AND TABLE_NAME = 'user_images' AND COLUMN_NAME = '"
                    + column
                    + "'")) {
      return rs.next() ? rs.getString(1) : null;
    }
  }
}
