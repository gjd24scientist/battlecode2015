package team158.units;

import team158.utils.Broadcast;
import battlecode.common.*;

public class Tank extends Unit {
	
	public Tank(RobotController newRC) {
		super(newRC);
	}
	
	@Override
	protected void actions() throws GameActionException {

		if (rc.isWeaponReady()) {
			RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.TANK.attackRadiusSquared, rc.getTeam().opponent());
			if (enemies.length > 0) {
				rc.attackLocation(selectTarget(enemies));
			}
        }

		if (rc.isCoreReady()) {
			MapLocation target = null;
			int approachStrategy = 0;
			if (groupTracker.isGrouped()) {
				boolean hasHQCommand = rc.readBroadcast(groupTracker.groupID) == 1;
				if (hasHQCommand) {
					target = Broadcast.readLocation(rc, Broadcast.groupTargetLocationChs);
					approachStrategy = 0;
				}
			}
			else {
				target = groupTracker.getRallyPoint();
				approachStrategy = 1;
			}
			moveToLocationWithMicro(target, approachStrategy);
		}	
	}
}