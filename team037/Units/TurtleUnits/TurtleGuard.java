package team037.Units.TurtleUnits;

import battlecode.common.*;
import team037.DataStructures.RobotTypeTracker;
import team037.Utilites.MapUtils;
import team037.Units.BaseUnits.BaseGaurd;
import team037.Utilites.FightMicroUtilites;
import team037.Unit;

public class TurtleGuard extends BaseGaurd
{
    private static int turnsArrivedLoc = -1;
    private static boolean arrived = false;
    private boolean chasingEnemies = false;
    private boolean healing = false;
    private boolean updatedTurtleSpot = false;
    private int[] enemySightings = new int[8];
    public static RobotTypeTracker robotTypeTracker;

    public TurtleGuard(RobotController rc)
    {
        super(rc);
        turtlePoint = MapUtils.getTurtleSpot2(alliedArchonStartLocs, enemyArchonStartLocs);
        robotTypeTracker = new RobotTypeTracker(RobotType.TURRET, rc);
    }

    @Override
    public boolean updateTarget() throws GameActionException
    {
        MapLocation target = navigator.getTarget();
        if (zombies.length > 0 || enemies.length > 0) return true;
        if (target == null) return true;
        if ((currentLocation.equals(target) || currentLocation.isAdjacentTo(target)) && (rc.getRoundNum() - turnsArrivedLoc) > 2) return true;
        if (rc.canSense(target) && !rc.onTheMap(target)) return true;
        if (rc.getHealth() <= 25) return true;

        return false;
    }

    @Override
    public boolean carryOutAbility() throws GameActionException
    {
        if (!rc.isCoreReady()) return false;
        if (turnsArrivedLoc == -1) return false;
        if (zombies.length > 0 || enemies.length > 0) return false;

        for (int i = dirs.length; --i>=0; )
        {
            MapLocation next = currentLocation.add(dirs[i]);
            if (rc.canSense(next) && rc.senseRubble(next) > 0)
            {
                rc.clearRubble(dirs[i]);
                return true;
            }
        }
        return false;
    }

    @Override
    public MapLocation getNextSpot() throws GameActionException
    {
        if (healing || rc.getHealth() <= 25)
        {
            healing = true;
            return turtlePoint.add(currentLocation.directionTo(turtlePoint), 3);
        }

        if (zombies.length > 0)
        {
            chasingEnemies = true;
            return MapUtils.closestUnit(zombies, currentLocation);
        }

        if (enemies.length > 0)
        {
            chasingEnemies = true;
            return MapUtils.closestUnit(enemies, currentLocation);
        }
        
        if (turnsArrivedLoc == -1 || chasingEnemies)
        {
            chasingEnemies = false;
            arrived = false;
            return turtlePoint.add(currentLocation.directionTo(turtlePoint), 3);
        }

        MapLocation defensePoint = getDefensePoint();

        if (defensePoint != null)
        {
            System.out.println("using defense point");
            rc.setIndicatorString(2, "Defense point x: " + defensePoint.x + " y: " + defensePoint.y + " round: " + rc.getRoundNum());
            return defensePoint;
        }

        for (int i = dirs.length; --i>=0; )
        {
            MapLocation possible = currentLocation.add(dirs[i], 3);

            if (rc.canSenseLocation(possible) && !rc.onTheMap(possible)) continue;

            if (possible.distanceSquaredTo(turtlePoint) <= 49)
            {
                arrived = false;
                return possible;
            }
        }

        for (int i = dirs.length; --i>=0; )
        {
            MapLocation possible = currentLocation.add(dirs[i], 6);

            if (rc.canSenseLocation(possible) && !rc.onTheMap(possible)) continue;

            if (possible.distanceSquaredTo(turtlePoint) <= 49)
            {
                arrived = false;
                return possible;
            }
        }

        for (int i = dirs.length; --i>=0; )
        {
            MapLocation possible = currentLocation.add(dirs[i], 10);

            if (rc.canSenseLocation(possible) && !rc.onTheMap(possible)) continue;

            if (possible.distanceSquaredTo(turtlePoint) <= 49)
            {
                arrived = false;
                return possible;
            }
        }

        return null;
    }

