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

import task4.engine.LevelScene;
import task4.level.SpriteTemplate;

public class Sprite implements Cloneable
{
	public static final int KIND_NONE = 0;
	public static final int KIND_MARIO = -31;
	public static final int KIND_GOOMBA = 80;
	public static final int KIND_GOOMBA_WINGED = 95;
	public static final int KIND_RED_KOOPA = 82;
	public static final int KIND_RED_KOOPA_WINGED = 97;
	public static final int KIND_GREEN_KOOPA = 81;
	public static final int KIND_GREEN_KOOPA_WINGED = 96;
	public static final int KIND_BULLET_BILL = 84;
	public static final int KIND_SPIKY = 93;
	public static final int KIND_SPIKY_WINGED = 99;
	//    public static final int KIND_ENEMY_FLOWER = 11;
	public static final int KIND_ENEMY_FLOWER = 91;
	public static final int KIND_WAVE_GOOMBA = 98; // TODO: !H!: same
	public static final int KIND_SHELL = 13;
	public static final int KIND_MUSHROOM = 2;
	public static final int KIND_GREEN_MUSHROOM = 9;
	public static final int KIND_PRINCESS = 49;
	public static final int KIND_FIRE_FLOWER = 3;
	public static final int KIND_PARTICLE = 21;
	public static final int KIND_SPARCLE = 22;
	public static final int KIND_COIN_ANIM = 1;
	public static final int KIND_FIREBALL = 25;

	public static final int KIND_UNDEF = -42;

	public byte kind = KIND_UNDEF;

	protected static final float GROUND_INERTIA = 0.89f;
	protected static final float AIR_INERTIA = 0.89f;

	public float xOld, yOld, x, y, xa, ya;
	public int mapX, mapY;

	protected static float creaturesGravity;
	protected static float windCoeff = 0;
	protected static float iceCoeff = 0;

	public boolean visible = true;

	public int layer = 1;

	// append
	public LevelScene world;
	public int facing;

	public SpriteTemplate spriteTemplate; // 相互参照やめてほしい

	@Override
	public Sprite clone() throws CloneNotSupportedException {
		Sprite s = (Sprite)super.clone();
		if(spriteTemplate != null) {
			s.spriteTemplate = (SpriteTemplate)this.spriteTemplate.clone();
		}
		return s;
	}


	public static void setCreaturesGravity(final float creaturesGravity)
	{
		Sprite.creaturesGravity = creaturesGravity;
	}

	public static void setCreaturesWind(final float wind)
	{
		Sprite.windCoeff = wind;
	}

	public static void setCreaturesIce(final float ice)
	{
		Sprite.iceCoeff = ice;
	}


	public static String getNameByKind(final int kind)
	{
		switch (kind)
		{
		case Sprite.KIND_MARIO:
			return "Mario";
		case Sprite.KIND_GOOMBA:
			return "Goomba";
		case Sprite.KIND_GOOMBA_WINGED:
			return "Goomba Winged";
		case Sprite.KIND_RED_KOOPA:
			return "Red Koopa";
		case Sprite.KIND_RED_KOOPA_WINGED:
			return "Red Koopa Winged";
		case Sprite.KIND_GREEN_KOOPA:
			return "Green Koopa";
		case Sprite.KIND_GREEN_KOOPA_WINGED:
			return "Green Koopa Winged";
		case Sprite.KIND_SPIKY:
			return "Spiky";
		case Sprite.KIND_SPIKY_WINGED:
			return "Spiky Winged";
		case Sprite.KIND_BULLET_BILL:
			return "Bullet";
		case Sprite.KIND_ENEMY_FLOWER:
			return "Flower";
		case Sprite.KIND_SHELL:
			return "Shell";
		case Sprite.KIND_MUSHROOM:
			return "Mushroom";
		case Sprite.KIND_FIRE_FLOWER:
			return "Power up Flower";
		case Sprite.KIND_GREEN_MUSHROOM:
			return "Green mushroom";
		case Sprite.KIND_WAVE_GOOMBA:
			return "Wave Goomba";
		case Sprite.KIND_FIREBALL:
			return "Fireball";
		case Sprite.KIND_PRINCESS:
            return "Princess";
		}

		return "Unknown";
	}

	public float iceScale(final float ice)
	{
		return ice;
	}

	public float windScale(final float wind, int facing)
	{
		return facing == 1 ? wind : -wind;
	}

	public void move()
	{
		x += xa;
		y += ya;
	}

	public final void tick()
	{
		xOld = x;
		yOld = y;
		move();
		mapY = (int) (y / 16);
		mapX = (int) (x / 16);
	}

	public final void tickNoMove()
	{
		xOld = x;
		yOld = y;
	}

	public void collideCheck()
	{
	}

	public void bumpCheck(final int xTile, final int yTile)
	{
	}

	public boolean shellCollideCheck(final Shell shell)
	{
		return false;
	}

	public void release(final Mario mario)
	{
	}

	public boolean fireballCollideCheck(Fireball fireball)
	{
		return false;
	}

	public boolean isDead()
	{
		return spriteTemplate != null && spriteTemplate.isDead;
	}
}

