package walker.utils;

import java.util.Date;

public enum Logger {
    
    INSTANCE;
    
    private static final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
    
    public enum TYPE {
        ERROR, INFO, DEBUG, EVENT, DB;
    }

    public void Log(TYPE type, String message) {
        String date = sdf.format(new Date());
        System.out.println("[" + type + "]"  + "[" + date + "]  " + message);
    }

}
