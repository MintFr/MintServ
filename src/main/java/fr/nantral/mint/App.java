package fr.nantral.mint;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;


public class App implements Runnable {

    // === picocli configuration ===

    @Option(names = "--config", description = "The configuration file")
    File configFile = new File("config.properties");

    @Option(names = "--file", required = true, description = "The netcdf file to import")
    File netcdfFile;

    @Option(names ="--skip-model", description = "Skip launching the model. No netcdf files will be created") boolean skipModel;
    @Option(names ="--skip-connection", description = "Skip connecting to the database. Crashes the process") boolean skipConnection;

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

        // TODO maybe check mandatory keys ?

        // STEP 1: Run the model
        if (!skipModel) {
            var modelFilepath = config.getProperty("model_sh");
            runModel(modelFilepath);
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

        // STEP 2: Import the model output into the database

        // Find model raster output directory
        var modelWorkdir = config.getProperty("model_dir");
        var rasterDir = Path.of(modelWorkdir)
                .resolve("RESULT/GRILLE")
                .toFile();
        if (!rasterDir.exists()) {
            throw new FileNotFoundException("Model raster directory doesn't exist: " + rasterDir);
        }

        // Import netcdf rasters
        File[] rasterPaths = rasterDir.listFiles(x -> x.isFile() && x.getName().endsWith(".nc"));
        for (var rasterPath: rasterPaths) {
            DatabaseController.importIntoDatabase(dbConnection, tableName, rasterPath.toPath());
        }

        // STEP 3: Manipulate the rasters in the database

        // Create an array of the raster names (used in the database)
        String[] rasterNames = new String[rasterPaths.length];
        for (int i = 0; i < rasterPaths.length; i++) {
            rasterNames[i] = rasterPaths[i].getName();
        }

        // TODO do calculations and manipulations
    }

    static Properties readConfigurationFile(File configFile) throws IOException {
        try (FileInputStream contents = new FileInputStream(configFile)) {
            var properties = new Properties();
            properties.load(contents);
            return properties;
        }
    }

    /** Run the model's shell script
     *
     * @param filepath The script's path
     * @throws ProcessExitException if the model fails
     * @throws IOException if launching the process fails
     * @throws InterruptedException if another thread interrupts while waiting for the process to finish
     */
    public static void runModel(String filepath) throws ProcessExitException, IOException, InterruptedException {
        // Maybe add arguments
        var cmd = new String[] {filepath};
        var process = new ProcessBuilder(cmd)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        System.out.println("$ " + String.join(" ", cmd));

        // Relay subprocess output prefixed with "model:"
		var bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		bufferedReader.lines().forEach(x -> System.out.println("model: " + x));

		// NB this might block the stderr thread, or not...

        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new ProcessExitException(cmd, exitCode);
        }
    }

    /** Create a database connection given $config
     *
     * @param username database username
     * @param password database password
     * @param dbUrl database JDBC url
     * @throws ClassNotFoundException if the database connector is missing
     * @throws SQLException if the database connection fails
     */
    public static Connection connectToDatabase(String username, String password, String dbUrl) throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(dbUrl, password, username);
    }
}
