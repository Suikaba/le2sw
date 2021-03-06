/*
 * Copyright (c) 2009-2010, Sergey Karakovskiy and Julian Togelius
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Mario AI nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
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

import ch.idsia.benchmark.mario.engine.GlobalOptions;
import task4.engine.LevelScene;


public class Enemy extends Sprite
{
	public static final int IN_FILE_POS_RED_KOOPA = 0;
	public static final int IN_FILE_POS_GREEN_KOOPA = 1;
	public static final int IN_FILE_POS_GOOMBA = 2;
	public static final int IN_FILE_POS_SPIKY = 3;
	public static final int IN_FILE_POS_FLOWER = 4;
	public static final int POSITION_WAVE_GOOMBA = 7;

	public boolean onGround = false;

	int width = 4;
	int height = 24;

	public float yaa = 1;

	public int facing;
	public int deadTime = 0;
	public boolean flyDeath = false;

	public boolean avoidCliffs = true;

	public boolean winged = true;
	public int wingTime = 0;

	public float yaw = 1;

	public boolean noFireballDeath;

	public Enemy(LevelScene levelScene, int x, int y, int dir, int type, boolean winged, int mapX, int mapY)
	{
		kind = (byte) type;
		this.winged = winged;

		this.x = x;
		this.y = y;
		this.mapX = mapX;
		this.mapY = mapY;

		this.world = levelScene;

		yaa = creaturesGravity * 2;
		yaw = creaturesGravity == 1 ? 1 : 0.3f * creaturesGravity;

		switch (type)
		{
		case KIND_GOOMBA:
		case KIND_GOOMBA_WINGED:
		case KIND_SPIKY:
		case KIND_SPIKY_WINGED:
		case KIND_ENEMY_FLOWER:
		case KIND_WAVE_GOOMBA:
			height = 12;
			break;
		}

		avoidCliffs = kind == KIND_RED_KOOPA;

		noFireballDeath = (kind == KIND_SPIKY || kind == KIND_SPIKY_WINGED);
		facing = dir;
		if (facing == 0) {
			facing = 1;
		}
	}

	@Override
	public Enemy clone() throws CloneNotSupportedException {
		Enemy clone = (Enemy)super.clone();
		clone.world = null;
		return clone;
	}

	public void collideCheck()
	{
		if (deadTime != 0) {
			return;
		}

		float xMarioD = world.mario.x - x;
		float yMarioD = world.mario.y - y;

		if (xMarioD > -width * 2 - 4 && xMarioD < width * 2 + 4) {
			if (yMarioD > -height && yMarioD < world.mario.height) {
				if ((kind != KIND_SPIKY && kind != KIND_SPIKY_WINGED && kind != KIND_ENEMY_FLOWER) && world.mario.ya > 0 && yMarioD <= 0 && (!world.mario.isOnGround() || !world.mario.wasOnGround())) {
					world.mario.stomp(this);
					if (winged) {
						winged = false;
						ya = 0;

					} else {
						if (spriteTemplate != null) {
                            spriteTemplate.isDead = true;
                        }
						deadTime = 10;
						winged = false;

						if (kind == KIND_RED_KOOPA || kind == KIND_RED_KOOPA_WINGED) {
							world.addSprite(new Shell(world, x, y, 0));
						} else if (kind == KIND_GREEN_KOOPA || kind == KIND_GREEN_KOOPA_WINGED) {
							world.addSprite(new Shell(world, x, y, 1));
						}
						++this.world.killedCreaturesTotal;
						++this.world.killedCreaturesByStomp;
					}

				} else {
					world.mario.getHurt(this.kind);
				}
			}
		}
	}

	public void move()
	{
		wingTime++;
		if (deadTime > 0) {
			deadTime--;

			if (deadTime == 0) {
				deadTime = 1;
				world.removeSprite(this);
			}

			if (flyDeath) {
				x += xa;
				y += ya;
				ya *= 0.95;
				ya += 1;
			}
			return;
		}

		float sideWaysSpeed = 1.75f;

		if (xa > 2) {
			facing = 1;
		} else if (xa < -2) {
			facing = -1;
		}

		xa = facing * sideWaysSpeed;

		if (!move(xa, 0))  {
			facing = -facing;
		}
		onGround = false;
		move(0, ya);

		ya *= winged ? 0.95f : 0.85f;
		if (onGround) {
			xa *= (GROUND_INERTIA + windScale(windCoeff, facing) + iceScale(iceCoeff));
		} else {
			xa *= (AIR_INERTIA + windScale(windCoeff, facing) + iceScale(iceCoeff));
		}

		if (!onGround) {
			if (winged) {
				ya += 0.6f * yaw;
			} else {
				ya += yaa;
			}
		} else if (winged) {
			ya = -10;
		}

	}

	public boolean move(float xa, float ya)
	{

		while (xa > 8) {
			if (!move(8, 0)) return false;
			xa -= 8;
		}
		while (xa < -8) {
			if (!move(-8, 0)) return false;
			xa += 8;
		}
		while (ya > 8) {
			if (!move(0, 8)) return false;
			ya -= 8;
		}
		while (ya < -8) {
			if (!move(0, -8)) return false;
			ya += 8;
		}


		boolean collide = false;
		if (ya > 0) {
			if (isBlocking(x + xa - width, y + ya, xa, 0)) {
				collide = true;
			} else if (isBlocking(x + xa + width, y + ya, xa, 0)) {
				collide = true;
			} else if (isBlocking(x + xa - width, y + ya + 1, xa, ya)) {
				collide = true;
			} else if (isBlocking(x + xa + width, y + ya + 1, xa, ya)) {
				collide = true;
			}
		}
		if (ya < 0) {
			if (isBlocking(x + xa, y + ya - height, xa, ya)) {
				collide = true;
			} else if (collide || isBlocking(x + xa - width, y + ya - height, xa, ya)) {
				collide = true;
			} else if (collide || isBlocking(x + xa + width, y + ya - height, xa, ya)) {
				collide = true;
			}
		}
		if (xa > 0) {
			if (isBlocking(x + xa + width, y + ya - height, xa, ya)) {
				collide = true;
			}
			if (isBlocking(x + xa + width, y + ya - height / 2, xa, ya)) {
				collide = true;
			}
			if (isBlocking(x + xa + width, y + ya, xa, ya)) {
				collide = true;
			}

			if (avoidCliffs && onGround && !world.level.isBlocking((int) ((x + xa + width) / 16), (int) ((y) / 16 + 1), xa, 1)) {
				collide = true;
			}
		}
		if (xa < 0) {
			if (isBlocking(x + xa - width, y + ya - height, xa, ya)) {
				collide = true;
			}
			if (isBlocking(x + xa - width, y + ya - height / 2, xa, ya)) {
				collide = true;
			}
			if (isBlocking(x + xa - width, y + ya, xa, ya)) {
				collide = true;
			}

			if (avoidCliffs && onGround && !world.level.isBlocking((int) ((x + xa - width) / 16), (int) ((y) / 16 + 1), xa, 1)) {
				collide = true;
			}
		}

		if (collide) {
			if (xa < 0) {
				x = (int) ((x - width) / 16) * 16 + width;
				this.xa = 0;
			}
			if (xa > 0) {
				x = (int) ((x + width) / 16 + 1) * 16 - width - 1;
				this.xa = 0;
			}
			if (ya < 0) {
				y = (int) ((y - height) / 16) * 16 + height;
				//                jumpTime = 0;
				this.ya = 0;
			}
			if (ya > 0) {
				y = (int) (y / 16 + 1) * 16 - 1;
				onGround = true;
			}
			return false;

		} else {
			if (GlobalOptions.areFrozenCreatures) {
				return true;
			}

			x += xa;
			y += ya;
			return true;
		}
	}

	private boolean isBlocking(float _x, float _y, float xa, float ya)
	{
		int x = (int) (_x / 16);
		int y = (int) (_y / 16);
		if (x == (int) (this.x / 16) && y == (int) (this.y / 16)) {
			return false;
		}

		boolean blocking = world.level.isBlocking(x, y, xa, ya);

		return blocking;
	}

	public boolean shellCollideCheck(Shell shell)
	{
		if (deadTime != 0) {
			return false;
		}

		float xD = shell.x - x;
		float yD = shell.y - y;

		if (xD > -16 && xD < 16) {
			if (yD > -height && yD < shell.height) {
				xa = shell.facing * 2;
				ya = -5;
				flyDeath = true;
				if (spriteTemplate != null) spriteTemplate.isDead = true;
				deadTime = 100;
				winged = false;
				++this.world.killedCreaturesTotal;
				++this.world.killedCreaturesByShell;
				return true;
			}
		}
		return false;
	}

	public boolean fireballCollideCheck(Fireball fireball)
	{
		if (deadTime != 0) {
			return false;
		}

		float xD = fireball.x - x;
		float yD = fireball.y - y;

		if (xD > -16 && xD < 16) {
			if (yD > -height && yD < fireball.height) {
				if (noFireballDeath) {
					return true;
				}

				xa = fireball.facing * 2;
				ya = -5;
				flyDeath = true;
				if (spriteTemplate != null) spriteTemplate.isDead = true;
				deadTime = 100;
				winged = false;
				++this.world.killedCreaturesTotal;
				++this.world.killedCreaturesByFireBall;
				return true;
			}
		}
		return false;
	}

	public void bumpCheck(int xTile, int yTile)
	{
		if (deadTime != 0) {
			return;
		}

		if (x + width > xTile * 16 && x - width < xTile * 16 + 16 && yTile == (int) ((y - 1) / 16)) {
			xa = -world.mario.facing * 2;
			ya = -5;
			flyDeath = true;
			if (spriteTemplate != null) {
				spriteTemplate.isDead = true;
			}
			deadTime = 100;
			winged = false;
		}
	}


	/**
	 * Spriteの種類が敵か判定
	 */
	public static boolean isEnemy(int kind) {
		switch (kind)
		{
		case KIND_GOOMBA:
		case KIND_GOOMBA_WINGED:
		case KIND_RED_KOOPA:
		case KIND_RED_KOOPA_WINGED:
		case KIND_GREEN_KOOPA:
		case KIND_GREEN_KOOPA_WINGED:
		case KIND_SPIKY:
		case KIND_SPIKY_WINGED:
		case KIND_ENEMY_FLOWER:
		case KIND_WAVE_GOOMBA:
			return true;
		}
		return false;
	}

}
