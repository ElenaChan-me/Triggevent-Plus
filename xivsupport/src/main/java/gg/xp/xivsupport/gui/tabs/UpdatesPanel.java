package gg.xp.xivsupport.gui.tabs;

import gg.xp.xivsupport.gui.TitleBorderFullsizePanel;
import gg.xp.xivsupport.gui.util.GuiUtil;
import gg.xp.xivsupport.persistence.PersistenceProvider;
import gg.xp.xivsupport.persistence.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * 日本語版の更新画面。
 * 本家の自動更新は本家の配布ファイルで上書きしてしまうため使わず、XIVToolHub から更新してもらう。
 */
public class UpdatesPanel extends TitleBorderFullsizePanel implements TabAware {
	private static final Logger log = LoggerFactory.getLogger(UpdatesPanel.class);
	private static final String UPDATE_GUIDE = "<html>この日本語版は本家の自動更新を使用しません。<br>"
			+ "更新は XIVToolHub の「最新版を確認」から行ってください。<br>"
			+ "（本家の更新を適用すると、日本語化と最適化が元に戻ります）</html>";

	public UpdatesPanel(PersistenceProvider pers, UpdaterConfig updateConfig) {
		super("更新");
		File installDir;
		try {
			installDir = Platform.getInstallDir();
		}
		catch (Throwable e) {
			log.error("Error setting up updates tab", e);
			add(new JLabel(UPDATE_GUIDE));
			return;
		}
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.weighty = 0;
		add(new JLabel(UPDATE_GUIDE), c);
		c.gridy++;
		add(new JLabel("インストール先: " + installDir), c);
		c.gridy++;
		JButton openInstallDirButton = new JButton("インストール先を開く");
		openInstallDirButton.addActionListener(l -> GuiUtil.openFile(installDir));
		add(openInstallDirButton, c);
		c.gridy++;
		c.weighty = 1;
		add(Box.createGlue(), c);
		// アドオン管理の「適用」から呼ばれても、本家の更新処理は起動しない
		updateConfig.setUpdateRunnable(() -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
				SwingUtilities.getWindowAncestor(this),
				"日本語版では本家の更新処理を使用しないため、アドオンの反映や更新は行えません。\n更新は XIVToolHub から行ってください。",
				"更新",
				JOptionPane.INFORMATION_MESSAGE)));
	}

	@Override
	public boolean hasWarning() {
		return false;
	}
}
