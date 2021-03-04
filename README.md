# MintServ

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

### TODO

- (Optional ?) Check mandatory configuration keys
- Check if postgresql jdbc is needed
