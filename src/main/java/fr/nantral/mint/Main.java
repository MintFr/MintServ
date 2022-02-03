/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.nantral.mint;

import java.io.*;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Lucas Deswarte
 */
public class Main {

    public static void main(String[] args) {
        Connection connection=null;

        try {
            Class.forName("org.postgresql.Driver");}
         catch (java.lang.ClassNotFoundException e) {
            System.err.println("class not found exception: " + e.getMessage());
        }
        String URL = "jdbc:postgresql://localhost:5433/testEmsPollution";
            String username = "postgres";
            String password = "Luckyluke31";
            String csvFilePath = "D:\\mes dossiers\\ei3\\Projet\\Case_2-2.csv";
        try{
            connection = DriverManager.getConnection(URL, username, password);
            importFromFile(connection, csvFilePath);
            connection.close();
            Driver theDriver = DriverManager.getDriver(URL);
            DriverManager.deregisterDriver(theDriver);
        } catch (SQLException ex) {
            System.err.println("SQL exception: " + ex.getMessage());
        }
    }

    public static void importFromFile(Connection connect, String filename) {
        try {
            String query = "INSERT INTO pollution(latitude,longitude,no2,co,the_geom) VALUES(?,?,?,?,(ST_SetSRID(ST_Point(?,?),4326)))";
            PreparedStatement stmt = connect.prepareStatement(query);

            BufferedReader csvReader = new BufferedReader(new FileReader(filename));
            int i = 0;
            String row = csvReader.readLine();
            while (row != null || !row.startsWith("*END")) {
                    row = row.trim().replace("\t", "");
                    row = row.replace("e", "E");
                    if (!row.isBlank() && i>6 ) {
                        stmt.clearParameters();
                        String[] data = row.split(";");
                        stmt.setDouble(1, Double.parseDouble(data[1]));
                        stmt.setDouble(2, Double.parseDouble(data[2]));
                        stmt.setDouble(3, Double.parseDouble(data[3]));
                        stmt.setDouble(4, Double.parseDouble(data[4]));
                        stmt.setDouble(5, Double.parseDouble(data[1]));
                        stmt.setDouble(6, Double.parseDouble(data[2]));
                        stmt.executeUpdate();
                }
                row=csvReader.readLine();
                i++;
            }
            csvReader.close();
            stmt.close();

        }catch(FileNotFoundException ex){
        Logger.getLogger(Main.class.getName()).log(Level.SEVERE,null,ex);
        } catch (IOException | SQLException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }

}
