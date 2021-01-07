package fr.nantral.mint;

import java.io.File;
import org.geotools.coverage.io.netcdf.NetCDFFormat;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.util.Arguments;

public class App {
    public static void main(String args[]) throws Exception {
        // GeoTools provides utility classes to parse command line arguments
        var processedArgs = new Arguments(args);
        var file = new File(processedArgs.getRequiredString("--file"));

        var format = new NetCDFFormat();
        var reader = format.getReader(file);
        GridCoverage2D gridCoverage = reader.read(null);
    }
}
