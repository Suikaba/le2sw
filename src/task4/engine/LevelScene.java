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

package task4.engine;

import ch.idsia.benchmark.mario.engine.GeneralizerLevelScene;
import ch.idsia.benchmark.mario.engine.GlobalOptions;
import ch.idsia.benchmark.mario.engine.level.LevelGenerator;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.level.Level;
import task4.level.SpriteTemplate;
import task4.sprites.*;

import java.awt.*;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LevelScene implements SpriteContext, Cloneable
{
public static final boolean[] defaultKeys = new boolean[Environment.numberOfKeys];
public static final String[] keysStr = {"<<L ", "R>> ", "\\\\//", "JUMP", " RUN", "^UP^"};

public static final int cellSize = 16;

private List<Sprite> sprites = new ArrayList<Sprite>();
private List<Sprite> spritesToAdd = new ArrayList<Sprite>();
private List<Sprite> spritesToRemove = new ArrayList<Sprite>();

public Level level;
public Mario mario;
public float xCam, yCam, xCamO, yCamO;

public int tickCount;

public int startTime = 0;
private int timeLeft;
private int width;
private int height;

private static boolean onLadder = false;

private Random randomGen = new Random(0);

final private List<Float> enemiesFloatsList = new ArrayList<Float>();
final private float[] marioFloatPos = new float[2];
final private int[] marioState = new int[11];
private int numberOfHiddenCoinsGained = 0;

private int greenMushroomMode = 0;

public String memo = "";
private Point marioInitialPos;
private int bonusPoints = -1;

//    public int getTimeLimit() {  return timeLimit; }

public void setTimeLimit(int timeLimit)
{ this.timeLimit = timeLimit; }

private int timeLimit = 200;

private long levelSeed;
private int levelType;
private int levelDifficulty;
private int levelLength;
private int levelHeight;
public static int killedCreaturesTotal;
public static int killedCreaturesByFireBall;
public static int killedCreaturesByStomp;
public static int killedCreaturesByShell;


public LevelScene()
{
    try
    {
        Level.loadBehaviors(new DataInputStream(LevelScene.class.getResourceAsStream("resources/tiles.dat")));
    } catch (IOException e)
    {
        System.err.println("[MarioAI ERROR] : error loading file resources/tiles.dat ; ensure this file exists in ch/idsia/benchmark/mario/engine ");
        e.printStackTrace();
        System.exit(0);
    }
    
    Sprite.spriteContext = this;
    sprites.clear();
    this.width = GlobalOptions.VISUAL_COMPONENT_WIDTH;
    this.height = GlobalOptions.VISUAL_COMPONENT_HEIGHT;
    mario = new Mario(this);
    sprites.add(mario);
    startTime = 1;
    timeLeft = timeLimit * GlobalOptions.mariosecondMultiplier;

    tickCount = 0;
}


@Override
protected Object clone() throws CloneNotSupportedException {
	LevelScene l = (LevelScene)super.clone();
	l.mario = (Mario)this.mario.clone();
	l.level = (Level)this.level.clone();
	l.mario.levelScene = l;
	
	List<Sprite> clone = new ArrayList<Sprite>(this.sprites.size());
	for(Sprite item : this.sprites) {
		if(item == mario) {
			clone.add(l.mario);
		} else {
			Sprite s = (Sprite)item.clone();
			if(s.kind == Sprite.KIND_SHELL && ((Shell)s).carried && l.mario.carried != null) {
				l.mario.carried = s;
			}
			//s.levelScene = l;
			clone.add(s);
		}
	}
	l.sprites = clone;
	
	return l;
}


// TODO: !H!: Move to MarioEnvironment !!

public float[] getEnemiesFloatPos()
{
    enemiesFloatsList.clear();
    for (Sprite sprite : sprites)
    {
        // TODO:[M]: add unit tests for getEnemiesFloatPos involving all kinds of creatures
        if (sprite.isDead()) continue;
        switch (sprite.kind)
        {
            case Sprite.KIND_GOOMBA:
            case Sprite.KIND_BULLET_BILL:
            case Sprite.KIND_ENEMY_FLOWER:
            case Sprite.KIND_GOOMBA_WINGED:
            case Sprite.KIND_GREEN_KOOPA:
            case Sprite.KIND_GREEN_KOOPA_WINGED:
            case Sprite.KIND_RED_KOOPA:
            case Sprite.KIND_RED_KOOPA_WINGED:
            case Sprite.KIND_SPIKY:
            case Sprite.KIND_SPIKY_WINGED:
            case Sprite.KIND_SHELL:
            {
                enemiesFloatsList.add((float) sprite.kind);
                enemiesFloatsList.add(sprite.x - mario.x);
                enemiesFloatsList.add(sprite.y - mario.y);
            }
        }
    }

    float[] enemiesFloatsPosArray = new float[enemiesFloatsList.size()];

    int i = 0;
    for (Float F : enemiesFloatsList)
        enemiesFloatsPosArray[i++] = F;

    return enemiesFloatsPosArray;
}

public int fireballsOnScreen = 0;

List<Shell> shellsToCheck = new ArrayList<Shell>();

public void checkShellCollide(Shell shell)
{
    shellsToCheck.add(shell);
}

List<Fireball> fireballsToCheck = new ArrayList<Fireball>();

public void checkFireballCollide(Fireball fireball)
{
    fireballsToCheck.add(fireball);
}

public void tick()
{
    if (GlobalOptions.isGameplayStopped)
        return;

    timeLeft--;
    if (timeLeft == 0)
        mario.die("Time out!");
    xCamO = xCam;
    yCamO = yCam;

    if (startTime > 0)
    {
        startTime++;
    }

    float targetXCam = mario.x - 160;

    xCam = targetXCam;

    if (xCam < 0) xCam = 0;
    if (xCam > level.length * cellSize - GlobalOptions.VISUAL_COMPONENT_WIDTH)
        xCam = level.length * cellSize - GlobalOptions.VISUAL_COMPONENT_WIDTH;

    fireballsOnScreen = 0;

    for (Sprite sprite : sprites)
    {
        if (sprite != mario)
        {
            float xd = sprite.x - xCam;
            float yd = sprite.y - yCam;
            if (xd < -64 || xd > GlobalOptions.VISUAL_COMPONENT_WIDTH + 64 || yd < -64 || yd > GlobalOptions.VISUAL_COMPONENT_HEIGHT + 64)
            {
                removeSprite(sprite);
            } else
            {
                if (sprite instanceof Fireball)
                    fireballsOnScreen++;
            }
        }
    }

    tickCount++;
    level.tick();

    for (int x = (int) xCam / cellSize - 1; x <= (int) (xCam + this.width) / cellSize + 1; x++)
        for (int y = (int) yCam / cellSize - 1; y <= (int) (yCam + this.height) / cellSize + 1; y++)
        {
            int dir = 0;

            if (x * cellSize + 8 > mario.x + cellSize) dir = -1;
            if (x * cellSize + 8 < mario.x - cellSize) dir = 1;

            SpriteTemplate st = level.getSpriteTemplate(x, y);

            if (st != null)
            {
                if (st.lastVisibleTick != tickCount - 1)
                {
                    if (st.sprite == null || !sprites.contains(st.sprite))
                        st.spawn(this, x, y, dir);
                }

                st.lastVisibleTick = tickCount;
            }

            if (dir != 0)
            {
                byte b = level.getBlock(x, y);
                if (((Level.TILE_BEHAVIORS[b & 0xff]) & Level.BIT_ANIMATED) > 0)
                {
                    if ((b % cellSize) / 4 == 3 && b / cellSize == 0)
                    {
                        if ((tickCount - x * 2) % 100 == 0)
                        {
                            for (int i = 0; i < 8; i++)
                            {
                                addSprite(new Sparkle(x * cellSize + 8, y * cellSize + (int) (Math.random() * cellSize), (float) Math.random() * dir, 0, 0, 1, 5));
                            }
                            addSprite(new BulletBill(this, x * cellSize + 8 + dir * 8, y * cellSize + 15, dir));
                        }
                    }
                }
            }
        }

    for (Sprite sprite : sprites) {
    	sprite.tick();
    }

    byte levelElement = level.getBlock(mario.mapX, mario.mapY);
    if (levelElement == (byte) (13 + 3 * 16) || levelElement == (byte) (13 + 5 * 16))
    {
        if (levelElement == (byte) (13 + 5 * 16))
            mario.setOnTopOfLadder(true);
        else
            mario.setInLadderZone(true);
    } else if (mario.isInLadderZone())
    {
        mario.setInLadderZone(false);
    }


    for (Sprite sprite : sprites)
        sprite.collideCheck();

    for (Shell shell : shellsToCheck)
    {
        for (Sprite sprite : sprites)
        {
            if (sprite != shell && !shell.dead)
            {
                if (sprite.shellCollideCheck(shell))
                {
                    if (mario.carried == shell && !shell.dead)
                    {
                        mario.carried = null;
                        mario.setRacoon(false);
                        shell.die();
                        ++this.killedCreaturesTotal;
                    }
                }
            }
        }
    }
    shellsToCheck.clear();

    for (Fireball fireball : fireballsToCheck)
        for (Sprite sprite : sprites)
            if (sprite != fireball && !fireball.dead)
                if (sprite.fireballCollideCheck(fireball))
                    fireball.die();
    fireballsToCheck.clear();


    sprites.addAll(0, spritesToAdd);
    sprites.removeAll(spritesToRemove);
    spritesToAdd.clear();
    spritesToRemove.clear();
}

public void addSprite(Sprite sprite)
{
    spritesToAdd.add(sprite);
    sprite.tick();
}

public void removeSprite(Sprite sprite)
{
	spritesToRemove.add(sprite);
}

public void bump(int x, int y, boolean canBreakBricks)
{
	byte block = level.getBlock(x, y);

    if ((Level.TILE_BEHAVIORS[block & 0xff] & Level.BIT_BUMPABLE) > 0)
    {
        //if (block == 1)
        //    Mario.gainHiddenBlock();
        bumpInto(x, y - 1);
        byte blockData = level.getBlockData(x, y);
        if (blockData < 0)
            level.setBlockData(x, y, (byte) (blockData + 1));

        if (blockData == 0)
        {
            level.setBlock(x, y, (byte) 4);
            level.setBlockData(x, y, (byte) 4);
        }

        if (((Level.TILE_BEHAVIORS[block & 0xff]) & Level.BIT_SPECIAL) > 0)
        {
            if (randomGen.nextInt(5) == 0 && level.difficulty > 4)
            {
                addSprite(new GreenMushroom(this, x * cellSize + 8, y * cellSize + 8));
                ++level.counters.greenMushrooms;
            } else
            {
                if (!mario.large)
                {
                    addSprite(new Mushroom(this, x * cellSize + 8, y * cellSize + 8));
                    ++level.counters.mushrooms;
                } else
                {
                    addSprite(new FireFlower(this, x * cellSize + 8, y * cellSize + 8));
                    ++level.counters.flowers;
                }
            }
        } else
        {
            //Mario.gainCoin();
            addSprite(new CoinAnim(x, y));
        }
    }

    if ((Level.TILE_BEHAVIORS[block & 0xff] & Level.BIT_BREAKABLE) > 0)
    {
        bumpInto(x, y - 1);
        if (canBreakBricks)
        {
            level.setBlock(x, y, (byte) 0);
            for (int xx = 0; xx < 2; xx++)
                for (int yy = 0; yy < 2; yy++)
                    addSprite(new Particle(x * cellSize + xx * 8 + 4, y * cellSize + yy * 8 + 4, (xx * 2 - 1) * 4, (yy * 2 - 1) * 4 - 8));
        } else
        {
            level.setBlockData(x, y, (byte) 4);
        }
    }
}

public void bumpInto(int x, int y)
{
    byte block = level.getBlock(x, y);
    if (((Level.TILE_BEHAVIORS[block & 0xff]) & Level.BIT_PICKUPABLE) > 0)
    {
        //Mario.gainCoin();
        level.setBlock(x, y, (byte) 0);
        addSprite(new CoinAnim(x, y + 1));
    }

    for (Sprite sprite : sprites)
    {
        sprite.bumpCheck(x, y);
    }
}

public int getTimeSpent() { return startTime / GlobalOptions.mariosecondMultiplier; }

public int getTimeLeft() { return timeLeft / GlobalOptions.mariosecondMultiplier; }

public int getKillsTotal()
{
    return killedCreaturesTotal;
}

public int getKillsByFire()
{
    return killedCreaturesByFireBall;
}

public int getKillsByStomp()
{
    return killedCreaturesByStomp;
}

public int getKillsByShell()
{
    return killedCreaturesByShell;
}

public int[] getMarioState()
{
    marioState[0] = this.getMarioStatus();
    marioState[1] = this.getMarioMode();
    marioState[2] = this.isMarioOnGround() ? 1 : 0;
    marioState[3] = this.isMarioAbleToJump() ? 1 : 0;
    marioState[4] = this.isMarioAbleToShoot() ? 1 : 0;
    marioState[5] = this.isMarioCarrying() ? 1 : 0;
    marioState[6] = this.getKillsTotal();
    marioState[7] = this.getKillsByFire();
    marioState[8] = this.getKillsByStomp();
    marioState[9] = this.getKillsByShell();
    marioState[10] = this.getTimeLeft();
    return marioState;
}

public void performAction(boolean[] action)
{
    // might look ugly , but arrayCopy is not necessary here:
    this.mario.keys = action;
}

public boolean isLevelFinished()
{
    return (mario.getStatus() != Mario.STATUS_RUNNING);
}

public boolean isMarioAbleToShoot()
{
    return mario.isAbleToShoot();
}

public int getMarioStatus()
{
    return mario.getStatus();
}

/**
 * first and second elements of the array are x and y Mario coordinates correspondingly
 *
 * @return an array of size 2*(number of creatures on screen) including mario
 */
public float[] getCreaturesFloatPos()
{
    float[] enemies = this.getEnemiesFloatPos();
    float ret[] = new float[enemies.length + 2];
    System.arraycopy(this.getMarioFloatPos(), 0, ret, 0, 2);
    System.arraycopy(enemies, 0, ret, 2, enemies.length);
    return ret;
}

public boolean isMarioOnGround()
{ return mario.isOnGround(); }

public boolean isMarioAbleToJump()
{ return mario.mayJump(); }

public float[] getMarioFloatPos()
{
    marioFloatPos[0] = this.mario.x;
    marioFloatPos[1] = this.mario.y;
    return marioFloatPos;
}

public int getMarioMode()
{ return mario.getMode(); }

public boolean isMarioCarrying()
{ return mario.carried != null; }

public int getLevelDifficulty()
{ return levelDifficulty; }

public long getLevelSeed()
{ return levelSeed; }

public int getLevelLength()
{ return levelLength; }

public int getLevelHeight()
{ return levelHeight; }

public int getLevelType()
{ return levelType; }


public void addMemoMessage(final String memoMessage)
{
    memo += memoMessage;
}

public Point getMarioInitialPos() {return marioInitialPos;}

public int getGreenMushroomMode()
{
    return greenMushroomMode;
}

public int getBonusPoints()
{
    return bonusPoints;
}

public void setBonusPoints(final int bonusPoints)
{
    this.bonusPoints = bonusPoints;
}

public void appendBonusPoints(final int superPunti)
{
    bonusPoints += superPunti;
}


public boolean setLevelScene(byte[][] data) {
	int HalfObsWidth = 9;
	int HalfObsHeight = 9;
	int MarioXInMap = (int)mario.x / 16;
	int MarioYInMap = (int)mario.y / 16;
	boolean gapAtLast = true;
	boolean gapAtSecondLast = true;
	int lastEventX = 0;
	int[] heights = new int[19];
	for(int i = 0; i < heights.length; ++i) {
		heights[i] = 0;
	}
	int gapBorderHeight = 0;
	int gapBorderMinusOneHeight = 0;
	int gapBorderMinusTwoHeight = 0;
	
	for(int y = MarioYInMap - HalfObsHeight, obsX = 0; y < MarioYInMap + HalfObsHeight; ++y, ++obsX) {
		for(int x = MarioXInMap - HalfObsWidth, obsY = 0; x < MarioXInMap + HalfObsWidth; ++x, ++obsY) {
			if(x >= 0 && x <= level.xExit && y >= 0 && y < level.height) {
				byte datum = data[obsX][obsY];
				if(datum != 0 && datum != -10 && datum != 1 && obsY > lastEventX) {
					lastEventX = obsY;
				}
				if(datum != 0 && datum != 1 && heights[obsY] == 0) {
					heights[obsY] = y;
				}
				if(x == MarioXInMap + HalfObsWidth - 3 && datum != 0 && y > 5) {
					if(gapBorderMinusTwoHeight == 0) {
						gapBorderMinusTwoHeight = y;
					}
				}
				if(x == MarioXInMap + HalfObsWidth - 2 && datum != 0 && y > 5) {
					if(gapBorderMinusOneHeight == 0) {
						gapBorderMinusOneHeight = y;
					}
					gapAtSecondLast = false;
				}
				if(x == MarioXInMap + HalfObsWidth - 1 && datum != 0 && y > 5) {
					if(gapBorderHeight == 0) {
						gapBorderHeight = y;
					}
					gapAtLast = false;
				}
				if(datum != 1 && level.getBlock(x, y) != 14) {
					//level.setBlock(x, y, datum);
					if(datum == GeneralizerLevelScene.BRICK) {
						level.setBlock(x, y, (byte)(0 + 1 * 16));
					} else if(datum == GeneralizerLevelScene.UNBREAKABLE_BRICK) {
						level.setBlock(x, y, (byte)(4 + 2 + 1 * 16));
					} else if(datum == GeneralizerLevelScene.BORDER_CANNOT_PASS_THROUGH) {
						level.setBlock(x, y, (byte)(1 + 9 * 16));	
					} else if(datum == GeneralizerLevelScene.FLOWER_POT_OR_CANNON) {
						//System.out.println("FLOWER POT");
						level.setBlock(x, y, (byte)(1 + 8 * 16));
					} else {
						//System.out.println("Others: " + datum);
					}
				}
			}
		}
	}
	
	if(gapBorderHeight == gapBorderMinusTwoHeight && gapBorderMinusOneHeight < gapBorderHeight) {
		//System.out.println("set block");
		//level.setBlock(MarioXInMap + HalfObsWidth - 2, gapBorderMinusOneHeight, (byte)14);
	}
	if(gapAtLast && !gapAtSecondLast) {
		int holeWidth = 3;
		for(int i = 0; i < holeWidth; ++i) {
			for(int j = 0; j < 15; ++j) {
				//System.out.println("set block");
				level.setBlock(MarioXInMap + HalfObsWidth + i, j, (byte)0);
			}
			level.isGap[MarioXInMap + HalfObsWidth + i] = true;
			level.gapHeight[MarioXInMap + HalfObsWidth + i] = gapBorderMinusOneHeight;
		}
		for(int i = gapBorderMinusOneHeight; i < 16; ++i) {
			//System.out.println("set block");
			//level.setBlock(MarioXInMap + HalfObsWidth + holeWidth, gapBorderMinusOneHeight, (byte)4);
		}
		return true;
	}
	return false;
}

public boolean setEnemies(float[] enemies) {
	boolean requireReplanning = false;
	List<Sprite> newSprites = new ArrayList<Sprite>();
	for(int i = 0; i < enemies.length; i += 3) {
		int kind = (int)enemies[i];
		float x = enemies[i + 1];
		float y = enemies[i + 2];
		// todo: kind の値について調査する
		if(kind == -1 || kind == 15) {
			continue;
		}
		int type = -1;
		boolean winged = false;
		switch(kind) {
		case(Sprite.KIND_BULLET_BILL):
			type = -2;
			break;
		case(Sprite.KIND_ENEMY_FLOWER):
			type = Enemy.KIND_ENEMY_FLOWER;
			break;
		case(Sprite.KIND_GOOMBA):
			type = Enemy.KIND_GOOMBA;
			break;
		case(Sprite.KIND_GOOMBA_WINGED):
			type = Enemy.KIND_GOOMBA_WINGED;
			winged = true;
			break;
		// todo
		}
		
		if(type == -1) {
			continue;
		}
		float maxDelta = 2.01f * 1.75f;
		boolean enemyFound = false;
		// もともとあったもの
		for(Sprite sprite : sprites) {
			if(sprite.kind == kind && Math.abs(sprite.x - x) < maxDelta
			   && (Math.abs(sprite.y - y) < maxDelta || sprite.kind == Sprite.KIND_ENEMY_FLOWER)) {
				if(Math.abs(sprite.x - x) > 0) {
					if(sprite.kind == Sprite.KIND_SHELL) {
						((Shell)sprite).facing *= -1;
					} else {
						((Enemy)sprite).facing *= -1;
					}
					requireReplanning = true;
					sprite.x = x;
				}
				if((sprite.y - y) != 0 && sprite.kind == Sprite.KIND_ENEMY_FLOWER) {
					//((Enemy)sprite).ya = (y - sprite.lastAccurateY) * 0.89f;
					sprite.y = y;
				}
				enemyFound = true;
			}
			
			if(enemyFound) {
				newSprites.add(sprite);
				//sprite.lastAccurateX = x;
				//sprite.lastAccurateY = y;
			}
		}
		
		// もともといなかった敵
		if(!enemyFound) {
			requireReplanning = true;
			Sprite sprite;
			if(type == Enemy.KIND_ENEMY_FLOWER) {
				int flowerX = (int)x / 16;
				int flowerY = (int)y / 16;
				//sprite = new FlowerEnemy(this, flowerX * 16 + 15, y, flowerX, flowerY, y);
				sprite = new FlowerEnemy(this, (int)x, (int)y, flowerX, flowerY);
			} else if(type == Sprite.KIND_BULLET_BILL) {
				int dir = -1;
				sprite = new BulletBill(this, x, y, dir);
			} else {
				sprite = new Enemy(this, (int)x, (int)y, -1, type, winged, (int)x / 16, (int)y / 16);
				sprite.xa = 2;
			}
			//sprite.lastAccurateX = x;
			//sprite.lastAccurateY = y;
			sprite.spriteTemplate = new SpriteTemplate(type);
			newSprites.add(sprite);
		}
	}
	
	newSprites.add(mario);
	for(Sprite sprite : sprites) {
		if(sprite.kind == Sprite.KIND_FIREBALL) {
			newSprites.add(sprite);
		}
	}
	this.sprites = newSprites;
	return requireReplanning;
}


}