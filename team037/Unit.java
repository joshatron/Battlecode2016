package team037;

import battlecode.common.*;
import team037.Enums.Bots;
import team037.Enums.CommunicationType;
import team037.Messages.BotInfoCommunication;
import team037.Messages.Communication;
import team037.Messages.MissionCommunication;
import team037.Messages.SimpleBotInfoCommunication;
import team037.Units.BaseArchon;
import team037.Units.ScoutingScout;

public abstract class Unit
{
    public static int turnCreated;
    public static RobotController rc;
    public static int range;
    public static RobotType type;
    public static int sightRange;
    public static Team us;
    public static Team opponent;
    public static RobotInfo[] nearByEnemies;
    public static RobotInfo[] nearByAllies;
    public static RobotInfo[] enemies;
    public static RobotInfo[] allies;
    public static RobotInfo[] nearByZombies;
    public static RobotInfo[] zombies;
    public static MapLocation target;
    public static Direction[] dirs;
    public static FightMicro fightMicro;
    public static Navigator navigator;
    public static Communicator communicator;
    public static Bots nextBot = null;
    public static Bots thisBot= null;
    public static int id;
    public static int round;
    public static Communication[] communications;
    public static MapKnowledge mapKnowledge = new MapKnowledge();
    public static MapLocation start;
    public static boolean repaired;
    public static int msgsSent = 0;

    public MapLocation locationLastTurn;
    public MapLocation previousLocation;
    public MapLocation currentLocation;
    public MapLocation rushTarget;
    public MapLocation rallyPoint;

    public Unit()
    {
        // default constructor
    }

