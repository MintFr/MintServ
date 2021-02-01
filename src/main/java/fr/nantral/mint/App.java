package fr.nantral.mint;

import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.*;
import java.util.Properties;
import java.sql.*;

//import org.geotools.coverage.io.netcdf.NetCDFFormat;
//import org.geotools.coverage.grid.GridCoverage2D;
//import org.geotools.util.Arguments;

public class App implements Runnable {

    @Option(names = "--config", description = "The configuration file")
    File configFile = new File("config.properties");

    @Option(names = "--file", required = true, description = "The netcdf file to import")
    File netcdfFile;

    public static void main(String... args) throws Exception {
        System.exit(new CommandLine(new App()).execute(args));
    }

    @Override
    public void run() {
        Properties config = null;
        try {
            config = readConfigurationFile();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Connection dbConnection = null;
        try {
            dbConnection = connectToDatabase(config);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return;
        }

        // DEBUG: Query some data and print it
        try {
            debugTestConnection(dbConnection);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return;
        }

        // FIXME hardcoded string
        try {
            importNetcdfIntoDatabase(dbConnection, netcdfFile.getPath());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

//        // GeoTools provides utility classes to parse command line arguments
//        var processedArgs = new Arguments(args);
//        var file = new File(processedArgs.getRequiredString("--file"));
//
//        var format = new NetCDFFormat();
//        var reader = format.getReader(file);
//        GridCoverage2D gridCoverage = reader.read(null);
    }

    // Sample code to test that the connection is working
    private static void debugTestConnection(Connection dbConnection) throws SQLException {
        var statement = dbConnection.createStatement();
        var dbResult = statement.executeQuery("SELECT * FROM pg_tables");
        var resultColumns = dbResult.getMetaData().getColumnCount();
        while (dbResult.next()) {
            String s = "";
            for (int i = 1; i <= resultColumns; i++) {
                s += dbResult.getString(i) + ",";
            }
            System.out.println(s);
        }
    }

    Properties readConfigurationFile() throws IOException {
        try (FileInputStream contents = new FileInputStream(configFile)) {
            var properties = new Properties();
            properties.load(contents);
            return properties;
        }
    }

    public static Connection connectToDatabase(Properties config) throws SQLException {
        // TODO make it less wonky if the fields don't exist
        var username = config.getProperty("username");
        var password = config.getProperty("password");
        var dbUrl = config.getProperty("database_url");

        try {
            Class.forName("org.postgresql.Driver");
            return DriverManager.getConnection(dbUrl, password, username);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    // Based on <https://robertwb.wordpress.com/2016/02/20/netcdf-data-and-postgis/>
    public static void importNetcdfIntoDatabase(Connection dbConnection, String filename) throws Exception, IOException, InterruptedException {
        // === Call raster2pgsql and write the output to a file ===

        var tableName = "conc_raster"; // WARN hardcoded tablename
        // cf. <https://docs.boundlessgeo.com/suite/1.1.1/dataadmin/pgGettingStarted/raster2pgsql.html>
        //     <https://gis.stackexchange.com/questions/18254/loading-a-raster-into-a-postgis-2-0-database-on-windows>
        //     <https://postgis.net/docs/using_raster_dataman.html>
        // -s 2154 -> SRID:2154 which is Lambert-93 (hardcoded)
        // -C -> Apply raster constraints. This is to register the raster properly (apparently ?)
        // -I -> Create GiST index. (Recommended)
        // -F -> Add a column with the filename
        var cmd = new String[] {"raster2pgsql", "-I", "-C", "-F", "-s", "2154", "NETCDF:" + filename, tableName};

        // NB We write the output to a file instead of keeping it in memory becayse it's easier to debug
        var outputFile = new File(filename
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "_") // Keep lowercase letters, numbers and underscore
                .replaceAll("_+", "_") // collapse consecutive underscores
                .concat(".psql") // Add file extension
        );

        var process = new ProcessBuilder(cmd)
                .redirectOutput(outputFile)
                .redirectError(ProcessBuilder.Redirect.INHERIT) // Print error messages to stderr
                .start();

        process.info().commandLine().ifPresent(v -> System.out.println("$ " + v));

        var exitStatus = process.waitFor();
        if (exitStatus != 0) {
            // Output file is created even if nothing is written to it
            outputFile.delete();
            throw new Exception("raster2pgsql failed"); // TODO make a better exception class
        }
        System.out.println("# raster2pgsql ran successfully");

        // === Read the sql file and execute it ===

        var importStatement = dbConnection.createStatement();
        var importRasterSql = new String(new FileInputStream(outputFile).readAllBytes());
        importStatement.execute(importRasterSql);
    }
}
