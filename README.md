# MintServ

Uses Java 11. Build the fatJar with `./gradlew shadowJar`.

Copy `config.properties.template` to `config.properties` and edit it.\
Then run the jar in the current directory: `java -jar build/libs/MintServ.jar`


## Developer notes

- Assumes the postgis and postgis\_raster extensions are loaded.
- Do we need to pass configuration to the model.sh / model.py or assume that it works on its own ?

### TODO

- Add raster metadata
- Add raster manipulation and calculations
- Check mandatory configuration keys
