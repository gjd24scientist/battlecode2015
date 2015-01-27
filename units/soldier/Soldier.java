package team158.units.soldier;

import java.util.Arrays;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import team158.units.Unit;
import team158.units.com.Navigation;
import team158.utils.DirectionHelper;
import team158.com.Broadcast;

public class Soldier extends Unit {
	
	private Harasser harasser;
	boolean attackTower;
	
	public Soldier(RobotController newRC) {
		super(newRC);
		harasser = new Harasser(newRC, this);
		attackTower = false;
	}

	@Override
	protected void actions() throws GameActionException {
		int gameStage = rc.readBroadcast(Broadcast.gameStageCh);
		
		if (gameStage == Broadcast.EARLY_GAME) { // then check if progression to mid game tower attacking is necessary
			// switch to mid condition 1 : powerful enemies
			RobotInfo[] enemyAround = rc.senseNearbyRobots(RobotType.SOLDIER.sensorRadiusSquared, rc.getTeam().opponent());
			int countLauncherTank = 0;
			for (RobotInfo e : enemyAround) { 
				if (e.type == RobotType.TANK || e.type == RobotType.LAUNCHER) {
					countLauncherTank++;
				}
				if (countLauncherTank >= 2) {
					rc.broadcast(Broadcast.gameStageCh, Broadcast.MID_GAME);
					actions();
					return;
				}
			}
		}
		
		if (rc.isWeaponReady()) {
			RobotInfo[] enemies = rc.senseNearbyRobots(5, rc.getTeam().opponent());
			if (enemies.length > 0) {
				rc.attackLocation(selectTarget(enemies));
			}
        }

		if (rc.isCoreReady()) {
			computeStuff();
			if (rc.getSupplyLevel() == 0) { // then go to retrieve supply
				//soldierMoveWithMicro(ownHQ);
				//return;
			}
		
			if (gameStage == Broadcast.EARLY_GAME) { // early-stage harass
				harasser.harass();
			}
			else if (gameStage == Broadcast.MID_GAME) {
				if (groupTracker.groupID == Broadcast.soldierGroup1Ch) {
					if (Broadcast.isNotInitialized(rc, Broadcast.soldierTowerTarget1ExistsCh)) {
						harasser.harass();
					}
					else {
						boolean attackTower = rc.readBroadcast(groupTracker.groupID) == 1;
						MapLocation towerLocation = Broadcast.readLocation(rc, Broadcast.soldierTowerTargetLocation1Chs);
						if (attackTower) {
							chargeToLocation(towerLocation);
						}
						else {
							rc.setIndicatorString(1, "" + rc.senseNearbyRobots(towerLocation, 34, rc.getTeam()).length);
							if (rc.senseNearbyRobots(towerLocation, 34, rc.getTeam()).length >= 11) {
								rc.broadcast(groupTracker.groupID, 1);
								chargeToLocation(towerLocation);
							}
							else {
								soldierMoveWithMicro(towerLocation);
							}
						}
					}
				}
				else if (groupTracker.groupID == Broadcast.soldierGroup2Ch) {
					if (Broadcast.isNotInitialized(rc, Broadcast.soldierTowerTarget2ExistsCh)) {
						harasser.harass();
					}
					else {
						boolean attackTower = rc.readBroadcast(groupTracker.groupID) == 1;
						MapLocation towerLocation = Broadcast.readLocation(rc, Broadcast.soldierTowerTargetLocation2Chs);
						if (attackTower) {
							chargeToLocation(towerLocation);
						}
						else {
							if (rc.senseNearbyRobots(towerLocation, 34, rc.getTeam()).length >= 11) {
								rc.broadcast(groupTracker.groupID, 1);
								chargeToLocation(towerLocation);
							}
							else {
								soldierMoveWithMicro(towerLocation);
							}
						}
					}
				}
			}
			else if (gameStage == Broadcast.LATE_GAME) {
				harasser.harass();
			}
		}
	}
	
	protected void chargeToLocation(MapLocation target) throws GameActionException {
		Team opponentTeam = rc.getTeam().opponent();
		// 25 covers the edge case with tanks if friendly units have vision
		RobotInfo[] enemies = rc.senseNearbyRobots(25, opponentTeam);
		if (enemies.length == 0) {
			if (target == null) {
				return;
			}
			if (rc.getLocation().distanceSquaredTo(target) <= 52) {
				navigation.moveToDestination(target, Navigation.AVOID_NOTHING);
			}
			else {
				navigation.moveToDestination(target, Navigation.AVOID_ENEMY_ATTACK_BUILDINGS);
			}
			return;
		}

		if (safeSpots[8] == 2) {
			navigation.moveToDestination(target, Navigation.AVOID_NOTHING);
			return;
		}
		
		// Approach enemy
		int myAttackRange = rc.getType().attackRadiusSquared;
		RobotInfo[] attackableRobots = rc.senseNearbyRobots(myAttackRange, opponentTeam);
		if (attackableRobots.length == 0) {
			for (RobotInfo r : enemies) {
				// approach enemies that outrange us
				if (r.type.attackRadiusSquared > myAttackRange) {
					navigation.moveToDestination(r.location, Navigation.AVOID_NOTHING);
					return;
				}
			}
			navigation.moveToDestination(target, Navigation.AVOID_NOTHING);
			return;
		}
		
		// Take less damage
		else {
			int bestDirection = 8;
			double bestDamage = 999999;
			for (int i = 0; i < 8; i++) {
				if (rc.canMove(DirectionHelper.directions[i]) && inRange[i] && damages[i] <= bestDamage) {
					bestDirection = i;
					bestDamage = damages[i];
				}
			}
			if (bestDamage < damages[8]) {
				navigation.stopObstacleTracking();
				rc.move(DirectionHelper.directions[bestDirection]);
			}
		}
	}
	
