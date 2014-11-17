package ab.utils;

import java.awt.Point;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

import ab.demo.other.Shot;
import ab.planner.TrajectoryPlanner;
import ab.vision.ABObject;
import ab.vision.ABType;
import ab.vision.Vision;

public class ABUtil {
	
	public static int gap = 5; //vision tolerance.
	private static TrajectoryPlanner tp = new TrajectoryPlanner();

	// If o1 supports o2, return true
	public static boolean isSupport(ABObject o2, ABObject o1)
	{
		if(o2.x == o1.x && o2.y == o1.y && o2.width == o1.width && o2.height == o1.height)
				return false;
		
		int ex_o1 = o1.x + o1.width;
		int ex_o2 = o2.x + o2.width;
		
		int ey_o2 = o2.y + o2.height;
		if(
			(Math.abs(ey_o2 - o1.y) < gap)
			&&  
 			!( o2.x - ex_o1  > gap || o1.x - ex_o2 > gap )
		  )
	        return true;

		return false;
	}

	/*public static List<ABObject> connectedBlocks(ABObject o1, List<ABObject> objs)
	{
		List<ABObject> result = new LinkedList<ABObject>();
		for (ABObject o1: objs) {
			if(o2.x == o1.x && o2.y == o1.y && o2.width == o1.width && o2.height == o1.height)
				continue;
			if (Math.abs(o1.x + (o1.width/2) - o2.x - (o2.width/2)) < gap && Math.abs(o1.y - o2.y) < gap)
				result.add(o1);
		}
		return result;
	}*/

	//Return a link list of ABObjects that support o1 (test by isSupport function ). 
	//objs refers to a list of potential supporters.
	//Empty list will be returned if no such supporters. 
	public static List<ABObject> getSupporters(ABObject o2, List<ABObject> objs)
	{
		List<ABObject> result = new LinkedList<ABObject>();
		//Loop through the potential supporters
        for(ABObject o1: objs)
        {
        	if(isSupport(o2,o1))
        		result.add(o1);
        }
        return result;
	}

	/*public static List<ABObject> getDependents(ABObject o2, List<ABObject> objs)
	{
		List<ABObject> result = new LinkedList<ABObject>();
		//Loop through the potential supporters
        for(ABObject o1: objs)
        {
        	if(isSupport(o1,o2))
        		result.add(o1);
        }
        return result;	
	}*/

	//Return true if the target can be hit by releasing the bird at the specified release point
	public static boolean isReachable(Vision vision, Point target, Shot shot)
	{ 
		//test whether the trajectory can pass the target without considering obstructions
		Point releasePoint = new Point(shot.getX() + shot.getDx(), shot.getY() + shot.getDy()); 
		int traY = tp.getYCoordinate(vision.findSlingshotMBR(), releasePoint, target.x);
		if (Math.abs(traY - target.y) > 100)
		{	
			//System.out.println(Math.abs(traY - target.y));
			return false;
		}
		boolean result = true;
		List<Point> points = tp.predictTrajectory(vision.findSlingshotMBR(), releasePoint);		
		for(Point point: points)
		{
		  if(point.x < 840 && point.y < 480 && point.y > 100 && point.x > 400)
			for(ABObject ab: vision.findBlocksMBR())
			{
				if( 
						((ab.contains(point) && !ab.contains(target))||Math.abs(vision.getMBRVision()._scene[point.y][point.x] - 72 ) < 10) 
						&& point.x < target.x
						)
					return false;
			}
		  
		}
		return result;
	}
	
	public static int difficultyOfTrajectory(Vision vision, Point target, Shot shot) {
		//test whether the trajectory can pass the target without considering obstructions
		Point releasePoint = new Point(shot.getX() + shot.getDx(), shot.getY() + shot.getDy()); 
		int traY = tp.getYCoordinate(vision.findSlingshotMBR(), releasePoint, target.x);
		if (Math.abs(traY - target.y) > 100)
		{	
			//System.out.println(Math.abs(traY - target.y));
			return Integer.MAX_VALUE;
		}
		int result = 0;
		List<Point> points = tp.predictTrajectory(vision.findSlingshotMBR(), releasePoint);		
		for(ABObject ab: vision.findBlocksMBR())
		{
			for (Point point: points) {
				if (point.x < 840 && point.y < 480 && point.y > 100 && point.x > 400) {
					if( 
						((ab.contains(point) && !ab.contains(target))||Math.abs(vision.getMBRVision()._scene[point.y][point.x] - 72 ) < 10) 
						&& point.x < target.x
						) {
						switch(ab.getType()) {
						case Hill:
							//System.out.println("HILL " + ab.id);
							result += 100;
							//ret += "Hill ";
							break;
						case Ice:
							//System.out.println("Ice " + ab.id);
							result += 2;
							//ret += "Ice ";
							break;
						case Wood:
							//System.out.println("Wood " + ab.id);
							result += 4;
							//ret += "Wood ";
							break;
						case Stone:
							//System.out.println("Stone " + ab.id);
							result += 8;
							//ret += "Stone ";
							break;
						default:
							result += 1;
							//ret += "default ";
						}
						break;
					}
				}
			}
		}
		//System.out.println(ret);
		return result;
	}

		public static boolean isHillInBetween(Vision vision, Point target, Shot shot) {
		//test whether the trajectory can pass the target without considering obstructions
		Point releasePoint = new Point(shot.getX() + shot.getDx(), shot.getY() + shot.getDy()); 
		int traY = tp.getYCoordinate(vision.findSlingshotMBR(), releasePoint, target.x);
		if (Math.abs(traY - target.y) > 100)
		{	
			//System.out.println(Math.abs(traY - target.y));
			return false;
		}
		List<Point> points = tp.predictTrajectory(vision.findSlingshotMBR(), releasePoint);
		//System.out.println(vision.findHills().size());		
		for(ABObject ab: vision.findHills())
		{
			for (Point point: points) {
				if (point.x < 840 && point.y < 480 && point.y > 100 && point.x > 400) {
					if( 
						((ab.contains(point) && !ab.contains(target))||Math.abs(vision.getMBRVision()._scene[point.y][point.x] - 72 ) < 10) 
						&& point.x < target.x
						) {
							return true;
					}
				}
			}
		}
		//System.out.println(ret);
		return false;
	}

	public static ABObject getLeftObsOfTrajectory(Vision vision, Point target, Shot shot) {
		//test whether the trajectory can pass the target without considering obstructions
		Point releasePoint = new Point(shot.getX() + shot.getDx(), shot.getY() + shot.getDy()); 
		int traY = tp.getYCoordinate(vision.findSlingshotMBR(), releasePoint, target.x);
		if (Math.abs(traY - target.y) > 100)
		{	
			//System.out.println(Math.abs(traY - target.y));
			return null;
		}
		ABObject obj = null;
		double min = Double.MAX_VALUE;
		List<Point> points = tp.predictTrajectory(vision.findSlingshotMBR(), releasePoint);		
		for(ABObject ab: vision.findBlocksMBR())
		{
			for (Point point: points) {
				if (point.x < 840 && point.y < 480 && point.y > 100 && point.x > 400) {
					if( 
						((ab.contains(point) && !ab.contains(target))||Math.abs(vision.getMBRVision()._scene[point.y][point.x] - 72 ) < 10) 
						&& point.x < target.x
						) {
							if (ab.getCenter().getX() - ab.width < min) {
								min = (ab.getCenter().getX() - ab.width);
								obj = ab;
							}
					}
				}
			}
		}
		//System.out.println(ret);
		return obj;
	}
}
