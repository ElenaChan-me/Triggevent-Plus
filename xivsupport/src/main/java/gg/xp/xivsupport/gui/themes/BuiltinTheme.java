package gg.xp.xivsupport.gui.themes;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import gg.xp.xivsupport.gui.i18n.UiTranslator;
import gg.xp.xivsupport.gui.util.HasFriendlyName;

import javax.swing.*;
import java.util.Map;
import java.util.function.Supplier;

/**
 * XIVToolHub・Glamaholic JP と共通の2つの配色。色の値はハブの Themes/Dark.xaml・Light.xaml と揃えている。
 * 列挙子の名前は保存済みの設定と互換を保つため、元の DARK / LIGHT のままにしている。
 */
public enum BuiltinTheme implements HasFriendlyName {

	DARK("Black / Red", "dark", FlatDarkLaf::new, Map.of(
			"@background", "#121212",
			"@foreground", "#EDEDED",
			"@accentColor", "#E23B44",
			"@selectionBackground", "#5A1E23",
			"@selectionInactiveBackground", "#3A1C1F",
			"Component.borderColor", "#363636",
			"TabbedPane.hoverColor", "#262626")),
	LIGHT("White / Light Blue", "light", FlatLightLaf::new, Map.of(
			"@background", "#F5FAFD",
			"@foreground", "#1D2B36",
			"@accentColor", "#2EA7E0",
			"@selectionBackground", "#BFE6F8",
			"@selectionForeground", "#1D2B36",
			"@selectionInactiveBackground", "#D4EEFA",
			"Component.borderColor", "#CBE2EF",
			"TabbedPane.hoverColor", "#E6F4FB"));

	private final String friendlyName;
	private final String hubCode;
	private final Supplier<FlatLaf> lafSupplier;
	private final Map<String, String> colors;

	BuiltinTheme(String friendlyName, String hubCode, Supplier<FlatLaf> lafSupplier, Map<String, String> colors) {
		this.friendlyName = friendlyName;
		this.hubCode = hubCode;
		this.lafSupplier = lafSupplier;
		this.colors = colors;
	}

	@Override
	public String getFriendlyName() {
		// コンボボックスの項目は自動翻訳の対象外なので、ここで訳す
		return UiTranslator.translate(friendlyName);
	}

	public LookAndFeel getLaf() {
		// 追加の色は Look and Feel の初期化時に読まれるため、生成より先に設定する
		FlatLaf.setGlobalExtraDefaults(colors);
		return lafSupplier.get();
	}

	/**
	 * XIVToolHub の設定ファイルに書かれるテーマのコード（dark / light）から探す。見つからなければ null。
	 */
	public static BuiltinTheme fromHubCode(String code) {
		for (BuiltinTheme theme : values()) {
			if (theme.hubCode.equals(code)) {
				return theme;
			}
		}
		return null;
	}
}
