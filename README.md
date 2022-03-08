# MintServ

# Caution
**This repo was used during the first year, and is using the software SIRANE. We did not use this software this year.**

The server launcher that is responsible for launching the pollution model,
import its output into the PostGIS database, and compute the pollution index. 

Written in Java 11. Build the fatJar with `./gradlew shadowJar`.
To run it, create a `config.properties` file based on `config.properties.template`
and place it in the current working directory. 

Then run either of these commands:
 
```sh
# If everything is set up correctly, this should work
java -jar build/libs/MintServ.jar

# For debugging use the following. It skips a few steps, so it will crash.
java -jar build/libs/MintServ.jar --model-args='--skip-download' --skip-connection

# or with gradle, useful for IntelliJ IDEA
./gradlew run --args="<jar arguments>"
```

## Deployment

Requirements: Java 11 runtime (and maybe PostgreSQL JDBC)

It is recommended to have the pollution model set up before deploying MintServ, as you specify
its location in the documentation.

- Build the fatJar with `./gradlew shadowJar`
- Create the configuration file `config.properties` based on `config.properties.template`.
- Copy `./builds/libs/MintServ-all.jar` and `config.properties` to the production folder
- Add an entry to the crontab to run this command every 15 minutes:\
  `/path/to/jdk11/bin/java -jar /path/to/MintServ-all.jar <args>`
  
To create a tarball, just launch `build-tar.sh`.

## Developer notes

- We don't perform initialization such as loading extensions or creating columns.
- Do we need to pass configuration to the model.sh / model.py or assume that it works on its own ?

### Manual import

Since currently, the server's `raster2pgsql` doesn't work properly, and `ST_Transform` doesn't work either, here's how we work around that:
- Take the netcdf file from SIRANE's output directory, and open it in QGIS's `Raster > Georeferencer`.
  - Load (or assign) the corner points defined in SIRANE's output grid file, and launch the computation using the Lambert-93 projection
- Open the created file (called by default `<filename>_modified.tiff`) and right-click export layer to file and make sure to choose Netcdf format and OSM's projection WGS 84 in the CRS dropdown.
- Create the Postgres command file using `raster2pgsql` using the new projected file. Use the SRID 4326.
- Execute the Postgres command. Make sure to modify the `CREATE TABLE STATEMENT` to `CREATE TABLE IF NOT EXISTS` to avoid Postgres rejecting the transaction. Also modify the table name to be directly `conc_proj_<species>` since we did the projection conversion by hand. Also, you should rename the `rast` column to `rast_<species>`.
  
  For reference, this is the SQL command used to rename the tables:
  ```sql
  ALTER TABLE conc_proj_no2   RENAME COLUMN rast TO rast_no2;
  ALTER TABLE conc_proj_o3    RENAME COLUMN rast TO rast_o3;
  ALTER TABLE conc_proj_pm10  RENAME COLUMN rast TO rast_pm10;
  ALTER TABLE conc_proj_pm2p5 RENAME COLUMN rast TO rast_pm2p5;
  ```
- Do the rest as normal

### TODO

- Importing the rasters with raster2pgsql doesn't work too well since the table already exists (you should add `IF NOT EXISTS`), and the import complains a lot about the indexes and constraints (so maybe remove the performance related command line arguments to raster2pgsql)

- (Optional ?) Check mandatory configuration keys
