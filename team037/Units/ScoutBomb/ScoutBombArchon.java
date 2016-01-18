package team037.Units.ScoutBomb;

import battlecode.common.*;
import team037.SlugNavigator;
import team037.Units.BaseUnits.BaseArchon;
import team037.Units.PacMan.PacMan;
import team037.Utilites.MapUtils;

public class ScoutBombArchon extends BaseArchon implements PacMan {




    private static String previousLastAction = "none";
    private static String lastAction = "none";
    private static final String MOVE_TO_BETTER_SPAWN = "moving to better location";
    private static final String SPAWN = "spawning";
    private static final String LET_SCOUT_HELP = "letting scout herd away zombie";
    private static final String RUN_AWAY_FROM_CROWD = "run away from crowd";
    private static final String FAST_CLOUD = "fast cloud spotted";


    private static SlugNavigator move;

    ZombieSpawnSchedule schedule;
    Direction last;

    public ScoutBombArchon(RobotController rc) {
        super(rc);
        schedule = rc.getZombieSpawnSchedule();
        move = new SlugNavigator(rc);
    }

    @Override
    public boolean act() throws GameActionException {
        healNearbyAllies();

        if (!rc.isCoreReady()) {
            rc.setIndicatorString(0, "waiting on core");
            return false;
        } else if (runAwayFromCrowd()) {
            previousLastAction = lastAction;
            lastAction = RUN_AWAY_FROM_CROWD;
        } else if (letScoutHelp()) {
            previousLastAction = lastAction;
            lastAction = MOVE_TO_BETTER_SPAWN;
        } else if (moveToBetterSpawn()) {
            previousLastAction = lastAction;
            lastAction = MOVE_TO_BETTER_SPAWN;
        } else if (spawn()) {
            previousLastAction = lastAction;
            lastAction = SPAWN;
        } else {
            rc.setIndicatorString(0, "NOTHING :(");
            return false;
        }
        rc.setIndicatorString(0, lastAction);
        return true;
    }

    /*
    ===============================
    RUN_AWAY_FROM_CROWD
    If there are lots of zombies/enemies nearby, run away!
    ===============================
     */
    private boolean runAwayFromCrowd() {
        // preconditions
        if (enemies.length > 0 || zombies.length > 3) {
            return runAway(null);
        }
        if (rc.getHealth() < .2 * type.maxHealth && (enemies.length > 0 || zombies.length > 3)) {
            return runAway(null);
        }
        return false;
    }

    /*
    ===============================
    LET_SCOUT_HELP
    There are a couple zombies nearby, if we have a unit just move away
    I was still trying to get scouts to intelligently herd away from ARCHONS
    but now with the guard, don't need to do that
    This should be changed, to move toward a guard?
        NOTE: only do this if the zombies.length <= 3
    ===============================
     */
    private boolean letScoutHelp() throws GameActionException {
        if (!(allies.length > 0 && zombies.length > 0)) {
            return false;
        }
        int nearestZ = Integer.MAX_VALUE;
        MapLocation nearestZombie = null;
        for (int i = zombies.length; --i >= 0;) {
            int dist = currentLocation.distanceSquaredTo(zombies[i].location);
            if (dist < nearestZ) {
                nearestZ = dist;
                nearestZombie = zombies[i].location;
            }
        }

        Direction away = nearestZombie.directionTo(currentLocation);
        Direction toMove = away;
        if (move.getTarget() != null) {
            toMove = MapUtils.addDirections(away, currentLocation.directionTo(move.getTarget()));
        }
        if (toMove.equals(Direction.NONE)) {
            toMove = away;
        }

        if (move.tryMove(toMove, currentLocation)) ;
        else if (move.tryMove(toMove.rotateLeft(), currentLocation)) ;
        else if (move.tryMove(toMove.rotateRight(), currentLocation)) ;
        else if (move.tryClear(toMove, currentLocation)) ;
        else if (move.tryClear(toMove.rotateLeft(), currentLocation)) ;
        else if (move.tryClear(toMove.rotateRight(), currentLocation)) ;
        else {
            return false;
        }
        return true;
    }

