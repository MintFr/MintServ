-- Given rasters in conc_raster, compute the pollution indices as val_* columns in osm_ways
-- Input 1 -> NO2 raster filename
--       2 -> O3 raster filename
--       3 -> PM10 raster filename
--       4 -> PM2.5 raster filename

-- Création de tables conc_*_proj avec les raster dans la même projection qu'osm_ways (SRID:4326)
CREATE TABLE conc_proj_no2
   AS SELECT ST_Transform(rast, 4326) AS rast_no2
        FROM conc_raster
       WHERE filename = ?; -- Insert no2 filename
CREATE TABLE conc_proj_o3;
   AS SELECT ST_Transform(rast, 4326) AS rast_o3
        FROM conc_raster;
       WHERE filename = ?; -- Insert o3 filename
CREATE TABLE conc_proj_pm10;
   AS SELECT ST_Transform(rast, 4326) AS rast_pm10
        FROM conc_raster;
       WHERE filename = ?; -- Insert pm10 filename
CREATE TABLE conc_proj_pm2p5;
   AS SELECT ST_Transform(rast, 4326) AS rast_pm2p5
        FROM conc_raster;
       WHERE filename = ?; -- Insert pm2.5 filename

-- Création d'une table `indice` pour les concentrations et le calcul des indices
  DROP TABLE indice;
CREATE TABLE indice
   AS SELECT osm_id,
             -- On récupère la valeur du raster correspondant au milieu de chaque way
             ST_Value(rast_no2,   ST_Centroid(the_geom)) AS val_no2,
             ST_Value(rast_o3,    ST_Centroid(the_geom)) AS val_o3,
             ST_Value(rast_pm10,  ST_Centroid(the_geom)) AS val_pm10,
             ST_Value(rast_pm2p5, ST_Centroid(the_geom)) AS val_pm2p5
        FROM osm_ways,
             conc_proj_no2,
             conc_proj_o3,
             conc_proj_pm10,
             conc_proj_pm2p5
       WHERE ST_Intersects(the_geom, rast_no2)
         AND ST_Intersects(the_geom, rast_o3)
         AND ST_Intersects(the_geom, rast_pm10)
         AND ST_Intersects(the_geom, rast_pm2p5);
-- Suppression des valeurs nulles
DELETE FROM indice WHERE val_no2 IS NULL;
DELETE FROM indice WHERE val_o3 IS NULL;
DELETE FROM indice WHERE val_pm10 IS NULL;
DELETE FROM indice WHERE val_pm2p5 IS NULL;

-- Calcul des indices (colonnes indice.val_* et indice.indice_gen)
ALTER TABLE indice
 ADD COLUMN indice_gen double precision;
UPDATE indice
   SET val_no2 =
           (CASE
            WHEN       val_no2 <= 40  THEN val_no2 = 1
            WHEN 40  < val_no2 <= 90  THEN val_no2 = 2
            WHEN 90  < val_no2 <= 120 THEN val_no2 = 3
            WHEN 120 < val_no2 <= 230 THEN val_no2 = 4
            WHEN 230 < val_no2 <= 340 THEN val_no2 = 5
            WHEN 340 < val_no2        THEN val_no2 = 6
            ELSE val_no2
            END),
   SET val_o3 =
            (CASE
            WHEN       val_o3 <= 50  THEN val_o3 = 1
            WHEN 50  < val_o3 <= 100 THEN val_o3 = 2
            WHEN 100 < val_o3 <= 130 THEN val_o3 = 3
            WHEN 130 < val_o3 <= 240 THEN val_o3 = 4
            WHEN 240 < val_o3 <= 380 THEN val_o3 = 5
            WHEN 380 < val_o3        THEN val_o3 = 6
            ELSE val_o3
            END),
   SET val_pm10 =
            (CASE
            WHEN       val_pm10 <= 20  THEN val_pm10 = 1
            WHEN 20  < val_pm10 <= 40  THEN val_pm10 = 2
            WHEN 40  < val_pm10 <= 50  THEN val_pm10 = 3
            WHEN 50  < val_pm10 <= 100 THEN val_pm10 = 4
            WHEN 100 < val_pm10 <= 150 THEN val_pm10 = 5
            WHEN 150 < val_pm10        THEN val_pm10 = 6
            ELSE val_pm10
            END),
   SET val_pm2p5 =
            (CASE
             WHEN      val_pm2p5 <= 10 THEN val_pm2p5 = 1
             WHEN 10 < val_pm2p5 <= 20 THEN val_pm2p5 = 2
             WHEN 20 < val_pm2p5 <= 25 THEN val_pm2p5 = 3
             WHEN 25 < val_pm2p5 <= 50 THEN val_pm2p5 = 4
             WHEN 50 < val_pm2p5 <= 75 THEN val_pm2p5 = 5
             WHEN 75 < val_pm2p5       THEN val_pm2p5 = 6
             ELSE val_pm2p5
             END),
   SET indice_gen
             SELECT MAX(val_no2, val_o3, val_pm10, val_pm2p5)
               FROM indice;

-- Ajout des indices à chaque tronçon d'OSM (colonnes osm_ways.conc_*)
UPDATE osm_ways
   SET conc_no2   = val_no2,
       conc_o3    = val_o3,
       conc_pm10  = val_pm10,
       conc_pm2p5 = val_pm2p5
  FROM indice
 WHERE osm_ways.osm_id = indice.osm_id;
