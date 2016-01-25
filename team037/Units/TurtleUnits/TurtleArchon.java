package team037.Units.TurtleUnits;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;
import team037.Enums.CommunicationType;
import team037.Enums.Strategies;
import team037.Messages.AttackCommunication;
import team037.Messages.Communication;
import team037.Messages.SimpleBotInfoCommunication;
import team037.RobotPlayer;
import team037.Units.PacMan.PacMan;
import team037.Utilites.FightMicroUtilites;
import team037.Utilites.MapUtils;
import battlecode.common.*;
import team037.Enums.Bots;
import team037.Units.BaseUnits.BaseArchon;

public class TurtleArchon extends BaseArchon implements PacMan
{
    private boolean reachedTurtleSpot = false;
    private boolean updatedTurtleSpot = false;
    private MapLocation origionalTurtleSpot;
    private boolean hiding = false;
    private int updateRound = 0;
    private int index = 0;
    private int ArchonDist;
    private boolean offensiveEnemies = false;
    private boolean offensiveAllies = false;
    private boolean underAttack = false;
    private static int lastUnderAttack = 0;
    private int lastZombieSighting = 0;
    private int lastEnemieSighting = 0;
    private static int adjacentTurrets = 0;

    public TurtleArchon(RobotController rc)
    {
        super(rc);
        turtlePoint = MapUtils.getTurtleSpot2(alliedArchonStartLocs, enemyArchonStartLocs);
        origionalTurtleSpot = new MapLocation(turtlePoint.x, turtlePoint.y);
        rc.setIndicatorString(0, "Turtle archon");

        for (int i = alliedArchonStartLocs.length; --i>=0;)
        {
            if (alliedArchonStartLocs[i].equals(currentLocation))
            {
                index = i;
                break;
            }
        }

        lastZombieSighting = rc.getRoundNum();

        try
        {
            ArchonDist = MapUtils.getCenterOfMass(alliedArchonStartLocs).distanceSquaredTo(MapUtils.getCenterOfMass(enemyArchonStartLocs));
        } catch (Exception e) {e.printStackTrace();}
    }

    @Override
    public boolean updateTarget() throws GameActionException
    {
        MapLocation target = navigator.getTarget();

        if (target == null) return true;
        if (FightMicroUtilites.offensiveEnemies(enemies) || FightMicroUtilites.offensiveEnemies(zombies)) return true;
        if (target.equals(turtlePoint) && currentLocation.distanceSquaredTo(target) <= 2) return true;
        if (currentLocation.equals(target)) return true;
        if (!target.equals(turtlePoint) && rc.canSenseLocation(target) && rc.senseParts(target) == 0 && (rc.senseRobotAtLocation(target) == null || rc.senseRobotAtLocation(target).team != Team.NEUTRAL)) return true;
        if (zombieTracker.getNextZombieRound() - rc.getRoundNum() <= 25) return true;

        return false;
    }

    @Override
    public boolean takeNextStep() throws GameActionException
    {
        if (underAttack) {
            return navigator.takeNextStep();
        }
        // if we are in a safe position, hold
        if (adjacentTurrets == 4) {
            return false;
        }
        // if it's later in the game and we have a lot of parts, spend that money!
        if (round > 500 && rc.getTeamParts() > 200) {
            return false;
        }
        return navigator.takeNextStep();
    }

