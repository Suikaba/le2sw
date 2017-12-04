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

import java.awt.Point;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import ch.idsia.benchmark.mario.engine.GeneralizerLevelScene;
import ch.idsia.benchmark.mario.engine.GlobalOptions;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.tools.MarioAIOptions;
import task4.level.Level;
import task4.level.SpriteTemplate;
import task4.sprites.BulletBill;
import task4.sprites.CoinAnim;
import task4.sprites.Enemy;
import task4.sprites.EnemyInfo;
import task4.sprites.FireFlower;
import task4.sprites.Fireball;
import task4.sprites.FlowerEnemy;
import task4.sprites.GreenMushroom;
import task4.sprites.Mario;
import task4.sprites.Mushroom;
import task4.sprites.Shell;
import task4.sprites.Sprite;
import task4.sprites.SpriteInfo;
import task4.sprites.WaveGoomba;

public final class LevelScene implements Cloneable
{
	public static final boolean[] defaultKeys = new boolean[Environment.numberOfKeys];
	public static final String[] keysStr = {"<<L ", "R>> ", "\\\\//", "JUMP", " RUN", "^UP^"};

	public static final int cellSize = 16;

	private ArrayList<Sprite> sprites = new ArrayList<Sprite>();
	private ArrayList<Sprite> spritesToAdd = new ArrayList<Sprite>();
	private ArrayList<Sprite> spritesToRemove = new ArrayList<Sprite>();

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
	public int killedCreaturesTotal;
	public int killedCreaturesByFireBall;
	public int killedCreaturesByStomp;
	public int killedCreaturesByShell;

	public int fireballsOnScreen = 0;
	ArrayList<Fireball> fireballsToCheck = new ArrayList<Fireball>();
	ArrayList<Shell> shellsToCheck = new ArrayList<Shell>();

	// デバッグ情報用
	public static final int DEBUG_MARIO = 1;
	public static final int DEBUG_BLOCK = 1 << 1;
	public static final int DEBUG_MAP = 1 << 2;
	public static final int DEBUG_ENEMY = 1 << 3;

	private int DEBUG_LEVEL = 0;


