package task4.sprites;

/**
 * 本体のゲームエンジン側のスプライト情報と，シミュレータのスプライト情報の同期のための橋渡しをするデータ
 * @note ワークアラウンド的解決なので将来的には本体側で解決をしてほしい．
 */
public class SpriteInfo {
	private float x, y;
	private float xa, ya;
	private int kind;

	public SpriteInfo(float x, float y, float xa, float ya, int kind) {
		this.x = x;
		this.y = y;
		this.xa = xa;
		this.ya = ya;
		this.kind = kind;
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
}
