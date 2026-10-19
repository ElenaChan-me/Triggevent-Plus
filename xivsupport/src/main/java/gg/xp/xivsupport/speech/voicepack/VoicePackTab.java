package gg.xp.xivsupport.speech.voicepack;

import gg.xp.reevent.events.EventMaster;
import gg.xp.reevent.scan.ScanMe;
import gg.xp.xivsupport.gui.TitleBorderFullsizePanel;
import gg.xp.xivsupport.gui.WrapLayout;
import gg.xp.xivsupport.gui.extra.PluginTab;
import gg.xp.xivsupport.gui.i18n.HubSettings;
import gg.xp.xivsupport.persistence.gui.BooleanSettingGui;
import gg.xp.xivsupport.persistence.gui.IntSettingSpinner;
import gg.xp.xivsupport.persistence.gui.StringSettingGui;
import gg.xp.xivsupport.speech.TtsRequest;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * ボイスパックの設定と試し再生。
 */
@ScanMe
public class VoicePackTab implements PluginTab {

	private final VoicePackPlayer player;
	private final EventMaster master;
	private final CalloutTranslator translator;
	private final NameVoiceRequester names;
	private JLabel status;
	private JTextField folderField;
	private JComboBox<Path> packCombo;
	// 一覧を作り直している間の選択の変化を、利用者の操作として扱わない
	private boolean refreshingPacks;

	public VoicePackTab(VoicePackPlayer player, EventMaster master, CalloutTranslator translator, NameVoiceRequester names) {
		this.player = player;
		this.master = master;
		this.translator = translator;
		this.names = names;
	}

	@Override
	public String getTabName() {
		return "Voice Pack";
	}

	@Override
	public Component getTabContents() {
		TitleBorderFullsizePanel outer = new TitleBorderFullsizePanel("Voice Pack");
		outer.setLayout(new BorderLayout());
		// 縦に引き伸ばされないよう、上に詰めて並べる
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.PAGE_AXIS));
		outer.add(content, BorderLayout.NORTH);

		JPanel options = new JPanel(new WrapLayout(FlowLayout.LEFT));
		options.add(new BooleanSettingGui(player.getEnabled(), "Use voice pack").getComponent());
		options.add(new BooleanSettingGui(player.getJapaneseFallback(), "Read callouts without recordings in Japanese via ACT TTS").getComponent());
		options.add(new IntSettingSpinner(player.getVolume(), "Volume (%)").getComponent());
		content.add(options);

		// 名前の音声は VoiceGen の画面（python -m voicegen ui）を起動している間だけ作れる
		JPanel nameOptions = new JPanel(new WrapLayout(FlowLayout.LEFT));
		nameOptions.add(new BooleanSettingGui(names.getEnabled(), "Generate party member name clips with VoiceGen").getComponent());
		nameOptions.add(new StringSettingGui(names.getUrl(), "VoiceGen URL", () -> names.getEnabled().get()).getComponent());
		content.add(nameOptions);

		// 日本語の対応表は表示言語が日本語のときだけ使うので、それ以外では設定も出さない
		if (translator.isJapanese()) {
			JPanel translation = new JPanel(new WrapLayout(FlowLayout.LEFT));
			translation.add(new BooleanSettingGui(translator.getDisplayEnabled(), "Show callout text in Japanese").getComponent());
			translation.add(new BooleanSettingGui(translator.getSpeechEnabled(), "Read callouts in Japanese via ACT TTS when no voice pack is used").getComponent());
			content.add(translation);
		}

		JPanel folderPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));
		folderPanel.add(new JLabel("Voice pack folder"));
		folderField = new JTextField(player.getFolder().get(), 40);
		folderField.setEditable(false);
		folderPanel.add(folderField);
		JButton browse = new JButton("Browse...");
		browse.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser(player.getFolder().get().isBlank() ? null : new File(player.getFolder().get()));
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (chooser.showOpenDialog(outer) == JFileChooser.APPROVE_OPTION) {
				selectFolder(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		folderPanel.add(browse);
		JButton reload = new JButton("Reload");
		reload.addActionListener(e -> {
			player.reload();
			refreshPacks();
			updateStatus();
		});
		folderPanel.add(reload);
		content.add(folderPanel);

		// 同じフォルダに並べた別のパック（本気用・かわいい用など）をすぐに切り替えられるようにする
		JPanel switchPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));
		switchPanel.add(new JLabel("Switch voice pack"));
		packCombo = new JComboBox<>();
		packCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				Object label = value instanceof Path path ? path.getFileName() : value;
				return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
			}
		});
		packCombo.addActionListener(e -> {
			if (!refreshingPacks && packCombo.getSelectedItem() instanceof Path path) {
				selectFolder(path.toString());
			}
		});
		switchPanel.add(packCombo);
		content.add(switchPanel);
		refreshPacks();

		status = new JLabel();
		JPanel statusPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));
		statusPanel.add(status);
		content.add(statusPanel);
		updateStatus();

		// 実際のコールアウトと同じ経路で鳴らし、差し替えが効くかを確かめられるようにする
		JPanel testPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));
		testPanel.add(new JLabel("Callout text to test"));
		JTextField testField = new JTextField("Raidwide", 30);
		testPanel.add(testField);
		JButton play = new JButton("Play");
		play.addActionListener(e -> {
			String text = testField.getText().trim();
			if (!text.isEmpty()) {
				master.pushEvent(new TtsRequest(text));
			}
		});
		testPanel.add(play);
		content.add(testPanel);
		return outer;
	}

	private void selectFolder(String dir) {
		if (dir.equals(player.getFolder().get())) {
			return;
		}
		player.getFolder().set(dir);
		folderField.setText(dir);
		refreshPacks();
		updateStatus();
	}

	private void refreshPacks() {
		Path current = currentFolder();
		List<Path> packs = current == null ? List.of() : VoicePack.listPacks(current.getParent());
		refreshingPacks = true;
		try {
			packCombo.removeAllItems();
			packs.forEach(packCombo::addItem);
			// List.of() の contains は null を渡すと例外になるので、フォルダ未設定のときは先に除く
			packCombo.setSelectedItem(current != null && packs.contains(current) ? current : null);
			packCombo.setEnabled(packs.size() > 1);
		}
		finally {
			refreshingPacks = false;
		}
	}

	private Path currentFolder() {
		String dir = player.getFolder().get();
		if (dir == null || dir.isBlank()) {
			return null;
		}
		try {
			return Path.of(dir).toAbsolutePath().normalize();
		}
		catch (InvalidPathException e) {
			// 設定ファイルを手で書き換えた場合などは、一覧を出さないだけにする
			return null;
		}
	}

	private void updateStatus() {
		if (status == null) {
			return;
		}
		boolean ja = "ja".equals(HubSettings.get().getLanguage());
		VoicePack pack = player.getPack();
		String text;
		if (pack != null) {
			String credit = pack.getCredit().isBlank() ? "" : " / " + pack.getCredit();
			text = ja
					? String.format("「%s」を読み込みました（音声 %d 件、対応する読み上げ %d 件）%s", pack.getName(), pack.getClipCount(), pack.getCalloutCount(), credit)
					: String.format("Loaded \"%s\" (%d clips, %d callouts)%s", pack.getName(), pack.getClipCount(), pack.getCalloutCount(), credit);
		}
		else if (player.getLoadError() != null) {
			text = (ja ? "読み込めませんでした: " : "Failed to load: ") + player.getLoadError();
		}
		else {
			text = ja ? "ボイスパックのフォルダを選んでください。" : "Choose a voice pack folder.";
		}
		status.setText(text);
	}
}