    /**
     * This method returns the side that we have seen the most enemies on
     *
     * @return
     */
    private MapLocation getDefensePoint()
    {
        int highestCount = 0;
        Direction direction = null;

        for (int i = enemySightings.length; --i>=0; )
        {
            if (enemySightings[i] > highestCount)
            {
                highestCount = enemySightings[i];
                direction = dirs[i];
            }
        }

        if (direction == null)
        {
            return null;
        }

        return turtlePoint.add(direction, 3);
    }

    @Override
    public void collectData() throws GameActionException
    {
        super.collectData();

        if (!arrived && (rc.canSense(turtlePoint)))
        {
            turnsArrivedLoc = rc.getRoundNum();
            arrived = true;
        }

        if (healing && rc.getHealth() > 100)
        {
            healing = false;
        }

        robotTypeTracker.scanForRobots(allies);

        if (rallyPoint != null)
        {
            rc.setIndicatorLine(currentLocation, rallyPoint, 0, 0, 255);
            rc.setIndicatorString(1, "Going to new rally point x: " + rallyPoint.x + " y: " + rallyPoint.y + " round: " + rc.getRoundNum());
            if (navigator.getTarget() != null)
            {
                rc.setIndicatorString(2, "NAvigation target x: " + navigator.getTarget().x + " y: " + navigator.getTarget().y + " round: " + rc.getRoundNum());
            }
            turnsArrivedLoc = -1;
            turtlePoint = rallyPoint;
            enemySightings = new int[8];
        }

        if (turtlePoint != null)
        {
            for (int i = zombies.length; --i>=0; )
            {
                MapLocation zombie = zombies[i].location;

                Direction dir = turtlePoint.directionTo(zombie);

                for (int j = dirs.length; --j >= 0; )
                {
                    if (dirs[j].equals(dir))
                    {
//                        System.out.println("set zombie to enemySightings: " + enemySightings[j]);
                        enemySightings[j]++;
                    }
                }
            }
        }

        if (!FightMicroUtilites.offensiveEnemies(enemies) && !FightMicroUtilites.offensiveEnemies(zombies))
        {
            for (int i = allies.length; --i>=0; )
            {
                // if we see a turret that has just shot then we should go support it
                if (allies[i].type == RobotType.TURRET && allies[i].weaponDelay > 1)
                {
                    MapLocation ally = allies[i].location;
                    navigator.setTarget(ally.add(currentLocation.directionTo(ally)));
                    break;
                }
            }
        }
    }

    // additional methods with default behavior
    public void handleMessages() throws GameActionException
    {
        communications = communicator.processCommunications();
        for(int k = communications.length; --k >= 0;)
        {
            switch(communications[k].opcode)
            {
                case SARCHON:
                case SENEMY:
                case SZOMBIE:
                case SDEN:
                case SPARTS:
                    int values[] = communications[k].getValues();

                    if (values.length >= 6)
                    {
                        int id = values[3];

                        if (RobotTypeTracker.contains(id))
                        {
                            int x = values[4];
                            int y = values[5];

                            MapLocation target = new MapLocation(x,y);
                            navigator.setTarget(target);
                        }
                    }

                    break;

                case CHANGEMISSION:
                    if(missionComs)
                    {
                        interpretMissionChange(communications[k]);
                    }
                    break;
                case ATTACK:
                case RALLY_POINT:
                    if(archonComs)
                    {
                        interpretLocFromArchon(communications[k]);
                    }
                    break;
                case ARCHON_DISTRESS:
                    if(archonDistressComs)
                    {
                        interpretDistressFromArchon(communications[k]);
                    }
                    break;
            }
        }
    }
}
