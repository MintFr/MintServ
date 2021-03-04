#!/bin/sh

# Build it
echo '$ ./gradlew shadowJar'
./gradlew shadowJar

# Create tar directory
echo "Copying files to add to the tar file"
mkdir -p tar
cp build/libs/MintServ-all.jar tar/
cp config.properties tar/

# Create tar file
echo "Creating the tar file"
cd tar
tar cf MintServ.tar MintServ-all.jar config.properties
cd ..

# Copy tar out of the tar folder
echo "Moving the tar file"
mv tar/MintServ.tar MintServ.tar
rm -r tar

echo "Created MintServ.tar"