	public LevelScene()
	{
		try {
			Level.loadBehaviors(new DataInputStream(LevelScene.class.getResourceAsStream("resources/tiles.dat")));
		} catch (IOException e) {
			System.err.println("[MarioAI ERROR] : error loading file resources/tiles.dat ; ensure this file exists in ch/idsia/benchmark/mario/engine ");
			e.printStackTrace();
			System.exit(0);
		}

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
	protected LevelScene clone() throws CloneNotSupportedException {
		LevelScene cloneScene = (LevelScene)super.clone();
		cloneScene.mario = (Mario)this.mario.clone();
		cloneScene.level = (Level)this.level.clone();
		cloneScene.mario.levelScene = cloneScene;

		ArrayList<Sprite> spritesClone = new ArrayList<Sprite>(this.sprites.size());
		// これ中身クローンしてしまうとダメな気がする．
		// sprites -> cloneA
		// spritesToAdd -> cloneB
		// で実は元は cloneA と cloneB が同じものを指していた場合に，完全に別物になってしまう．
		// arraylist remove は equals により同一判定を行う．
		// unique ID を振ればまあ良い．が，tick() 呼び出しで全部使い切る(clear)なら，クローン作らないのもアリか．
		/*ArrayList<Sprite> spritesToAddClone = new ArrayList<Sprite>(this.spritesToAdd.size());
		ArrayList<Sprite> spritesToRemoveClone = new ArrayList<Sprite>(this.spritesToRemove.size());
		ArrayList<Fireball> fireballsToCheckClone = new ArrayList<Fireball>(this.fireballsToCheck.size());
		ArrayList<Shell> shellsToCheckClone = new ArrayList<Shell>(this.shellsToCheck.size());*/
		for(Sprite sprite : this.sprites) {
			if(sprite == mario) {
				spritesClone.add(cloneScene.mario);
			} else {
				Sprite s = (Sprite)sprite.clone();
				if(s.kind == Sprite.KIND_SHELL && ((Shell)s).carried && cloneScene.mario.carried != null) {
					cloneScene.mario.carried = s;
				}
				s.world = cloneScene;
				spritesClone.add(s);
			}
		}
		cloneScene.sprites = spritesClone;
		/*cloneScene.spritesToAdd = spritesToAddClone;
		cloneScene.spritesToRemove = spritesToRemoveClone;
		cloneScene.fireballsToCheck = fireballsToCheckClone;
		cloneScene.shellsToCheck = shellsToCheckClone;*/

		return cloneScene;
	}

	public void reset(MarioAIOptions marioAIOptions) {
	    this.setTimeLimit(marioAIOptions.getTimeLimit());

	    killedCreaturesTotal = 0;
	    killedCreaturesByFireBall = 0;
	    killedCreaturesByStomp = 0;
	    killedCreaturesByShell = 0;

	    marioInitialPos = marioAIOptions.getMarioInitialPos();
	    //greenMushroomMode = marioAIOptions.getGreenMushroomMode();

	    this.levelSeed = level.randomSeed;
	    this.levelLength = level.length;
	    this.levelHeight = level.height;
	    this.levelType = level.type;
	    this.levelDifficulty = level.difficulty;

	    sprites.clear();
	    this.width = GlobalOptions.VISUAL_COMPONENT_WIDTH;
	    this.height = GlobalOptions.VISUAL_COMPONENT_HEIGHT;

	    Sprite.setCreaturesGravity(marioAIOptions.getCreaturesGravity());
	    Sprite.setCreaturesWind(marioAIOptions.getWind());
	    Sprite.setCreaturesIce(marioAIOptions.getIce());
	    this.mario.reset(marioAIOptions);

	    bonusPoints = -1;

	    memo = "";
	}


	public void checkShellCollide(Shell shell) {
		shellsToCheck.add(shell);
	}


	public void checkFireballCollide(Fireball fireball) {
		fireballsToCheck.add(fireball);
	}

	public void tick() {
		if (GlobalOptions.isGameplayStopped) {
			return;
		}

		timeLeft--;
		if (timeLeft == 0) {
			mario.die("Time out!");
		}
		xCamO = xCam;
		yCamO = yCam;

		if (startTime > 0) {
			startTime++;
		}

		float targetXCam = mario.x - 160;

		xCam = targetXCam;

		if (xCam < 0) {
			xCam = 0;
		}
		if (xCam > level.length * cellSize - GlobalOptions.VISUAL_COMPONENT_WIDTH) {
			xCam = level.length * cellSize - GlobalOptions.VISUAL_COMPONENT_WIDTH;
		}

		fireballsOnScreen = 0;

		for (Sprite sprite : sprites) {
			if (sprite != mario) {
				float xd = sprite.x - xCam;
				float yd = sprite.y - yCam;
				if (xd < -64 || xd > GlobalOptions.VISUAL_COMPONENT_WIDTH + 64 || yd < -64 || yd > GlobalOptions.VISUAL_COMPONENT_HEIGHT + 64) {
					removeSprite(sprite);
				} else {
					if (sprite instanceof Fireball) {
						fireballsOnScreen++;
					}
				}
			}
		}

		tickCount++;
		// パット見エフェクト処理だけなので，シミュレートにはいらない
		//level.tick();

		for (int x = (int) xCam / cellSize - 1; x <= (int) (xCam + this.width) / cellSize + 1; x++) {
			for (int y = (int) yCam / cellSize - 1; y <= (int) (yCam + this.height) / cellSize + 1; y++) {
				int dir = 0;
				if (x * cellSize + 8 > mario.x + cellSize) {
					dir = -1;
				}
				if (x * cellSize + 8 < mario.x - cellSize) {
					dir = 1;
				}

				SpriteTemplate st = level.getSpriteTemplate(x, y);

				if (st != null) {
					if (st.lastVisibleTick != tickCount - 1) {
						if (st.sprite == null || !sprites.contains(st.sprite)) {
							st.spawn(this, x, y, dir);
						}
					}

					st.lastVisibleTick = tickCount;
				}

				if (dir != 0) {
					byte b = level.getBlock(x, y);
					if (((Level.TILE_BEHAVIORS[b & 0xff]) & Level.BIT_ANIMATED) > 0) {
						if ((b % cellSize) / 4 == 3 && b / cellSize == 0) {
							if ((tickCount - x * 2) % 100 == 0) {
								addSprite(new BulletBill(this, x * cellSize + 8 + dir * 8, y * cellSize + 15, dir));
							}
						}
					}
				}
			}
		}

		for (Sprite sprite : sprites) {
			sprite.tick();
		}

		byte levelElement = level.getBlock(mario.mapX, mario.mapY);
		if (levelElement == (byte) (13 + 3 * 16) || levelElement == (byte) (13 + 5 * 16)) {
			if (levelElement == (byte) (13 + 5 * 16)) {
				mario.setOnTopOfLadder(true);
			} else {
				mario.setInLadderZone(true);
			}
		} else if (mario.isInLadderZone()) {
			mario.setInLadderZone(false);
		}


		for (Sprite sprite : sprites) {
			sprite.collideCheck();
		}

		for (Shell shell : shellsToCheck) {
			for (Sprite sprite : sprites) {
				if (sprite != shell && !shell.dead) {
					if (sprite.shellCollideCheck(shell)) {
						if (mario.carried == shell && !shell.dead) {
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

		for (Fireball fireball : fireballsToCheck) {
			for (Sprite sprite : sprites) {
				if (sprite != fireball && !fireball.dead) {
					if (sprite.fireballCollideCheck(fireball)) {
						fireball.die();
					}
				}
			}
		}
		fireballsToCheck.clear();

		sprites.removeAll(spritesToRemove);
		sprites.addAll(spritesToAdd);
		spritesToAdd.clear();
		spritesToRemove.clear();
	}

	public void addSprite(Sprite sprite) {
		spritesToAdd.add(sprite);
		sprite.tick();
	}

	public void removeSprite(Sprite sprite) {
		spritesToRemove.add(sprite);
	}

	public void bump(int x, int y, boolean canBreakBricks) {
		byte block = level.getBlock(x, y);

		if ((Level.TILE_BEHAVIORS[block & 0xff] & Level.BIT_BUMPABLE) > 0) {
			//if (block == 1)
			//    Mario.gainHiddenBlock();
			bumpInto(x, y - 1);
			byte blockData = level.getBlockData(x, y);
			if (blockData < 0) {
				level.setBlockData(x, y, (byte) (blockData + 1));
			}

			if (blockData == 0) {
				level.setBlock(x, y, (byte) 4);
				level.setBlockData(x, y, (byte) 4);
			}

			if (((Level.TILE_BEHAVIORS[block & 0xff]) & Level.BIT_SPECIAL) > 0) {
				if (randomGen.nextInt(5) == 0 && level.difficulty > 4) {
					addSprite(new GreenMushroom(this, x * cellSize + 8, y * cellSize + 8));
					++level.counters.greenMushrooms;
				} else {
					if (!mario.large) {
						addSprite(new Mushroom(this, x * cellSize + 8, y * cellSize + 8));
						++level.counters.mushrooms;
					} else {
						addSprite(new FireFlower(this, x * cellSize + 8, y * cellSize + 8));
						++level.counters.flowers;
					}
				}
			} else {
				//Mario.gainCoin();
				addSprite(new CoinAnim(x, y));
			}
		}

		if ((Level.TILE_BEHAVIORS[block & 0xff] & Level.BIT_BREAKABLE) > 0) {
			bumpInto(x, y - 1);
			if (canBreakBricks) {
				level.setBlock(x, y, (byte) 0);
			} else {
				level.setBlockData(x, y, (byte) 4);
			}
		}
	}

	public void bumpInto(int x, int y) {
		byte block = level.getBlock(x, y);
		if (((Level.TILE_BEHAVIORS[block & 0xff]) & Level.BIT_PICKUPABLE) > 0) {
			//Mario.gainCoin();
			level.setBlock(x, y, (byte) 0);
			addSprite(new CoinAnim(x, y + 1));
		}

		for (Sprite sprite : sprites) {
			sprite.bumpCheck(x, y);
		}
	}

	public int getTimeSpent() {
		return startTime / GlobalOptions.mariosecondMultiplier;
	}

	public int getTimeLeft() {
		return timeLeft / GlobalOptions.mariosecondMultiplier;
	}

	public int getKillsTotal() {
		return killedCreaturesTotal;
	}

	public int getKillsByFire() {
		return killedCreaturesByFireBall;
	}

	public int getKillsByStomp() {
		return killedCreaturesByStomp;
	}

	public int getKillsByShell() {
		return killedCreaturesByShell;
	}

	public int[] getMarioState() {
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

	public void performAction(boolean[] action) {
		this.mario.keys = action;
	}

	public boolean isLevelFinished() {
		return (mario.getStatus() != Mario.STATUS_RUNNING);
	}

	public boolean isMarioAbleToShoot() {
		return mario.isAbleToShoot();
	}

	public int getMarioStatus() {
		return mario.getStatus();
	}

	public boolean isMarioOnGround() {
		return mario.isOnGround();
	}

	public boolean isMarioAbleToJump() {
		return mario.mayJump();
	}

	public float[] getMarioFloatPos() {
		marioFloatPos[0] = this.mario.x;
		marioFloatPos[1] = this.mario.y;
		return marioFloatPos;
	}

	public int getMarioMode() {
		return mario.getMode();
	}

	public boolean isMarioCarrying() {
		return mario.carried != null;
	}

	public int getLevelDifficulty() {
		return levelDifficulty;
	}

	public long getLevelSeed() {
		return levelSeed;
	}

	public int getLevelLength() {
		return levelLength;
	}

	public int getLevelHeight() {
		return levelHeight;
	}

	public int getLevelType() {
		return levelType;
	}


	public void addMemoMessage(final String memoMessage) {
		memo += memoMessage;
	}

	public Point getMarioInitialPos() {
		return marioInitialPos;
	}

	public int getGreenMushroomMode() {
		return greenMushroomMode;
	}

	public int getBonusPoints() {
		return bonusPoints;
	}

	public void setBonusPoints(final int bonusPoints) {
		this.bonusPoints = bonusPoints;
	}

	public void appendBonusPoints(final int superPunti) {
		bonusPoints += superPunti;
	}


	public boolean setMapData(byte[][] data) {
		int MarioXInMap = (int)mario.x / 16;
		int MarioYInMap = (int)mario.y / 16;

		if((DEBUG_LEVEL & (DEBUG_MAP)) > 0) {
			System.out.println("[LevelScene mapData]:");
			for(int i = 0; i < data.length; ++i) {
				for(int j = 0; j < data[i].length; ++j) {
					System.out.print(String.format("%4d", data[i][j]) + " ");
				}
				System.out.println();
			}
		}

		// 注意！
		// data[y][x] だが，map[x][y] なので入れ替える必要がある！！！
		for(int y = MarioYInMap - 9, obsX = 0; y <= MarioYInMap + 9; ++y, ++obsX) {
			for(int x = MarioXInMap - 9, obsY = 0; x <= MarioXInMap + 9; ++x, ++obsY) {
				if(x >= 0 && x <= level.xExit && y >= 0 && y < level.height) {
					byte datum = data[obsX][obsY];
					/* Copy from Level Generator
			    	first component of sum : position on  Y axis
			    	second component of sum : position  on X axis
			    	starting at 0
					 	*16 because size of the picture is 16x16 pixels
			    	0+9*16 -- left side of the ground
			    	1+9*16 -- upper side of ground; common block telling "it's smth (ground) here". Is processed further.
			    	2+9*16 -- right side of the earth
			    	3+9*16 -- peice of the earth
			    	9+0*16 -- block of a ladder
			    	14+0*16 -- cannon barrel (CANNON MUZZLE)
			    	14+1*16 -- base for cannon barrel
			    	14+2*16 -- cannon pole
			    	4+8*16 -- left piece of a hill of ground
			    	4+11*16 -- left piece of a hill of ground as well
			    	6+8*16 --  right upper peice of a hill
			    	6+11*16 -- right upper peice of a hill on earth
			    	2+2*16 --  animated coin
			    	4+2+1*16 -- a rock with animated question symbol with power up
			    	4+1+1*16 -- a rock with animated question symbol with coin
			    	2+1*16 -- brick with power up. when broken becomes a rock
			    	1+1*16 -- brick with power coin. when broken becomes a rock
			    	0+1*16 -- break brick
			    	1+10*16 -- earth, bottom piece
			    	1+8*16 --  earth, upper piece
			    	3+10*16 -- piece of earth
			    	3+11*16 -- piece of earth
			    	2+8*16 -- right part of earth
			    	0+8*16 -- left upper part of earth
			    	3+8*16 -- piece of earth
			 	  	2+10*16 -- right bottomp iece of earth
			    	0+10*16 -- left bottom piece of earth
					 */
					if(datum != 1 && level.getBlock(x, y) != 14) {
						if(datum == GeneralizerLevelScene.BREAKABLE_BRICK) {
							level.setBlock(x, y, (byte)(0 + 1 * 16));
						} else if(datum == GeneralizerLevelScene.UNBREAKABLE_BRICK) {
							level.setBlock(x, y, (byte)(1 + 9 * 16));
						} else if(datum == GeneralizerLevelScene.BORDER_CANNOT_PASS_THROUGH) {
							level.setBlock(x, y, (byte)(1 + 9 * 16));
						} else if(datum == GeneralizerLevelScene.FLOWER_POT) {
							level.setBlock(x, y, (byte)(1 + 8 * 16));
						} else if(datum == GeneralizerLevelScene.CANNON_MUZZLE) {
							//level.setBlock(x, y, (byte)(14 + 0 * 16));
							level.setBlock(x, y, (byte)(1 + 8 * 16));
						} else if(datum == GeneralizerLevelScene.CANNON_TRUNK) {
							level.setBlock(x, y, (byte)(14 + 1 * 16));
						} else if(datum == GeneralizerLevelScene.BORDER_HILL) {
							level.setBlock(x, y, (byte)(4+11*16));
						} else if(datum == GeneralizerLevelScene.COIN_ANIM) {
							//level.setBlock(x, y, (byte)(2 + 2 * 16));
							level.setBlock(x, y, (byte)0); // todo: コインは見にくいのでとりあえず無しで．
						} else {
							level.setBlock(x, y, datum);
						}
					}
				}
			}
		}
		return false;
	}

	// ここに与えられる敵座標は，マリオからの相対座標であることに注意せよ
	public void updateSpriteInfo(ArrayList<EnemyInfo> enemyInfo, ArrayList<SpriteInfo> spriteInfo) {
		ArrayList<Sprite> newSprites = new ArrayList<Sprite>();
		boolean[] used = new boolean[sprites.size()];
		for(EnemyInfo info : enemyInfo) {
			float x = info.getX();
			float y = info.getY();
			float xa = info.getXA();
			float ya = info.getYA();
			int kind = info.getKind();
			int facing = info.getFacing();

			if((DEBUG_LEVEL & DEBUG_ENEMY) > 0) {
				String mes = "[LevelScene updateEnemy]: (x, y, xa, ya, kind) == (";
				mes += String.format("%.2f, %.2f, %.2f, %.2f, %s", x, y, xa, ya, Sprite.getNameByKind(kind));
				System.out.println(mes);
			}

			boolean winged = false;
			switch(kind) {
			case(Sprite.KIND_GOOMBA_WINGED):
			case(Sprite.KIND_GREEN_KOOPA):
			case(Sprite.KIND_GREEN_KOOPA_WINGED):
			case(Sprite.KIND_RED_KOOPA_WINGED):
			case(Sprite.KIND_SPIKY_WINGED):
				winged = true;
				break;
			}

			Sprite sprite;
			if(kind == Sprite.KIND_ENEMY_FLOWER) {
				int mapX = (int)(x / 16);
				int mapY = (int)(y / 16);
				sprite = new FlowerEnemy(this, (int)x, (int)256, mapX, mapY); // 初期 y 座標は startY になるので
				sprite.y = y;
				sprite.xa = info.getXA();
				sprite.ya = info.getYA();
			} else if(kind == Sprite.KIND_WAVE_GOOMBA) { // sideWaysCounter を元データから取れないので，前のやつを利用する必要あり．
				sprite = null; // エラー回避
				boolean found = false;
				final float delta = 2.0f;
				for(int i = 0; i < sprites.size(); ++i) {
					Sprite old = sprites.get(i);
					if(used[i] || old.kind != Sprite.KIND_WAVE_GOOMBA) {
						continue;
					}
					float dist = (float)Math.hypot(x - old.x, y - old.y);
					if(dist < delta) {
						sprite = old;
						used[i] = found = true;
						break;
					}
				}
				if(!found) {
					sprite = new WaveGoomba(this, (int)x, (int)y, facing, (int)(x / 16), (int)(y / 16));
				}
			} else if(kind == Sprite.KIND_SHELL) {
				sprite = new Shell(this, x, y, 0);
			} else {
				sprite = new Enemy(this, (int)x, (int)y, facing, kind, winged, (int)(x / 16), (int)(y / 16));
				sprite.xa = info.getXA();
				sprite.ya = info.getYA();
			}
			newSprites.add(sprite);
		}
		for(SpriteInfo info : spriteInfo) {
			int kind = info.getKind();
			float x = info.getX();
			float y = info.getY();
			float xa = info.getXA();
			float ya = info.getYA();

			Sprite sprite;
			if(kind == Sprite.KIND_MARIO) {
				this.mario.x = x;
				this.mario.y = y;
				this.mario.xa = xa;
				this.mario.ya = ya;
			} else if(kind == Sprite.KIND_COIN_ANIM || kind == Sprite.KIND_PRINCESS) {
				continue;
			} else if(kind == Sprite.KIND_MUSHROOM) {
				continue; // todo
			} else if(kind == Sprite.KIND_GREEN_MUSHROOM) {
				continue; // todo
			} else if(kind == Sprite.KIND_FIRE_FLOWER) {
				continue; // todo
			} else if(kind == Sprite.KIND_FIREBALL) {
				//newSprites.add(new Fireball(this, x, y, facing));
			} else if(kind == Sprite.KIND_BULLET_BILL) {
				int dir = -1;
				if(xa > 0) {
					dir = 1;
				}
				sprite = new BulletBill(this, x, y, dir);
				sprite.xa = info.getXA();
				sprite.ya = info.getYA();
				newSprites.add(sprite);
			}
		}

		newSprites.add(mario);
		/*for(Sprite sprite : sprites) {
			if(sprite.kind == Sprite.KIND_FIREBALL) {
				newSprites.add(sprite);
			}
		}*/
		this.sprites = newSprites;
	}


	// for debug
	public void setDebugLevel(int debugLevel) {
		this.DEBUG_LEVEL = debugLevel;
	}

	void printSpritePos() {
		for(Sprite sprite : this.sprites) {
			if(sprite.kind == Sprite.KIND_MARIO || sprite.kind == Sprite.KIND_FIREBALL) {
				continue;
			}
			String mes = "[LevelScene]: (x, y, xa, ya, kind) == (";
			mes += String.format("%.2f, %.2f, %.2f, %.2f, %s", sprite.x, sprite.y, sprite.xa, sprite.ya, Sprite.getNameByKind(sprite.kind));
			System.out.println(mes);
		}
	}
	void printMap() {
		int marioX = this.mario.mapX;
		int marioY = this.mario.mapY;
		System.out.println("[LevelScene]: mario int pos = (" + marioX + ", " + marioY + ")");
		for(int y = marioY - 9; y <= marioY + 9; ++y) {
			for(int x = marioX - 9; x <= marioX + 9; ++x) {
				System.out.print(String.format("%4d ", level.getBlock(x, y)));
			}
			System.out.println();
		}
	}

}
