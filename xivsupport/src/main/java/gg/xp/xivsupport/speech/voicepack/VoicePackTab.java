package gg.xp.xivsupport.speech.voicepack;

import gg.xp.reevent.events.EventMaster;
import gg.xp.reevent.scan.ScanMe;
import gg.xp.xivsupport.gui.TitleBorderFullsizePanel;
import gg.xp.xivsupport.gui.WrapLayout;
import gg.xp.xivsupport.gui.extra.PluginTab;
import gg.xp.xivsupport.gui.i18n.HubSettings;
import gg.xp.xivsupport.persistence.gui.BooleanSettingGui;
import gg.xp.xivsupport.persistence.gui.IntSettingSpinner;
import gg.xp.xivsupport.speech.TtsRequest;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * ボイスパックの設定と試し再生。
 */
@ScanMe
public class VoicePackTab implements PluginTab {

	private final VoicePackPlayer player;
	private final EventMaster master;
	private JLabel status;

	public VoicePackTab(VoicePackPlayer player, EventMaster master) {
		this.player = player;
		this.master = master;
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

		JPanel folderPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));
		folderPanel.add(new JLabel("Voice pack folder"));
		JTextField folderField = new JTextField(player.getFolder().get(), 40);
		folderField.setEditable(false);
		folderPanel.add(folderField);
		JButton browse = new JButton("Browse...");
		browse.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser(player.getFolder().get().isBlank() ? null : new File(player.getFolder().get()));
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (chooser.showOpenDialog(outer) == JFileChooser.APPROVE_OPTION) {
				player.getFolder().set(chooser.getSelectedFile().getAbsolutePath());
				folderField.setText(player.getFolder().get());
				updateStatus();
			}
		});
		folderPanel.add(browse);
		JButton reload = new JButton("Reload");
		reload.addActionListener(e -> {
			player.reload();
			updateStatus();
		});
		folderPanel.add(reload);
		content.add(folderPanel);

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
