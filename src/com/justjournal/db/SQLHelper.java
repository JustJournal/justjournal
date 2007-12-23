/*
Copyright (c) 2003-2007, Lucas Holt
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

  Redistributions of source code must retain the above copyright notice, this list of
  conditions and the following disclaimer.

  Redistributions in binary form must reproduce the above copyright notice, this
  list of conditions and the following disclaimer in the documentation and/or other
  materials provided with the distribution.

  Neither the name of the Just Journal nor the names of its contributors
  may be used to endorse or promote products derived from this software without
  specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

//
//  SQLHelper.java
//
//
//  Created by Lucas Holt on Mon Feb 10 2003.
//

package com.justjournal.db;

import sun.jdbc.rowset.CachedRowSet;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


/**
 * A simple datatbase connectivity solution.  Depends on Sun's beta CachedRowSet.
 *
 * @author Lucas Holt
 * @version $Id: SQLHelper.java,v 1.4 2007/12/23 01:39:06 laffer1 Exp $
 * @since 1.0
 */
public final class SQLHelper {
    private static Context ctx = null;
    private static DataSource ds = null;
    private static final String DbEnv = "java:comp/env/jdbc/jjDB";

    SQLHelper() {
        try {
            ctx = new InitialContext();
            ds = (DataSource) ctx.lookup(DbEnv);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Connect to the Database Server.
     * <p/>
     * Caller must call close() on the connection when they are done.
     *
     * @return Active database connection
     * @throws java.sql.SQLException The database connection failed
     */
    public static Connection getConn()
            throws java.sql.SQLException {
        if (ctx == null || ds == null) {
            try {
                ctx = new InitialContext();
                ds = (DataSource) ctx.lookup(DbEnv);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return ds.getConnection();
    }

    public static int executeNonQuery(final String commandText)
            throws Exception {
        int RowsAffected = 0;
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = getConn();

            /*
             * Now, use normal JDBC programming to work with
             * MySQL, making sure to close each resource when you're
             * finished with it, which allows the connection pool
             * resources to be recovered as quickly as possible
             */

            stmt = conn.createStatement();
            stmt.execute(commandText);
            RowsAffected = stmt.getUpdateCount();
            stmt.close();

            conn.close();
        } catch (Exception e) {
            throw new Exception("Error getting connect or executing it", e);
        } finally {
            /*
             * Close any JDBC instances here that weren't
             * explicitly closed during normal code path, so
             * that we don't 'leak' resources...
             */

            try {
                stmt.close();
            } catch (SQLException sqlEx) {
                // ignore -- as we can't do anything about it here
            } catch (NullPointerException e) {

            }


            try {
                conn.close();
            } catch (SQLException sqlEx) {
                // ignore -- as we can't do anything about it here
            } catch (NullPointerException e) {

            }
        }

        return RowsAffected;
    }

    public static CachedRowSet executeResultSet(final String commandText)
            throws Exception {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs;
        CachedRowSet crs = null;

        try {
            conn = getConn();
            conn.setReadOnly(true);

            /*
             * Now, use normal JDBC programming to work with
             * MySQL, making sure to close each resource when you're
             * finished with it, which allows the connection pool
             * resources to be recovered as quickly as possible
             */

            stmt = conn.createStatement();
            rs = stmt.executeQuery(commandText);

            crs = new CachedRowSet();
            crs.populate(rs);

            rs.close();
            stmt.close();

            conn.setReadOnly(false);
            conn.close();
        } catch (Exception e) {
            throw new Exception("Error getting connect or executing it", e);
        } finally {
            /*
             * Close any JDBC instances here that weren't
             * explicitly closed during normal code path, so
             * that we don't 'leak' resources...
             */

            try {
                stmt.close();
            } catch (SQLException sqlEx) {
                // ignore -- as we can't do anything about it here
            } catch (NullPointerException e) {

            }

            try {
                conn.setReadOnly(false);
                conn.close();
            } catch (SQLException sqlEx) {
                // ignore -- as we can't do anything about it here
            } catch (NullPointerException e) {

            }

        }

        return crs;
    }

// --Commented out by Inspection START (3/24/06 11:08 PM):
//    public static String executeXMLResult(final String commandText)
//            throws SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException {
//        StringWriter sw = new StringWriter();
//
//        // DB Access
//        //  Driver DriverRecordset1 = (Driver) Class.forName( driverText ).newInstance();
//        /*Connection oConn = DriverManager.getConnection( connectURI, UserName, Password );
//        oConn.setReadOnly( true );
//        Statement stmt = oConn.createStatement();
//
//        ResultSet rs = stmt.executeQuery( commandText );
//        ResultSetMetaData rsmd = rs.getMetaData();
//
//        // Meta Data Properties
//        int numberOfColumns = rsmd.getColumnCount();
//        String[] namesOfColumns = new String[numberOfColumns];
//
//        // Create XML Document
//        sw.write( "<?xml version=\"1.0\" ?>\n\n" );
//        sw.write( "<records>\n" );
//
//        while ( rs.next() ) {
//            // sw.write( );
//        }
//
//        sw.write( "</records>" );
//        // End of XML Document Generation
//
//        //rsmd.close();
//        rs.close();  // close the disconnected recordset
//        stmt.close();
//        oConn.close();  */
//
//        return sw.toString();
//    }
// --Commented out by Inspection STOP (3/24/06 11:08 PM)
}
