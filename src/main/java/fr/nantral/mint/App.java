package fr.nantral.mint;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.Arrays;
import java.util.Properties;

/**
 *
 * ## Raster naming scheme
 *
 * The model outputs netcdf rasters in `$modelDir/RESULT/GRILLE/` with the following naming scheme:
 * ```raku
 * regex species { 'NO2' | 'O3' | 'PM10' | 'PM25' }
 * regex YYYYMMDDHH { <[0..9]> ** 10 } # Year, month, day, and hour
 * regex filename { 'Conc_' <species> '_' <YYYYMMDDHH> '.nc' }
 * ```
 * For example, 'Conc_NO2_20210216.nc' for the NO2 raster of 2021-10-02 at 16:00:00.
 */
public class App implements Runnable {

    // === picocli configuration ===

    @Option(names = "--config", description = "The configuration file")
    File configFile = new File("config.properties");

    @Option(names = "--skip-model", description = "Skip launching the model. No netcdf files will be created") boolean skipModel;
    @Option(names = "--skip-connection", description = "Skip connecting to the database. Crashes the process") boolean skipConnection;
    @Option(names = "--model-args", defaultValue = "") String modelArgs;

    public static void main(String... args) throws Exception {
        System.exit(new CommandLine(new App()).execute(args));
    }

    // ---

    @Override
    public void run() {
        try {
            fallibleRun();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /** The main business logic */
    private void fallibleRun() throws Exception {
        Properties config = readConfigurationFile(configFile);

        // TODO maybe check mandatory keys in configuration file ?

        // STEP 1: Run the model
        if (!skipModel) {
            var modelFilepath = config.getProperty("model_sh");
            runModel(modelFilepath, modelArgs);
        }

        // STEP 1.5: Connect to the database
        Connection dbConnection = null;
        if (!skipConnection) {
            var username = config.getProperty("username");
            var password = config.getProperty("password");
            var dbUrl = config.getProperty("database_url", "jdbc:postgresql://localhost");
            dbConnection = connectToDatabase(username, password, dbUrl);
        }
        var tableName = config.getProperty("raster_table", "conc_raster");

        // STEP 2: Import the model output rasters into the database

        // Find model raster output directory
        var modelWorkdir = config.getProperty("sirane_dir");
        File rasterDir = Path.of(modelWorkdir).resolve("RESULT/GRILLE").toFile();
        if (!rasterDir.exists()) {
            throw new FileNotFoundException("Model raster directory doesn't exist: " + rasterDir);
        }

        // Choose the raster data time by looking at the files of one species (eg. NO2)
        var rasterPaths = rasterDir.listFiles(x -> {
            var name = x.getName();
            return x.isFile() && name.startsWith("Conc_NO2") && name.endsWith(".nc");
        });
        // We take the 1st calculated step (ie. 2nd file) of the simulation
        Arrays.sort(rasterPaths);
        String raster_time = rasterPaths[1].getName()
                // Remove prefix and suffix
                .replace("Conc_NO2_", "")
                .replace(".nc", "");

        // The 4 raster filepaths (see § Raster naming scheme)
        Path no2_raster = rasterDir.toPath().resolve("Conc_NO2_" + raster_time + ".nc");
        Path o3_raster = rasterDir.toPath().resolve("Conc_O3_" + raster_time + ".nc");
        Path pm10_raster = rasterDir.toPath().resolve("Conc_PM10_" + raster_time + ".nc");
        Path pm2p5_raster = rasterDir.toPath().resolve("Conc_PM25_" + raster_time + ".nc");

        // Import the 4 rasters
        DatabaseController.importRaster(dbConnection, tableName, no2_raster);
        DatabaseController.importRaster(dbConnection, tableName, o3_raster);
        DatabaseController.importRaster(dbConnection, tableName, pm10_raster);
        DatabaseController.importRaster(dbConnection, tableName, pm2p5_raster);

        // STEP 3: Compute the pollution indices using the raster data in the database

        DatabaseController.computePollution(
                dbConnection, tableName,
                no2_raster.getFileName().toString(),
                o3_raster.getFileName().toString(),
                pm10_raster.getFileName().toString(),
                pm2p5_raster.getFileName().toString()
        );
    }


    /** Read the configuration properties from the file
     *
     * @param configFile
     * @return the Properties object
     *
     * @throws IOException if opening the file fails
     */
    static Properties readConfigurationFile(File configFile) throws IOException {
        try (FileInputStream contents = new FileInputStream(configFile)) {
            var properties = new Properties();
            properties.load(contents);
            return properties;
        }
    }


    /** Run the model's shell script and relay its output
     *
     * @param filepath The script's path (shell escaped)
     * @param arguments The script's arguments as a single string (subject to shell splitting)
     *
     * @throws ProcessExitException if the model fails
     * @throws IOException if launching the process fails
     * @throws InterruptedException if another thread interrupts while waiting for the process to finish
     */
    public static void runModel(String filepath, String arguments) throws ProcessExitException, IOException, InterruptedException {
        var cmd = filepath + " " + arguments;

        // Use Runtime.exec instead of ProcessBuilder to use shell splitting (needed for arguments)
        // also relay subprocess output prefixed with "m:" or "m_e:"
        System.out.println("$ " + cmd);
        var process = Runtime.getRuntime().exec(cmd);
        var stderrThread = new Thread(new ForwardStderr(process.getErrorStream(), "m_e: "));
        var stdoutThread = new Thread(new ForwardStdout(process.getInputStream(), "m: "));
        stderrThread.start();
        stdoutThread.start();
        
        var exitCode = process.waitFor();
        stderrThread.join();
        stdoutThread.join();

        if (exitCode != 0) {
            throw new ProcessExitException(new String[] {cmd}, exitCode);
        }
    }


    /** Create a database connection given credentials
     *
     * @param username database username
     * @param password database password
     * @param dbUrl database JDBC url
     *
     * @throws ClassNotFoundException if the database connector is missing
     * @throws SQLException if the database connection fails
     */
    public static Connection connectToDatabase(String username, String password, String dbUrl) throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(dbUrl, password, username);
    }
}
