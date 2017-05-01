/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package walker.utils;

import POGOProtos.Enums.PokemonIdOuterClass;
import com.pokegoapi.api.pokemon.Pokemon;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DAO {

    Connection connection;
    private ExecutorService executorService = Executors.newFixedThreadPool(2);

    public DAO() {

        executorService.execute(new Runnable() {
            public void run() {
                try {
                    System.out.println("[DB] : Starting connection setup");
                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection("jdbc:sqlite:pgo.db");
                    Logger.INSTANCE.Log(Logger.TYPE.DB, "Connection Successful.");
                    boolean setup = testTables();
                    System.out.println("[DB] Checking setup: " + testTables());
                    if (!setup) {
                        setupDatabase();
                    }
                } catch (ClassNotFoundException | SQLException ex) {
                    Logger.INSTANCE.Log(Logger.TYPE.DB, "Error! " + ex.toString());
                    ex.printStackTrace();
                }
            }
        });
    }

    public void updatePokedex(String pokemon, boolean caught) {
        executorService.execute(() -> {
            try {
                Statement stmt = connection.createStatement();
                String query = "UPDATE pokedex SET Caught = " + (caught ? 1 : 0) + " WHERE Name = \"" + pokemon + "\"";
                stmt.executeUpdate(query);
            } catch (SQLException ex) {
                System.out.println("Error updating Pokedex: " + ex.toString());
                ex.printStackTrace();
            }
        });
    }

    public void clearPokebag() {
        executorService.execute(new Runnable() {
            public void run() {
                try {
                    Statement stmt = connection.createStatement();
                    String query = "DELETE FROM pokebag;";
                    stmt.executeUpdate(query);
                } catch (Exception ex) {
                    System.out.println("Error clearing Pokebag: " + ex.toString());
                }
            }
        });
    }

    public void updatePokebag(List<Pokemon> myPokemon) {
        List<Pokemon> copyList = new ArrayList<>(myPokemon);

        executorService.execute(new Runnable() {
            public void run() {
                try {
                    Iterator<Pokemon> itr = copyList.iterator();
                    while (itr.hasNext()) {
                        Pokemon pokemon = itr.next();
                        Statement stmt = connection.createStatement();
                        String query = "INSERT INTO pokebag VALUES (" + pokemon.getSettings().getPokemonIdValue() + ",  '" + pokemon.getPokemonId().name() + "', " + pokemon.getIvInPercentage() + ", "
                                + pokemon.getCp() + ", '" + pokemon.getMove1().name() + "', '" + pokemon.getMove2().name() + "');";
                        stmt.executeUpdate(query);
                    }
                } catch (Exception ex) {
                    System.out.println("Error updating Pokebag: " + ex.toString());
                }
            }
        });
    }

    private boolean testTables() throws SQLException {
        Statement stmt = connection.createStatement();
        String query = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='pokedex'";
        boolean check = false;

        ResultSet rs = stmt.executeQuery(query);
        rs.next();
        if (rs.getInt(1) == 1) {
            check = true;
        }
        return check;
    }

    private void setupDatabase() throws SQLException {
        // NEEDS ERROR LOG??
        // Setup Pokedex
        System.out.println("[DB] Setting Up Pokedex");
        Statement stmt = connection.createStatement();
        String query = "CREATE TABLE pokedex ("
                + "Name VARCHAR(30) PRIMARY KEY NOT NULL, "
                + "Caught INT NOT NULL)";
        // Set up Pokebag
        stmt.executeUpdate(query);
        query = "CREATE TABLE pokebag ("
                + "ID INT NOT NULL, "
                + "Name VARCHAR(30) NOT NULL, "
                + "IVs DOUBLE NOT NULL, "
                + "CP INTEGER NOT NULL, "
                + "MOVE1 VARCHAR(30), "
                + "MOVE2 VARCHAR(30)) ";
        stmt.executeUpdate(query);

        for (int i = 1; i < 251; i++) {
            PokemonIdOuterClass.PokemonId pkmn = PokemonIdOuterClass.PokemonId.forNumber(i);
            query = "INSERT INTO pokedex VALUES (\"" + pkmn.name() + "\", 0)";
            stmt.executeUpdate(query);
        }
    }
}
