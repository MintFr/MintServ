package fr.nantral.mint;

import java.io.*;
import java.nio.file.*;
import java.sql.*;

public class DatabaseController {
    // Based on <https://robertwb.wordpress.com/2016/02/20/netcdf-data-and-postgis/>
    /** Import a netcdf file into the database
     *
     * @param dbConnection The database connection
     * @param filepath The raster file's name
     * @param tableName The table in which we insert the raster
     *
     * @throws SQLException is the SQL statement fails
     * @throws IOException if launching the subprocess fails
     * @throws ProcessExitException if the process doesn't exit successfully
     * @throws InterruptedException if another thread interrupts while waiting for the subprocess to finish
     * */
    public static void importRaster(Connection dbConnection, String tableName, Path filepath)
            throws SQLException, IOException, ProcessExitException, InterruptedException
    {
        // === Call raster2pgsql and write the output to a file ===

        // cf. <https://docs.boundlessgeo.com/suite/1.1.1/dataadmin/pgGettingStarted/raster2pgsql.html>
        //     <https://gis.stackexchange.com/questions/18254/loading-a-raster-into-a-postgis-2-0-database-on-windows>
        //     <https://postgis.net/docs/using_raster_dataman.html>
        // -s 2154 -> SRID:2154 which is Lambert-93 (hardcoded)
        // -C -> Apply raster constraints. This is to register the raster properly (apparently ?)
        // -I -> Create GiST index. (Recommended)
        // -F -> Add a column with the filename (named "filename")
        var cmd = new String[] {"raster2pgsql", "-I", "-C", "-F", "-s", "2154", "NETCDF:" + filepath, tableName};

        // NB We write the output to a file instead of keeping it in memory because it's easier to debug
        var filename = filepath.getFileName();
        var outputFile = new File(filename
                .toString().toLowerCase()
                .replaceAll("[^a-z0-9_]", "_") // Keep lowercase letters, numbers and underscore
                .replaceAll("_+", "_") // Collapse consecutive underscores
                .concat(".psql") // Add file extension
        );

        var process = new ProcessBuilder(cmd)
                .redirectOutput(outputFile)
                .redirectError(ProcessBuilder.Redirect.INHERIT) // Print error messages to stderr
                .start();

        System.out.println("$ " + String.join(" ", cmd));

        var exitStatus = process.waitFor();
        if (exitStatus != 0) {
            // Output file is created even if nothing is written to it
            if (!outputFile.delete()) {
                System.err.println("Failed to delete " + outputFile);
            }
            throw new ProcessExitException(cmd, exitStatus);
        }

        // === Read the sql file and execute it ===
        try {
            var importRasterSql = Files.readString(outputFile.toPath());
            // Edit the SQL to return an rid. This is hackish, but should work properly
            // TODO remove because it doesn't work if we split the file
            importRasterSql = importRasterSql.replace(filename + "');", filename + "') RETURNING rid;");

            System.out.println("Importing " + filename + " using " + outputFile);

            var importStatement = dbConnection.createStatement();
            var importResult = importStatement.executeQuery(importRasterSql);

        } finally {
            // Delete SQL command file
            if (!outputFile.delete()) {
                System.err.println("Failed to delete " + outputFile);
            }
        }
    }

    /** Compute the pollution indices from the rasters in the database.
     *
     * This function updates the values of the osm_ways.conc_* columns based on conc_raster.*
     * and using a temporary table indice.
     *
     * See also resources/fr.nantral.mint/compute_pollution.sql
     *
     * @param dbConnection The database connection
     * @param tableName The name of the raster table (must be "conc_raster")
     * @throws SQLException if the SQL command fails
     * @throws IOException if reading the resource file containing the SQL code fails
     * @throws MintException if tableName is not "conc_raster"
     */
    public static void computePollution(
            Connection dbConnection, String tableName,
            String no2_filename, String o3_filename, String pm10_filename, String pm2p5_filename
    ) throws SQLException, IOException, MintException {
        if (!(tableName.equals("conc_raster"))) {
            throw new MintException("The raster table name is actually hardcoded to be conc_raster");
        }

        // Slurp `compute_pollution.sql` into `sql`
        String sql;
        try (var inputStream = DatabaseController.class.getResourceAsStream("compute_pollution.sql")) {
            sql = new String(inputStream.readAllBytes());
        }

        var importStatement = dbConnection.prepareStatement(sql);
        importStatement.setString(1, no2_filename);
        importStatement.setString(2, o3_filename);
        importStatement.setString(3, pm10_filename);
        importStatement.setString(4, pm2p5_filename);

        importStatement.execute();
    }
}
