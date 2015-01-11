package team158.units.com;


import team158.units.Unit;
import team158.utils.DirectionHelper;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public class Navigation {
	
	public static final boolean USE_WALL_HUGGING = true;
	public static final int MAX_TOWERS_IN_RANGE = 3; // 3 towers that can attack you at once in some maps we win on
	
	public static void moveToDestination(RobotController rc, Unit unit, MapLocation destination, boolean isAvoidAllAttack) {
		// optimization: stop avoiding current obstacle if destination changes
		if (unit.destination != null && !unit.destination.equals(destination)) { // then no longer obstacle
			unit.isAvoidingObstacle = false;
		}
		
		// new destination
		unit.destination = destination;
		if (unit.destination.equals(rc.getLocation())) {
			return;
		}
		unit.isAvoidAllAttack = isAvoidAllAttack;
		
		rc.setIndicatorString(0, unit.destination.toString());
		rc.setIndicatorString(1, Boolean.toString(unit.isAvoidingObstacle));
		
		if (USE_WALL_HUGGING) {
			wallHuggingToDestination(rc, unit);
		} else {
			greedyMoveToDestination(rc, unit);
		}
	}
	
	// wall hugging!
	public static void wallHuggingToDestination(RobotController rc, Unit unit) {
		try {
			Direction directDirection = rc.getLocation().directionTo(unit.destination);
			MapLocation directLocation = rc.getLocation().add(directDirection);
			
			if(unit.isAvoidingObstacle) { // then hug wall in counterclockwise motion
				Direction dirToObstacle = rc.getLocation().directionTo(unit.monitoredObstacle);
				Direction clockwiseDirections[] = DirectionHelper.getClockwiseDirections(dirToObstacle);
				for (Direction attemptedDir: clockwiseDirections) {
					MapLocation attemptedLocation = rc.getLocation().add(attemptedDir);
					
					// if there is a unit there blocking the hug path, move greedily
					if(isMobileUnit(rc, attemptedLocation)) {
						stopObstacleTracking(unit);
						greedyMoveToDestination(rc, unit);
						return;
					}
					// move in that direction. newLocation = attemptedLocation. Handle updating logic

					else if (isPassable(rc, unit, attemptedLocation, attemptedDir)) {
						// search for next monitored obstacle, which is one of four directions from next location
						Direction obstacleSearch[] = {Direction.NORTH, Direction.EAST,	Direction.SOUTH, Direction.WEST};
						MapLocation potNextObsts[] = new MapLocation[4];
						int numObstacles = 0;
						for (Direction dir: obstacleSearch) {
							MapLocation potentialObstacle = attemptedLocation.add(dir);
							// ignore yourself - not obstacle
							if (dir == attemptedDir.opposite()) {
								continue;
							}
							if (isObstacle(rc, unit, attemptedLocation, attemptedDir)) { // then is obstacle
								potNextObsts[numObstacles] = potentialObstacle;
								numObstacles++;
							}
						}
						
						// obstacle to monitor next is one that is farthest away from the current location
						double maxDistanceSquared = -1;
						MapLocation bestObstacle = null;
						for (int j = 0; j < numObstacles; j++) {
							double distanceSquared = rc.getLocation().distanceSquaredTo(potNextObsts[j]);
							if (distanceSquared > maxDistanceSquared) {
								maxDistanceSquared = distanceSquared;
								bestObstacle = potNextObsts[j];
							}
						}
						
						if (bestObstacle != null) {
							unit.monitoredObstacle = bestObstacle;
						} 
						
						// have traversed past a part of the obstacle if
						// going in the same direction again
						if(attemptedDir == unit.origDirection) {
							stopObstacleTracking(unit);
						}
						if (rc.canMove(attemptedDir)) {
							rc.move(attemptedDir);
						}
						return;
					}
				}
			}
			// not in state of avoiding obstacle

			else if (isPassable(rc, unit, directLocation, directDirection)) {
				rc.move(directDirection);
			// possibly found obstacle
			} else {
				if (isObstacle(rc, unit, directLocation, directDirection)) {
					startObstacleTracking(unit, directLocation, directDirection);
				} else { // otherwise, using bugging gets scary with moving obstacles
					greedyMoveToDestination(rc, unit);
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	// greedy movement
	public static void greedyMoveToDestination(RobotController rc, Unit unit) {
		try {
			MapLocation myLocation = rc.getLocation();
			int dirint = DirectionHelper.directionToInt(myLocation.directionTo(unit.destination));
			if (dirint == -1) {
				System.out.println(myLocation);
				System.out.println(unit.destination);
			}
			int offsetIndex = 0;
			int[] offsets = {0,1,-1,2,-2};
			Direction direction = DirectionHelper.directions[dirint];
			while (offsetIndex < 5 && (!rc.canMove(direction) || isObstacle(rc, unit, rc.getLocation().add(direction), direction))) {
				offsetIndex++;
				if (offsetIndex < 5) {
					direction = DirectionHelper.directions[(dirint+offsets[offsetIndex]+8)%8];
				}	
			}
			Direction moveDirection = null;
			if (offsetIndex < 5) {
				moveDirection = DirectionHelper.directions[(dirint+offsets[offsetIndex]+8)%8];
			}
			if (moveDirection != null && myLocation.add(moveDirection).distanceSquaredTo(unit.destination) <= myLocation.distanceSquaredTo(unit.destination)) {
				if (rc.canMove(moveDirection)) {
					rc.move(moveDirection);
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	////////////////////////////////////////////////////////////////////////////////////////
	// Helper methods
	public static void stopObstacleTracking(Unit unit) {
		unit.isAvoidingObstacle = false;
		unit.monitoredObstacle = null;
		unit.origDirection = null;
	}
	
	public static void startObstacleTracking(Unit unit, MapLocation obstacle, Direction collisionDirection) {
		unit.isAvoidingObstacle = true;
		unit.monitoredObstacle = obstacle;
		unit.origDirection = collisionDirection;
	}
	
	////////////////////////////////////////////////////////////////////////////////////////
	// Location analysis methods
	// - 
	
	// treat as passable or not
	public static boolean isPassable(RobotController rc, Unit unit, MapLocation location, Direction movementDirection) {
		boolean isPassable = rc.canMove(movementDirection); 
		if(unit.isAvoidAllAttack) {
			RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, rc.getTeam().opponent());
			boolean[] possibleMovesAvoidingEnemies = moveDirectionsAvoidingAttack(rc, enemies, 5);
			if(!possibleMovesAvoidingEnemies[DirectionHelper.directionToInt(movementDirection)]) {
				return false;
			}
		} else {
			isPassable = isPassable && !isNearMultipleEnemyTowers(rc, location);	
		}
		return isPassable;
	}
	
	// treat as obstacle or not to wall hug along
	public static boolean isObstacle (RobotController rc, Unit unit, MapLocation location, Direction movementDirection) throws GameActionException {
		if(unit.isAvoidAllAttack) {
			RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, rc.getTeam().opponent());
			boolean[] possibleMovesAvoidingEnemies = moveDirectionsAvoidingAttack(rc, enemies, 5);
			if (!possibleMovesAvoidingEnemies[DirectionHelper.directionToInt(movementDirection)]) {
				return true;
			}
		}
		return isStationaryBlock(rc, unit, location);
	}
	
	// get list of directions you can move while avoiding all attacks
	
	// set range to infinity -> only avoid towers and HQ
	// otherwise, also avoid units with attack range greater than rangeSquared
	
	public static boolean[] moveDirectionsAvoidingAttack(RobotController rc, RobotInfo[] enemies, int rangeSquared) {
		boolean[] possibleMovesAvoidingEnemies = {true,true,true,true,true,true,true,true,true};
		MapLocation myLocation = rc.getLocation();
		// enemies
		if (enemies.length > 0) {
			for (RobotInfo enemy : enemies) {
				if (enemy.type.attackRadiusSquared > rangeSquared) {
					if (myLocation.distanceSquaredTo(enemy.location) <= enemy.type.attackRadiusSquared) {
						possibleMovesAvoidingEnemies[8] = false;
					}
					for (Direction d : DirectionHelper.directions) {
						if (myLocation.add(d).distanceSquaredTo(enemy.location) <= enemy.type.attackRadiusSquared) {
							possibleMovesAvoidingEnemies[DirectionHelper.directionToInt(d)] = false;
						}
					}
				}
			}
		}
		// towers
		MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
		for (MapLocation l : enemyTowers) {
			int initDistance = myLocation.distanceSquaredTo(l);
			if (initDistance <= 34) {
				if (initDistance <= 24) {
					possibleMovesAvoidingEnemies[8] = false;
				}
				for (Direction d : DirectionHelper.directions) {
					if (myLocation.add(d).distanceSquaredTo(l) <= 24) {
						possibleMovesAvoidingEnemies[DirectionHelper.directionToInt(d)] = false;
					}
				}
			}
		}
		// hq
		MapLocation enemyHQ = rc.senseEnemyHQLocation();
		int initDistance = myLocation.distanceSquaredTo(enemyHQ);
		if (enemyTowers.length < 2) {
			if (initDistance <= 34) {
				if (initDistance <= 24) {
					possibleMovesAvoidingEnemies[8] = false;
				}
				for (Direction d : DirectionHelper.directions) {
					if (myLocation.add(d).distanceSquaredTo(enemyHQ) <= 24) {
						possibleMovesAvoidingEnemies[DirectionHelper.directionToInt(d)] = false;
					}
				}
			}
		}
		else {
			if (initDistance <= 52) {
				if (initDistance <= 35) {
					possibleMovesAvoidingEnemies[8] = false;
				}
				for (Direction d : DirectionHelper.directions) {
					if (myLocation.add(d).distanceSquaredTo(enemyHQ) <= 24) {
						possibleMovesAvoidingEnemies[DirectionHelper.directionToInt(d)] = false;
					}
				}
			}
		}
		return possibleMovesAvoidingEnemies;
	}
	
	// checks if location is a danger with respect to the number of towers
		// that can attack a location
		public static boolean isNearMultipleEnemyTowers(RobotController rc, MapLocation location) {
			MapLocation[] enemyTowerLocs = rc.senseEnemyTowerLocations();
			int numCloseEnemyTowers = 0;
			for (MapLocation enemyTowerLo: enemyTowerLocs) {
				if (enemyTowerLo.distanceSquaredTo(location) <= 35) {
					numCloseEnemyTowers++;
					if (numCloseEnemyTowers > MAX_TOWERS_IN_RANGE) {
						return true;
					}
				}
			}
			return false;
		}
	
	// check if the location is somewhere a bot cannot go more or less for the entire game
	public static boolean isStationaryBlock(RobotController rc, Unit unit, MapLocation potentialObstacle) throws GameActionException{
		if (unit.isAvoidAllAttack) {
			return !rc.senseTerrainTile(potentialObstacle).isTraversable() || isBuilding(rc, potentialObstacle);
		} else {
			return !rc.senseTerrainTile(potentialObstacle).isTraversable() || isBuilding(rc, potentialObstacle) || isNearMultipleEnemyTowers(rc, potentialObstacle);
		}
	}
	
	// check if there is a building at a location
	public static boolean isBuilding(RobotController rc, MapLocation potentialBuilding) throws GameActionException{
		RobotInfo robot = rc.senseRobotAtLocation(potentialBuilding);
		if (robot == null) {
			return false;
		}
		RobotType type = robot.type;
		return type == RobotType.AEROSPACELAB || 
			   type == RobotType.BARRACKS ||
			   type == RobotType.HANDWASHSTATION ||
			   type == RobotType.HELIPAD ||
			   type == RobotType.HQ ||
			   type == RobotType.MINERFACTORY ||
			   type == RobotType.SUPPLYDEPOT ||
			   type == RobotType.TANKFACTORY ||
			   type == RobotType.TECHNOLOGYINSTITUTE ||
			   type == RobotType.TOWER ||
			   type == RobotType.TRAININGFIELD;
	}
	
	// check if is unit that moves often
	// MAY NEED TO EDIT
	public static boolean isMobileUnit(RobotController rc, MapLocation potentialUnit) throws GameActionException{
		RobotInfo robot = rc.senseRobotAtLocation(potentialUnit);
		if (robot == null) {
			return false;
		}
		RobotType type = robot.type;
		return type == RobotType.BASHER ||
			   type == RobotType.BEAVER ||
			   type == RobotType.COMMANDER ||
			   type == RobotType.DRONE ||
			   type == RobotType.SOLDIER;
	}
}