    @Override
    public void collectData() throws GameActionException
    {
        super.collectData();

        boolean offensiveZombies = FightMicroUtilites.offensiveEnemies(zombies);
        offensiveEnemies = FightMicroUtilites.offensiveEnemies(enemies);

        int round = rc.getRoundNum();

        if (offensiveZombies)
        {
            lastZombieSighting = round;
        }

        if (offensiveEnemies)
        {
            lastEnemieSighting = round;
        }

        offensiveEnemies = offensiveEnemies || offensiveZombies;
        offensiveAllies = FightMicroUtilites.offensiveEnemies(allies);

        adjacentTurrets = 0;
        RobotInfo [] toCheck = allies;
        if (allies.length > 10) {
            toCheck = rc.senseNearbyRobots(1, us);
        }
        for (int i = 0; --i >= 0;) {
            if (toCheck[i].location.distanceSquaredTo(currentLocation) == 1)
                if (toCheck[i].type.equals(RobotType.TURRET) || toCheck[i].type.equals(RobotType.TTM)) {
                    adjacentTurrets += 1;
                }
        }

        underAttack = underAttack();
        if (underAttack) {
            lastUnderAttack = round;
        }

        if (rallyPoint != null)
        {
            turtlePoint = rallyPoint;
            updateRound = rc.getRoundNum();
        }

        if (RobotPlayer.strategy.equals(Strategies.DYNAMIC_TURTLE) && (round - updateRound) > 100)
        {
            int closestDen = 99999;
            MapLocation den = null;

            if (mapKnowledge.dens.hasLocations()) {
                for (int i = mapKnowledge.dens.length; --i >= 0; ) {
                    if (mapKnowledge.dens.array[i] != null) {
                        MapLocation currentDen = mapKnowledge.dens.array[i];

                        if (rc.canSenseLocation(currentDen) && (rc.senseRobotAtLocation(currentDen) == null || rc.senseRobotAtLocation(currentDen).team != Team.ZOMBIE)) {
                            mapKnowledge.dens.remove(currentDen);

                            if (!underAttack) {
                                Communication communication = new SimpleBotInfoCommunication();
                                communication.opcode = CommunicationType.DEAD_DEN;
                                communication.setValues(new int[]{CommunicationType.toInt(CommunicationType.DEAD_DEN), 0, currentDen.x, currentDen.y});
                                communicator.sendCommunication(400, communication);
                            }
                        } else {
                            int currentDist = currentDen.distanceSquaredTo(turtlePoint);

                            if (currentDist < closestDen) {
                                closestDen = currentDist;
                                den = currentDen;
                            }
                        }
                    }
                }
                if (den != null && den.distanceSquaredTo(turtlePoint) > 20)
                {
                    if (!underAttack)
                    {
                        rc.setIndicatorString(2, "We have a den location!!! x: " + den.x + " y: " + den.y + " round " + rc.getRoundNum());
                        rc.setIndicatorLine(currentLocation, den, 0, 0, 0);
                        turtlePoint = den.add(den.directionTo(turtlePoint), 3);
                        Communication newRallyPoint = new AttackCommunication();
                        newRallyPoint.setValues(new int[]{CommunicationType.toInt(CommunicationType.RALLY_POINT), turtlePoint.x, turtlePoint.y});
                        communicator.sendCommunication(1600, newRallyPoint);
                    }

                    hiding = false;
                }
            }
        }
        else
        {
            if ((round - updateRound) < 100)
            {
            }
            else if (round < 300)
            {
            }
            else if (round % 5 != index)
            {
            }
            else if (zombieTracker.getNextZombieRound() - rc.getRoundNum() < 25)
            {
            }
            else
            {
                if (round % 100 == index)
                {
                    Communication newRallyPoint = new AttackCommunication();
                    newRallyPoint.setValues(new int[]{CommunicationType.toInt(CommunicationType.RALLY_POINT), turtlePoint.x, turtlePoint.y});
                    communicator.sendCommunication(1600, newRallyPoint);
                }


                rc.setIndicatorString(1, "len: " + mapKnowledge.dens.length);

                int closestDen = 99999;
                MapLocation den = null;

                if (mapKnowledge.dens.hasLocations())
                {
                    for (int i = mapKnowledge.dens.length; --i >= 0; )
                    {
                        if (mapKnowledge.dens.array[i] != null)
                        {
                            MapLocation currentDen = mapKnowledge.dens.array[i];

                            if (rc.canSenseLocation(currentDen) && (rc.senseRobotAtLocation(currentDen) == null || rc.senseRobotAtLocation(currentDen).team != Team.ZOMBIE))
                            {
                                mapKnowledge.dens.remove(currentDen);

                                if (!underAttack)
                                {
                                    Communication communication = new SimpleBotInfoCommunication();
                                    communication.opcode = CommunicationType.DEAD_DEN;
                                    communication.setValues(new int[] {CommunicationType.toInt(CommunicationType.DEAD_DEN), 0, currentDen.x, currentDen.y});
                                    communicator.sendCommunication(400, communication);
                                }
                            }
                            else
                            {
                                int currentDist = currentDen.distanceSquaredTo(turtlePoint);

                                if (currentDist < closestDen)
                                {
                                    closestDen = currentDist;
                                    den = currentDen;
                                }
                            }
                        }
                    }
                }

                if (den != null && den.distanceSquaredTo(turtlePoint) > 20 && den.distanceSquaredTo(turtlePoint) < 400 && rc.getRoundNum() < 1000)
                {
                    if (!underAttack)
                    {
                        rc.setIndicatorString(2, "We have a den location!!! x: " + den.x + " y: " + den.y + " round " + rc.getRoundNum());
                        rc.setIndicatorLine(currentLocation, den, 0, 0, 0);
                        turtlePoint = den.add(den.directionTo(turtlePoint), 3);
                        Communication newRallyPoint = new AttackCommunication();
                        newRallyPoint.setValues(new int[]{CommunicationType.toInt(CommunicationType.RALLY_POINT), turtlePoint.x, turtlePoint.y});
                        communicator.sendCommunication(1600, newRallyPoint);
                    }

                    hiding = false;
                }
                else
                {
                    MapLocation concentration = null; //sortedParts.getMassivConcentration();
                    if (concentration != null)
                    {
                        if (!underAttack)
                        {
                            turtlePoint = concentration;
                            Communication newRallyPoint = new AttackCommunication();
                            newRallyPoint.setValues(new int[]{CommunicationType.toInt(CommunicationType.RALLY_POINT), concentration.x, concentration.y});
                            communicator.sendCommunication(1600, newRallyPoint);
                        }
                    }
                    else if (!hiding || round % 50 == index)
                    {
                        int leftX = mapKnowledge.minX;
                        int rightX = mapKnowledge.maxX;
                        int topY = mapKnowledge.minY;
                        int bottomY = mapKnowledge.maxY;

                        int currentX = turtlePoint.x;
                        int currentY = turtlePoint.y;

                        int distToTopLeft = (leftX - currentX) * (leftX - currentX) + (topY - currentY) * (topY - currentY);
                        int distToBottonLeft = (leftX - currentX) * (leftX - currentX) + (bottomY - currentY) * (bottomY - currentY);
                        int distToTopRight = (rightX - currentX) * (rightX - currentX) + (topY - currentY) * (topY - currentY);
                        int distToBottonRight = (rightX - currentX) * (rightX - currentX) + (bottomY - currentY) * (bottomY - currentY);

                        // go left
                        if (distToTopLeft < distToTopRight)
                        {
                            if (distToTopLeft < distToBottonLeft)
                            {
                                turtlePoint = new MapLocation(leftX, topY);
                                turtlePoint = turtlePoint.add(turtlePoint.directionTo(new MapLocation(rightX, bottomY)), 5);
                            }
                            else
                            {
                                turtlePoint = new MapLocation(leftX, bottomY);
                                turtlePoint = turtlePoint.add(turtlePoint.directionTo(new MapLocation(rightX, topY)), 5);
                            }
                        }
                        // go right
                        else
                        {
                            if (distToTopRight < distToBottonRight)
                            {
                                turtlePoint = new MapLocation(rightX, topY);
                                turtlePoint = turtlePoint.add(turtlePoint.directionTo(new MapLocation(leftX, bottomY)), 5);
                            }
                            else
                            {
                                turtlePoint = new MapLocation(rightX, bottomY);
                                turtlePoint = turtlePoint.add(turtlePoint.directionTo(new MapLocation(leftX, topY)), 5);
                            }
                        }

                        if (!underAttack)
                        {
                            Communication newRallyPoint = new AttackCommunication();
                            newRallyPoint.setValues(new int[]{CommunicationType.toInt(CommunicationType.RALLY_POINT), turtlePoint.x, turtlePoint.y});
                            communicator.sendCommunication(1600, newRallyPoint);
                        }

                        // no need to keep updating this
                        hiding = true;
                    }
                }
            }
        }



        if (hiding)
        {
            rc.setIndicatorLine(currentLocation, turtlePoint, 0, 255, 0);
        }
    }

