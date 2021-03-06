package edu.vanderbilt.locom;

import java.util.HashSet;
import java.util.Set;

//class to hold a set of interests a user has and provide a simple
// interface for checking whether a user has matching interest of a broadcast
public class InterestTags {

	private Set<String> tags = new HashSet<String>();
	
	public InterestTags(String[] arrTags){
		for (String tag: arrTags){
			this.tags.add(tag);
		}
	}
	
	public Boolean hasInterest(String singleTag){
		
		return this.tags.contains(singleTag);	
	}
	
	public Boolean hasInterests(String[] tags){
		
		for (String tag: tags){
			if (this.tags.contains(tag)){
				return true;
			}
		}
		return false;
	}
    public int getSize(){
        return tags.size();
    }

    public Set<String> getTags(){
        return this.tags;
    }
}
