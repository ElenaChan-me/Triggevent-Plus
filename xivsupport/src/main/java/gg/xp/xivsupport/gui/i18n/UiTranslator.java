package gg.xp.xivsupport.gui.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 画面の文字列を選択した言語（日本語・韓国語・中国語）に置き換える。
 * 本家のソースの文字列を直接書き換えると更新の取り込みが難しくなるため、
 * 部品が画面に追加・変更されたときに辞書（/i18n/言語コード.tsv）を引いて表示だけを差し替える。
 * 辞書に無い文字列と、英語を選んだ場合は本家の英語のまま表示する。
 */
public final class UiTranslator {
	private static final Logger log = LoggerFactory.getLogger(UiTranslator.class);

	private static final String DICTIONARY_FORMAT = "/i18n/%s.tsv";
	// コンテンツ名やコールアウトの説明（技名）をゲームデータから自動で作った辞書。手で書いた辞書を優先する
	private static final String GENERATED_DICTIONARY_FORMAT = "/i18n/%s-callouts.tsv";
	// -Dtriggevent.translate=false で英語表示に戻せる（不具合の切り分け用）
	private static final String DISABLE_PROPERTY = "triggevent.translate";
	private static final String HOOKED_KEY = "triggevent.i18n.hooked";
	private static final String TOOLTIP_KEY = "ToolTipText";

	private static final Map<String, String> dictionary = load(HubSettings.get().getLanguage());
	private static volatile boolean installed;

	private UiTranslator() {
	}

	/**
	 * 辞書にあれば日本語を、無ければ元の文字列を返す。
	 * 「Error: 」のように後ろに値が続くラベルがあるため、前後の空白は辞書から外して元の文字列のものを引き継ぐ。
	 */
	public static String translate(String text) {
		if (text == null) {
			return null;
		}
		String core = text.strip();
		if (core.isEmpty()) {
			return text;
		}
		String translated = dictionary.get(core);
		if (translated == null) {
			return text;
		}
		int start = text.indexOf(core);
		return text.substring(0, start) + translated + text.substring(start + core.length());
	}

	public static int size() {
		return dictionary.size();
	}

