package edu.vanderbilt.locom;

import java.util.HashSet;
import java.util.Set;

//not used on front end
public class Users {

	public Set<User> users = new HashSet<User>();
	
	public Users(){
		//no op
	}
	
	//adds user returns false if user already exists
	public Boolean addUser(User user){
		return this.users.add(user);
	}
	
	public Boolean exists(User user){
		return this.users.contains(user);
	}
	
	//removes user returns true if user existed and operation was sucesfull
	public Boolean removeUser(User user){
		return this.users.remove(user);
	}
}
