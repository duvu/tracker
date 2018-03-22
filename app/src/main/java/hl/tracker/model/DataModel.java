package hl.tracker.model;

import android.location.Location;

import hl.tracker.StringTools;

/**
 * Created by beou on 3/2/17.
 */

public class DataModel {
    private String deviceId; //could be IMEI, or Instance-Id
    private Double latitude, longitude;
    private Double altitude;
    private int statusCode;
    private Float speedKph;
    private Float heading;
    private Long timestamp;
    private String address;
    private int satCount;
    float signalStrength;
    private String note;
    //---
    private float batteryLevel;
    //---

    public DataModel(String deviceId) {
        this.deviceId = deviceId;
    }

    public DataModel() {
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Float getSpeedKph() {
        return speedKph;
    }

    public void setSpeedKph(Float speedKph) {
        this.speedKph = speedKph;
    }

    public float getHeading() {
        return heading;
    }

    public void setHeading(Float heading) {
        this.heading = heading;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public int getSatCount() {
        return satCount;
    }

    public void setSatCount(int satCount) {
        this.satCount = satCount;
    }

    public float getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(float signalStrength) {
        this.signalStrength = signalStrength;
    }

    public float getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(float batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public static DataModel fromLocation(Location location) {
        DataModel d = new DataModel();
        d.setLatitude(location.getLatitude());
        d.setLongitude(location.getLongitude());
        d.setAltitude(location.getAltitude());
        d.setHeading(location.getBearing());
        d.setTimestamp(location.getTime()/1000);
        float speedMps = location.getSpeed();
        float speedKph = StringTools.mps2kph(speedMps);
        d.setSpeedKph(speedKph);
        d.setSpeedKph(location.getSpeed());
        //--missing address, note, deviceId, batteryLevel, statuscode
        return d;
    }
    public DataModel updateLocation(Location location) {
        this.setLatitude(location.getLatitude());
        this.setLongitude(location.getLongitude());
        this.setAltitude(location.getAltitude());
        this.setHeading(location.getBearing());
        this.setTimestamp(location.getTime()/1000);
        float speedMps = location.getSpeed();
        float speedKph = StringTools.mps2kph(speedMps);
        this.setSpeedKph(speedKph);
        //--missing address, note, deviceId, batteryLevel, statuscode
        return this;
    }
}