    public Unit(RobotController robotController)
    {
        rc = robotController;
        type = rc.getType();
        id = rc.getID();
        range = type.attackRadiusSquared;
        sightRange = type.sensorRadiusSquared;
        us = rc.getTeam();
        opponent = us.opponent();
        dirs = new Direction[]{Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
        Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
        fightMicro = new FightMicro(rc);
        navigator = new Navigator(rc);
        communicator = new Communicator(rc);
        turnCreated = rc.getRoundNum();
        locationLastTurn = rc.getLocation();
        previousLocation = locationLastTurn;
        currentLocation = locationLastTurn;
        start = rc.getLocation();
        repaired = false;

    }

    public boolean act() throws GameActionException {
        if (fight() || fightZombies());
        else if (carryOutAbility());
        else if (takeNextStep());

        return true;
    }

    // abstract methods that all units will need to implement
    public abstract boolean takeNextStep() throws GameActionException;
    public abstract boolean fight() throws GameActionException;
    public abstract boolean fightZombies() throws GameActionException;

    // additional methods with default behavior
    public void handleMessages() throws GameActionException
    {
        int rubbleUpdate = 0;
        rc.setIndicatorString(0, "Round num: " + rc.getRoundNum() + " Bytecodes: " + Clock.getBytecodeNum());

        communications = communicator.processCommunications();
        int[] values;
        int dist = 0;

        if (type == RobotType.SCOUT || type == RobotType.ARCHON)
            dist = Math.max(type.sensorRadiusSquared * 2, Math.min(type.sensorRadiusSquared * 7, rc.getLocation().distanceSquaredTo(start)));

        for(int k = 0; k < communications.length; k++)
        {
            switch (communications[k].opcode)
            {
                case CHANGEMISSION:
                    MissionCommunication comm = (MissionCommunication) communications[k];
                    if(comm.id == rc.getID())
                    {
                        nextBot = comm.newBType;
                    }
                    else if(comm.id == 0 && comm.bType == thisBot)
                    {
                        nextBot = comm.newBType;
                    }
                    else if(comm.id == 0 && comm.rType == rc.getType())
                    {
                        nextBot = comm.newBType;
                    }
                    break;
                case SDEN:
                    values = communications[k].getValues();
                    MapLocation den = new MapLocation(values[2], values[3]);

                    if (!mapKnowledge.denLocations.contains(den))
                    {
                        mapKnowledge.addDenLocation(den);

                        if (type == RobotType.SCOUT || type == RobotType.ARCHON && msgsSent < 20)
                        {
                            Communication communication = new SimpleBotInfoCommunication();
                            communication.setValues(new int[] {CommunicationType.toInt(CommunicationType.SDEN), values[1], values[2], values[3]});
                            communicator.sendCommunication(dist, communication);
                            msgsSent++;
                        }
                    }

                    break;
                case PARTS:
                    if (type == RobotType.SCOUT || type == RobotType.ARCHON)
                    {
                        // if we get a new msg about parts then send it out
                        values = communications[k].getValues();
                        MapLocation loc = new MapLocation(values[2], values[3]);

                        if (!mapKnowledge.partListed(loc) && msgsSent < 20)
                        {
                            mapKnowledge.addPartsAndNeutrals(loc);
                            communicator.sendCommunication(dist, communications[k]);
                            msgsSent++;
                        }

                        if (type == RobotType.ARCHON && !BaseArchon.sortedParts.contains(loc))
                        {
                            BaseArchon.sortedParts.addParts(loc, currentLocation, values[1], false);
                        }
                    }
                    break;

                case NEUTRAL:
                    if (type == RobotType.SCOUT || type == RobotType.ARCHON)
                    {
                        values = communications[k].getValues();
                        MapLocation loc = new MapLocation(values[2], values[3]);

                        if (!mapKnowledge.partListed(loc) && msgsSent < 20)
                        {
                            mapKnowledge.addPartsAndNeutrals(loc);
                            communicator.sendCommunication(dist, communications[k]);
                            msgsSent++;
                        }

                        if (type == RobotType.ARCHON && !BaseArchon.sortedParts.contains(loc))
                        {
                            BaseArchon.sortedParts.addParts(loc, currentLocation, values[1], true);
                        }
                    }
                    break;

                case SKILLED_DEN:
                case DEAD_DEN:
                    values = communications[k].getValues();
                    MapLocation spot = new MapLocation(values[2], values[3]);

                    if (mapKnowledge.denLocations.contains(spot))
                    {
                        mapKnowledge.denLocations.remove(spot);

                        if (type == RobotType.SCOUT && msgsSent < 20)
                        {
                            communicator.sendCommunication(dist, communications[k]);
                            msgsSent++;
                        }
                    }
                    break;

                case MAP_BOUNDS:
                    values = communications[k].getValues();

                    rc.setIndicatorString(1, "xMin: " + values[1] + " yMin: " + values[3] + " width: " + values[2] + " height: " + values[4]);

                    if (mapKnowledge.updateEdges(values[1], values[3], values[1] + values[2], values[3] + values[4]))
                    {
                        if (type == RobotType.SCOUT || type == RobotType.ARCHON)
                        {
                            if (msgsSent < 20)
                            {
                                communicator.sendCommunication(dist, communications[k]);
                                msgsSent++;
                            }
                        }
                        else if (type == RobotType.VIPER)
                        {
                            rushTarget = mapKnowledge.getOppositeCorner(start);
                            System.out.println("x: " + rushTarget.x + " y: " + rushTarget.y);
                        }
                    }
                    break;

                case EXPLORE_EDGE:

                    values = communications[k].getValues();

                    if (rc.getRoundNum() < 30)
                    {

                        if (type == RobotType.SCOUT)
                        {
//                        if (!mapKnowledge.edgesBeingExplored[values[2]] &&  msgsSent < 20)
//                        {
//                            communicator.sendCommunication(dist, communications[k]);
//                            msgsSent++;
//                        }

                            if (ScoutingScout.getScoutDir() == values[2] && id > values[1])
                            {
                                ScoutingScout.updateScoutDirection();
                            }
                        }


                        mapKnowledge.setEdgesBeingExplored(values[2]);
                    }

                    break;

                case EDGE_EXPLORED:
                    values = communications[k].getValues();

                    if (type == RobotType.SCOUT && !mapKnowledge.exploredEdges[values[2]] &&  msgsSent < 20)
                    {
                        communicator.sendCommunication(dist, communications[k]);
                        msgsSent++;
                    }

                    mapKnowledge.exploredEdges[values[2]] = true;
                    break;

                case ATTACK:

                    values = communications[k].getValues();

                    if (type == RobotType.VIPER)
                    {
                        System.out.println("WE got a target attack location");
                    }

                    rushTarget = new MapLocation(values[1], values[2]);

                    break;

                case SARCHON:

                    values = communications[k].getValues();
                    MapLocation archon = new MapLocation(values[2], values[3]);

                    if (!mapKnowledge.archonStartPosition.contains(archon))
                    {
                        mapKnowledge.archonStartPosition.add(archon);
                    }

                    break;

                case RALLY_POINT:
                    values = communications[k].getValues();
                    rallyPoint = new MapLocation(values[1], values[2]);

                    System.out.println("received rally_point msg x: " + values[1] + " y: " + values[2]);

                    break;

                case RUBBLE:
                    if (type != RobotType.SCOUT && rubbleUpdate < 2) {
                        rubbleUpdate++;
                        Navigation.map.updateFromComms(communications[k]);
                    }
                    break;
                case ENEMY:
                    BotInfoCommunication communication = (BotInfoCommunication) communications[k];
                    if(communication.type == RobotType.ARCHON)
                    {
                        RobotInfo bot = new RobotInfo(communication.id, opponent, RobotType.ARCHON, new MapLocation(communication.x, communication.y), 0,0,0,0,0,0,0);
                        mapKnowledge.updateEnemyArchonLocation(bot, round);
                    }
            }
        }
    }

    public void sendMessages() throws GameActionException
    {
        return;
    }

    public Unit getNewStrategy(Unit current) throws GameActionException
    {
        if(nextBot != null && nextBot != thisBot)
        {
            Unit toReturn = Bots.returnUnit(nextBot, rc);
            toReturn.thisBot = nextBot;
            toReturn.nextBot = null;
            return toReturn;
        }

        return current;
    }

    public boolean carryOutAbility() throws GameActionException
    {
        return false;
    }

    public void collectData() throws GameActionException
    {

        nearByEnemies = rc.senseNearbyRobots(range, opponent);
        nearByAllies = rc.senseNearbyRobots(range, us);

        enemies = rc.senseNearbyRobots(sightRange, opponent);
        allies = rc.senseNearbyRobots(sightRange, us);

        nearByZombies = rc.senseNearbyRobots(range, Team.ZOMBIE);
        zombies = rc.senseNearbyRobots(sightRange, Team.ZOMBIE);

        round = rc.getRoundNum();

        MapLocation newLoc = rc.getLocation();
        locationLastTurn = currentLocation;
        if (!newLoc.equals(currentLocation)) {
            previousLocation = currentLocation;
        }

        currentLocation = newLoc;
    }
}