    public boolean underAttack()
    {
        if (!offensiveEnemies) return false;
        if (allies.length == 0) return true;
        if (!offensiveAllies) return true;
        if ((enemies.length + zombies.length + 2) > allies.length) return true;

        return false;
    }

    @Override
    public void handleMessages() throws GameActionException
    {
        if (!underAttack)
        {
            super.handleMessages();
        }
    }

    @Override
    public MapLocation getNextSpot() throws GameActionException
    {
        if (index == 0) return turtlePoint;
        int round = rc.getRoundNum();
        int nextZombieRound = zombieTracker.getNextZombieRound();
        if (nextZombieRound - round <= 25) return turtlePoint;
        if (FightMicroUtilites.offensiveEnemies(enemies) || FightMicroUtilites.offensiveEnemies(zombies)) return turtlePoint;
        if (!reachedTurtleSpot && currentLocation.distanceSquaredTo(turtlePoint) > 10) return turtlePoint;
        if (!reachedTurtleSpot) reachedTurtleSpot = true;

        MapLocation bestParts = getNextPartLocation();

        if (bestParts == null) return turtlePoint;

        int turtleDist = turtlePoint.distanceSquaredTo(bestParts);

        // if we have a lot of parts, don't go looking for more!
        if (rc.getTeamParts() > 1000) return turtlePoint;
        if (turtleDist > (1600 - (round/2))) return turtlePoint;
        if (Math.sqrt(turtleDist) > (nextZombieRound - round)) return turtlePoint;

        rc.setIndicatorString(1, "BestParts x: " + bestParts.x + " y: " + bestParts.y);

        return bestParts;
    }

