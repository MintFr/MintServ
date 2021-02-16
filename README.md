# MintServ

Uses Java 11. Build the fatJar with `./gradlew shadowJar`.

Copy `config.properties.template` to `config.properties` and edit it.\
Then run the jar in the current directory:
 
```sh
java -jar build/libs/MintServ.jar --file=data/Conc_NO2_2014010100.nc

# Current debug command is
java -jar build/libs/MintServ.jar --file=data/Conc_NO2_2014010100.nc --model-args='--skip-download' --skip-connection
# or with gradle:
./gradlew run --args="--file=data/Conc_NO2_2014010100.nc --model-args='--skip-download' --skip-connection"
```


## Developer notes

- Assumes the postgis and postgis\_raster extensions are loaded.
- Do we need to pass configuration to the model.sh / model.py or assume that it works on its own ?

### TODO

- (Optional ?) Check mandatory configuration keys
