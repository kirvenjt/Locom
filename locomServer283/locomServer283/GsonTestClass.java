package locomServer283;

import java.io.PrintWriter;
import java.util.Date;

public class GsonTestClass {
	String type = "connect";
	Date date = new Date();
	String strNull = null;
	public Location location = new Location(15.5, 15.5);
	
	public InterestTags tags = new InterestTags(new String[]{"a","b","c"});
	
}