    @Override
    public boolean fight() throws GameActionException
    {
        if (!FightMicroUtilites.offensiveEnemies(enemies)) return false;
        if (turtlePoint != null) navigator.setTarget(turtlePoint);

        return runAway(null);
    }

    @Override
    public boolean fightZombies() throws GameActionException
    {
        if (!FightMicroUtilites.offensiveEnemies(zombies)) return false;
        if (turtlePoint != null) navigator.setTarget(turtlePoint);

        return runAway(null);
    }

    @Override
    public void sendInitialMessages(Direction dir) throws GameActionException
    {
        if (!underAttack)
        {
            super.sendInitialMessages(dir);
            Communication newRallyPoint = new AttackCommunication();
            newRallyPoint.setValues(new int[]{CommunicationType.toInt(CommunicationType.RALLY_POINT), turtlePoint.x, turtlePoint.y});
            communicator.sendCommunication(2, newRallyPoint);
        }
    }

    @Override
    public Bots getDefaultBotTypes(RobotType type)
    {
        switch (type)
        {
            case SOLDIER:
                return Bots.TURTLESOLDIER;
            case TURRET:
                return Bots.TURTLETURRET;
            case TTM:
                return Bots.TURTLETURRET;
            case GUARD:
                return Bots.TURTLEGUARD;
            case SCOUT:
                return Bots.SCOUTBOMBSCOUT;
            case ARCHON:
                return Bots.TURTLEARCHON;
            case VIPER:
                return Bots.RUSHINGVIPER;
            default:
                return Bots.TURTLESOLDIER;
        }
    }

    @Override
    public boolean carryOutAbility() throws GameActionException
    {
        // preconditions
        if ((FightMicroUtilites.offensiveEnemies(enemies) || FightMicroUtilites.offensiveEnemies(zombies)) && ((enemies.length + zombies.length) > allies.length)) return false;
        if (currentLocation.distanceSquaredTo(turtlePoint) >= 100 && rc.getTeamParts() < 500) return false;

        return buildNextUnit();
    }

    @Override
    public Bots changeBuildOrder(Bots nextBot)
    {
        rc.setIndicatorString(2, "Zombies: " + zombieTracker.getNextZombieRound());
        int round = rc.getRoundNum();

        if (round - lastZombieSighting < 300 && round - lastEnemieSighting > 25)
        {
            if (zombieTracker.getNextZombieRoundStrength() < 5 &&  zombieTracker.getNextZombieRound() - round < 30)
            {
                nextType = RobotType.SCOUT;
                return Bots.SCOUTBOMBSCOUT;
            }
            else if (zombieTracker.getNextZombieRound() - round < 50)
            {
                nextType = RobotType.SCOUT;
                return Bots.SCOUTBOMBSCOUT;
            }
            else if (ArchonDist > 2500 && zombieTracker.getNextZombieRound() - round < 100)
            {
                nextType = RobotType.SCOUT;
                return Bots.SCOUTBOMBSCOUT;
            }
        }

        if (rc.getTeamParts() > 300)
        {
            return Bots.TURTLETURRET;
        }

        // late game if we aren't under attack spawn a lot of scout bombs
        if (round > 2000 && lastUnderAttack < 1500) {
            return Bots.SCOUTBOMBSCOUT;
        }

        // and a some vipers to make them go "BRAAAAAAAIIIIIIINNNNNS"
        if (round > 2500) {
            return Bots.SCOUTBOMBVIPER;
        }

        if (round > 1000)
        {
            int soldierCount = 0;
            int guardCount = 0;

            for (int i = allies.length; --i>=0; )
            {
                switch (allies[i].type)
                {
                    case SOLDIER:
                        soldierCount++;
                        break;
                    case GUARD:
                        guardCount++;
                        break;
                }
            }

            if (soldierCount < 1)
            {
                return Bots.TURTLESOLDIER;
            }
            else if (guardCount < 1)
            {
                return Bots.TURTLEGUARD;
            }
        }


        return nextBot;
    }
}
