package gg.xp.xivsupport.gui.i18n;

import com.formdev.flatlaf.FlatLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * XIVToolHub で選んだ日本語フォントを画面とコールアウトに使う。
 * 追加フォントにはかなしか収録していないものもあるため、StyleContext の合成フォントにして
 * 足りない文字は標準のフォントで表示されるようにする。
 */
public final class UiFont {
	private static final Logger log = LoggerFactory.getLogger(UiFont.class);
	private static final int DEFAULT_SIZE = 13;

	private static volatile boolean registered;
	private static volatile String family;

	private UiFont() {
	}

	/**
	 * 選択フォントのファミリー名。フォントが選ばれていない、または読み込めない場合は fallback を返す。
	 */
	public static String familyOr(String fallback) {
		register();
		return family != null ? family : fallback;
	}

	/**
	 * 画面全体の既定フォントを選択フォントにする。テーマを適用した後に呼ぶ。
	 */
	public static void apply() {
		String chosen = familyOr(null);
		if (chosen == null) {
			return;
		}
		Font current = UIManager.getFont("defaultFont");
		int size = current != null ? current.getSize() : DEFAULT_SIZE;
		Font composite = StyleContext.getDefaultStyleContext().getFont(chosen, Font.PLAIN, size);
		UIManager.put("defaultFont", new FontUIResource(composite));
		FlatLaf.updateUI();
		log.info("UI font: {} ({}pt)", chosen, size);
	}

	private static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		File file = HubSettings.get().getFontFile();
		if (file == null) {
			return;
		}
		try {
			Font font = Font.createFont(Font.TRUETYPE_FONT, file);
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
			family = font.getFamily();
		}
		catch (FontFormatException | IOException e) {
			// フォントが壊れていても、既定のフォントで起動を続ける
			log.warn("Failed to load font {}", file, e);
		}
	}
}
