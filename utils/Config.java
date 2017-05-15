package walker.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Config {

    private static String LOGIN = "mdstrainer53";
    private static String PASSWORD = "Five@live332";
    private static double LATITUDE = 50.715943;
    private static double LONGITUDE = -1.8762018;
    private static double ALTITUDE = 0.0;
    private static String HASH_KEY = "5M1O7O8L1L7T0S3L6C1S";

    public Config() {
        final String CONFIGFILE = "config.ini";
        File f = new File(CONFIGFILE);
        if (!f.exists()) {
            Logger.INSTANCE.Log(Logger.TYPE.ERROR, "No Config found!");
        } else {
            try (BufferedReader br = new BufferedReader(new FileReader(CONFIGFILE))) {

                String sCurrentLine;

                while ((sCurrentLine = br.readLine()) != null) {
                    if (!sCurrentLine.substring(0, 1).equals("#")) {
                        String variable = sCurrentLine.split("=")[0];
                        String value = sCurrentLine.split("=")[1];
                        System.out.println(variable + "=" + value);
                        switch (variable) {
                            case "login":
                                LOGIN = value;
                                break;
                            case "password":
                                PASSWORD = value;
                                break;
                            case "latitude":
                                LATITUDE = Double.parseDouble(value);
                                break;
                            case "longitude":
                                LONGITUDE = Double.parseDouble(value);
                                break;
                            case "altitude":
                                ALTITUDE = Integer.parseInt(value);
                                break;
                            case "hashkey":
                                HASH_KEY = value;
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getLOGIN() {
        return LOGIN;
    }

    public static String getPASSWORD() {
        return PASSWORD;
    }

    public static double getLATITUDE() {
        return LATITUDE;
    }

    public static double getLONGITUDE() {
        return LONGITUDE;
    }

    public static double getALTITUDE() {
        return ALTITUDE;
    }

    public static String getHASH_KEY() {
        return HASH_KEY;
    }
}
