
package task4.sprites;

/**
 * 敵情報の橋渡しをする
 */
public class EnemyInfo {
	private float x, y;
	private float xa, ya;
	private int kind;
	private int facing;

	public EnemyInfo(float x, float y, float xa, float ya, int kind, int facing) {
		this.x = x;
		this.y = y;
		this.xa = xa;
		this.ya = ya;
		this.kind = kind;
		this.facing = facing;
	}

	public float getX() {
		return x;
	}
	public float getY() {
		return y;
	}
	public float getXA() {
		return xa;
	}
	public float getYA() {
		return ya;
	}
	public int getKind() {
		return kind;
	}
	public int getFacing() {
		return facing;
	}
}
