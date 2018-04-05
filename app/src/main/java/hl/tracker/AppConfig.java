package hl.tracker;

class AppConfig {
    public static final boolean DEBUG                   = true;
    public static final String TAG                      = "GpsTracker";
    public static final int NOTIFICATION_ID             = 20180404;
    private static final long MILLISECONDS              = 1000;
    public static final long UPDATE_INTERVAL            = (DEBUG ? 10 : 60) * MILLISECONDS;
    public static final long FASTEST_UPDATE_INTERVAL    = UPDATE_INTERVAL / 2;
    public static final float SMALLEST_DISPLACEMENT     = DEBUG ? 0 : 50;
}