    /*
    ===============================
    MOVE_TO_BETTER_SPAWN
    Assuming there are no zombies nearby, move to a "better" spawn location
    //TODO: this will move right onto DENS
    // TODO: this moves direction away from enemy center of mass
              so if archons are spread evenly, it doesn't really move us away from them

    TODO: take into account closest enemy starting achons, take into account dens
    TODO: try and get into a corner?
    ===============================
     */
    public boolean moveToBetterSpawn() throws GameActionException {
        // precondition
        if (lastAction.equals(MOVE_TO_BETTER_SPAWN) && previousLastAction.equals(MOVE_TO_BETTER_SPAWN)) {
            return false;
        }

        // TODO: take into account dens here

        if (move.getTarget() == null || currentLocation.isAdjacentTo(move.getTarget())) {
            move.setTarget(getNextBestSpawnLocation());
        }

        if (move.getTarget() == null) {
            return false;
        }

        rc.setIndicatorLine(currentLocation, move.getTarget(), 0, 0, 0);
        return move.moveAndClear();
    }


    private MapLocation getNextBestSpawnLocation() throws GameActionException {
        MapLocation target;
        MapLocation halfTarget;


        int radius = (int)Math.floor(Math.sqrt(type.sensorRadiusSquared));
        int diagRadius = (int)Math.floor(Math.sqrt(type.sensorRadiusSquared) / 1.5);

        Direction toMove = enemyArchonCenterOfMass.directionTo(currentLocation);

        if (toMove.isDiagonal()) {
            target = currentLocation.add(toMove, diagRadius);
            halfTarget = currentLocation.add(toMove, diagRadius/2);
        } else {
            target = currentLocation.add(toMove, radius);
            halfTarget = currentLocation.add(toMove, radius/2);
        }

        if (rc.onTheMap(target)) {
            return target;
        }
        if (rc.onTheMap(halfTarget)) {
            return halfTarget;
        }

        if ((currentLocation.x + currentLocation.y) % 2 == 0) {
            toMove = toMove.rotateLeft().rotateLeft();
        } else {
            toMove = toMove.rotateRight().rotateRight();
        }

        if (toMove.isDiagonal()) {
            target = currentLocation.add(toMove, diagRadius);
            halfTarget = currentLocation.add(toMove, diagRadius/2);
        } else {
            target = currentLocation.add(toMove, radius);
            halfTarget = currentLocation.add(toMove, radius/2);
        }
        if (rc.onTheMap(target)) {
            return target;
        }
        if (rc.onTheMap(halfTarget)) {
            return halfTarget;
        }

        toMove = toMove.opposite();
        if (toMove.isDiagonal()) {
            target = currentLocation.add(toMove, diagRadius);
            halfTarget = currentLocation.add(toMove, diagRadius/2);
        } else {
            target = currentLocation.add(toMove, radius);
            halfTarget = currentLocation.add(toMove, radius/2);
        }
        if (rc.onTheMap(target)) {
            return target;
        }
        if (rc.onTheMap(halfTarget)) {
            return halfTarget;
        }

        return null;
    }


    /*
    ===============================
    SPAWN
    Spawn something!
    ===============================
    */
    private boolean spawn() throws GameActionException {
        int numGuards = 0;
        for (int i = allies.length; --i > 0;) {
            if (allies[i].type.equals(RobotType.GUARD)) {
                numGuards++;
            }
        }
        if (zombies.length > 0 || numGuards < Math.min(8, round / 100)) {
            if (rc.hasBuildRequirements(RobotType.GUARD)) {
                Direction toSpawn = MapUtils.getRCCanMoveDirection(this);
                if (!toSpawn.equals(Direction.NONE)) {
                    rc.build(toSpawn, RobotType.GUARD);
                    rc.setIndicatorString(1, "zombies are about, best spawn a guard");
                    return true;
                }
            }
        } else {
            if (rc.hasBuildRequirements(RobotType.SCOUT)) {
                Direction toSpawn = MapUtils.getRCCanMoveDirection(this);
                if (!toSpawn.equals(Direction.NONE)) {
                    rc.build(toSpawn, RobotType.SCOUT);
                    rc.setIndicatorString(1, "all clear, spawn a scout");
                    return true;
                }
            }
        }

        return false;
    }



    @Override
    public void sendMessages() {
        return;
    }

    @Override
    public void handleMessages() throws GameActionException {
        return;
    }

}