	// ONLY WORKS IF SUPPLIED
	protected void soldierMoveWithMicro(MapLocation target) throws GameActionException {
		Team opponentTeam = rc.getTeam().opponent();
		// 25 covers the edge case with tanks if friendly units have vision
		RobotInfo[] enemies = rc.senseNearbyRobots(25, opponentTeam);
		// no enemies in range
		if (enemies.length == 0) { // then move towards destination safely
			if (target != null) {
				navigation.moveToDestination(target, Navigation.AVOID_ENEMY_ATTACK_BUILDINGS);
			}
			return;
		}

		MapLocation myLocation = rc.getLocation();
		int myAttackRange = rc.getType().attackRadiusSquared;

		rc.setIndicatorString(0, Arrays.toString(damages));
		if (safeSpots[8] == 2) {
			// Check if almost in range of an enemy
			boolean almostInRange = false;
			for (RobotInfo r : enemies) {
				if (myLocation.distanceSquaredTo(r.location) <= rangeFunction(r.type.attackRadiusSquared)) {
					almostInRange = true;
					if (r.type.attackRadiusSquared <= myAttackRange) {
						Direction potentialMove = myLocation.directionTo(r.location);
						if (!rc.canMove(potentialMove)) {
							continue;
						}
						int dirInt = DirectionHelper.directionToInt(potentialMove);
						// double newWeaponDelay = rc.getWeaponDelay() + (rc.senseTerrainTile(myLocation.add(potentialMove)) == TerrainTile.VOID ? 1 : rc.getType().loadingDelay - 1);
						if (damages[dirInt] < 8 && rc.getWeaponDelay() < 2) {
							navigation.stopObstacleTracking();
							rc.move(potentialMove);
							return;
						}
					}
				}
			}
			if (almostInRange) {
				return;
			}
			
			// Get almost in range of an enemy, or get closer to the enemies
			boolean[] reasonableMoves = new boolean[8];
			for (int i = 0; i < 8; i++) {
				Direction d = DirectionHelper.directions[i];
				MapLocation potentialLocation = myLocation.add(d);		
				// if cannot move in direction, do not consider
				if (!rc.canMove(d) || safeSpots[i] != 2 || rc.getCoreDelay() + (i%2 == 1 ? 2.8 : 2) > 3) { // also avoids getting too close to missiles
					continue;
				}
				
				reasonableMoves[i] = true;
				boolean isGoodMove = false;
				
				for (RobotInfo r : enemies) {
					if (r.type == RobotType.COMMANDER) {
						if (potentialLocation.distanceSquaredTo(r.location) <= 20) {
							isGoodMove = false;
							reasonableMoves[i] = false;
							break;
						}
					}
					else {
						int potentialLocationDistance = potentialLocation.distanceSquaredTo(r.location);
						// Assume enemy can move immediately
						if (potentialLocationDistance <= rangeFunction(r.type.attackRadiusSquared)) {
							isGoodMove = true;
						}
					}
				}
				
				if (isGoodMove) {
					navigation.stopObstacleTracking();
					rc.move(d);
					return;
				}
			}
			Direction dirToEnemy = rc.getLocation().directionTo(enemies[0].location);
			Direction moveDirection = dirToEnemy;
			if (reasonableMoves[DirectionHelper.directionToInt(moveDirection)]) {
				navigation.stopObstacleTracking();
				rc.move(moveDirection);
			    return;
			}
			
			moveDirection = dirToEnemy.rotateLeft();
			if (reasonableMoves[DirectionHelper.directionToInt(moveDirection)]) {
				navigation.stopObstacleTracking();
			    rc.move(moveDirection);
			    return;
			}
			
			moveDirection = dirToEnemy.rotateRight();
			if (reasonableMoves[DirectionHelper.directionToInt(moveDirection)]) {
				navigation.stopObstacleTracking();
			    rc.move(moveDirection);
			    return;
			}
		}
		// Take less damage
		else {
			int bestDirection = 8;
			double bestDamage = 999999;
			for (int i = 0; i < 8; i++) {
				if (rc.canMove(DirectionHelper.directions[i]) && damages[i] <= bestDamage && damages[i] + i%2 <= bestDamage) {
					bestDirection = i;
					bestDamage = damages[i] + i%2;
				}
			}
			if (bestDamage < damages[8]) {
				navigation.stopObstacleTracking();
				rc.move(DirectionHelper.directions[bestDirection]);
			}
			else if (bestDamage == damages[8] && !inRange[8]) {
				navigation.moveToDestination(ownHQ, Navigation.AVOID_NOTHING);
			}
		}
	}
	
	// soldier-specific
	private int rangeFunction(int range) {
		switch (range) {
		case 0: case 2: case 5: case 8: return 13; // also includes buildings
		case 10: return 20;
		case 15: return 24; // doesn't work perfectly for 4^2 + 3^2 case
		default: return 0;
		}
	}
}