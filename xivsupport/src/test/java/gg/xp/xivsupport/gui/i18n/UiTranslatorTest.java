package gg.xp.xivsupport.gui.i18n;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class UiTranslatorTest {

	@Test
	public void translatesKnownStrings() {
		Assert.assertEquals(UiTranslator.translate("Plugin Settings"), "プラグイン設定");
		Assert.assertEquals(UiTranslator.translate("Events"), "イベント");
		// 後ろに値が続くラベルは、元の空白を残したまま置き換える
		Assert.assertEquals(UiTranslator.translate("Error: "), "エラー: ");
		Assert.assertEquals(UiTranslator.translate("  Events"), "  イベント");
		// 一部だけ一致する文字列は置き換えない
		Assert.assertEquals(UiTranslator.translate("Events of the day"), "Events of the day");
	}

	@Test
	public void leavesUnknownStringsAsIs() {
		Assert.assertEquals(UiTranslator.translate("Some Unknown Label"), "Some Unknown Label");
		Assert.assertNull(UiTranslator.translate(null));
	}

	@Test
	public void translatedTextIsNotTranslatedAgain() {
		// 翻訳後の文字列が再び辞書に引っかかると、表示の変更が繰り返されてしまう
		String once = UiTranslator.translate("Settings");
		Assert.assertEquals(UiTranslator.translate(once), once);
	}

	@Test
	public void unescapesNewlines() {
		Assert.assertEquals(UiTranslator.unescape("a\\nb\\tc"), "a\nb\tc");
	}

	@Test
	public void dictionaryHasNoDuplicateOrMalformedKeys() throws IOException {
		Set<String> keys = new HashSet<>();
		try (InputStream stream = UiTranslator.class.getResourceAsStream("/i18n/ja.tsv")) {
			Assert.assertNotNull(stream);
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split("\t", -1);
				Assert.assertEquals(parts.length, 2, "Line " + lineNumber + " must have exactly one tab: " + line);
				Assert.assertFalse(parts[1].isEmpty(), "Line " + lineNumber + " has no translation");
				// 前後の空白は無視して引くため、空白だけが違うキーも重複とみなす
				Assert.assertTrue(keys.add(parts[0].strip()), "Duplicate key at line " + lineNumber + ": " + parts[0]);
			}
		}
		Assert.assertEquals(UiTranslator.size(), keys.size());
	}
}
