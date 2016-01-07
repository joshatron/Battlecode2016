package team037.Units;

import team037.Unit;
import battlecode.common.*;

public class BaseSoldier extends Unit
{
    public BaseSoldier(RobotController rc)
    {
        super(rc);
    }

    public boolean takeNextStep() throws GameActionException
    {
        if (target == null || rc.getLocation() == target)
        {
            target = rc.getLocation().add(dirs[(int) (Math.random() * 8)], 5);
            navigator.setTarget(target);
        }
        return navigator.takeNextStep();
    }

    public boolean fight() throws GameActionException
    {
        return fightMicro.basicNetFightMicro(nearByEnemies, nearByAllies, enemies, allies, target);
    }

    public boolean fightZombies() throws GameActionException
    {
        return fightMicro.basicNetZombieFightMicro(nearByZombies, nearByAllies, zombies, allies, target);
    }

    public Unit getNewStrategy(Unit current) throws GameActionException
    {
        return current;
    }

    public boolean carryOutAbility() throws GameActionException
    {
        return false;
    }
}
