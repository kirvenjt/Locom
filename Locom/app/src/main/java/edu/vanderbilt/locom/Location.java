package edu.vanderbilt.locom;

import java.util.Set;

//Simple location abstraction for longitude and latitude
//provides a function to find the distance between locations
public class Location {

	private double longitude, latitude;
	
	//constructor
	public Location(double longitude, double latitude){
		this.longitude = longitude;
		this.latitude = latitude;
	}
	
	//updates long and lat of location
	public void update(double longitude, double latitude){
		this.longitude = longitude;
		this.latitude = latitude;
	}
	
	//returns true if distance between this location and the passed
	// in location is less than Radius
	public Boolean inRange(Location location, double Radius){
		 return (this.getDistance(location) < Radius);
	}
	
	public float getDistance(Location location){

        float[] results = {0,0,0};
        android.location.Location.distanceBetween( latitude, longitude, location.latitude,location.longitude, results);
		
		return results[0];
	}
    public double getLat(){
        return this.latitude;
    }
    public double getLong() { return this.longitude;}
	
}
