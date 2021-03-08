-- Given rasters in conc_raster, compute the pollution indices as val_* columns in ways
-- Input 1 -> NO2 raster filename
--       2 -> O3 raster filename
--       3 -> PM10 raster filename
--       4 -> PM2.5 raster filename

-- Suppression des tables conc_*_proj pour les recréer ensuite
  DROP TABLE IF EXISTS conc_proj_no2;
  DROP TABLE IF EXISTS conc_proj_o3;
  DROP TABLE IF EXISTS conc_proj_pm10;
  DROP TABLE IF EXISTS conc_proj_pm2p5;
-- Création de tables conc_*_proj avec les raster dans la même projection que ways (SRID:4326)
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
  DROP TABLE IF EXISTS indice;
CREATE TABLE indice
   AS SELECT osm_id,
             -- On récupère la valeur du raster correspondant au milieu de chaque way
             ST_Value(rast_no2,   ST_Centroid(the_geom)) AS val_no2,
             ST_Value(rast_o3,    ST_Centroid(the_geom)) AS val_o3,
             ST_Value(rast_pm10,  ST_Centroid(the_geom)) AS val_pm10,
             ST_Value(rast_pm2p5, ST_Centroid(the_geom)) AS val_pm2p5
        FROM ways,
             conc_proj_no2,
             conc_proj_o3,
             conc_proj_pm10,
             conc_proj_pm2p5
       WHERE ST_Intersects(the_geom, rast_no2)
         AND ST_Intersects(the_geom, rast_o3)
         AND ST_Intersects(the_geom, rast_pm10)
         AND ST_Intersects(the_geom, rast_pm2p5);
-- Suppression des valeurs nulles
DELETE FROM indice WHERE val_no2   IS NULL;
DELETE FROM indice WHERE val_o3    IS NULL;
DELETE FROM indice WHERE val_pm10  IS NULL;
DELETE FROM indice WHERE val_pm2p5 IS NULL;

-- Calcul des indices (colonnes indice.val_* et indice.indice_gen)
ALTER TABLE indice
 ADD COLUMN indice_gen double precision;
UPDATE indice
   -- NB If we need to store the concentrations, we'll need to assign the indices in new columns
   --    (to keep the concentration values)
   SET val_no2 =
           (CASE
            WHEN       val_no2 <= 40  THEN 1
            WHEN 40  < val_no2 <= 90  THEN 2
            WHEN 90  < val_no2 <= 120 THEN 3
            WHEN 120 < val_no2 <= 230 THEN 4
            WHEN 230 < val_no2 <= 340 THEN 5
            WHEN 340 < val_no2        THEN 6
            ELSE val_no2
            END),
   SET val_o3 =
           (CASE
            WHEN       val_o3 <= 50  THEN 1
            WHEN 50  < val_o3 <= 100 THEN 2
            WHEN 100 < val_o3 <= 130 THEN 3
            WHEN 130 < val_o3 <= 240 THEN 4
            WHEN 240 < val_o3 <= 380 THEN 5
            WHEN 380 < val_o3        THEN 6
            ELSE val_o3
            END),
   SET val_pm10 =
           (CASE
            WHEN       val_pm10 <= 20  THEN 1
            WHEN 20  < val_pm10 <= 40  THEN 2
            WHEN 40  < val_pm10 <= 50  THEN 3
            WHEN 50  < val_pm10 <= 100 THEN 4
            WHEN 100 < val_pm10 <= 150 THEN 5
            WHEN 150 < val_pm10        THEN 6
            ELSE val_pm10
            END),
   SET val_pm2p5 =
            (CASE
             WHEN      val_pm2p5 <= 10 THEN 1
             WHEN 10 < val_pm2p5 <= 20 THEN 2
             WHEN 20 < val_pm2p5 <= 25 THEN 3
             WHEN 25 < val_pm2p5 <= 50 THEN 4
             WHEN 50 < val_pm2p5 <= 75 THEN 5
             WHEN 75 < val_pm2p5       THEN 6
             ELSE val_pm2p5
             END),
   SET val_gen =
             SELECT MAX(val_no2, val_o3, val_pm10, val_pm2p5)
               FROM indice;

-- Add columns to ways if they don't exist yet
ALTER TABLE ways
 ADD COLUMN IF NOT EXISTS conc_no2   double precision,
 ADD COLUMN IF NOT EXISTS conc_o3    double precision,
 ADD COLUMN IF NOT EXISTS conc_pm10  double precision,
 ADD COLUMN IF NOT EXISTS conc_pm2p5 double precision,
 ADD COLUMN IF NOT EXISTS conc_gen   double precision;

-- Ajout des indices à chaque tronçon d'OSM (colonnes ways.conc_*)
UPDATE ways
   -- TODO maybe rename columns
   -- TODO maybe also store concentration values
   SET conc_no2   = val_no2,
       conc_o3    = val_o3,
       conc_pm10  = val_pm10,
       conc_pm2p5 = val_pm2p5,
       conc_gen   = val_gen
  FROM indice
 WHERE ways.osm_id = indice.osm_id;
