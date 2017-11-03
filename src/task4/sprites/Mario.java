/*
 * Copyright (c) 2009-2010, Sergey Karakovskiy and Julian Togelius
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *  Neither the name of the Mario AI nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package task4.sprites;

import ch.idsia.benchmark.mario.engine.Art;
import ch.idsia.benchmark.mario.engine.GlobalOptions;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.benchmark.mario.environments.MarioEnvironment;
import ch.idsia.tools.MarioAIOptions;
import task4.engine.LevelScene;
import task4.level.Level;

public final class Mario extends Sprite implements Cloneable
{
public static final String[] MODES = new String[]{"small", "Large", "FIRE"};

//        fire = (mode == MODE.MODE_FIRE);
public static final int KEY_LEFT = 0;
public static final int KEY_RIGHT = 1;
public static final int KEY_DOWN = 2;
public static final int KEY_JUMP = 3;
public static final int KEY_SPEED = 4;
public static final int KEY_UP = 5;

public static final int STATUS_RUNNING = 2;
public static final int STATUS_WIN = 1;
public static final int STATUS_DEAD = 0;

private static float marioGravity;

public boolean large = false;
public boolean fire = false;
public int coins = 0;
public int hiddenBlocksFound = 0;
public int collisionsWithCreatures = 0;
public int mushroomsDevoured = 0;
public int greenMushroomsDevoured = 0;
public int flowersDevoured = 0;

private static boolean isTrace;

private static boolean isMarioInvulnerable;

private int status = STATUS_RUNNING;
// for racoon when carrying the shell
private int prevWPic;
private int prevxPicO;
private int prevyPicO;
private int prevHPic;

private boolean isRacoon;
private float yaa = 1;

private static float windCoeff = 0f;
private static float iceCoeff = 0f;
private static float jumpPower;
private boolean inLadderZone;
private boolean onLadder;
private boolean onTopOfLadder = false;

public void resetStatic(MarioAIOptions marioAIOptions)
{
    large = marioAIOptions.getMarioMode() > 0;
    fire = marioAIOptions.getMarioMode() == 2;
    coins = 0;
    hiddenBlocksFound = 0;
    mushroomsDevoured = 0;
    flowersDevoured = 0;
    collisionsWithCreatures = 0;

    isMarioInvulnerable = marioAIOptions.isMarioInvulnerable();
    marioGravity = marioAIOptions.getMarioGravity();
    jumpPower = marioAIOptions.getJumpPower();

    isTrace = marioAIOptions.isTrace();

    iceCoeff = marioAIOptions.getIce();
    windCoeff = marioAIOptions.getWind();
}

public int getMode()
{
    return ((large) ? 1 : 0) + ((fire) ? 1 : 0);
}

//    private static float GROUND_INERTIA = 0.89f;
//    private static float AIR_INERTIA = 0.89f;

public boolean[] keys = new boolean[Environment.numberOfKeys];
public boolean[] cheatKeys;
private float runTime;
boolean wasOnGround = false;
boolean onGround = false;
private boolean mayJump = false;
private boolean ducking = false;
private boolean sliding = false;
private int jumpTime = 0;
private float xJumpSpeed;
private float yJumpSpeed;

private boolean ableToShoot = false;

public int width = 4;
public int height = 24;

public LevelScene levelScene;
public int facing;

public int xDeathPos, yDeathPos;

public int deathTime = 0;
public int winTime = 0;
private int invulnerableTime = 0;

public Sprite carried = null;

private int damage = 0;

private float jT;


public Mario(LevelScene levelScene)
{
    kind = KIND_MARIO;
    this.levelScene = levelScene;
    x = 32;
    y = 0;
    mapX = (int) (x / 16);
    mapY = (int) (y / 16);

    facing = 1;
    setMode(true, true);
    yaa = marioGravity * 3;
    jT = jumpPower / (marioGravity);
}

void setMode(boolean large, boolean fire)
{
    if (fire) large = true;
    if (!large) fire = false;

    this.large = large;
    this.fire = fire;
}

public void setKeys(boolean[] actions) {
	for(int i = 0; i < Environment.numberOfKeys; ++i) {
		keys[i] = actions[i];
	}
}

public void setRacoon(boolean isRacoon)
{
    this.isRacoon = isRacoon;
    if (isRacoon)
    {
        savePrevState();

        xPicO = 16;
        yPicO = 31;
        wPic = hPic = 32;
        this.sheet = Art.racoonmario;
    } else
    {

        this.sheet = prevSheet;
        this.xPicO = this.prevxPicO;
        this.yPicO = this.prevyPicO;
        wPic = prevWPic;
        hPic = prevHPic;
    }
}

private void savePrevState()
{
    this.prevSheet = this.sheet;
    prevWPic = wPic;
    prevHPic = hPic;
    this.prevxPicO = xPicO;
    this.prevyPicO = yPicO;
}

public void move()
{
	//System.out.println("[Mario]: call move ");
	//System.out.println("[Mario]: current pos -> " + x + " " + y);
	if (GlobalOptions.isFly)
    {
        xa = ya = 0;
        ya = keys[KEY_DOWN] ? 10 : ya;
        ya = keys[KEY_UP] ? -10 : ya;
        xa = keys[KEY_RIGHT] ? 10 : xa;
        xa = keys[KEY_LEFT] ? -10 : xa;
    }

    if (this.inLadderZone)
    {
        if (keys[KEY_UP] && !onLadder)
        {
            onLadder = true;
        }

        if (!keys[KEY_UP] && !keys[KEY_DOWN] && onLadder)
            ya = 0;

        if (onLadder)
        {
            if (!onTopOfLadder)
            {
                ya = keys[KEY_UP] ? -10 : ya;
            } else
            {
                ya = 0;
                ya = keys[KEY_DOWN] ? 10 : ya;
                if (keys[KEY_DOWN])
                    onTopOfLadder = false;
            }
            onGround = true;
        }
    }

    if (mapY > -1 && isTrace)
        ++levelScene.level.marioTrace[this.mapX][this.mapY];

    if (winTime > 0)
    {
        winTime++;

        xa = 0;
        ya = 0;
        return;
    }

    if (deathTime > 0)
    {
        deathTime++;
        if (deathTime < 11)
        {
            xa = 0;
            ya = 0;
        } else if (deathTime == 11)
        {
            ya = -15;
        } else
        {
            ya += 2;
        }
        x += xa;
        y += ya;
        return;
    }

    if (invulnerableTime > 0) invulnerableTime--;
    visible = ((invulnerableTime / 2) & 1) == 0;

    wasOnGround = onGround;
    //System.out.println("OnGround: " + onGround);
    float sideWaysSpeed = keys[KEY_SPEED] ? 1.2f : 0.6f;

    if (onGround)
    {
        ducking = keys[KEY_DOWN] && large;
    }

    if (xa > 2)
    {
        facing = 1;
    }
    if (xa < -2)
    {
        facing = -1;
    }

    if (keys[KEY_JUMP] || (jumpTime < 0 && !onGround && !sliding))
    {
        if (jumpTime < 0)
        {
            xa = xJumpSpeed;
            ya = -jumpTime * yJumpSpeed;
            jumpTime++;
        } else if (onGround && mayJump)
        {
            xJumpSpeed = 0;
            yJumpSpeed = -1.9f;
            jumpTime = (int) jT;
            ya = jumpTime * yJumpSpeed;
            onGround = false;
            sliding = false;
        } else if (sliding && mayJump)
        {
            xJumpSpeed = -facing * 6.0f;
            yJumpSpeed = -2.0f;
            jumpTime = -6;
            xa = xJumpSpeed;
            ya = -jumpTime * yJumpSpeed;
            onGround = false;
            sliding = false;
            facing = -facing;
        } else if (jumpTime > 0)
        {
            xa += xJumpSpeed;
            ya = jumpTime * yJumpSpeed;
            jumpTime--;
        }
    } else
    {
        jumpTime = 0;
    }

    if (keys[KEY_LEFT] && !ducking)
    {
        if (facing == 1) sliding = false;
        xa -= sideWaysSpeed;
        if (jumpTime >= 0) facing = -1;
    }

    if (keys[KEY_RIGHT] && !ducking)
    {
        if (facing == -1) sliding = false;
        xa += sideWaysSpeed;
        if (jumpTime >= 0) facing = 1;
    }

    if ((!keys[KEY_LEFT] && !keys[KEY_RIGHT]) || ducking || ya < 0 || onGround)
    {
        sliding = false;
    }

    if (keys[KEY_SPEED] && ableToShoot && this.fire && levelScene.fireballsOnScreen < 2)
    {
        levelScene.addSprite(new Fireball(levelScene, x + facing * 6, y - 20, facing));
    }
    if (GlobalOptions.isPowerRestoration && keys[KEY_SPEED] && (!this.large || !this.fire))
        setMode(true, true);
    ableToShoot = !keys[KEY_SPEED];

    mayJump = (onGround || sliding) && !keys[KEY_JUMP];

    xFlipPic = facing == -1;

    runTime += (Math.abs(xa)) + 5;
    if (Math.abs(xa) < 0.5f)
    {
        runTime = 0;
        xa = 0;
    }

    calcPic();

    if (sliding)
    {
        ya *= 0.5f;
    }

    onGround = false;
    move(xa, 0);
    move(0, ya);

    if (y > levelScene.level.height * LevelScene.cellSize + LevelScene.cellSize) {
    	die("Gap");
    }


    if (x < 0)
    {
        x = 0;
        xa = 0;
    }

    if (mapX >= levelScene.level.xExit && mapY <= levelScene.level.yExit)
    {
        x = (levelScene.level.xExit + 1) * LevelScene.cellSize;
        win();
    }

    if (x > levelScene.level.length * LevelScene.cellSize)
    {
        x = levelScene.level.length * LevelScene.cellSize;
        xa = 0;
    }

    ya *= 0.85f;
    if (onGround)
    {
        xa *= (GROUND_INERTIA + windScale(windCoeff, facing) + iceScale(iceCoeff));
    } else
    {
        xa *= (AIR_INERTIA + windScale(windCoeff, facing) + iceScale(iceCoeff));
    }

    if (!onGround)
    {
        ya += yaa;
    }

    if (carried != null)
    {
        carried.x = x + facing * 8; //TODO:|L| move to cellSize_2 = cellSize/2;
        carried.y = y - 2;
        if (!keys[KEY_SPEED])
        {
            carried.release(this);
            carried = null;
            setRacoon(false);
        }
    }
}

private void calcPic()
{
    int runFrame;

    if (large || isRacoon)
    {
        runFrame = ((int) (runTime / 20)) % 4;
        if (runFrame == 3) runFrame = 1;
        if (carried == null && Math.abs(xa) > 10) runFrame += 3;
        if (carried != null) runFrame += 10;
        if (!onGround)
        {
            if (carried != null) runFrame = 12;
            else if (Math.abs(xa) > 10) runFrame = 7;
            else runFrame = 6;
        }
    } else
    {
        runFrame = ((int) (runTime / 20)) % 2;
        if (carried == null && Math.abs(xa) > 10) runFrame += 2;
        if (carried != null) runFrame += 8;
        if (!onGround)
        {
            if (carried != null) runFrame = 9;
            else if (Math.abs(xa) > 10) runFrame = 5;
            else runFrame = 4;
        }
    }

    if (onGround && ((facing == -1 && xa > 0) || (facing == 1 && xa < 0)))
    {
        if (xa > 1 || xa < -1) runFrame = large ? 9 : 7;
    }

    if (large)
    {
        if (ducking) runFrame = 14;
        height = ducking ? 12 : 24;
    } else
    {
        height = 12;
    }

    xPic = runFrame;
}

private boolean move(float xa, float ya)
{
    while (xa > 8)
    {
        if (!move(8, 0)) return false;
        xa -= 8;
    }
    while (xa < -8)
    {
        if (!move(-8, 0)) return false;
        xa += 8;
    }
    while (ya > 8)
    {
        if (!move(0, 8)) return false;
        ya -= 8;
    }
    while (ya < -8)
    {
        if (!move(0, -8)) return false;
        ya += 8;
    }

    boolean collide = false;
    if (ya > 0)
    {
    	if (isBlocking(x + xa - width, y + ya, xa, 0)) collide = true;
        else if (isBlocking(x + xa + width, y + ya, xa, 0)) collide = true;
        else if (isBlocking(x + xa - width, y + ya + 1, xa, ya)) collide = true;
        else if (isBlocking(x + xa + width, y + ya + 1, xa, ya)) collide = true;
    }
    if (ya < 0)
    {
        if (isBlocking(x + xa, y + ya - height, xa, ya)) collide = true;
        else if (collide || isBlocking(x + xa - width, y + ya - height, xa, ya)) collide = true;
        else if (collide || isBlocking(x + xa + width, y + ya - height, xa, ya)) collide = true;
    }
    if (xa > 0)
    {
        sliding = true;
        if (isBlocking(x + xa + width, y + ya - height, xa, ya)) collide = true;
        else sliding = false;
        if (isBlocking(x + xa + width, y + ya - height / 2, xa, ya)) collide = true;
        else sliding = false;
        if (isBlocking(x + xa + width, y + ya, xa, ya)) collide = true;
        else sliding = false;
    }
    if (xa < 0)
    {
        sliding = true;
        if (isBlocking(x + xa - width, y + ya - height, xa, ya)) collide = true;
        else sliding = false;
        if (isBlocking(x + xa - width, y + ya - height / 2, xa, ya)) collide = true;
        else sliding = false;
        if (isBlocking(x + xa - width, y + ya, xa, ya)) collide = true;
        else sliding = false;
    }

    if (collide)
    {
    	//System.out.println("[Mario move2]: collide");
        if (xa < 0)
        {
            x = (int) ((x - width) / 16) * 16 + width;
            this.xa = 0;
        }
        if (xa > 0)
        {
            x = (int) ((x + width) / 16 + 1) * 16 - width - 1;
            this.xa = 0;
        }
        if (ya < 0)
        {
            y = (int) ((y - height) / 16) * 16 + height;
            jumpTime = 0;
            this.ya = 0;
        }
        if (ya > 0)
        {
            y = (int) ((y - 1) / 16 + 1) * 16 - 1;
            onGround = true;
        }
        return false;
    } else
    {
        x += xa;
        y += ya;
        return true;
    }
}

private boolean isBlocking(final float _x, final float _y, final float xa, final float ya)
{
    int x = (int) (_x / 16);
    int y = (int) (_y / 16);
    if (x == (int) (this.x / 16) && y == (int) (this.y / 16)) return false;

    boolean blocking = levelScene.level.isBlocking(x, y, xa, ya);

    byte block = levelScene.level.getBlock(x, y);

    if (((Level.TILE_BEHAVIORS[block & 0xff]) & Level.BIT_PICKUPABLE) > 0)
    {
        //gainCoin();
        levelScene.level.setBlock(x, y, (byte) 0);
    }

    if (blocking && ya < 0)
    {
        levelScene.bump(x, y, large);
    }

    //System.out.println("[Mario isBlocking]: " + blocking);
    return blocking;
}

public void stomp(final Enemy enemy)
{
    if (deathTime > 0) return;

    float targetY = enemy.y - enemy.height / 2;
    move(0, targetY - y);
    mapY = (int) y / 16;

    xJumpSpeed = 0;
    yJumpSpeed = -1.9f;
    jumpTime = (int) jT + 1;
    ya = jumpTime * yJumpSpeed;
    onGround = false;
    sliding = false;
    invulnerableTime = 1;
    levelScene.appendBonusPoints(MarioEnvironment.IntermediateRewardsSystemOfValues.stomp);
}

public void stomp(final Shell shell)
{
    if (deathTime > 0) return;

    if (keys[KEY_SPEED] && shell.facing == 0)
    {
        carried = shell;
        shell.carried = true;
        setRacoon(true);
    } else
    {
        float targetY = shell.y - shell.height / 2;
        move(0, targetY - y);
        mapY = (int) y / 16;

        xJumpSpeed = 0;
        yJumpSpeed = -1.9f;
        jumpTime = (int) jT + 1;
        ya = jumpTime * yJumpSpeed;
        onGround = false;
        sliding = false;
        invulnerableTime = 1;
    }
    levelScene.appendBonusPoints(MarioEnvironment.IntermediateRewardsSystemOfValues.stomp);
}

public void getHurt(final int spriteKind)
{
	if (deathTime > 0 || isMarioInvulnerable) {
    	return;
    }
    if (invulnerableTime > 0) {
    	return;
    }

    //System.out.println("[Mario] get hurt! by " + spriteKind);
    ++damage;
    ++collisionsWithCreatures;
    levelScene.appendBonusPoints(-MarioEnvironment.IntermediateRewardsSystemOfValues.kills);
    if (large)
    {
//        levelScene.paused = true;
//        powerUpTime = -3 * FractionalPowerUpTime;
        if (fire)
        {
            levelScene.mario.setMode(true, false);
        } else
        {
            levelScene.mario.setMode(false, false);
        }
        invulnerableTime = 32;
    } else
    {
        die("Collision with a creature [" + Sprite.getNameByKind(spriteKind) + "]");
    }
}

public void win()
{
    xDeathPos = (int) x;
    yDeathPos = (int) y;
    winTime = 1;
    status = Mario.STATUS_WIN;
    levelScene.appendBonusPoints(MarioEnvironment.IntermediateRewardsSystemOfValues.win);
}

public void die(final String reasonOfDeath)
{
    xDeathPos = (int) x;
    yDeathPos = (int) y;
    deathTime = 25;
    damage += 2;
    //levelScene.paused = true;
    //System.out.println("[Mario die]: Simulate Mario " + reasonOfDeath);
    status = Mario.STATUS_DEAD;
}

public void devourFlower()
{
    if (deathTime > 0) return;

    if (!fire)
    {
    	if(!large) {
    		damage -= 2;
    	} else {
    		damage -= 1;
    	}
        levelScene.mario.setMode(true, true);
    } else
    {
        //Mario.gainCoin();
    }
    ++flowersDevoured;
    levelScene.appendBonusPoints(MarioEnvironment.IntermediateRewardsSystemOfValues.flowerFire);
}

public void devourMushroom()
{
    if (deathTime > 0) return;

    if (!large)
    {
        levelScene.mario.setMode(true, false);
    } else
    {
        //Mario.gainCoin();
    }
    ++mushroomsDevoured;
    levelScene.appendBonusPoints(MarioEnvironment.IntermediateRewardsSystemOfValues.mushroom);
}

public void devourGreenMushroom(final int mushroomMode)
{
    ++greenMushroomsDevoured;
    if (mushroomMode == 0)
        getHurt(Sprite.KIND_GREEN_MUSHROOM);
    else
        die("Collision with a creature [" + Sprite.getNameByKind(Sprite.KIND_GREEN_MUSHROOM) + "]");
}

public void kick(final Shell shell)
{
//        if (deathTime > 0 || levelScene.paused) return;

    if (keys[KEY_SPEED])
    {
        carried = shell;
        shell.carried = true;
        setRacoon(true);
//        System.out.println("shell = " + shell);
    } else
    {
        invulnerableTime = 1;
    }
}

public void stomp(final BulletBill bill)
{
    if (deathTime > 0)
        return;

    float targetY = bill.y - bill.height / 2;
    move(0, targetY - y);
    mapY = (int) y / 16;

    xJumpSpeed = 0;
    yJumpSpeed = -1.9f;
    jumpTime = (int) jT + 1;
    ya = jumpTime * yJumpSpeed;
    onGround = false;
    sliding = false;
    invulnerableTime = 1;
    levelScene.appendBonusPoints(MarioEnvironment.IntermediateRewardsSystemOfValues.stomp);
}


public int getStatus()
{
    return status;
}

public boolean isOnGround()
{
    return onGround;
}

public boolean mayJump()
{
    return mayJump;
}

public boolean isAbleToShoot()
{
    return ableToShoot;
}

public void setInLadderZone(final boolean inLadderZone)
{
    this.inLadderZone = inLadderZone;
    if (!inLadderZone)
    {
        onLadder = false;
        onTopOfLadder = false;
    }
}

public boolean isInLadderZone()
{
    return this.inLadderZone;
}

public void setOnTopOfLadder(final boolean onTop)
{
    this.onTopOfLadder = onTop;
}

public boolean isOnTopOfLadder()
{
    return this.onTopOfLadder;
}

// append:
public int getJumpTime() {
	return jumpTime;
}

public void addDamage(int d) {
	damage += d;
}
public int getDamage() {
	return damage;
}

}