	public static synchronized void install() {
		if (installed || dictionary.isEmpty() || "false".equalsIgnoreCase(System.getProperty(DISABLE_PROPERTY))) {
			return;
		}
		installed = true;
		Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
			try {
				if (event instanceof ContainerEvent ce && ce.getID() == ContainerEvent.COMPONENT_ADDED) {
					translateTree(ce.getChild());
					if (ce.getContainer() instanceof JTabbedPane tabs) {
						translateTabs(tabs);
					}
				}
				else if (event instanceof WindowEvent we && we.getID() == WindowEvent.WINDOW_OPENED) {
					translateWindowTitle(we.getWindow());
					translateTree(we.getWindow());
				}
			}
			catch (Throwable t) {
				// 翻訳に失敗しても画面の動作は止めない
				log.warn("Translation failed", t);
			}
		}, AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK);
		log.info("UI translation enabled ({} entries)", dictionary.size());
	}

	private static void translateTree(Component component) {
		if (component == null) {
			return;
		}
		if (component instanceof JComponent jc) {
			// 一度処理した部品の子は、追加されたときにそれぞれ処理されるため辿り直さない
			if (Boolean.TRUE.equals(jc.getClientProperty(HOOKED_KEY))) {
				return;
			}
			jc.putClientProperty(HOOKED_KEY, Boolean.TRUE);
			hook(jc);
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				translateTree(child);
			}
		}
	}

	private static void hook(JComponent jc) {
		if (jc instanceof JLabel label) {
			translateLabel(label);
			label.addPropertyChangeListener("text", e -> translateLabel(label));
		}
		else if (jc instanceof AbstractButton button) {
			translateButton(button);
			button.addPropertyChangeListener("text", e -> translateButton(button));
		}
		if (jc instanceof JTabbedPane tabs) {
			translateTabs(tabs);
			tabs.addChangeListener((ChangeEvent e) -> translateTabs(tabs));
		}
		if (jc instanceof JTable table) {
			hookTable(table);
		}
		translateTooltip(jc);
		jc.addPropertyChangeListener(TOOLTIP_KEY, e -> translateTooltip(jc));
		translateBorder(jc.getBorder());
		PropertyChangeListener borderListener = e -> translateBorder(jc.getBorder());
		jc.addPropertyChangeListener("border", borderListener);
	}

	private static void translateLabel(JLabel label) {
		String text = label.getText();
		String translated = translate(text);
		// 置き換え後の文字列は辞書に無いので、ここで再び呼ばれても止まる
		if (translated != null && !translated.equals(text)) {
			label.setText(translated);
		}
	}

	private static void translateButton(AbstractButton button) {
		String text = button.getText();
		String translated = translate(text);
		if (translated != null && !translated.equals(text)) {
			button.setText(translated);
		}
	}

	private static void translateTooltip(JComponent jc) {
		if (jc.getClientProperty(TOOLTIP_KEY) instanceof String tooltip) {
			String translated = translate(tooltip);
			if (!translated.equals(tooltip)) {
				jc.setToolTipText(translated);
			}
		}
	}

	private static void translateTabs(JTabbedPane tabs) {
		for (int i = 0; i < tabs.getTabCount(); i++) {
			String title = tabs.getTitleAt(i);
			String translated = translate(title);
			if (translated != null && !translated.equals(title)) {
				tabs.setTitleAt(i, translated);
			}
		}
	}

	private static void translateBorder(Border border) {
		if (border instanceof TitledBorder titled) {
			String title = titled.getTitle();
			String translated = translate(title);
			if (translated != null && !translated.equals(title)) {
				titled.setTitle(translated);
			}
		}
		else if (border instanceof CompoundBorder compound) {
			translateBorder(compound.getOutsideBorder());
			translateBorder(compound.getInsideBorder());
		}
	}

	private static void hookTable(JTable table) {
		translateColumns(table.getColumnModel());
		table.getColumnModel().addColumnModelListener(new HeaderTranslator(table));
		table.addPropertyChangeListener("columnModel", e -> {
			TableColumnModel model = table.getColumnModel();
			translateColumns(model);
			model.addColumnModelListener(new HeaderTranslator(table));
		});
	}

	private static void translateColumns(TableColumnModel model) {
		for (TableColumn column : Collections.list(model.getColumns())) {
			if (column.getHeaderValue() instanceof String header) {
				String translated = translate(header);
				if (!translated.equals(header)) {
					column.setHeaderValue(translated);
				}
			}
		}
	}

	private record HeaderTranslator(JTable table) implements TableColumnModelListener {
		@Override
		public void columnAdded(TableColumnModelEvent e) {
			translateColumns(table.getColumnModel());
		}

		@Override
		public void columnRemoved(TableColumnModelEvent e) {
		}

		@Override
		public void columnMoved(TableColumnModelEvent e) {
		}

		@Override
		public void columnMarginChanged(ChangeEvent e) {
		}

		@Override
		public void columnSelectionChanged(ListSelectionEvent e) {
		}
	}

	private static void translateWindowTitle(Window window) {
		if (window instanceof Frame frame) {
			String translated = translate(frame.getTitle());
			if (translated != null && !translated.equals(frame.getTitle())) {
				frame.setTitle(translated);
			}
		}
		else if (window instanceof Dialog dialog) {
			String translated = translate(dialog.getTitle());
			if (translated != null && !translated.equals(dialog.getTitle())) {
				dialog.setTitle(translated);
			}
		}
	}

	static Map<String, String> load(String language) {
		Map<String, String> map = new HashMap<>();
		if (language == null || "en".equals(language)) {
			return Map.of();
		}
		loadInto(map, GENERATED_DICTIONARY_FORMAT.formatted(language), false);
		loadInto(map, DICTIONARY_FORMAT.formatted(language), true);
		return Map.copyOf(map);
	}

	private static void loadInto(Map<String, String> map, String resource, boolean required) {
		try (InputStream stream = UiTranslator.class.getResourceAsStream(resource)) {
			if (stream == null) {
				if (required) {
					log.warn("Translation dictionary not found: {}", resource);
				}
				return;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				int tab = line.indexOf('\t');
				if (tab <= 0) {
					continue;
				}
				String key = unescape(line.substring(0, tab)).strip();
				String value = unescape(line.substring(tab + 1)).strip();
				if (!key.isEmpty() && !value.isEmpty()) {
					map.put(key, value);
				}
			}
		}
		catch (IOException e) {
			log.error("Error loading translation dictionary {}", resource, e);
		}
	}

	static String unescape(String text) {
		return text.replace("\\n", "\n").replace("\\t", "\t");
	}
}
