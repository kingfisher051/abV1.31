/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
 **This work is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License. 
 **To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-sa/3.0/ 
 *or send a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
 *****************************************************************************/
package ab.demo;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.LogRecord;
import java.util.logging.Level;

import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
import ab.planner.TrajectoryPlanner;
import ab.utils.StateUtil;
import ab.utils.ABUtil;
import ab.vision.ABType;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor.GameState;
import ab.vision.Vision;

public class NaiveAgent implements Runnable {

	private ActionRobot aRobot;
	private Random randomGenerator;
	public int currentLevel = 1;
	public static int time_limit = 12;
	private Map<Integer,Integer> scores = new LinkedHashMap<Integer,Integer>();
	TrajectoryPlanner tp;
	private boolean firstShot;
	private Point prevTarget;
	private boolean writeToFile;

	private Logger logger;  
    private FileHandler fh;
    private int repeat;
    //private int hit;
	// a standalone implementation of the Naive Agent

    public class newFormatter extends SimpleFormatter {
		public String format(LogRecord record){
			if(record.getLevel() == Level.INFO){
				return record.getMessage() + "\r\n";
			}else{
				return super.format(record);
			}
		}
    }
    newFormatter formatter;

	public NaiveAgent(String filename) {
		
		aRobot = new ActionRobot();
		tp = new TrajectoryPlanner();
		prevTarget = null;
		firstShot = true;
		randomGenerator = new Random();
		// --- go to the Poached Eggs episode level selection page ---
		ActionRobot.GoFromMainMenuToLevelSelection();
		try {
			logger = Logger.getLogger("MyLog");
			fh = new FileHandler(filename);
			logger.addHandler(fh);
	        formatter = new newFormatter();  
	        fh.setFormatter(formatter);
	        writeToFile = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		//hit = 0;
	}

	public NaiveAgent() {
		aRobot = new ActionRobot();
		tp = new TrajectoryPlanner();
		prevTarget = null;
		firstShot = true;
		randomGenerator = new Random();
		// --- go to the Poached Eggs episode level selection page ---
		ActionRobot.GoFromMainMenuToLevelSelection();
		writeToFile = false;
		//hit = 0;
	}

	
	// run the client
	public void run(){

		aRobot.loadLevel(currentLevel);
		repeat = 0;
		if (writeToFile) {
			try {
				logger.info("Level " + currentLevel + ":");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		while (true) {
			GameState state = solve();
			if (state == GameState.WON) {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				int score = StateUtil.getScore(ActionRobot.proxy);
				if(!scores.containsKey(currentLevel))
					scores.put(currentLevel, score);
				else
				{
					if(scores.get(currentLevel) < score)
						scores.put(currentLevel, score);
				}
				int totalScore = 0;
				for(Integer key: scores.keySet()){

					totalScore += scores.get(key);
					System.out.println(" Level " + key
							+ " Score: " + scores.get(key) + " ");
				}
				System.out.println("Total Score: " + totalScore);
				repeat = 0;
				aRobot.loadLevel(++currentLevel);
				if (writeToFile) {
					try {
						logger.info("		Level " + (currentLevel-1) + " Score: " + scores.get(currentLevel-1));
						logger.info("		Total Score: " + totalScore + "\n");
						logger.info("Level " + currentLevel + ":");
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				// make a new trajectory planner whenever a new level is entered
				tp = new TrajectoryPlanner();

				// first shot on this level, try high shot first
				firstShot = true;
			} else if (state == GameState.LOST) {
				System.out.println("Restart");
				aRobot.restartLevel();
				repeat++;
				if (writeToFile) {
					try {
						logger.info("Restarting Level " + currentLevel + ":");
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} else if (state == GameState.LEVEL_SELECTION) {
				System.out
				.println("Unexpected level selection page, go to the last current level : "
						+ currentLevel);
				aRobot.loadLevel(currentLevel);
			} else if (state == GameState.MAIN_MENU) {
				System.out
				.println("Unexpected main menu page, go to the last current level : "
						+ currentLevel);
				ActionRobot.GoFromMainMenuToLevelSelection();
				aRobot.loadLevel(currentLevel);
			} else if (state == GameState.EPISODE_MENU) {
				System.out
				.println("Unexpected episode menu page, go to the last current level : "
						+ currentLevel);
				ActionRobot.GoFromMainMenuToLevelSelection();
				aRobot.loadLevel(currentLevel);
			}

		}

	}

	private double distance(Point p1, Point p2) {
		return Math
				.sqrt((double) ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y)
						* (p1.y - p2.y)));
	}

	public GameState solve()
	{

		// capture Image
		BufferedImage screenshot = ActionRobot.doScreenShot();

		// process image
		Vision vision = new Vision(screenshot);

		// find the slingshot
		Rectangle sling = vision.findSlingshotMBR();

		// confirm the slingshot
		while (sling == null && aRobot.getState() == GameState.PLAYING) {
			System.out
			.println("No slingshot detected. Please remove pop up or zoom out");
			ActionRobot.fullyZoomOut();
			screenshot = ActionRobot.doScreenShot();
			vision = new Vision(screenshot);
			sling = vision.findSlingshotMBR();
		}
        // get all the pigs
 		List<ABObject> pigs = vision.findPigsMBR();

		GameState state = aRobot.getState();

		// if there is a sling, then play, otherwise just skip.
		if (sling != null) {

			if (!pigs.isEmpty()) {

				Point releasePoint = null;
				Shot shot = new Shot();
				int dx,dy;
				{
					// random pick up a pig
					//ABObject pig = pigs.get(randomGenerator.nextInt(pigs.size()));
					ABObject pig = null;
					double min = Double.MAX_VALUE;
					double rating;
					int cnt = 0, index = cnt;
					if (writeToFile) {
						try {
							logger.info("		Number of pigs: " + pigs.size() + "; ");							
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					//if (repeat == 0 || randomGenerator.nextInt(2) == 0) {
					if (randomGenerator.nextInt(2) == 0) {
						for (ABObject p: pigs) {
							//rating = (p.getCenter().getX() + 5) * (p.getCenter().getY() + 100);
							rating = (p.getCenter().getX()) + (p.getCenter().getY()*3);
							//System.out.println(p.getCenter().getX() + " " + p.getCenter().getY());
							System.out.println(rating);
							if (rating < min) {
								min = rating;
								index = cnt;
							}
							cnt++;
						}
					} else {
						for (ABObject p: pigs) {
							//rating = (p.getCenter().getX() + 5) * (p.getCenter().getY() + 100);
							rating = (3*p.getCenter().getX()) + (p.getCenter().getY());
							//System.out.println(p.getCenter().getX() + " " + p.getCenter().getY());
							System.out.println(rating);
							if (rating < min) {
								min = rating;
								index = cnt;
							}
							cnt++;
						}
					}
					pig = pigs.get(index);
					if (writeToFile) {
						try {
							logger.info("		Chosen pig: " + index + "; ");
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					Point _tpt = pig.getCenter();// if the target is very close to before, randomly choose a
					// point near it

					if (prevTarget != null && distance(prevTarget, _tpt) < 10) {
						/*if (hit == 2) {
							_tpt = pigs.get((index+1)%pigs.size()).getCenter();
							hit = 0;
						} else {*/
							double _angle = randomGenerator.nextDouble() * Math.PI * 2;
							_tpt.x = _tpt.x + (int) (Math.cos(_angle) * 5);
							_tpt.y = _tpt.y + (int) (Math.sin(_angle) * 5);
							System.out.println("Randomly changing to " + _tpt);
						//	hit++;
						//}
					}

					prevTarget = new Point(_tpt.x, _tpt.y);

					// estimate the trajectory
					ArrayList<Point> pts = tp.estimateLaunchPoint(sling, _tpt);
					
					// do a high shot when entering a level to find an accurate velocity
					if (pts.size() >= 1) 
					{
						releasePoint = pts.get(0);
					}
					else
						if(pts.isEmpty())
						{
							System.out.println("No release point found for the target");
							System.out.println("Try a shot with 45 degree");
							releasePoint = tp.findReleasePoint(sling, Math.PI/4);
						}
					
					// Get the reference point
					Point refPoint = tp.getReferencePoint(sling);
					boolean yellow = false;
					//Calculate the tapping time according the bird type 
					if (releasePoint != null) {
						double releaseAngle = tp.getReleaseAngle(sling,
								releasePoint);
						System.out.println("Release Point: " + releasePoint);
						System.out.println("Release Angle: "
								+ Math.toDegrees(releaseAngle));
						int tapInterval = 0;
						switch (aRobot.getBirdTypeOnSling()) 
						{
						case RedBird:
							tapInterval = 0; 
							if (writeToFile) {
								try {
									logger.info("		RedBird: " + tapInterval);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
							break;               // start of trajectory
						case YellowBird:
							yellow = true;
							tapInterval = 66 + randomGenerator.nextInt(18);
							if (writeToFile) {
								try {
									logger.info("		YellowBird: " + tapInterval);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
							break; // 65-90% of the way
						case WhiteBird:
							tapInterval =  70 + randomGenerator.nextInt(20);break; // 70-90% of the way
						case BlackBird:
							tapInterval =  70 + randomGenerator.nextInt(20);break; // 70-90% of the way
						case BlueBird:
							tapInterval =  55 + randomGenerator.nextInt(20);
							if (writeToFile) {
								try {
									logger.info("		BlueBird: " + tapInterval);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
							break; // 65-85% of the way
						default:
							tapInterval =  60;
						}
						boolean reachable = true;
						int tapTime = tp.getTapTime(sling, releasePoint, _tpt, tapInterval);

						dx = (int)pts.get(0).getX() - refPoint.x;
						dy = (int)pts.get(0).getY() - refPoint.y;
						Shot shot1 = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
						shot = shot1;

						if (!ABUtil.isReachable(vision, _tpt, shot1) && !yellow) {
							dx = (int)pts.get(1).getX() - refPoint.x;
							dy = (int)pts.get(1).getY() - refPoint.y;
							System.out.println("here");
							Shot shot2 = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
							shot = shot2;

							if (!ABUtil.isReachable(vision, _tpt, shot2)) {
								shot = shot1;
								System.out.println("cancel");
								reachable = false;
							}
						}
						if (repeat != 0 && randomGenerator.nextInt(2) == 0 && _tpt != null && shot != null) {
							ABObject obj = ABUtil.getLeftObsOfTrajectory(vision, _tpt, shot);
							if (obj != null) {
								_tpt = obj.getCenter();
								_tpt.x -= obj.width;
								_tpt.y -= obj.height;
								pts = tp.estimateLaunchPoint(sling, _tpt);
								if (pts.size() > 0) {
									dx = (int)pts.get(0).getX() - refPoint.x;
									dy = (int)pts.get(0).getY() - refPoint.y;
									shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
								}
							}
						}

						if (repeat == 0 && !reachable && _tpt != null && shot != null) {
							System.out.println("repeating " + vision.findBirdsMBR().size());
							screenshot = ActionRobot.doScreenShot();
							vision = new Vision(screenshot);
							int difficulty = ABUtil.difficultyOfTrajectory(vision, _tpt, shot);
							List<ABObject> birds = vision.findBirdsMBR();
							boolean hit = false;
							if (birds.size() == 1 && birds.get(0).getType() == ABType.RedBird && difficulty > 2) {
								hit = true;
							}
							if (randomGenerator.nextInt(3) == 0 || hit) {
								List<ABObject> supporters = ABUtil.getSupporters(pig, findAllBlocks());
								if (!supporters.isEmpty()) {
									double min1 = 10000000;
									ABObject obj = null; 
									for (ABObject a: supporters) {
										if (a.x < min1) {
											min1 = a.x;
											obj = a;
										}
									}
									if (supporters.size() == 1) {
										obj = supporters.get(0);
										System.out.println("repeating - one obj");
									}
									if (obj != null) {
										if (obj.getType() != ABType.Hill) {
											_tpt = obj.getCenter();
											_tpt.x -= obj.width;
											pts = tp.estimateLaunchPoint(sling, _tpt);
											dx = (int)pts.get(0).getX() - refPoint.x;
											dy = (int)pts.get(0).getY() - refPoint.y;
											if (pts.size() > 0)
												shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
											System.out.println("repeating - left most");
										}
									}
								}
							}
						}
						if (ABUtil.isHillInBetween(vision, _tpt, shot)) {
							if (pts.size() > 1 && !yellow) {
								dx = (int)pts.get(1).getX() - refPoint.x;
								dy = (int)pts.get(1).getY() - refPoint.y;
								System.out.println("here2");
								shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
							}
						}
					}
					else
					{
						System.err.println("No Release Point Found");
						return state;
					}
					//System.out.println(ABUtil.difficultyOfTrajectory(vision, _tpt, shot));
				}

				// check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
				{
					ActionRobot.fullyZoomOut();
					screenshot = ActionRobot.doScreenShot();
					vision = new Vision(screenshot);
					Rectangle _sling = vision.findSlingshotMBR();
					if(_sling != null)
					{
						double scale_diff = Math.pow((sling.width - _sling.width),2) +  Math.pow((sling.height - _sling.height),2);
						if(scale_diff < 25)
						{
							if(dx < 0)
							{
								aRobot.cshoot(shot);
								/*if (writeToFile) {
									try {							
										logger.info("\n");
									} catch (Exception e) {
										e.printStackTrace();
									}
								}*/
								state = aRobot.getState();
								if ( state == GameState.PLAYING )
								{
									screenshot = ActionRobot.doScreenShot();
									vision = new Vision(screenshot);
									List<Point> traj = vision.findTrajPoints();
									tp.adjustTrajectory(traj, sling, releasePoint);
									firstShot = false;
								}
							}
						}
						else
							System.out.println("Scale is changed, can not execute the shot, will re-segement the image");
					}
					else
						System.out.println("no sling detected, can not execute the shot, will re-segement the image");
				}

			}

		}
		return state;
	}

	public static void main(String args[]) {

		NaiveAgent na = new NaiveAgent();
		if (args.length > 0)
			na.currentLevel = Integer.parseInt(args[0]);
		na.run();

	}

	List<ABObject> findAllBlocks() {
		// capture Image
		BufferedImage screenshot = ActionRobot.doScreenShot();

		// process image
		Vision vision = new Vision(screenshot);

		List<ABObject> allBlocks = new ArrayList<ABObject>();

		List<ABObject> objects = vision.findBlocksRealShape();
		for (ABObject obj: objects) {
			allBlocks.add(obj);
		}

		objects.clear();
		objects = vision.findTNTs();
		for (ABObject obj: objects) {
			allBlocks.add(obj);
		}

		objects.clear();
		objects = vision.findHills();
		for (ABObject obj: objects) {
			allBlocks.add(obj);
		}

		return allBlocks;
	}

	/*public float getMaxHeight(List<ABObject> objs, List<ABObject> all_logs) {
		if (objs.isEmpty())
			return 0;

		float height = 0, mheight = 0;
		for (ABObject a: objs) {
			height += a.height + getMaxHeight(ABUtil.getDependents(a, all_logs));
			if (height > mheight)
				mheight = height;
		}
		return mheight;
	}

	public void decideStructure(){ 
        List<ABObject> all_logs = findAllBlocks();
        List<ABObject> base_logs = new ArrayList<ABObject>();
        int max = 0;
        for(ABObject a : all_logs){ 
            if(a.getCenterY() >= max){
                
                if(a.getCenterY() > max){
                    base_logs.clear();
                }
                base_logs.add(a);
                max = a.getCenterY();
            }  
        }

        int min = 1000000;
        ABObject obj;
        for(ABObject a : base_logs){
     		if (a.x < min) {
 				obj = a;
 				min = a.x;
     		}   
        }

        List<ABObject> connected = ABUtil.connectedBlocks(obj, base_logs);
        float maxwidth = 0, maxheight = 0;
        float width = 0, height = 0;
        for (ABObject a: connected) {
        	width += a.width;
        }
        if (width > maxwidth) {
        	maxwidth = width;
        }
        maxheight = getMaxHeight(connected, all_logs);
        if (maxheight > maxwidth) {

        } else {

        }
    }*/

}
